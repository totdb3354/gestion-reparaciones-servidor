package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.FilaItems;
import com.reparaciones.servidor.nsys.LectorItems;
import com.reparaciones.servidor.nsys.LectorMaestro;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Ensayo del cruce con copias reales (spec pieza 2 §10), sin red ni base de datos: solo con ENSAYO_PEDIDOS. Escribe el
 *  resumen en ENSAYO_PEDIDOS_SALIDA; nada de esto va al repo. */
@EnabledIfEnvironmentVariable(named = "ENSAYO_PEDIDOS", matches = ".+")
class EnsayoPedidosTest {

    @Test void cruzaLasCopiasRealesYEscribeElResumen() throws Exception {
        Path dir = Path.of(System.getenv("ENSAYO_PEDIDOS"));
        List<PedidoShippyPro> shippy = new ArrayList<>();
        List<String> sinFecha = new ArrayList<>();
        for (JsonNode o : new ObjectMapper().readTree(dir.resolve("shippypro.json").toFile()).path("Orders")) {
            try {
                shippy.add(ClienteShippyPro.pedido(o));
            } catch (IllegalArgumentException e) {
                sinFecha.add(o.path("transaction_id").asText("(sin número)"));
            }
        }
        List<FilaPedidos2026> p2026 = LectorPedidos2026.filas(tabla(dir.resolve("pedidos2026.xlsx"), "PEDIDOS 2026", 4000));
        List<FilaSimulador> sim = LectorSimulador.filas(tabla(dir.resolve("simulador.xlsx"), "Registro actividad", 4000));
        Map<String, MovilNsys> moviles = new HashMap<>();
        try (InputStream in = Files.newInputStream(dir.resolve("items.xlsx"))) {
            for (FilaItems f : LectorItems.leer(in)) {
                EstadoMovil e = "Released".equals(f.status()) ? EstadoMovil.VENDIDO : EstadoMovil.STOCK;
                moviles.put(NsysDAO.claveCruce(f.imeiSn()), new MovilNsys(f.imeiSn(), f.batch(), f.goods(), f.grade(), f.status(),
                        f.location(), f.supplier(), f.receivingDate(), f.soldDate(), null, null, null, null, null, null, false,
                        f.location(), f.location(), e, null, null));
            }
        }
        AceptaSku acepta;
        try (InputStream in = Files.newInputStream(dir.resolve("maestro.xlsx"))) {
            acepta = new AceptaSku(LectorMaestro.leer(in).productos());
        }
        String canales = System.getenv().getOrDefault("ENSAYO_PEDIDOS_CANALES", "");
        CrucePedidos.Resultado r = CrucePedidos.cruzar(new CrucePedidos.Entrada(shippy, Canal.deAjuste(canales), p2026, sim, acepta, moviles));

        Map<String, int[]> porCanal = new TreeMap<>();
        for (PedidoLeido p : r.pedidos()) {
            int[] c = porCanal.computeIfAbsent(p.canal().name(), k -> new int[4]);
            c[0]++;
            for (PedidoLeido.Unidad u : p.unidades()) {
                if (u.motivoCierre() != null) continue;
                c[1]++;
                if (u.imei() != null) c[2]++;
                if (u.sinPedidoConcreto()) c[3]++;
            }
        }
        StringBuilder s = new StringBuilder("Ensayo de pedidos\n\nCanal · pedidos · unidades abiertas · con IMEI · sin pedido concreto\n");
        porCanal.forEach((k, c) -> s.append(String.format("%s · %d · %d · %d · %d%n", k, c[0], c[1], c[2], c[3])));
        s.append("\nPedidos de ShippyPro sin fecha válida (saltados): ").append(sinFecha)
                .append("\nSKU desconocidos de ShippyPro: ").append(r.desconocidosShippyPro())
                .append("\nSKU desconocidos del Simulador: ").append(r.desconocidosSimulador())
                .append("\nFilas de PEDIDOS 2026 leídas: ").append(p2026.size())
                .append("\nLíneas del Simulador leídas: ").append(sim.size()).append('\n');
        Files.writeString(Path.of(System.getenv("ENSAYO_PEDIDOS_SALIDA")), s.toString());
    }

    /** Las últimas {@code filas} filas de una pestaña, como las daría Sheets: números (las fechas, como número de serie) y textos. */
    private static TablaHoja tabla(Path xlsx, String pestana, int filas) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(Files.newInputStream(xlsx))) {
            Sheet hoja = libro.getSheet(pestana);
            Map<String, Integer> columnas = new HashMap<>();
            Row cab = hoja.getRow(0);
            for (Cell c : cab) if (c.getCellType() == CellType.STRING) columnas.putIfAbsent(c.getStringCellValue().strip(), c.getColumnIndex());
            int ultima = hoja.getLastRowNum();
            List<TablaHoja.Fila> lista = new ArrayList<>();
            for (int i = Math.max(1, ultima - filas + 1); i <= ultima; i++) {
                Row row = hoja.getRow(i);
                if (row == null) continue;
                List<JsonNode> celdas = new ArrayList<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell c = row.getCell(j);
                    celdas.add(c == null ? null : switch (c.getCellType()) {
                        case NUMERIC -> DoubleNode.valueOf(c.getNumericCellValue());
                        case STRING -> TextNode.valueOf(c.getStringCellValue());
                        case FORMULA -> c.getCachedFormulaResultType() == CellType.NUMERIC ? DoubleNode.valueOf(c.getNumericCellValue())
                                : TextNode.valueOf(c.getStringCellValue());
                        default -> null;
                    });
                }
                lista.add(new TablaHoja.Fila(i + 1, celdas));
            }
            return new TablaHoja(ultima, columnas, lista);
        }
    }
}
