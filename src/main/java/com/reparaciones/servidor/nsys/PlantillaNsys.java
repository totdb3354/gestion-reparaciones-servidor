package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Las dos plantillas para subir a NSYS (spec 0.9.22 §4): la «Upload batch with IMEI» vacía de NSYS (recurso
 *  nsys/plantilla-nsys-vacia.xlsx), con su hoja y sus filas 1 a 6 tal cual, y un móvil por fila desde la 7, columnas A-N. Lo que
 *  hay que rellenar a mano va en rojo; un color sacado del maestro (NSYS nunca lo ha escrito), en amarillo. */
final class PlantillaNsys {

    static final String RECURSO = "/nsys/plantilla-nsys-vacia.xlsx";
    /** La fila 7 de Excel (0 es la 1): la primera de datos; su formato por columna vale para todas. */
    static final int PRIMERA_FILA = 6;
    static final int COLUMNAS = 14;
    static final String FABRICANTE = "Apple";
    static final String GRUPO = "iPhone";
    static final String LOTE_COLOR = "COLOR";
    static final String LOTE_ALTA = "AJUSTE";
    /** Status 0: «In process» (NSYS lo deja así al subirlo). */
    static final int ESTADO = 0;
    /** Rojo: rellenar en el Excel antes de subir. Amarillo: color sacado del maestro, compruébalo. */
    static final byte[] FONDO_ROJO = {(byte) 0xFD, (byte) 0xE8, (byte) 0xE8};
    static final byte[] TEXTO_ROJO = {(byte) 0xB9, (byte) 0x1C, (byte) 0x1C};
    static final byte[] FONDO_AMARILLO = {(byte) 0xFF, (byte) 0xF7, (byte) 0xCC};

    /** Una fila de la plantilla de color: todo como lo tiene hoy NSYS menos el color. */
    record FilaColor(String imeiSn, String modelo, String capacidad, String color, boolean compruebalo, String grado,
                     BigDecimal coste, String proveedor) {

        /** Del lote actual del móvil: modelo y capacidad de su Goods, el color del producto a poner, su grado de hoy, su coste y
         *  su proveedor tal cual. Va a comprobar si el color es del maestro o si el producto lleva la eSIM detrás de la
         *  capacidad, que la plantilla no sabe dónde poner. */
        static FilaColor de(MovilNsys m, String producto, boolean compruebalo, BigDecimal coste) {
            return new FilaColor(m.imeiSn(), GrafiasNsys.modeloDelGoods(m.goods()), GrafiasNsys.capacidadDelGoods(m.goods()),
                    GrafiasNsys.colorDelGoods(producto), compruebalo || GrafiasNsys.esimDetras(producto), m.grade(), coste, m.supplier());
        }
    }

    /** Una fila de la plantilla de altas: el IMEI y el modelo del taller («iPhone 14», a comprobar: el taller no sabe la eSIM). */
    record FilaAlta(String imeiSn, String modelo) {}

    private enum Marca { NADA, ROJO, AMARILLO }

    private PlantillaNsys() {}

    static byte[] color(List<FilaColor> filas) {
        return escribir(filas, (hoja, f) -> {
            hoja.texto(0, f.imeiSn(), Marca.NADA);
            hoja.texto(1, FABRICANTE, Marca.NADA);
            hoja.texto(2, f.modelo(), Marca.NADA);
            hoja.texto(3, f.capacidad(), Marca.NADA);
            hoja.texto(4, f.color(), f.compruebalo() ? Marca.AMARILLO : Marca.NADA);
            hoja.texto(5, f.grado(), Marca.NADA);
            if (f.coste() == null) hoja.vacia(6, Marca.ROJO);
            else hoja.numero(6, f.coste().doubleValue());
            hoja.texto(7, f.proveedor(), Marca.NADA);
            hoja.texto(8, LOTE_COLOR, Marca.NADA);
            hoja.texto(9, GRUPO, Marca.NADA);
            hoja.numero(13, ESTADO);
        });
    }

    static byte[] altas(List<FilaAlta> filas) {
        return escribir(filas, (hoja, f) -> {
            hoja.texto(0, f.imeiSn(), Marca.NADA);
            hoja.texto(1, FABRICANTE, Marca.NADA);
            hoja.texto(2, f.modelo(), Marca.ROJO);
            for (int c = 3; c <= 7; c++) hoja.vacia(c, Marca.ROJO);
            hoja.texto(8, LOTE_ALTA, Marca.NADA);
            hoja.texto(9, GRUPO, Marca.NADA);
            hoja.numero(13, ESTADO);
        });
    }

