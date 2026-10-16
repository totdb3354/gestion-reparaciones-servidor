package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

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
}
