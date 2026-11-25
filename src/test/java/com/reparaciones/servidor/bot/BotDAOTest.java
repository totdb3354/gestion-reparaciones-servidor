package com.reparaciones.servidor.bot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Escrituras de BotDAO (JdbcTemplate simulado; IMEI inventados). */
class BotDAOTest {

    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 25, 10, 12, 3);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 10, 15);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BotDAO dao = new BotDAO(jdbc);

    @Test void guardarInsertaIgnorandoRepetidosYDevuelveSiEntro() {
        when(jdbc.update(eq(BotDAO.SQL_GUARDAR), any(Object[].class))).thenReturn(1, 0);
        MovimientoBot m = new MovimientoBot("350000000000001", "DESCONTADO", "OK", "CAJA LISTOS", "Pedidos realizados", null, BOT);
        assertEquals(1, dao.guardar("run-1", m, AHORA));
        assertEquals(0, dao.guardar("run-1", m, AHORA));
        verify(jdbc, times(2)).update(BotDAO.SQL_GUARDAR, "run-1", "350000000000001", "DESCONTADO", "OK", "CAJA LISTOS",
                "Pedidos realizados", null, Timestamp.valueOf(BOT), Timestamp.valueOf(AHORA));
    }

    @Test void elSqlDeGuardarEsInsertIgnore() {
        assertEquals("INSERT IGNORE INTO Bot_movimiento (RUN_ID, IMEI, ACCION, RESULTADO, ORIGEN_NSYS, DESTINO_NSYS, MOTIVO, FECHA, RECIBIDO)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", BotDAO.SQL_GUARDAR);
    }

    @Test void apuntarEscribeSinUsuarioConElImeiQueSalio() {
        dao.apuntar(29116, AHORA, "350000000000001", "SIN_DESTINO", "sin caja");
        verify(jdbc).update("INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
                + " VALUES (?, NULL, ?, ?, NULL, ?, ?)", 29116, Timestamp.valueOf(AHORA), "350000000000001", "SIN_DESTINO", "sin caja");
    }

    @Test void candidatosSaleDeLosDescontadosQueSalieronDeUnaFilaYHoyNoEstanEnNinguna() {
        LocalDateTime desde = LocalDateTime.of(2026, 8, 27, 10, 0);
        when(jdbc.queryForList(BotDAO.SQL_CANDIDATOS, String.class, Timestamp.valueOf(desde), 1000)).thenReturn(List.of("350000000000001"));
        assertEquals(List.of("350000000000001"), dao.candidatos(desde, 1000));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK'"));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = b.IMEI"));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE f.IMEI = b.IMEI)"));
    }

    @Test void sinImeisLasLecturasNoConsultan() {
        assertEquals(Map.of(), dao.pasos(List.of()));
        assertEquals(List.of(), dao.apuntes(List.of()));
        assertEquals(Map.of(), dao.entradasEnPedidos(List.of()));
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void lasLecturasConImeisUsanUnInConSusMarcas() {
        dao.pasos(List.of("350000000000001", "350000000000002"));
        verify(jdbc).query(eq(BotDAO.SQL_PASOS + "?, ?" + BotDAO.ORDEN_PASOS), any(RowMapper.class), eq("350000000000001"), eq("350000000000002"));
    }
}
