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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Taller_vuelta_ignorada con el JdbcTemplate mockeado, sin base de datos (como DescarteDAOTest). */
class VueltaIgnoradaDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 6, 12, 31);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VueltaIgnoradaDAO dao = new VueltaIgnoradaDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losIgnoradosConQuienPorSuNombre() {
        assertTrue(dao.todos().isEmpty());
        verify(jdbc).query(eq(VueltaIgnoradaDAO.SQL_TODOS), any(RowMapper.class));
        assertEquals("SELECT i.IMEI_SN, i.ID_USU, u.NOMBRE_USUARIO, i.FECHA FROM Taller_vuelta_ignorada i"
                + " LEFT JOIN Usuario u ON u.ID_USU = i.ID_USU", VueltaIgnoradaDAO.SQL_TODOS);
    }

    @Test void ignorarOtraVezPoneQuienYCuandoDeAhora() {
        dao.ignorar("350000000000123", 7, AHORA);
        verify(jdbc).update(VueltaIgnoradaDAO.SQL_IGNORAR, "350000000000123", 7, Timestamp.valueOf(AHORA));
        assertEquals("INSERT INTO Taller_vuelta_ignorada (IMEI_SN, ID_USU, FECHA) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA)", VueltaIgnoradaDAO.SQL_IGNORAR);
    }

    @Test void deshacerDiceCuantosQuito() {
        when(jdbc.update(VueltaIgnoradaDAO.SQL_QUITAR, "350000000000123")).thenReturn(1);
        assertEquals(1, dao.deshacer("350000000000123"));
        assertEquals(0, dao.deshacer("350000000000131"));
        assertEquals("DELETE FROM Taller_vuelta_ignorada WHERE IMEI_SN = ?", VueltaIgnoradaDAO.SQL_QUITAR);
    }

    @SuppressWarnings("unchecked")
    @Test void quitarEsUnLoteYSinNadaNoEscribe() {
        dao.quitar(List.of("350000000000123", "350000000000149"));
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(VueltaIgnoradaDAO.SQL_QUITAR), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{"350000000000123"}, lote.getValue().get(0));
        clearInvocations(jdbc);
        dao.quitar(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void unaFilaDeTallerVueltaIgnorada() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI_SN")).thenReturn("350000000000123");
        when(rs.getInt("ID_USU")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn(null);
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AHORA));
        assertEquals(new VueltaIgnorada("350000000000123", null, null, AHORA), VueltaIgnoradaDAO.ignorado(rs, 0));
    }
}
