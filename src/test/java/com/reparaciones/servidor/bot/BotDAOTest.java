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
import static org.mockito.ArgumentMatchers.anyString;
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
        // Salio de una fila: un apunte IMEI que lo quito o su reserva soltada (sustituida, o quitada por cancelada o marcada; «ya no
        // está» y «en Pedidos realizados» entran para quedar sin destino; nunca enviada sin IMEI, con etiqueta, no encontrado, puesto
        // en otra ni no disponible).
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("c.IMEI_ANTERIOR = b.IMEI AND (c.TIPO IN ('IMEI', 'RESERVA_SUSTITUIDA')"
                + " OR (c.TIPO = 'RESERVA_QUITADA' AND c.DETALLE IN ('CANCELADA', 'MARCADA', 'YA_NO_ESTA', 'EN_PEDIDOS_REALIZADOS')))"));
        // O un pedido de ShippyPro cancelado o cerrado sin envio que se quedo con el IMEI.
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("OR EXISTS (SELECT 1 FROM Pedido_fila x WHERE x.IMEI = b.IMEI AND x.ORIGEN = 'SHIPPYPRO'"
                + " AND (x.MARCA = 'CANCELADO' OR (x.CERRADA_EN IS NOT NULL AND x.ENVIO_FECHA IS NULL)))"));
        // Una fila abierta impide, salvo que este cancelada; tambien si el IMEI es su reserva (vender gana a devolver).
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE (f.IMEI = b.IMEI OR f.IMEI_RESERVADO = b.IMEI)"
                + " AND f.CERRADA_EN IS NULL AND (f.MARCA IS NULL OR f.MARCA <> 'CANCELADO'))"));
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
        verify(jdbc).query(eq(String.format(BotDAO.SQL_FILAS_CON_IMEI, "?, ?", "?, ?")), any(RowMapper.class), eq("350000000000001"),
                eq("350000000000002"), eq("350000000000001"), eq("350000000000002"));
        verify(jdbc).query(eq(String.format(BotDAO.SQL_VUELTAS, "?")), any(RowMapper.class), eq("350000000000001"));
        // Todas las filas con el IMEI (abiertas incluidas), con cierre, envio, impresion, falta por enviar, marca y cuando se puso.
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("f.ID_FILA, f.IMEI, f.IMEI_RESERVADO, f.RESERVADA_EN, f.CERRADA_EN, f.ENVIO_FECHA, f.IMPRESA_EN, f.FALTA_ENVIAR_EN, f.MARCA, f.MARCA_EN, f.ORIGEN"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("(SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = f.MARCA_USU) AS MARCA_POR"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.contains("(SELECT MAX(c.FECHA) FROM Pedido_fila_cambio c WHERE c.ID_FILA = f.ID_FILA"
                + " AND c.TIPO = 'IMEI' AND c.IMEI_NUEVO = f.IMEI) AS PUESTO_EN"));
        assertTrue(BotDAO.SQL_FILAS_CON_IMEI.endsWith("FROM Pedido_fila f WHERE f.IMEI IN (%s) OR f.IMEI_RESERVADO IN (%s)"));
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

    @SuppressWarnings("unchecked")
    @Test void filasConImeiCuentaLaReservaVivaComoElImeiDeLaFilaPuestoAlReservar() throws Exception {
        String reservado = "350000000000001", otro = "350000000000009";
        LocalDateTime reservadaEn = LocalDateTime.of(2026, 11, 25, 9, 0);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("IMEI")).thenReturn(otro);
        when(rs.getString("IMEI_RESERVADO")).thenReturn(reservado);
        when(rs.getTimestamp("RESERVADA_EN")).thenReturn(Timestamp.valueOf(reservadaEn));
        when(rs.getInt("ID_FILA")).thenReturn(29300);
        when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            ((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0);
            return null;
        });
        // Solo se pidio el reservado: la fila cuenta para el, no para el IMEI puesto (que no se busco).
        Map<String, List<ReglaDevoluciones.FilaConImei>> filas = dao.filasConImei(List.of(reservado));
        assertEquals(Map.of(reservado, List.of(new ReglaDevoluciones.FilaConImei(29300, null, null, null, null, false, null, null,
                reservadaEn, false))), filas);
    }

    @Test void unApunteSacaAlImeiDeSuFilaSiLoQuitaOSueltaSuReservaSinQueNadieLoPicara() {
        String imei = "350000000000001";
        LocalDateTime t = LocalDateTime.of(2026, 11, 25, 9, 0);
        assertEquals(new ReglaDevoluciones.Salida(7, t, "Ana", "IMEI"), new BotDAO.Apunte(7, t, imei, null, "IMEI", null, "Ana").salida(imei));
        // Se pico otro encima del reservado: la devolucion prioritaria.
        assertEquals(new ReglaDevoluciones.Salida(7, t, "Ana", "RESERVA"),
                new BotDAO.Apunte(7, t, imei, "350000000000009", "RESERVA_SUSTITUIDA", null, "Ana").salida(imei));
        for (String motivo : List.of("CANCELADA", "MARCADA"))
            assertEquals(new ReglaDevoluciones.Salida(7, t, "el ERP", "RESERVA"),
                    new BotDAO.Apunte(7, t, imei, null, "RESERVA_QUITADA", motivo, null).salida(imei), motivo);
        // «Ya no está en ShippyPro»: salida, pero la regla la deja sin destino (como en la 0.9.47).
        assertEquals(new ReglaDevoluciones.Salida(7, t, "ShippyPro", "YA_NO_ESTA"),
                new BotDAO.Apunte(7, t, imei, null, "RESERVA_QUITADA", "YA_NO_ESTA", null).salida(imei));
        // Ya en «Pedidos realizados» sin al-bot (vendido por otro lado, o al-bot apagado después): sin destino, una persona comprueba.
        assertEquals(new ReglaDevoluciones.Salida(7, t, "el ERP", "COMPROBAR"),
                new BotDAO.Apunte(7, t, imei, null, "RESERVA_QUITADA", "EN_PEDIDOS_REALIZADOS", null).salida(imei));
        // NO_DISPONIBLE tampoco: se vendio (p. ej. en el Simulador) o se dio de baja; devolverlo dejaria un movil fantasma.
        for (String motivo : List.of("ENVIADA_SIN_IMEI", "CON_ETIQUETA", "NO_ENCONTRADO", "PUESTO_EN_OTRA", "NO_DISPONIBLE"))
            assertEquals(null, new BotDAO.Apunte(7, t, imei, null, "RESERVA_QUITADA", motivo, null).salida(imei), motivo);
        assertEquals(null, new BotDAO.Apunte(7, t, imei, imei, "RESERVA_CONFIRMADA", null, "Ana").salida(imei));
        assertEquals(null, new BotDAO.Apunte(7, t, "350000000000009", null, "IMEI", null, "Ana").salida(imei));
        assertEquals(null, new BotDAO.Apunte(7, t, imei, null, "SIN_DESTINO", "sin caja", null).salida(imei));
    }

    @Test void unImeiSePoneEnUnaFilaTecleadoOReservado() {
        String imei = "350000000000001";
        LocalDateTime t = LocalDateTime.of(2026, 11, 25, 9, 0);
        assertTrue(new BotDAO.Apunte(7, t, null, imei, "IMEI", null, "Ana").puesto(imei));
        assertTrue(new BotDAO.Apunte(7, t, null, imei, "RESERVADO", "puesto 1", null).puesto(imei));
        assertTrue(!new BotDAO.Apunte(7, t, "350000000000009", imei, "RESERVA_SUSTITUIDA", null, "Ana").puesto(imei));
        assertTrue(!new BotDAO.Apunte(7, t, null, "350000000000009", "RESERVADO", null, null).puesto(imei));
    }

    @Test void losApuntesYLaUltimaSalidaMiranTambienLaReserva() {
        assertTrue(BotDAO.SQL_APUNTES.contains("c.TIPO IN ('IMEI', 'SIN_DESTINO', 'RESERVADO', 'RESERVA_SUSTITUIDA', 'RESERVA_QUITADA')"));
        assertTrue(BotDAO.SQL_ULTIMA_SALIDA.contains("c.IMEI_ANTERIOR = ? AND " + BotDAO.ES_SALIDA));
        // «Ya no está» nunca se devuelve solo: no da nota de devolucion.
        assertTrue(BotDAO.SQL_ULTIMA_SALIDA.contains("AND NOT (c.TIPO = 'RESERVA_QUITADA' AND c.DETALLE IN ('YA_NO_ESTA', 'EN_PEDIDOS_REALIZADOS'))"));
    }

    @SuppressWarnings("unchecked")
    @Test void ultimaSalidaDeUnaReservaSustituida() {
        String imei = "350000000000001";
        BotDAO.Apunte apunte = new BotDAO.Apunte(11, LocalDateTime.of(2026, 11, 25, 9, 0), imei, "350000000000009", "RESERVA_SUSTITUIDA", null, "Ana");
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_SALIDA), any(RowMapper.class), eq(imei))).thenReturn(List.of(apunte));
        when(jdbc.query(eq(BotDAO.SQL_ULTIMA_CANCELACION), any(RowMapper.class), eq(imei))).thenReturn(List.of());
        assertEquals(Optional.of(new ReglaDevoluciones.Salida(11, apunte.fecha(), "Ana", "RESERVA")), dao.ultimaSalida(imei));
    }
}
