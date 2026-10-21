package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/** «Terminar asignación» devuelve las reparaciones que crea (0.9.21, pieza 2 del SKU del taller: el taller las aplica). */
class ReparacionDAOCreadasTest {

    private static final String IMEI = "355400000000111";
    private static final String HOY = LocalDate.now(ZoneId.of("Europe/Madrid")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    /** Deja pasar las comprobaciones y numera los ID_REP como la base de datos: 1, 2, 3… */
    private static ReparacionDAO dao() {
        AtomicInteger siguiente = new AtomicInteger();
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            if (metodo.equals("queryForObject"))
                return String.valueOf((Object) invocacion.getArgument(0)).contains("SUBSTRING_INDEX") ? siguiente.incrementAndGet() : 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            return RETURNS_DEFAULTS.answer(invocacion);
        });
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

    @Test void devuelveUnaReparacionPorFilaConUsoEnSuOrden() {
        assertEquals(List.of("R" + HOY + "_1", "R" + HOY + "_2"),
                dao().insertarCompleta(List.of(uso(101), solicitud(102), uso(131)), IMEI, 4, null, "A20260916_1", null));
    }

    @Test void unaGlassDevuelveSusG() {
        assertEquals(List.of("G" + HOY + "_1"), dao().insertarCompleta(List.of(uso(141)), IMEI, 4, null, "AG20260916_2", null));
    }

    @Test void sinFilasConUsoNoDevuelveNinguna() {
        assertEquals(List.of(), dao().insertarCompleta(List.of(), IMEI, 4, null, "A20260916_1", null));
        assertEquals(List.of(), dao().insertarCompleta(List.of(solicitud(102)), IMEI, 4, null, "A20260916_1"));
    }
}
