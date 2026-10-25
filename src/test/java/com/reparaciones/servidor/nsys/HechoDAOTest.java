package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Taller_hecho con el JdbcTemplate mockeado, sin base de datos (como TallerDAOTest). */
class HechoDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HechoDAO dao = new HechoDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void lasMarcasConQuienPorSuNombre() {
        assertTrue(dao.todas().isEmpty());
        verify(jdbc).query(eq(HechoDAO.SQL_TODAS), any(RowMapper.class));
        assertEquals("SELECT h.IMEI_SN, h.TARJETA, h.ID_USU, u.NOMBRE_USUARIO, h.FECHA, h.VOLVIO FROM Taller_hecho h"
                + " LEFT JOIN Usuario u ON u.ID_USU = h.ID_USU", HechoDAO.SQL_TODAS);
    }

    @SuppressWarnings("unchecked")
    @Test void marcarEsUnaFilaPorImeiYVolverAMarcarPoneLaHoraNuevaYQuitaElVolvio() {
        dao.marcar(TarjetaHecho.GRADO, List.of("350000000000001", "350000000000002"), 7, AHORA);
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_MARCAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000001", "GRADO", 7, Timestamp.valueOf(AHORA)}, lote.getValue().get(0));
        assertEquals("INSERT INTO Taller_hecho (IMEI_SN, TARJETA, ID_USU, FECHA, VOLVIO) VALUES (?, ?, ?, ?, NULL)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), VOLVIO = NULL", HechoDAO.SQL_MARCAR);
    }

    @Test void deshacerQuitaLaMarcaDeEsaTarjeta() {
        dao.desmarcar(TarjetaHecho.ALTA, "350000000000088");
        verify(jdbc).update(HechoDAO.SQL_DESMARCAR, "350000000000088", "ALTA");
    }

    @SuppressWarnings("unchecked")
    @Test void elRepasoBorraUnasYHaceVolverOtrasYSinNadaNoEscribe() {
        HechoTaller seFue = new HechoTaller("350000000000001", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(1), null);
        HechoTaller sigue = new HechoTaller("350000000000002", TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA.minusHours(1), null);
        dao.repasar(List.of(seFue), List.of(sigue), AHORA);
        ArgumentCaptor<List<Object[]>> borradas = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_QUITAR), borradas.capture());
        assertArrayEquals(new Object[]{"350000000000001", "COLOR", Timestamp.valueOf(AHORA.minusHours(1))}, borradas.getValue().get(0));
        ArgumentCaptor<List<Object[]>> vueltas = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(HechoDAO.SQL_VOLVIO), vueltas.capture());
        assertArrayEquals(new Object[]{Timestamp.valueOf(AHORA), "350000000000002", "GRADO", Timestamp.valueOf(AHORA.minusHours(1))}, vueltas.getValue().get(0));
        clearInvocations(jdbc);
        dao.repasar(List.of(), List.of(), AHORA);
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerHecho() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("350000000000001");
        when(rs.getString("TARJETA")).thenReturn("COLOR");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        assertEquals(new HechoTaller("350000000000001", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA, null), HechoDAO.hecho(rs, 0));
        when(rs.getTimestamp("VOLVIO")).thenReturn(Timestamp.valueOf(AHORA.plusHours(1)));
        assertEquals(AHORA.plusHours(1), HechoDAO.hecho(rs, 0).volvio());
    }
}