    /** El nombre del modelo del taller (Telefono.MODELO, un código del ERP como «13pro»), calco de traducirModelo de la web
     *  (src/shared/lib/modelos.ts): «iPhone 13 Pro». null sin código. */
    static String nombreModelo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        String limpio = codigo.strip();
        String especial = ESPECIALES.get(limpio);
        if (especial != null) return especial;
        String numero = limpio.replaceAll("[^0-9]", "");
        String variante = limpio.replaceAll("[0-9]", "");
        return "iPhone " + numero + SUFIJOS.getOrDefault(variante, "");
    }

    private static final Map<String, String> ESPECIALES = Map.of("se2020", "iPhone SE 2020", "x", "iPhone X", "xr", "iPhone XR",
            "xs", "iPhone XS", "xsmax", "iPhone XS Max", "6s", "iPhone 6S", "6splus", "iPhone 6S Plus", "air", "iPhone Air");
    private static final Map<String, String> SUFIJOS = Map.of("plus", " Plus", "mini", " Mini", "pro", " Pro",
            "promax", " Pro Max", "e", "e");

    private static <T> byte[] escribir(List<T> filas, BiConsumer<Hoja, T> rellenar) {
        try (InputStream vacia = PlantillaNsys.class.getResourceAsStream(RECURSO)) {
            if (vacia == null) throw new IllegalStateException("Falta la plantilla vacía de NSYS: " + RECURSO);
            try (XSSFWorkbook libro = new XSSFWorkbook(vacia); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
                Hoja hoja = new Hoja(libro);
                for (int i = 0; i < filas.size(); i++) {
                    hoja.fila(i);
                    rellenar.accept(hoja, filas.get(i));
                }
                libro.write(salida);
                return salida.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir la plantilla de NSYS", e);
        }
    }

    /** La hoja de la plantilla: cada celda con el formato de la fila 7 en su columna (texto, número…) y el color de la marca
     *  encima. Los estilos con marca se crean una vez por columna y marca. */
    private static final class Hoja {

        private final XSSFWorkbook libro;
        private final XSSFSheet hoja;
        private final CellStyle[] base = new CellStyle[COLUMNAS];
        private final Map<String, CellStyle> marcados = new HashMap<>();
        private XSSFRow fila;

        Hoja(XSSFWorkbook libro) {
            this.libro = libro;
            this.hoja = libro.getSheetAt(0);
            XSSFRow modelo = hoja.getRow(PRIMERA_FILA);
            for (int c = 0; c < COLUMNAS; c++) {
                XSSFCell celda = modelo == null ? null : modelo.getCell(c);
                base[c] = celda == null ? null : celda.getCellStyle();
            }
        }

        void fila(int n) {
            XSSFRow existente = hoja.getRow(PRIMERA_FILA + n);
            fila = existente != null ? existente : hoja.createRow(PRIMERA_FILA + n);
        }

        /** Un texto; vacío o null, la celda queda vacía en rojo (hay que rellenarla). */
        void texto(int columna, String valor, Marca marca) {
            if (valor == null || valor.isBlank()) {
                vacia(columna, Marca.ROJO);
                return;
            }
            celda(columna, marca).setCellValue(valor);
        }

        void numero(int columna, double valor) {
            celda(columna, Marca.NADA).setCellValue(valor);
        }

        void vacia(int columna, Marca marca) {
            celda(columna, marca).setBlank();
        }

        private XSSFCell celda(int columna, Marca marca) {
            XSSFCell c = fila.getCell(columna) != null ? fila.getCell(columna) : fila.createCell(columna);
            CellStyle estilo = estilo(columna, marca);
            if (estilo != null) c.setCellStyle(estilo);
            return c;
        }

        private CellStyle estilo(int columna, Marca marca) {
            if (marca == Marca.NADA) return base[columna];
            return marcados.computeIfAbsent(columna + ":" + marca, k -> {
                XSSFCellStyle s = libro.createCellStyle();
                if (base[columna] != null) s.cloneStyleFrom(base[columna]);
                s.setFillForegroundColor(new XSSFColor(marca == Marca.ROJO ? FONDO_ROJO : FONDO_AMARILLO, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                if (marca == Marca.ROJO) {
                    XSSFFont original = s.getFont();
                    XSSFFont rojo = libro.createFont();
                    rojo.setFontName(original.getFontName());
                    rojo.setFontHeight(original.getFontHeight());
                    rojo.setColor(new XSSFColor(TEXTO_ROJO, null));
                    s.setFont(rojo);
                }
                return s;
            });
        }
    }
}
