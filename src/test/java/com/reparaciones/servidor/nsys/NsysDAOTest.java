package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

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
        dao.reemplazarProductos(List.of(new ProductoMaestro("clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true)));
        verify(jdbc).update("DELETE FROM Nsys_producto");
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_PRODUCTO), lote.capture());
        assertArrayEquals(new Object[]{"clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true}, lote.getValue().get(0));
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
}
