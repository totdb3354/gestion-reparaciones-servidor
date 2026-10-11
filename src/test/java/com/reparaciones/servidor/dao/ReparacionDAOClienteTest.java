package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Cliente guardado en cada trabajo (spec 0.9.8 §4): cada cierre copia el cliente del teléfono; reabrir lo vacía. */
class ReparacionDAOClienteTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final String DEL_IMEI = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = ?)";
    private static final String DE_LA_FILA = "(SELECT tc.ID_CLI FROM Telefono tc WHERE tc.IMEI = Reparacion.IMEI)";

    /** Deja pasar las comprobaciones previas; sin solicitudes pendientes, así que la asignación se cierra. */
    private static JdbcTemplate jdbcQueDejaPasar() {
        return mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            Object[] args = invocacion.getArguments();
            String sql = args.length > 0 ? String.valueOf(args[0]) : "";
            if (metodo.equals("queryForObject"))
                return sql.startsWith("SELECT COUNT(*) FROM Reparacion_componente") ? 0 : 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            if (metodo.equals("queryForList") && sql.startsWith("SELECT IMEI, ID_TEC, COMENTARIO_ASIGNACION"))
                return List.of(Map.of("IMEI", IMEI, "ID_TEC", 4));
            return RETURNS_DEFAULTS.answer(invocacion);
        });
    }

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static FilaReparacion uso(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        return f;
    }

    /** Cada update emitido como [sql, p1, p2…] (varargs ya expandidos). */
    private static List<List<Object>> updates(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> Arrays.asList(i.getArguments()))
                .toList();
    }

    /** El único update cuyo SQL empieza por {@code prefijo}. */
    private static List<Object> unico(JdbcTemplate jdbc, String prefijo) {
        List<List<Object>> u = updates(jdbc).stream()
                .filter(a -> String.valueOf(a.get(0)).startsWith(prefijo)).toList();
        assertEquals(1, u.size(), () -> "updates que empiezan por «" + prefijo + "»: " + updates(jdbc));
        return u.get(0);
    }

    @Test void completarGuardaElClienteEnLaPiezaYEnLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(101)), IMEI, 4, null, ASIG, null);
        List<Object> pieza = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(pieza.get(0)).contains("ENTREGADO_POR, ID_CLI)"), pieza.toString());
        assertTrue(String.valueOf(pieza.get(0)).endsWith(DEL_IMEI + ")"), pieza.toString());
        assertEquals(IMEI, pieza.get(pieza.size() - 1));
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA + " WHERE ID_REP = ?", ASIG),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void guardarFilaGuardaElClienteEnLaPieza() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(101)), IMEI, 4, null, ASIG);
        List<Object> pieza = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(pieza.get(0)).endsWith(DEL_IMEI + ")"), pieza.toString());
        assertEquals(IMEI, pieza.get(pieza.size() - 1));
    }

    @Test void terminarAsignacionGuardaElCliente() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).completar(ASIG);
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA
                        + " WHERE ID_REP = ? AND FECHA_FIN IS NULL", ASIG),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void pulidoHechoGuardaElClienteEnElPYEnSuAP() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).completarPulido("AP20260916_1");
        List<Object> p = unico(jdbc, "INSERT INTO Reparacion (");
        assertTrue(String.valueOf(p.get(0)).endsWith(DEL_IMEI + ")"), p.toString());
        assertEquals(IMEI, p.get(p.size() - 1));
        assertEquals(List.of("UPDATE Reparacion SET FECHA_FIN = NOW(), ID_CLI = " + DE_LA_FILA + " WHERE ID_REP = ?",
                        "AP20260916_1"),
                unico(jdbc, "UPDATE Reparacion SET FECHA_FIN"));
    }

    @Test void altaAntiguaGuardaElCliente() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertar(IMEI, 4, LocalDateTime.of(2026, 9, 16, 8, 0), LocalDateTime.of(2026, 9, 16, 9, 0));
        List<Object> r = unico(jdbc, "INSERT INTO Reparacion (");
        assertEquals("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, FECHA_FIN, ID_CLI)"
                + " VALUES (?,?,?,?,?," + DEL_IMEI + ")", r.get(0));
        assertEquals(IMEI, r.get(r.size() - 1));
    }

    @SuppressWarnings("unchecked")
    @Test void reabrirVaciaElClienteGuardado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String idRep = "R20260721_1";
        String idRepOrig = "A20260721_1";
        when(jdbc.query(eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ?"), any(RowMapper.class), eq(idRep)))
                .thenReturn(List.of(IMEI));
        when(jdbc.query(contains("ID_REP_ANTERIOR IS NOT NULL"), any(RowMapper.class), eq(idRep)))
                .thenReturn(List.of(idRepOrig));
        when(jdbc.queryForObject(contains("ID_REP_ANTERIOR = ? AND ID_REP LIKE ?"), eq(Integer.class),
                eq(idRepOrig), eq("R%"), eq(idRep)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(1);

        dao(jdbc).eliminar(idRep);

        verify(jdbc).update("UPDATE Reparacion SET FECHA_FIN = NULL, ID_CLI = NULL"
                + " WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NOT NULL", idRepOrig);
    }
}
