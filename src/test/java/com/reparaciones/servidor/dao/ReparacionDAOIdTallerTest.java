package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Lo que el DAO de reparaciones hace por el SKU del taller (0.9.21, pieza 2): un ID de R… que ya tiene historia en el taller
 *  (Taller_cambio, Taller_aviso) no se repite aunque su reparación se haya borrado, y el IMEI de una R… se lee antes, fuera de
 *  la transacción, para coger el candado del móvil antes que el de su fila (que después lo confirma con FOR UPDATE). */
class ReparacionDAOIdTallerTest {

    private static final String IMEI = "355400000000111";
    private static final String HOY = LocalDate.now(ZoneId.of("Europe/Madrid")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    private final List<Object[]> escrituras = new ArrayList<>();

    /** Un JdbcTemplate de mentira: el correlativo de hoy llega a {@code enReparacion} en Reparacion y a {@code enTaller} en el
     *  taller (0: nada; las consultas dan MAX+1, como la de verdad); deja pasar las comprobaciones y apunta las escrituras. */
    private JdbcTemplate jdbc(int enReparacion, int enTaller) {
        return mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            String sql = invocacion.getArguments().length == 0 ? "" : String.valueOf((Object) invocacion.getArgument(0));
            if (metodo.equals("queryForObject")) {
                if (sql.contains("Taller_")) return enTaller + 1;
                if (sql.contains("SUBSTRING_INDEX")) return enReparacion + 1;
                return 1;
            }
            if (metodo.equals("update")) {
                escrituras.add(invocacion.getArguments());
                return 1;
            }
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            if (metodo.equals("queryForList") && sql.startsWith("SELECT IMEI, ID_TEC")) {
                Map<String, Object> pulido = new HashMap<>();
                pulido.put("IMEI", IMEI);
                pulido.put("ID_TEC", 4);
                return List.of(pulido);
            }
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

    /** El ID_REP de la primera fila que se inserta en Reparacion. */
    private String insertado() {
        return escrituras.stream().filter(a -> String.valueOf(a[0]).startsWith("INSERT INTO Reparacion ("))
                .map(a -> String.valueOf(a[1])).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- el ID no se repite con historia en el taller

    @Test void unaRConHistoriaEnElTallerNoSeRepite() {
        JdbcTemplate jdbc = jdbc(3, 5);
        assertEquals(List.of("R" + HOY + "_6"), dao(jdbc).insertarCompleta(List.of(uso(131)), IMEI, 4, null, "A20260916_1", null));
        String like = "R" + HOY + "_%";
        verify(jdbc).queryForObject(contains("FROM Reparacion WHERE ID_REP LIKE ? FOR UPDATE"), eq(Integer.class), eq(like));
        verify(jdbc).queryForObject(and(contains("Taller_cambio"), contains("Taller_aviso")), eq(Integer.class), eq(like), eq(like));
    }

    @Test void siElTallerVaPorDetrasMandaReparacion() {
        assertEquals(List.of("R" + HOY + "_8"), dao(jdbc(7, 5)).insertarCompleta(List.of(uso(131)), IMEI, 4, null, "A20260916_1", null));
    }

    @Test void sinNadaEnElTallerComoSiempre() {
        assertEquals(List.of("R" + HOY + "_4"), dao(jdbc(3, 0)).insertarCompleta(List.of(uso(131)), IMEI, 4, null, "A20260916_1", null));
        assertEquals("R" + HOY + "_4", dao(jdbc(3, 0)).guardarFilaIndividual(List.of(uso(131)), IMEI, 4, null, "A20260916_1"));
    }

    @Test void losDemasPrefijosNoMiranElTaller() {
        JdbcTemplate jdbc = jdbc(3, 5);
        ReparacionDAO dao = dao(jdbc);
        assertEquals(List.of("G" + HOY + "_4"), dao.insertarCompleta(List.of(uso(141)), IMEI, 4, null, "AG20260916_2", null));
        assertEquals("A" + HOY + "_4", dao.insertarAsignacion(IMEI, 4, null, false, false, null, 8));
        assertEquals("AG" + HOY + "_4", dao.insertarAsignacionGlass(IMEI, 4, null, false, null, 8));
        assertEquals("AP" + HOY + "_4", dao.insertarAsignacionPulido(IMEI, 4, null, null, 8));
        escrituras.clear();
        dao.completarPulido("AP20260916_1");
        assertEquals("P" + HOY + "_4", insertado());
        assertEquals(List.of(), mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getArguments().length > 0 && String.valueOf(i.getArguments()[0]).contains("Taller_")).toList());
    }

    // ---------------------------------------------------------------- el IMEI de una R…, con su fila bloqueada

    @Test void elImeiDeUnaReparacionSeLeeConSuFilaBloqueada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ? FOR UPDATE"), any(RowMapper.class), eq("R20260916_5")))
                .thenReturn(List.of(IMEI));
        assertEquals(Optional.of(IMEI), dao(jdbc).bloquearReparacion("R20260916_5"));
        assertEquals(Optional.empty(), dao(jdbc).bloquearReparacion("R20260916_9"));
    }

    // ---------------------------------------------------------------- el IMEI de una R…, antes y fuera de la transacción

    @Test void elImeiDeUnaReparacionSeLeeAntesSinBloquear() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ?"), any(RowMapper.class), eq("R20260916_5")))
                .thenReturn(List.of(IMEI));
        assertEquals(Optional.of(IMEI), dao(jdbc).imeiDe("R20260916_5"));
        assertEquals(Optional.empty(), dao(jdbc).imeiDe("R20260916_9"));
    }

    /** Sin transacción (la del que llama queda suspendida): va por otra conexión, sin candados ni foto de REPEATABLE READ. */
    @Test void elImeiDeUnaReparacionSeLeeFueraDeLaTransaccion() throws NoSuchMethodException {
        Transactional tx = ReparacionDAO.class.getMethod("imeiDe", String.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.NOT_SUPPORTED, tx.propagation());
        assertTrue(tx.readOnly());
    }
}
