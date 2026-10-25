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
import java.util.Set;

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
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "MANO", null, 7, "motivo", t, "VIGENTE",
                null, null);
        orden.verify(jdbc).queryForList(TallerDAO.SQL_VIGENTE, Integer.class, IMEI, "GRADO");
        orden.verify(jdbc).update(TallerDAO.SQL_SUSTITUIR, t, 4);
        orden.verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "GRADO", "B", "B", "MANO", null, 7, "motivo", t, "QUITADO", t, 4);
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

    // ---------------------------------------------------------------- 0.9.21, pieza 2: desde las reparaciones

    @Test void unCambioDeUnaReparacionLlevaSuOrigenYSuReparacion() {
        dao.guardar(IMEI, List.of(new TallerDAO.Nuevo(DatoTaller.COLOR, "Rojo", "Azul", CambioTaller.Estado.VIGENTE)),
                "R20261021_3", 8, "chasis chai13red nuevo", HOY);
        verify(jdbc).update(TallerDAO.SQL_INSERTAR, IMEI, "COLOR", "Rojo", "Azul", "REPARACION", "R20261021_3", 8,
                "chasis chai13red nuevo", Timestamp.valueOf(HOY), "VIGENTE", null, null);
        assertTrue(TallerDAO.SQL_INSERTAR.contains("ORIGEN, ID_REP, ID_USU"), TallerDAO.SQL_INSERTAR);
    }

    @Test void guardarDesdeUnaReparacionTambienEsTodoONada() throws NoSuchMethodException {
        assertNotNull(TallerDAO.class.getMethod("guardar", String.class, List.class, String.class, int.class, String.class,
                LocalDateTime.class).getAnnotation(Transactional.class));
    }

    @Test void elCandadoDiceSiNsysConoceElImei() {
        when(jdbc.queryForList(TallerDAO.SQL_BLOQUEAR, String.class, IMEI)).thenReturn(List.of(IMEI));
        assertTrue(dao.bloquear(IMEI));
        assertFalse(dao.bloquear("350000000000099"));
    }

    @SuppressWarnings("unchecked")
    @Test void losCambiosDeUnaReparacion() {
        assertTrue(dao.deReparacion("R20261021_3").isEmpty());
        verify(jdbc).query(eq("SELECT " + TallerDAO.COLUMNAS + TallerDAO.DESDE + " WHERE c.ID_REP = ?" + TallerDAO.ORDEN),
                any(RowMapper.class), eq("R20261021_3"));
    }

    @Test void deshacerSustituyeSoloUnVigenteYReactivarSoloUnSustituido() {
        when(jdbc.update(TallerDAO.SQL_SUSTITUIR, Timestamp.valueOf(HOY), 5)).thenReturn(1);
        assertEquals(1, dao.deshacer(5, HOY));
        dao.reactivar(4);
        verify(jdbc).update(TallerDAO.SQL_REACTIVAR, 4);
        assertTrue(TallerDAO.SQL_REACTIVAR.contains("SET ESTADO = 'VIGENTE', CERRADO_EN = NULL"), TallerDAO.SQL_REACTIVAR);
        assertTrue(TallerDAO.SQL_REACTIVAR.endsWith(" WHERE ID_CAMBIO = ? AND ESTADO = 'SUSTITUIDO'"), TallerDAO.SQL_REACTIVAR);
    }

    @Test void unAvisoConYSinCausa() {
        Timestamp t = Timestamp.valueOf(HOY);
        dao.avisar(new AvisoTaller.Nuevo(IMEI, "R20261021_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA,
                DatoTaller.COLOR, "Rojo", "Azul", "chasis chai13red nuevo"), 8, HOY);
        verify(jdbc).update(TallerDAO.SQL_AVISAR, IMEI, "R20261021_3", "DESHECHO", "BORRADA", "COLOR", "Rojo", "Azul",
                "chasis chai13red nuevo", 8, t);
        dao.avisar(new AvisoTaller.Nuevo(IMEI, "R20261021_4", AvisoTaller.Tipo.SIN_COLOR, null, DatoTaller.COLOR, "chai13xxx",
                null, "chasis chai13xxx nuevo"), 8, HOY);
        verify(jdbc).update(TallerDAO.SQL_AVISAR, IMEI, "R20261021_4", "SIN_COLOR", null, "COLOR", "chai13xxx", null,
                "chasis chai13xxx nuevo", 8, t);
    }

    @SuppressWarnings("unchecked")
    @Test void losAvisosDeUnImeiConQuienPorSuNombre() {
        assertTrue(dao.avisosDeImei(IMEI).isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_AVISOS), any(RowMapper.class), eq(IMEI));
        assertTrue(TallerDAO.SQL_AVISOS.contains("LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU"), TallerDAO.SQL_AVISOS);
        assertTrue(TallerDAO.SQL_AVISOS.endsWith(" WHERE v.IMEI_SN = ? ORDER BY v.FECHA, v.ID_AVISO"), TallerDAO.SQL_AVISOS);
    }

    @Test void unaFilaDeTallerAviso() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_AVISO")).thenReturn(2);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("ID_REP")).thenReturn("R20261021_3");
        when(rs.getString("TIPO")).thenReturn("SIN_GRADO");
        when(rs.getString("DATO")).thenReturn("GRADO");
        when(rs.getString("DE")).thenReturn("ID");
        when(rs.getString("MOTIVO")).thenReturn("batería bati13 nueva");
        when(rs.getInt("ID_USU")).thenReturn(8);
        when(rs.getString("NOMBRE_USUARIO")).thenReturn("Técnico 2");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        assertEquals(new AvisoTaller(2, IMEI, "R20261021_3", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO, "ID", null,
                "batería bati13 nueva", 8, "Técnico 2", HOY), TallerDAO.aviso(rs, 0));
        when(rs.getString("CAUSA")).thenReturn("EDITADA");
        assertEquals(AvisoTaller.Causa.EDITADA, TallerDAO.aviso(rs, 0).causa());
    }

    @SuppressWarnings("unchecked")
    @Test void losSkuDeLosComponentesEnUnaConsulta() {
        assertTrue(dao.tiposDeComponentes(List.of(101, 131, 101)).isEmpty());
        verify(jdbc).query(eq("SELECT ID_COM, TIPO FROM Componente WHERE ID_COM IN (?, ?)"), any(RowMapper.class), eq(101), eq(131));
        clearInvocations(jdbc);
        assertTrue(dao.tiposDeComponentes(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void laPiezaDeUnaReparacionEsSuFilaConUso() {
        assertTrue(dao.piezaDeReparacion("R20261021_3").isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_PIEZA), any(RowMapper.class), eq("R20261021_3"));
        assertTrue(TallerDAO.SQL_PIEZA.contains("rc.ES_SOLICITUD = 0"), TallerDAO.SQL_PIEZA);
    }

    @Test void lasReparacionesQueSiguenExistiendo() {
        when(jdbc.queryForList("SELECT ID_REP FROM Reparacion WHERE ID_REP IN (?, ?)", String.class, "R1", "R2"))
                .thenReturn(List.of("R2"));
        assertEquals(Set.of("R2"), dao.reparacionesQueExisten(List.of("R1", "R2")));
        clearInvocations(jdbc);
        assertEquals(Set.of(), dao.reparacionesQueExisten(List.of()));
        verifyNoInteractions(jdbc);
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 2: «Revisar a mano»

    @SuppressWarnings("unchecked")
    @Test void losAvisosPorRevisarSonLosSinRevisarNiCorregidosAMano() {
        assertTrue(dao.avisosPorRevisar().isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_POR_REVISAR), any(RowMapper.class));
        String sql = TallerDAO.SQL_POR_REVISAR;
        assertTrue(sql.contains(" WHERE v.TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"), sql);
        assertTrue(sql.contains(" AND NOT EXISTS (SELECT 1 FROM Taller_cambio c WHERE c.IMEI_SN = v.IMEI_SN AND c.DATO = v.DATO"
                + " AND c.ORIGEN = 'MANO' AND c.FECHA > v.FECHA)"), sql);
        assertTrue(sql.endsWith(" ORDER BY v.FECHA, v.ID_AVISO"), sql);
    }

    @Test void revisarApuntaQuienYCuandoSoloSiNoEstabaRevisado() {
        dao.revisar(2, 7, HOY);
        verify(jdbc).update(TallerDAO.SQL_REVISAR, 7, Timestamp.valueOf(HOY), 2);
        assertEquals("UPDATE Taller_aviso SET REVISADO_POR = ?, REVISADO_EN = ? WHERE ID_AVISO = ? AND REVISADO_EN IS NULL"
                + " AND TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER')", TallerDAO.SQL_REVISAR);
    }

    @SuppressWarnings("unchecked")
    @Test void unAvisoPorSuIdYConQuienLoReviso() {
        assertTrue(dao.avisoPorId(2).isEmpty());
        verify(jdbc).query(eq(TallerDAO.SQL_AVISO), any(RowMapper.class), eq(2));
        assertTrue(TallerDAO.SQL_AVISO.endsWith(" WHERE v.ID_AVISO = ?"), TallerDAO.SQL_AVISO);
        for (String sql : List.of(TallerDAO.SQL_AVISOS, TallerDAO.SQL_POR_REVISAR, TallerDAO.SQL_AVISO))
            assertTrue(sql.contains("v.REVISADO_EN, r.NOMBRE_USUARIO AS REVISOR FROM Taller_aviso v"
                    + " LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU LEFT JOIN Usuario r ON r.ID_USU = v.REVISADO_POR"), sql);
    }

    @Test void unaFilaDeTallerAvisoRevisada() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_AVISO")).thenReturn(2);
        when(rs.getString("IMEI_SN")).thenReturn(IMEI);
        when(rs.getString("ID_REP")).thenReturn("R20261021_3");
        when(rs.getString("TIPO")).thenReturn("SIN_COLOR");
        when(rs.getString("DATO")).thenReturn("COLOR");
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(HOY));
        when(rs.getString("REVISOR")).thenReturn("Supertécnico 1");
        when(rs.getTimestamp("REVISADO_EN")).thenReturn(Timestamp.valueOf(HOY.plusHours(2)));
        AvisoTaller a = TallerDAO.aviso(rs, 0);
        assertEquals("Supertécnico 1", a.revisadoPor());
        assertEquals(HOY.plusHours(2), a.revisadoEn());
        assertEquals(java.util.Set.of(AvisoTaller.Tipo.SIN_COLOR, AvisoTaller.Tipo.SIN_GRADO, AvisoTaller.Tipo.SIN_DESHACER),
                AvisoTaller.POR_REVISAR);
    }
}
