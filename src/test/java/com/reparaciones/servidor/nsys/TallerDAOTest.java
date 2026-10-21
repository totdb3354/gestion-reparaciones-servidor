package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El acceso a Taller_cambio con el JdbcTemplate mockeado, sin base de datos (como NsysDAOTest). */
class TallerDAOTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 21, 10, 0);
    private static final String IMEI = "350000000000001";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TallerDAO dao = new TallerDAO(jdbc);

    @SuppressWarnings("unchecked")
    @Test void losVigentesDeTodoElTallerVanEnUnaConsultaDelMasAntiguoAlMasReciente() {
        assertTrue(dao.vigentes().isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.ESTADO = 'VIGENTE'" + TallerDAO.ORDEN),
                any(RowMapper.class));
        assertEquals(" ORDER BY c.FECHA, c.ID_CAMBIO", TallerDAO.ORDEN);
    }

    @Test void losVigentesDeUnosImeisVanEnTandasDeQuinientos() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.vigentes(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertTrue(sql.get(0).contains(" WHERE c.ESTADO = 'VIGENTE' AND c.IMEI_SN IN (?, ?"), sql.get(0));
        assertTrue(sql.get(1).endsWith(" AND c.IMEI_SN IN (?)" + TallerDAO.ORDEN), sql.get(1));
        clearInvocations(jdbc);
        assertTrue(dao.vigentes(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void todosLosCambiosDeUnImeiParaElRastro() {
        assertTrue(dao.deImei(IMEI).isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.IMEI_SN = ?" + TallerDAO.ORDEN),
                any(RowMapper.class), eq(IMEI));
    }

    @Test void loQueValiaAntesSaleDelAnteriorOLoDeNsysYQuienPorSuNombre() {
        assertTrue(TallerDAO.COLUMNAS.contains("CASE WHEN a.ID_CAMBIO IS NULL THEN c.VALOR_NSYS ELSE a.VALOR END AS VALOR_ANTES"),
                TallerDAO.COLUMNAS);
        assertTrue(TallerDAO.DESDE.contains("LEFT JOIN Taller_cambio a ON a.ID_CAMBIO = c.ID_CAMBIO_ANTERIOR"), TallerDAO.DESDE);
        assertTrue(TallerDAO.DESDE.contains("LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"), TallerDAO.DESDE);
    }

    @Test void unaFilaDeTallerCambio() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_CAMBIO")).thenReturn(5);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("DATO")).thenReturn("GRADO");
        when(rs.getString("VALOR")).thenReturn("A100");
        when(rs.getString("VALOR_ANTES")).thenReturn("B");
        when(rs.getString("VALOR_NSYS")).thenReturn("B");
        when(rs.getString("ORIGEN")).thenReturn("MANO");
        when(rs.getInt("ID_USU")).thenReturn(7);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Técnico 1");
        when(rs.getString("MOTIVO")).thenReturn("batería cambiada");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.getString("ESTADO")).thenReturn("PASADO");
        when(rs.getTimestamp("CERRADO_EN")).thenReturn(Timestamp.valueOf(HOY.plusHours(2)));
        when(rs.getString("VALOR_CIERRE")).thenReturn("A100");
        when(rs.getInt("ID_CAMBIO_ANTERIOR")).thenReturn(3);
        assertEquals(new CambioTaller(5, IMEI, DatoTaller.GRADO, "A100", "B", "B", "MANO", null, 7, "Técnico 1",
                "batería cambiada", HOY, CambioTaller.Estado.PASADO, HOY.plusHours(2), "A100", 3), TallerDAO.cambio(rs, 0));
    }

    @Test void sinUsuarioNiAnteriorNiCierreSonNulos() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("DATO")).thenReturn("COLOR");
        when(rs.getString("ESTADO")).thenReturn("VIGENTE");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.wasNull()).thenReturn(true);
        CambioTaller c = TallerDAO.cambio(rs, 0);
        assertNull(c.idUsu());
        assertNull(c.idAnterior());
        assertNull(c.cerradoEn());
    }

    @Test void guardarBloqueaElMovilSustituyeElVigenteYApuntaAEl() {
        when(jdbc.queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "GRADO")).thenReturn(List.of(4));
        dao.guardar(IMEI, List.of(new TallerDAO.Nuevo(DatoTaller.COLOR, "Rojo", "Azul", CambioTaller.Estado.VIGENTE),
                new TallerDAO.Nuevo(DatoTaller.GRADO, "B", "B", CambioTaller.Estado.QUITADO)), 7, "motivo", HOY);
        Timestamp t = Timestamp.valueOf(HOY);
        InOrder orden = inOrder(jdbc);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_BLOQUEAR, String.class, IMEI);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "COLOR");
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "MANO", 7, "motivo", t, "VIGENTE",
                null, null);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "GRADO");
        orden.verify(jdbc).update(TallerDAO.SQL_SUSTITUIR, t, 4);
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "GRADO", "B", "B", "MANO", 7, "motivo", t, "QUITADO", t, 4);
        verifyNoMoreInteractions(jdbc);
    }

    @Test void guardarEsTodoONada() throws NoSuchMethodException {
        assertNotNull(TallerDAO.class.getMethod("guardar", String.class, List.class, int.class, String.class,
                LocalDateTime.class).getAnnotation(Transactional.class));
    }

    @Test void elCierreYLaSustitucionSoloTocanUnVigente() {
        when(jdbc.update(TallerDAO.SQL_CERRAR, "MANDA_NSYS", Timestamp.valueOf(HOY), "Verde", 5)).thenReturn(1);
        assertEquals(1, dao.cerrar(5, CambioTaller.Estado.MANDA_NSYS, "Verde", HOY));
        assertTrue(TallerDAO.SQL_CERRAR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'"), TallerDAO.SQL_CERRAR);
        assertTrue(TallerDAO.SQL_SUSTITUIR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'"), TallerDAO.SQL_SUSTITUIR);
        assertTrue(TallerDAO.SQL_BLOQUEAR.endsWith(" FOR UPDATE"), TallerDAO.SQL_BLOQUEAR);
    }
}
