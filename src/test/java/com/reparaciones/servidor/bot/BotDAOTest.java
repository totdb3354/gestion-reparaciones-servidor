package com.reparaciones.servidor.bot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Test void candidatosSaleDeLosDescontadosQueSalieronDeUnaFilaYHoyNoEstanEnNingunaFilaAbierta() {
        LocalDateTime desde = LocalDateTime.of(2026, 8, 27, 10, 0);
        when(jdbc.queryForList(BotDAO.SQL_CANDIDATOS, String.class, Timestamp.valueOf(desde), 1000)).thenReturn(List.of("350000000000001"));
        assertEquals(List.of("350000000000001"), dao.candidatos(desde, 1000));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK'"));
        // Condicion 4 de la regla: solo los que NSYS tiene hoy en Pedidos realizados.
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("EXISTS (SELECT 1 FROM Nsys_movil n WHERE n.IMEI_SN = b.IMEI"
                + " AND n.ESTADO = 'DE_PEDIDO' AND n.LOCATION = 'Pedidos realizados')"));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = b.IMEI"));
        // O un pedido de ShippyPro cancelado o cerrado sin envio que se quedo con el IMEI.
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("OR EXISTS (SELECT 1 FROM Pedido_fila x WHERE x.IMEI = b.IMEI AND x.ORIGEN = 'SHIPPYPRO'"
                + " AND (x.MARCA = 'CANCELADO' OR (x.CERRADA_EN IS NOT NULL AND x.ENVIO_FECHA IS NULL)))"));
        // Una fila abierta impide, salvo que este cancelada.
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE f.IMEI = b.IMEI AND f.CERRADA_EN IS NULL"
                + " AND (f.MARCA IS NULL OR f.MARCA <> 'CANCELADO'))"));
    }

    @Test void sinImeisLasLecturasNoConsultan() {
        assertEquals(Map.of(), dao.pasos(List.of()));
        assertEquals(List.of(), dao.apuntes(List.of()));
        assertEquals(Map.of(), dao.entradasEnPedidos(List.of()));
        assertEquals(Map.of(), dao.filasConImei(List.of()));
        assertEquals(Map.of(), dao.vueltas(List.of()));
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test void lasLecturasConImeisUsanUnInConSusMarcas() {
        dao.pasos(List.of("350000000000001", "350000000000002"));
        verify(jdbc).query(eq(BotDAO.SQL_PASOS + "?, ?" + BotDAO.ORDEN_PASOS), any(RowMapper.class), eq("350000000000001"), eq("350000000000002"));
    }

    @SuppressWarnings("unchecked")
    @Test void filasYVueltasUsanUnInConSusMarcasYSusCondiciones() {
        dao.filasConImei(List.of("350000000000001", "350000000000002"));
        dao.vueltas(List.of("350000000000001"));
        verify(jdbc).query(eq(String.format(BotDAO.SQL_FILAS_CON_IMEI, "?, ?")), any(RowMapper.class), eq("350000000000001"), eq("350000000000002"));
        verify(jdbc).query(eq(String.format(BotDAO.SQL_VUELTAS, "?")), any(RowMapper.class), eq("350000000000001"));
        // Todas las filas con el IMEI (abiertas incluidas), con cierre, envio, impresion, falta por enviar, marca y cuando se puso.
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("f.ID_FILA, f.IMEI, f.CERRADA_EN, f.ENVIO_FECHA, f.IMPRESA_EN, f.FALTA_ENVIAR_EN, f.MARCA, f.MARCA_EN, f.ORIGEN"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("(SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = f.MARCA_USU) AS MARCA_POR"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("(SELECT MAX(c.FECHA) FROM Pedido_fila_cambio c WHERE c.ID_FILA = f.ID_FILA"
                + " AND c.TIPO = 'IMEI' AND c.IMEI_NUEVO = f.IMEI) AS PUESTO_EN"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.endsWith("FROM Pedido_fila f WHERE f.IMEI IN (%s)"));
        assertTrue(BotDAO.SQL_VUELTAS.contains("TIPO IN ('VENTA_DESHECHA', 'REENTRADA')"));
        assertTrue(BotDAO.SQL_VUELTAS.contains("TIPO = 'UBICACION' AND (DE = 'Pedidos realizados' OR COALESCE(A_NSYS, '') = '')"));
    }

    @SuppressWarnings("unchecked")
    @Test void ultimaSalidaEsLaMasRecienteEntreElApunteYLaCancelacion() {
        String imei = "350000000000001";
        BotDAO.Apunte apunte = new BotDAO.Apunte(11, LocalDateTime.of(2026, 11, 24, 18, 7), imei, null, "IMEI", "LIBRO", null);
        ReglaDevoluciones.Salida cancelacion = new ReglaDevoluciones.Salida(29200, LocalDateTime.of(2026, 11, 24, 19, 0), "Juan", "CANCELADO");
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_SALIDA), any(RowMapper.class), eq(imei))).thenReturn(List.of(apunte));
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_CANCELACION), any(RowMapper.class), eq(imei))).thenReturn(List.of(cancelacion));
        assertEquals(Optional.of(cancelacion), dao.ultimaSalida(imei));

        ReglaDevoluciones.Salida vieja = new ReglaDevoluciones.Salida(29200, LocalDateTime.of(2026, 11, 20, 9, 0), "Juan", "CANCELADO");
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_CANCELACION), any(RowMapper.class), eq(imei))).thenReturn(List.of(vieja));
        assertEquals(Optional.of(new ReglaDevoluciones.Salida(11, apunte.fecha(), "el libro de ventas", "IMEI")), dao.ultimaSalida(imei));

        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_SALIDA), any(RowMapper.class), eq(imei))).thenReturn(List.of());
        assertEquals(Optional.of(vieja), dao.ultimaSalida(imei));
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_CANCELACION), any(RowMapper.class), eq(imei))).thenReturn(List.of());
        assertEquals(Optional.empty(), dao.ultimaSalida(imei));
        assertTrue(BotDAO.SQL_ULTIMA_CANCELACION.contains("f.IMEI = ? AND f.ORIGEN = 'SHIPPYPRO' AND f.MARCA = 'CANCELADO'"));
        // Una cancelada con etiqueta impresa salio: nunca lleva nota de «pedido cancelado» (igual que en la regla).
        assertTrue(BotDAO.SQL_ULTIMA_CANCELACION.contains("AND f.MARCA_EN IS NOT NULL AND f.IMPRESA_EN IS NULL"));
    }

    @SuppressWarnings("unchecked")
    @Test void filasConImeiMapeaLaCancelacionSoloSiEstaCanceladaYElOrigenHistorico() throws Exception {
        String imei = "350000000000001";
        LocalDateTime marca = LocalDateTime.of(2026, 11, 24, 19, 0);
        LocalDateTime cierre = LocalDateTime.of(2026, 11, 20, 12, 0);

        java.sql.ResultSet cancelada = mock(java.sql.ResultSet.class);
        when(cancelada.getString("IMEI")).thenReturn(imei);
        when(cancelada.getInt("ID_FILA")).thenReturn(29200);
        when(cancelada.getString("MARCA")).thenReturn("CANCELADO");
        when(cancelada.getTimestamp("MARCA_EN")).thenReturn(Timestamp.valueOf(marca));
        when(cancelada.getString("MARCA_POR")).thenReturn("Juan");
        when(cancelada.getString("ORIGEN")).thenReturn("SHIPPYPRO");

        // Con MARCA_EN y MARCA_POR rellenos pero sin MARCA CANCELADO: no es una cancelacion. ORIGEN HISTORICO.
        java.sql.ResultSet historica = mock(java.sql.ResultSet.class);
        when(historica.getString("IMEI")).thenReturn(imei);
        when(historica.getInt("ID_FILA")).thenReturn(100);
        when(historica.getString("MARCA")).thenReturn(null);
        when(historica.getTimestamp("MARCA_EN")).thenReturn(Timestamp.valueOf(marca));
        when(historica.getString("MARCA_POR")).thenReturn("Juan");
        when(historica.getTimestamp("CERRADA_EN")).thenReturn(Timestamp.valueOf(cierre));
        when(historica.getString("ORIGEN")).thenReturn("HISTORICO");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            RowMapper<Object> mapper = inv.getArgument(1);
            mapper.mapRow(cancelada, 0);
            mapper.mapRow(historica, 1);
            return null;
        });

        List<ReglaDevoluciones.FilaConImei> filas = dao.filasConImei(List.of(imei)).get(imei);
        assertEquals(new ReglaDevoluciones.FilaConImei(29200, null, null, null, null, true, marca, "Juan", null, false), filas.get(0));
        assertEquals(new ReglaDevoluciones.FilaConImei(100, cierre, null, null, null, false, null, null, null, true), filas.get(1));
    }
}
