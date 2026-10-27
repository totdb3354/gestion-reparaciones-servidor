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

/** Taller_descarte con el JdbcTemplate mockeado, sin base de datos (como HechoDAOTest). */
class DescarteDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DescarteDAO dao = new DescarteDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losDescartesConQuienPorSuNombre() {
        assertTrue(dao.todos().isEmpty());
        verify(jdbc).query(eq(DescarteDAO.SQL_TODOS), any(RowMapper.class));
        assertEquals("SELECT d.IMEI_SN, d.ID_USU, u.NOMBRE_USUARIO, d.FECHA, d.MOTIVO FROM Taller_descarte d"
                + " LEFT JOIN Usuario u ON u.ID_USU = d.ID_USU", DescarteDAO.SQL_TODOS);
    }

    @Test void descartarOtraVezPoneQuienCuandoYElMotivoDeAhora() {
        dao.descartar("SIN-IMEI-CAM", 7, "no es un móvil", AHORA);
        verify(jdbc).update(DescarteDAO.SQL_DESCARTAR, "SIN-IMEI-CAM", 7, Timestamp.valueOf(AHORA), "no es un móvil");
        assertEquals("INSERT INTO Taller_descarte (IMEI_SN, ID_USU, FECHA, MOTIVO) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), MOTIVO = VALUES(MOTIVO)",
                DescarteDAO.SQL_DESCARTAR);
    }

    @Test void deshacerDiceCuantosQuito() {
        when(jdbc.update(DescarteDAO.SQL_QUITAR, "SIN-IMEI-CAM")).thenReturn(1);
        assertEquals(1, dao.deshacer("SIN-IMEI-CAM"));
        assertEquals(0, dao.deshacer("350000000000088"));
        assertEquals("DELETE FROM Taller_descarte WHERE IMEI_SN = ?", DescarteDAO.SQL_QUITAR);
    }

    @SuppressWarnings("unchecked")
    @Test void quitarEsUnLoteYSinNadaNoEscribe() {
        dao.quitar(List.of("350000000000088", "350000000000099"));
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(DescarteDAO.SQL_QUITAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000088"}, lote.getValue().get(0));
        clearInvocations(jdbc);
        dao.quitar(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerDescarte() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("SIN-IMEI-CAM");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        when(rs.getString("MOTIVO")).thenReturn("no es un móvil");
        assertEquals(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA, "no es un móvil"), DescarteDAO.descarte(rs, 0));
        when(rs.wasNull()).thenReturn(true);
        assertNull(DescarteDAO.descarte(rs, 0).idUsu(), "el usuario ya no existe");
    }
}
