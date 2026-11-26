package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Locale;

/** Reglas de texto del espejo de NSYS (spec pieza 1 §4.4 y §7): claves del maestro, cajas, modelos, eSIM y batería. */
public final class Normalizacion {

    private Normalizacion() {}

    /** Clave del maestro: «Goods» + «Grade» sin espacios y en minúsculas, igual que el verificador de SKU de las
     *  automatizaciones. Cortada al ancho de Nsys_producto.CLAVE aquí, en un solo sitio: el maestro al leerse y el
     *  importador al buscar cortan igual, y la clave que vuelve de la base de datos sigue encontrándose. */
    public static String clave(String goods, String grade) {
        String texto = (goods == null ? "" : goods) + (grade == null ? "" : grade);
        return AnchosNsys.corta(texto.replaceAll("\\s+", "").toLowerCase(Locale.ROOT), AnchosNsys.CLAVE);
    }

    /** Clave de una fila del maestro a partir de su «Product Name Calidad». */
    public static String clave(String productNameCalidad) {
        return clave(productNameCalidad, null);
    }

    /** La localizacion de NSYS que hace de «sin localizacion» (0.9.48.3, Imad 26/11): NSYS no deja mover un movil a «sin
     *  localizacion», asi que para devolverle a donde estaba se usa esta, que cuenta como stock y no significa nada mas. */
    public static final String SIN_LOCALIZACION = "--------------";

    /** Caja de NSYS tal como se busca en Nsys_ubicacion: sin espacios a los lados; sin caja = "". {@link #SIN_LOCALIZACION} es
     *  lo mismo que sin caja («En revisión»). */
    public static String caja(String location) {
        String caja = location == null ? "" : location.strip();
        return SIN_LOCALIZACION.equals(caja) ? "" : caja;
    }

    /** Código de modelo del ERP a partir de la columna «Modelo» del maestro: en minúsculas, sin espacios y sin «eSIM»
     *  ("13 PRO" → 13pro, "16 e" → 16e, "SE2020" → se2020). null si el código no está en el catálogo del ERP: entonces el
     *  modelo se escribe a mano, como cuando IMEIcheck no lo sabía. */
    public static String modeloErp(String modeloMaestro) {
        if (modeloMaestro == null) return null;
        String codigo = modeloMaestro.toLowerCase(Locale.ROOT).replace("esim", "").replaceAll("\\s+", "");
        return CatalogoPiezas.esModelo(codigo) ? codigo : null;
    }

    /** NSYS escribe «eSIM» en el nombre del producto de los móviles solo eSIM. */
    public static boolean esEsim(String goods) {
        return goods != null && goods.toLowerCase(Locale.ROOT).contains("esim");
    }

    /** El grado sin la batería: "A-100" → "A-", "C100" → "C". La batería va en su propia columna. */
    public static String gradoSinBateria(String grade) {
        if (grade == null) return "";
        return bateriaNueva(grade) ? grade.substring(0, grade.length() - 3) : grade;
    }

    /** El «100» del final del grado de NSYS es la batería nueva al 100 %. */
    public static boolean bateriaNueva(String grade) {
        return grade != null && grade.length() > 3 && grade.endsWith("100");
    }


    /** Dos textos de NSYS o del maestro iguales sin espacios a los lados; null y vacío cuentan igual. Distingue mayúsculas,
     *  como NSYS (0.9.21, SKU del taller: grados, colores y SKU). */
    public static boolean mismoTexto(String a, String b) {
        return (a == null ? "" : a.strip()).equals(b == null ? "" : b.strip());
    }
}
