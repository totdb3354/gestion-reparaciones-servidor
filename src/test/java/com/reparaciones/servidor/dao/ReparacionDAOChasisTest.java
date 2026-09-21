package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/** Autodetección de chasis por SKU (spec web-formulario 2026-09-19 §5.4). */
class ReparacionDAOChasisTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final String SQL_CHASIS =
            "UPDATE Reparacion SET ES_CHASIS = TRUE, UPDATED_AT = UPDATED_AT" +
            " WHERE ID_REP = ? AND ES_CHASIS = FALSE" +
            " AND EXISTS (SELECT 1 FROM Componente c WHERE c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%')";

    /** JdbcTemplate que deja pasar las comprobaciones previas de las tres transacciones. */
    private static JdbcTemplate jdbcQueDejaPasar() {
        return mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            if (metodo.equals("queryForObject")) return 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
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

    private static FilaReparacion solicitud(int idCom) {
        FilaReparacion f = uso(idCom);
        f.esSolicitud = true;
        return f;
    }

    /** Parámetros (idAsignacion, idCom) de cada UPDATE de chasis emitido, en orden. */
    private static List<List<Object>> marcados(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> Arrays.asList(i.getArguments()))          // varargs ya expandidos: [sql, p1, p2…]
                .filter(args -> SQL_CHASIS.equals(args.get(0)))
                .map(args -> args.subList(1, args.size()))
                .toList();
    }

    @Test void completarConPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(101), uso(131)), IMEI, 4, null, ASIG, null);
        assertEquals(List.of(List.of(ASIG, 101), List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void guardarFilaConPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(131)), IMEI, 4, null, ASIG);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void agotarPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).agotarComponente(ASIG, 131, 1, null);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void solicitudChaDentroDeCompletaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(solicitud(131)), IMEI, 4, null, ASIG, null);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void piezaQueNoEsChaNoMarca() {
        // Quién es "cha" lo decide la base de datos dentro de la propia sentencia: para una batería (101) el
        // EXISTS no se cumple y el UPDATE no toca ninguna fila. Se pasa el idCom de la fila, no el del master.
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(101)), IMEI, 4, null, ASIG);
        assertEquals(List.of(List.of(ASIG, 101)), marcados(jdbc));
        assertTrue(SQL_CHASIS.contains("c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%'"), "el filtro por SKU va en el SQL");
        assertTrue(SQL_CHASIS.contains("AND ES_CHASIS = FALSE"), "solo marca, nunca desmarca");
        assertTrue(SQL_CHASIS.contains("UPDATED_AT = UPDATED_AT"), "no toca UPDATED_AT");
        boolean algunaDesmarca = mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> String.valueOf(i.getArguments()[0]))
                .anyMatch(sql -> sql.contains("ES_CHASIS = FALSE,") || sql.contains("ES_CHASIS = ?"));
        assertFalse(algunaDesmarca, "ninguna sentencia de estas transacciones quita el chasis");
    }

    @Test void sinAsignacionNoMarca() {
        // Filas añadidas al editar una reparación ya hecha: no hay asignación que marcar.
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(131)), IMEI, 4, null, null, "R");
        assertEquals(List.of(), marcados(jdbc));
    }
}
