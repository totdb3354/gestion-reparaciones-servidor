package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lectura del log (spec 6 §4.5, G3 y G5): días de Madrid convertidos a UTC con "desde" inclusivo y "hasta" exclusivo
 *  al día siguiente, orden con desempate por ID_LOG y LIMIT solo cuando llega. SQL y parámetros capturados con
 *  ArgumentCaptor (un captor por parámetro de la lista variable, en orden); JdbcTemplate mockeado, sin Spring. */
@SuppressWarnings("unchecked")
class LogDAOFiltroTest {

    private static final String BASE = "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE, l.MOTIVO "
            + "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1";
    private static final String ORDEN = " ORDER BY l.FECHA DESC, l.ID_LOG DESC";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LogDAO dao = new LogDAO(jdbc);
    private final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<Object> p = ArgumentCaptor.forClass(Object.class);

    // ── límites de día: Madrid → UTC ──
    @Test void inicioDeUnDiaDeVeranoEsLas22DelDiaAnteriorEnUtc() {
        assertEquals(LocalDateTime.of(2026, 6, 9, 22, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 6, 10)));
    }

    @Test void inicioDeUnDiaDeInviernoEsLas23DelDiaAnteriorEnUtc() {
        assertEquals(LocalDateTime.of(2026, 1, 14, 23, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 1, 15)));
    }

    /** El 29/03/2026 cambia la hora: empieza en invierno (UTC+1) y el siguiente ya en verano (UTC+2). */
    @Test void elDiaDelCambioDeHoraSeCalculaConLaZonaDeCadaExtremo() {
        assertEquals(LocalDateTime.of(2026, 3, 28, 23, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 3, 29)));
        assertEquals(LocalDateTime.of(2026, 3, 29, 22, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 3, 30)));
    }

    // ── filtros de fecha ──
    /** Un LOGIN a las 01:30 del 10/06 en Madrid (23:30 UTC del 09/06) sale filtrando por el 10/06: desde 22:00 UTC
     *  del 09/06 hasta antes de las 22:00 UTC del 10/06. */
    @Test void desdeYHastaEnVeranoSonLosLimitesDelDiaDeMadridEnUtc() {
        dao.getFiltered(null, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture());
        assertEquals(BASE + " AND l.FECHA >= ? AND l.FECHA < ?" + ORDEN, sql.getValue());
        assertEquals(List.of(LocalDateTime.of(2026, 6, 9, 22, 0), LocalDateTime.of(2026, 6, 10, 22, 0)), p.getAllValues());
    }

    @Test void desdeYHastaEnInviernoSonLosLimitesDelDiaDeMadridEnUtc() {
        dao.getFiltered(null, null, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture());
        assertEquals(List.of(LocalDateTime.of(2026, 1, 14, 23, 0), LocalDateTime.of(2026, 1, 20, 23, 0)), p.getAllValues());
    }

    @Test void hastaSoloEsExclusivoAlInicioDelDiaSiguiente() {
        dao.getFiltered(null, null, null, LocalDate.of(2026, 1, 15));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture());
        assertEquals(BASE + " AND l.FECHA < ?" + ORDEN, sql.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 15, 23, 0), p.getValue());
        assertFalse(sql.getValue().contains("DATE("), "ya no se usa DATE(FECHA) en UTC");
    }

    // ── orden, acción, usuario y límite ──
    @Test void sinFiltrosNiLimiteNoLlevaParametrosYOrdenaConDesempate() {
        dao.getFiltered(null, null, null, null);
        // sin parámetros la llamada es query(sql, mapper, new Object[0]): any(Object[].class) casa el array vacío
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertEquals(BASE + ORDEN, sql.getValue());
        assertFalse(sql.getValue().contains("LIMIT"));
    }

    @Test void laSobrecargaDeCuatroEsSinLimite() {
        LogDAO espia = spy(dao);
        espia.getFiltered("LOGIN", "usuario-a", null, null);
        verify(espia).getFiltered("LOGIN", "usuario-a", null, null, null);
    }

    @Test void accionYUsuarioFiltranPorIgualdadYElLimiteVaAlFinal() {
        dao.getFiltered("CREAR_USUARIO", "admin-prueba", LocalDate.of(2026, 6, 10), null, 1000);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture(), p.capture(), p.capture());
        assertEquals(BASE + " AND l.ACCION = ? AND u.NOMBRE_USUARIO = ? AND l.FECHA >= ?" + ORDEN + " LIMIT ?",
                sql.getValue());
        assertEquals(List.of("CREAR_USUARIO", "admin-prueba", LocalDateTime.of(2026, 6, 9, 22, 0), 1000),
                p.getAllValues());
    }

    @Test void accionYUsuarioEnBlancoNoFiltran() {
        dao.getFiltered("  ", "", null, null, 5);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture());
        assertEquals(BASE + ORDEN + " LIMIT ?", sql.getValue());
        assertEquals(5, p.getValue());
    }

    // ── acciones ──
    @Test void accionesSonLasDistintasEnOrdenAlfabetico() {
        when(jdbc.queryForList("SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION", String.class))
                .thenReturn(List.of("CREAR_USUARIO", "LOGIN"));
        assertEquals(List.of("CREAR_USUARIO", "LOGIN"), dao.getAcciones());
    }
}
