package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NsysDAOTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 16, 9, 1);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NsysDAO dao = new NsysDAO(jdbc);

    private static MovilNsys movil(int n) {
        return new MovilNsys(String.format("3500000000%05d", n), "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null, "i13128AzulA", "13", "13", "128GB",
                "Azul", "A", false, "Listos", "Listos", EstadoMovil.STOCK, HOY, HOY);
    }

    @SuppressWarnings("unchecked")
    @Test void aplicarGuardaPorLotesDeMilYDespuesLosEventos() {
        List<MovilNsys> moviles = new ArrayList<>();
        for (int i = 0; i < 2500; i++) moviles.add(movil(i));
        EventoNsys e = new EventoNsys("350000000000001", "BT-9001", HOY, false, TipoEvento.UBICACION, "Taller · Glass",
                "Listos", "CAJA LISTOS");
        dao.aplicar(new ResultadoComparacion(moviles, List.of(e), 0, Set.of()), 7);

        ArgumentCaptor<List<Object[]>> lotes = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(3)).batchUpdate(eq(NsysDAO.SQL_GUARDAR_MOVIL), lotes.capture());
        assertEquals(List.of(1000, 1000, 500), lotes.getAllValues().stream().map(List::size).toList());
        ArgumentCaptor<List<Object[]>> eventos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_EVENTO), eventos.capture());
        assertArrayEquals(new Object[]{"350000000000001", "BT-9001", Timestamp.valueOf(HOY), false, "UBICACION",
                "Taller · Glass", "Listos", "CAJA LISTOS", 7}, eventos.getValue().get(0));
    }

    @Test void losArgumentosDeUnMovilVanEnElOrdenDeLasColumnas() {
        assertArrayEquals(new Object[]{"350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", java.sql.Date.valueOf(LocalDate.of(2026, 9, 2)), null, "i13128AzulA", "13",
                "13", "128GB", "Azul", "A", false, "Listos", "Listos", "STOCK", Timestamp.valueOf(HOY),
                Timestamp.valueOf(HOY)}, NsysDAO.argumentos(movil(1)));
    }

    @SuppressWarnings("unchecked")
    @Test void reemplazarProductosBorraYCargaTodo() {
        dao.reemplazarProductos(List.of(new ProductoMaestro("clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true,
                "Apple iPhone 13 128GB Blue")));
        verify(jdbc).update("DELETE FROM Nsys_producto");
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_PRODUCTO), lote.capture());
        assertArrayEquals(new Object[]{"clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true,
                "Apple iPhone 13 128GB Blue"}, lote.getValue().get(0));
        assertTrue(NsysDAO.SQL_PRODUCTO.contains("ES_ESIM, NOMBRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), NsysDAO.SQL_PRODUCTO);
    }

    @SuppressWarnings("unchecked")
    @Test void cargarProductosLeeElNombre() throws SQLException {
        dao.cargarProductos();
        ArgumentCaptor<RowMapper<ProductoMaestro>> mapeo = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(contains(", NOMBRE FROM Nsys_producto"), mapeo.capture());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CLAVE")).thenReturn("clave");
        when(rs.getString("NOMBRE")).thenReturn("Apple iPhone 13 128GB Blue");
        assertEquals("Apple iPhone 13 128GB Blue", mapeo.getValue().mapRow(rs, 0).nombre());
    }

    @Test void productosSinNombreMiraSiNingunoLoTiene() {
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class)).thenReturn(true);
        assertTrue(dao.productosSinNombre());
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class)).thenReturn(null);
        assertFalse(dao.productosSinNombre());
        assertEquals("SELECT COUNT(*) > 0 AND COALESCE(SUM(NOMBRE IS NOT NULL), 0) = 0 FROM Nsys_producto",
                NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE);
    }

    @Test void empezarImportacionDevuelveElIdGenerado() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 42));
            return 1;
        });
        assertEquals(42, dao.empezarImportacion("Items_2026.10.16_09.01.xlsx", HOY, "ACTUAL", HOY.plusMinutes(3)));
    }

    @Test void cerrarHistoricoInsertaLosEventosYMarcaOkEnLaMismaLlamada() {
        EventoNsys e = new EventoNsys("350000000000001", "BT-9001", HOY, false, TipoEvento.UBICACION, "Taller · Glass",
                "Listos", "CAJA LISTOS");
        dao.cerrarHistorico(List.of(e), 42, 70, HOY);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_EVENTO), anyList());
        verify(jdbc).update(NsysDAO.SQL_TERMINAR, "OK", 70, 1, null, Timestamp.valueOf(HOY), 42);
    }

    @Test void terminarImportacionRecortaElMensaje() {
        dao.terminarImportacion(42, "ERROR", 0, 0, "x".repeat(600), HOY);
        verify(jdbc).update(NsysDAO.SQL_TERMINAR, "ERROR", 0, 0, "x".repeat(500), Timestamp.valueOf(HOY), 42);
    }

    @SuppressWarnings("unchecked")
    @Test void ultimaImportacionFiltraPorOrigenYResultados() {
        when(jdbc.query(contains("ORDER BY FECHA_EXPORTACION DESC"), any(RowMapper.class), eq("ACTUAL"), eq("OK"), eq("RECHAZADO")))
                .thenReturn(List.of(new Importacion(3, "Items_2026.10.16_09.01.xlsx", HOY, "OK", 70000, null)));
        assertEquals(Optional.of(new Importacion(3, "Items_2026.10.16_09.01.xlsx", HOY, "OK", 70000, null)),
                dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "OK")));
        assertEquals(Optional.empty(), dao.ultimaImportacion("MAESTRO", Set.of("OK")));
    }

    @Test void rellenarModelosDelTallerConElEspejo() {
        when(jdbc.update(NsysDAO.SQL_RELLENAR_MODELOS)).thenReturn(4);
        assertEquals(4, dao.rellenarModelosTelefono());
    }

    @Test void rellenarModelosSoloVaciosDe15DigitosQueYaExisten() {
        String sql = NsysDAO.SQL_RELLENAR_MODELOS;
        assertTrue(sql.startsWith("UPDATE Telefono t JOIN ("), sql);
        assertTrue(sql.contains("WHERE (t.MODELO IS NULL OR t.MODELO = '')"), sql);
        assertTrue(sql.contains("AND t.IMEI REGEXP '^[0-9]{15}$'"), sql);
    }

    @SuppressWarnings("unchecked")
    @Test void modeloDeUnImeiQueNoEstaEsNull() {
        when(jdbc.query(contains("FROM Nsys_movil WHERE IMEI_SN = ? AND MODELO IS NOT NULL"), any(RowMapper.class), eq("350000000000001")))
                .thenReturn(List.of());
        assertNull(dao.modeloDe("350000000000001"));
    }

    // En la primera carga todos los lotes tienen el mismo VISTO_PRIMERO, y el texto del lote no va en orden de fechas:
    // desempata la fecha de recepción y, al final, el lote.
    private static final String MAS_RECIENTE_PRIMERO = " ORDER BY VISTO_PRIMERO DESC, RECEIVING_DATE DESC, BATCH DESC";

    @SuppressWarnings("unchecked")
    @Test void elModeloSaleDelLoteMasReciente() {
        dao.modeloDe("350000000000001");
        verify(jdbc).query(endsWith(MAS_RECIENTE_PRIMERO + " LIMIT 1"), any(RowMapper.class), eq("350000000000001"));
    }

    @SuppressWarnings("unchecked")
    @Test void losLotesDeUnImeiVanDelMasRecienteAlMasAntiguo() {
        dao.porImei("350000000000001");
        verify(jdbc).query(endsWith(MAS_RECIENTE_PRIMERO), any(RowMapper.class), eq("350000000000001"));
    }

    @SuppressWarnings("unchecked")
    @Test void buscarLimpiaElTextoYSinTextoNoConsulta() {
        dao.buscar(" 35%_00 ", 10);
        verify(jdbc).query(contains("WHERE IMEI_SN LIKE ?"), any(RowMapper.class), eq("%3500%"), eq(10));
        clearInvocations(jdbc);
        assertTrue(dao.buscar("%%", 10).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test void lasHorasDelTallerPasanDeUtcAMadrid() {
        // La BD del taller guarda UTC; el espejo y su API van en hora de Madrid (verano +2, invierno +1).
        assertEquals(LocalDateTime.of(2026, 9, 12, 13, 1), NsysDAO.utcAMadrid(Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 11, 1))));
        assertEquals(LocalDateTime.of(2026, 1, 12, 12, 1), NsysDAO.utcAMadrid(Timestamp.valueOf(LocalDateTime.of(2026, 1, 12, 11, 1))));
        assertNull(NsysDAO.utcAMadrid(null));
    }

    // ---------------------------------------------------------------- 0.9.18: trabajos abiertos (spec §6.1)

    @Test void lasAsignacionesAbiertasSonLasAConFechaFinNulaYConElTecnico() {
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("r.ID_REP LIKE 'A%'"), "A, AG y AP");
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("r.FECHA_FIN IS NULL"));
        assertTrue(NsysDAO.SQL_TRABAJOS_ABIERTOS.contains("JOIN Tecnico t ON t.ID_TEC = r.ID_TEC"));
    }

    @SuppressWarnings("unchecked")
    @Test void lasAsignacionesAbiertasDeTodoElTallerVanEnUnaConsulta() {
        assertTrue(dao.trabajosAbiertos().isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_TRABAJOS_ABIERTOS + NsysDAO.ORDEN_TRABAJOS), any(RowMapper.class));
    }

    private static ResultSet filaDeReparacion(String imei, String idRep, LocalDateTime asignada, LocalDateTime entregada)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn(imei);
        when(rs.getString("ID_REP")).thenReturn(idRep);
        when(rs.getString("NOMBRE")).thenReturn("Técnico 3");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(Timestamp.valueOf(asignada));
        when(rs.getTimestamp("ENTREGADO_AT")).thenReturn(entregada == null ? null : Timestamp.valueOf(entregada));
        return rs;
    }

    @Test void unaAsignacionAbiertaLlevaSuCategoriaYSusHorasEnMadrid() throws SQLException {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 16, 8, 30);
        NsysDAO.FilaTrabajoAbierto glass = NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "AG20261016_1", utc, utc.plusHours(1)), 0);
        assertEquals("350000000000001", glass.imei());
        assertEquals(new TrabajoAbierto("GLASS", "Técnico 3", LocalDateTime.of(2026, 10, 16, 10, 30),
                LocalDateTime.of(2026, 10, 16, 11, 30)), glass.trabajo(), "UTC → Madrid (verano, +2)");
        assertEquals("PULIDO", NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "AP20261016_1", utc, null), 0).trabajo().categoria());
        TrabajoAbierto reparacion = NsysDAO.filaTrabajoAbierto(
                filaDeReparacion("350000000000001", "A20261016_1", utc, utc), 0).trabajo();
        assertEquals("REPARACION", reparacion.categoria());
        assertNull(reparacion.entregado(), "la entrega solo cuenta en la glass");
    }

    @Test void losTrabajosSeAgrupanPorImeiYUnNumeroDeSerieEnMinusculasCruza() {
        TrabajoAbierto r = new TrabajoAbierto("REPARACION", "Técnico 1", HOY, null);
        TrabajoAbierto g = new TrabajoAbierto("GLASS", "Técnico 3", HOY, null);
        Map<String, List<TrabajoAbierto>> mapa = NsysDAO.porImeiDelTaller(List.of(
                new NsysDAO.FilaTrabajoAbierto("350000000000001", r), new NsysDAO.FilaTrabajoAbierto("350000000000001", g),
                new NsysDAO.FilaTrabajoAbierto(" f2lxabc123 ", r)));
        assertEquals(List.of(r, g), mapa.get("350000000000001"), "reparación y glass: dos trabajos, en su orden");
        assertEquals(List.of(r), mapa.get(NsysDAO.claveCruce("F2LXABC123")));
        assertEquals("F2LXABC123", NsysDAO.claveCruce(" f2lxabc123 "));
        assertEquals("", NsysDAO.claveCruce(null));
    }

    @Test void losTrabajosDeUnosImeisVanEnTandasDeQuinientos() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.trabajosAbiertos(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(0).startsWith(NsysDAO.SQL_TRABAJOS_ABIERTOS + " AND r.IMEI IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).contains(" AND r.IMEI IN (?)" + NsysDAO.ORDEN_TRABAJOS), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.trabajosAbiertos(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    // ---------------------------------------------------------------- 0.9.18: comprobar una lista (spec §6.2)

    @Test void losLotesDeUnosImeisVanEnTandasYDelMasRecienteAlMasAntiguo() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.porImeis(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size());
        assertTrue(sql.get(0).contains("FROM Nsys_movil WHERE IMEI_SN IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).endsWith("IMEI_SN IN (?)" + MAS_RECIENTE_PRIMERO), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.porImeis(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void losGoodsContadosSeLeenDeUnaVez() {
        dao.goodsContados();
        verify(jdbc).query(eq(NsysDAO.SQL_GOODS_CONTADOS), any(RowMapper.class));
        assertEquals("SELECT MODELO_MAESTRO, GOODS, COUNT(*) AS N FROM Nsys_movil WHERE GOODS IS NOT NULL"
                + " GROUP BY MODELO_MAESTRO, GOODS", NsysDAO.SQL_GOODS_CONTADOS);
    }

    // ---------------------------------------------------------------- 0.9.18: lista de importaciones (spec §6.5)

    @SuppressWarnings("unchecked")
    @Test void lasImportacionesSonDeUnOrigenDeLaMasRecienteALaMasAntigua() {
        dao.importaciones("ACTUAL", 20);
        verify(jdbc).query(and(contains("FROM Nsys_importacion WHERE ORIGEN = ?"),
                        contains("ORDER BY FECHA_EXPORTACION DESC, ID_IMPORTACION DESC LIMIT ?")),
                any(RowMapper.class), eq("ACTUAL"), eq(20));
    }

    @Test void unaImportacionEnCursoNoTieneHoraDeFin() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_IMPORTACION")).thenReturn(9);
        when(rs.getString("FICHERO")).thenReturn("Items_2026.10.16_13.31.xlsx");
        when(rs.getTimestamp("FECHA_EXPORTACION")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 16, 13, 31)));
        when(rs.getString("RESULTADO")).thenReturn("EN_CURSO");
        when(rs.getTimestamp("EMPEZADA")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 16, 13, 36)));
        assertEquals(new RegistroImportacion(9, "Items_2026.10.16_13.31.xlsx", LocalDateTime.of(2026, 10, 16, 13, 31),
                "EN_CURSO", 0, 0, LocalDateTime.of(2026, 10, 16, 13, 36), null, null), NsysDAO.registroImportacion(rs, 0));
    }

    @SuppressWarnings("unchecked")
    @Test void elResumenDelHistoricoCuentaTodasLasHechasYLaFechaDeLaUltimaHecha() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<ResumenHistorico>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        dao.resumenHistorico();
        verify(jdbc).queryForObject(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("FROM Nsys_importacion WHERE ORIGEN = 'HISTORICO'"), sql.getValue());
        assertTrue(sql.getValue().contains("RESULTADO IN ('OK', 'RECHAZADO')"), sql.getValue());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("TOTAL")).thenReturn(3);
        when(rs.getInt("HECHOS")).thenReturn(2);
        when(rs.getTimestamp("ULTIMO")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2025, 5, 3, 22, 31)));
        assertEquals(new ResumenHistorico(3, 2, LocalDateTime.of(2025, 5, 3, 22, 31)), mapper.getValue().mapRow(rs, 0));
    }

    // ---------------------------------------------------------------- 0.9.21: el producto de cada color (spec §2.4)

    @SuppressWarnings("unchecked")
    @Test void elGoodsMasUsadoDeCadaColorDelModeloEnUnaConsulta() {
        assertTrue(dao.goodsMasUsados(movil(1)).isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_GOODS_POR_COLOR), any(RowMapper.class), eq("13"), eq("13"), eq("128GB"), eq(false));
        assertTrue(NsysDAO.SQL_GOODS_POR_COLOR.contains("WHERE MODELO <=> ? AND MODELO_MAESTRO = ? AND CAPACIDAD <=> ? AND ES_ESIM = ?"),
                NsysDAO.SQL_GOODS_POR_COLOR);
        assertTrue(NsysDAO.SQL_GOODS_POR_COLOR.endsWith(" GROUP BY COLOR, GOODS ORDER BY COLOR, N DESC, GOODS"),
                NsysDAO.SQL_GOODS_POR_COLOR);
    }

    @Test void deCadaColorValeElPrimeroQueLlega() {
        assertEquals(Map.of("Azul", "Apple iPhone 13 128GB Blue", "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"),
                NsysDAO.primeroDeCadaColor(List.of(new NsysDAO.GoodsDeColor("Azul", "Apple iPhone 13 128GB Blue"),
                        new NsysDAO.GoodsDeColor("Azul", "Apple iPhone 13 128GB Azul"),
                        new NsysDAO.GoodsDeColor("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"))));
    }

    @Test void losColoresSeUsanSinEspaciosALosLados() {
        assertEquals(Map.of("Azul", "Apple iPhone 13 128GB Blue"),
                NsysDAO.primeroDeCadaColor(List.of(new NsysDAO.GoodsDeColor(" Azul ", "Apple iPhone 13 128GB Blue"),
                        new NsysDAO.GoodsDeColor("Azul", "Apple iPhone 13 128GB Azul"))));
    }

    @Test void sinModeloEnElMaestroNoSeBuscaProducto() {
        assertTrue(dao.goodsMasUsados(TallerDePrueba.sinMaestro("350000000000001", "B")).isEmpty());
        verifyNoInteractions(jdbc);
    }

    // ---------------------------------------------------------------- 0.9.21: corrección en NSYS

    @SuppressWarnings("unchecked")
    @Test void losLotesDeLosImeisConVariosYLasBajasConHoraSeLeenDeUnaVez() {
        dao.lotesDeImeisConVarios();
        verify(jdbc).query(and(contains("FROM Nsys_movil WHERE IMEI_SN IN"), contains("GROUP BY IMEI_SN HAVING COUNT(*) > 1")),
                any(RowMapper.class));
        dao.bajasConHora();
        verify(jdbc).query(and(contains("FROM Nsys_evento"), contains("TIPO = 'ESTADO' AND A = 'Removed' AND NOT SOLO_DIA")),
                any(RowMapper.class));
    }

    // ---------------------------------------------------------------- 0.9.22: coste de cada móvil

    private static FilaItems conCoste(String imei, String coste) {
        return new FilaItems(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 2), null, coste == null ? null : new BigDecimal(coste));
    }

    @SuppressWarnings("unchecked")
    @Test void costesCambiadosDevuelveSoloLosQueCambian() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES), any(RowMapper.class))).thenReturn(List.of(
                new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("535.41")),
                new NsysDAO.Coste(new ClaveMovil("350000000000002", "BT-9001"), new BigDecimal("200.00"))));
        List<Object[]> cambios = dao.costesCambiados(List.of(conCoste("350000000000001", "535.41"),
                conCoste("350000000000002", "210.50"), conCoste("350000000000003", "99.90"),
                conCoste("350000000000004", null)));
        assertEquals(2, cambios.size());
        assertArrayEquals(new Object[]{new BigDecimal("210.50"), "350000000000002", "BT-9001"}, cambios.get(0));
        assertArrayEquals(new Object[]{new BigDecimal("99.90"), "350000000000003", "BT-9001"}, cambios.get(1));
        verify(jdbc, never()).batchUpdate(anyString(), any(List.class));
    }

    @SuppressWarnings("unchecked")
    @Test void unImeiYLoteRepetidosEnElFicheroCuentanLaPrimeraFila() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES), any(RowMapper.class))).thenReturn(List.of());
        List<Object[]> cambios = dao.costesCambiados(List.of(conCoste("350000000000001", null),
                conCoste("350000000000001", "50.00"), conCoste("350000000000002", "10.00"),
                conCoste("350000000000002", "20.00")));
        assertEquals(1, cambios.size());
        assertArrayEquals(new Object[]{new BigDecimal("10.00"), "350000000000002", "BT-9001"}, cambios.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test void guardarCostesEscribeElLoteEnUnBatchUpdate() {
        List<Object[]> lote = List.<Object[]>of(new Object[]{new BigDecimal("1.00"), "350000000000001", "BT-9001"});
        dao.guardarCostes(lote);
        verify(jdbc).batchUpdate(NsysDAO.SQL_GUARDAR_COSTE, lote);
    }

    @SuppressWarnings("unchecked")
    @Test void sinCostesNuevosNoSeEscribeNada() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES), any(RowMapper.class))).thenReturn(List.of(
                new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("535.41"))));
        assertEquals(0, dao.costesCambiados(List.of(conCoste("350000000000001", "535.410"))).size());
        verify(jdbc, never()).batchUpdate(eq(NsysDAO.SQL_GUARDAR_COSTE), any(List.class));
        assertEquals("UPDATE Nsys_movil SET COSTE = ? WHERE IMEI_SN = ? AND BATCH = ?", NsysDAO.SQL_GUARDAR_COSTE);
        assertEquals("SELECT IMEI_SN, BATCH, COSTE FROM Nsys_movil WHERE COSTE IS NOT NULL", NsysDAO.SQL_COSTES);
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 2: «No está en NSYS» y la plantilla

    @Test void lasClavesDelEspejoSonUnaPorImeiSinEspaciosYEnMayusculas() throws SQLException {
        doAnswer(inv -> {
            RowCallbackHandler filas = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI_SN")).thenReturn("350000000000001", " f2lxabc123 ");
            filas.processRow(rs);
            filas.processRow(rs);
            return null;
        }).when(jdbc).query(eq("SELECT DISTINCT IMEI_SN FROM Nsys_movil"), any(RowCallbackHandler.class));
        assertEquals(Set.of("350000000000001", "F2LXABC123"), dao.clavesDelEspejo());
    }

    @Test void losImeisDelTallerDeUnaVez() {
        dao.imeisDelTaller();
        verify(jdbc).queryForList("SELECT DISTINCT IMEI FROM Reparacion", String.class);
    }

    @Test void losTrabajosDeLasAltasVanEnTandasConElClienteDelTrabajoODelTelefono() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.altas(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(1).endsWith(" WHERE r.IMEI IN (?) ORDER BY r.FECHA_ASIG, r.ID_REP"), sql.get(1));
        assertTrue(NsysDAO.SQL_TRABAJOS_DE_IMEIS.contains("JOIN Telefono tel ON tel.IMEI = r.IMEI"
                + " LEFT JOIN Cliente c ON c.ID_CLI = COALESCE(r.ID_CLI, tel.ID_CLI)"), NsysDAO.SQL_TRABAJOS_DE_IMEIS);
    }

    @SuppressWarnings("unchecked")
    @Test void losCostesDeUnosLotesSonSoloLosDeEsosLotes() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES + " AND IMEI_SN IN (?)"), any(RowMapper.class), eq("350000000000001")))
                .thenReturn(List.of(new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("412.35")),
                        new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-8000"), new BigDecimal("300.00"))));
        assertEquals(Map.of(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("412.35")),
                dao.costes(List.of(new ClaveMovil("350000000000001", "BT-9001"))));
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: trabajos con su fecha (spec §4.1 y §6)

    @Test void lasHorasDeMadridPasanAUtcParaPreguntarALaBd() {
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 10, 49)), NsysDAO.madridAUtc(LocalDateTime.of(2026, 10, 22, 12, 49)),
                "verano, +2");
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 1, 12, 11, 1)), NsysDAO.madridAUtc(LocalDateTime.of(2026, 1, 12, 12, 1)),
                "invierno, +1");
    }

    @SuppressWarnings("unchecked")
    @Test void losTrabajosDesdeUnaHoraVanEnUnaConsultaConLaHoraEnUtc() {
        assertEquals("SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN FROM Reparacion r WHERE r.FECHA_ASIG >= ?", NsysDAO.SQL_TRABAJOS_DESDE);
        assertTrue(dao.trabajosDesde(LocalDateTime.of(2026, 10, 22, 12, 49)).isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_TRABAJOS_DESDE + NsysDAO.ORDEN_TRABAJOS), any(RowMapper.class),
                eq(Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 10, 49))));
    }

    @Test void losTrabajosConFechaDeUnosImeisVanEnTandasYSinImeisNoConsulta() {
        assertTrue(dao.trabajosDe(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.trabajosDe(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query")).map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertEquals(NsysDAO.SQL_FECHAS_DE_IMEIS + "?)" + NsysDAO.ORDEN_TRABAJOS, sql.get(1));
    }

    @Test void unTrabajoConSuFechaEnMadrid() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn("350000000000001");
        when(rs.getString("ID_REP")).thenReturn("R20261026_201");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 26, 16, 28)));
        assertEquals(new NsysDAO.TrabajoFecha("350000000000001", "R20261026_201", LocalDateTime.of(2026, 10, 26, 17, 28), null),
                NsysDAO.trabajoFecha(rs, 0), "UTC → Madrid (invierno, +1); sin FECHA_FIN, terminado null");
        when(rs.getTimestamp("FECHA_FIN")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 27, 7, 5)));
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 5), NsysDAO.trabajoFecha(rs, 0).terminado(), "FECHA_FIN también a Madrid");
    }

    @Test void lasConsultasDeTrabajosTraenLaFechaDeFin() {
        assertTrue(NsysDAO.SQL_FECHAS_DE_IMEIS.startsWith("SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN FROM Reparacion r"));
    }
}
