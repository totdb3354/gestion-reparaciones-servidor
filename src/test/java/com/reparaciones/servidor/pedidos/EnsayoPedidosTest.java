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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<FilaPedidos2026> p2026 = LectorPedidos2026.filas(tabla(dir.resolve("pedidos2026.xlsx"), "PEDIDOS 2026", LectorPedidos2026.FORMATO, 4000));
        List<FilaSimulador> sim = LectorSimulador.filas(tabla(dir.resolve("simulador.xlsx"), "Registro actividad", LectorSimulador.FORMATO, 4000));
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
        List<FilaPedidos2026> conNumero = p2026.stream()
                .filter(f -> CrucePedidos.canalAmazon(f.plataforma()) != null && f.pedidoAmazon() != null).toList();
        Set<String> pendientes = new HashSet<>();
        for (PedidoShippyPro p : shippy) {
            Canal canal = Canal.deAjuste(canales).get(p.marketplace());
            if (canal == Canal.AMAZON || canal == Canal.AMAZON_STOCKROOM) pendientes.add(canal + "|" + p.numero().strip());
        }
        StringBuilder s = new StringBuilder("Ensayo de pedidos\n\nCanal · pedidos · unidades abiertas · con IMEI · sin pedido concreto\n");
        porCanal.forEach((k, c) -> s.append(String.format("%s · %d · %d · %d · %d%n", k, c[0], c[1], c[2], c[3])));
        s.append("\nPedidos de ShippyPro sin fecha válida (saltados): ").append(sinFecha)
                .append("\nSKU desconocidos de ShippyPro: ").append(r.desconocidosShippyPro())
                .append("\nSKU desconocidos del Simulador: ").append(r.desconocidosSimulador())
                .append("\nFilas de PEDIDOS 2026 leídas: ").append(p2026.size())
                .append("\nFilas de Amazon / Stockroom: ").append(p2026.stream().filter(f -> CrucePedidos.canalAmazon(f.plataforma()) == Canal.AMAZON).count())
                .append(" / ").append(p2026.stream().filter(f -> CrucePedidos.canalAmazon(f.plataforma()) == Canal.AMAZON_STOCKROOM).count())
                .append("\nFilas de Amazon/Stockroom con «Pedido Amazon»: ").append(conNumero.size())
                .append(" (de un pedido pendiente: ").append(conNumero.stream().filter(f -> pendientes.contains(clave(f))).count()).append(')')
                .append("\nLíneas del Simulador leídas: ").append(sim.size()).append('\n');
        Files.writeString(Path.of(System.getenv("ENSAYO_PEDIDOS_SALIDA")), s.toString());
    }

    private static String clave(FilaPedidos2026 f) {
        return CrucePedidos.canalAmazon(f.plataforma()) + "|" + f.pedidoAmazon().strip();
    }

    /** Las últimas {@code filas} filas de una pestaña, como las daría Sheets: números (las fechas, como número de serie) y textos. */
    private static TablaHoja tabla(Path xlsx, String pestana, FormatoHoja formato, int filas) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(Files.newInputStream(xlsx))) {
            Sheet hoja = libro.getSheet(pestana);
            Map<String, Integer> columnas = new HashMap<>();
            Row cab = hoja.getRow(0);
            for (Cell c : cab) if (c.getCellType() == CellType.STRING) columnas.putIfAbsent(c.getStringCellValue().strip(), c.getColumnIndex());
            int colClave = columnas.get(formato.clave());
            int ultima = 0;
            for (int i = hoja.getLastRowNum(); i >= 1; i--) {
                Row row = hoja.getRow(i);
                if (formato.cuenta().test(row == null ? null : TablaHoja.texto(valor(row.getCell(colClave))))) {
                    ultima = i;
                    break;
                }
            }
            List<TablaHoja.Fila> lista = new ArrayList<>();
            for (int i = Math.max(1, ultima - filas + 1); i <= ultima; i++) {
                Row row = hoja.getRow(i);
                if (row == null) continue;
                List<JsonNode> celdas = new ArrayList<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    celdas.add(valor(row.getCell(j)));
                }
                lista.add(new TablaHoja.Fila(i + 1, celdas));
            }
            return new TablaHoja(ultima, columnas, lista);
        }
    }

    private static JsonNode valor(Cell c) {
        return c == null ? null : switch (c.getCellType()) {
            case NUMERIC -> DoubleNode.valueOf(c.getNumericCellValue());
            case STRING -> TextNode.valueOf(c.getStringCellValue());
            case FORMULA -> c.getCachedFormulaResultType() == CellType.NUMERIC ? DoubleNode.valueOf(c.getNumericCellValue())
                    : TextNode.valueOf(c.getStringCellValue());
            default -> null;
        };
    }
}
