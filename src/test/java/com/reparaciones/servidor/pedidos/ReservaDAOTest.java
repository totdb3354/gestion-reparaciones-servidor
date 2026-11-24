package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Los metodos de {@link FilasDAO} de la reserva de IMEI (spec reserva §5 y §8). Datos inventados. */
@SuppressWarnings("unchecked")
class ReservaDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final Timestamp T = Timestamp.valueOf(AHORA);
    private static final String IMEI_1 = "350000000000001";
    private static final String IMEI_2 = "350000000000002";
    private static final String BLOQUEO = "SELECT ORIGEN, IMEI, MARCA, CERRADA_EN, IMEI_RESERVADO, SIN_STOCK_EN FROM Pedido_fila"
            + " WHERE ID_FILA = ? FOR UPDATE";
    private static final String RESERVAR = "UPDATE Pedido_fila SET IMEI_RESERVADO = ?, RESERVADA_EN = ?, SIN_STOCK_EN = NULL WHERE ID_FILA = ?";
    private static final String QUITAR = "UPDATE Pedido_fila SET IMEI_RESERVADO = NULL, RESERVADA_EN = NULL WHERE ID_FILA = ?";
    private static final String SIN_STOCK = "UPDATE Pedido_fila SET SIN_STOCK_EN = ? WHERE ID_FILA = ?";
    private static final String APUNTE = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String APUNTE_TIPO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    /** La fila 5 bloqueada con estos datos. */
    private void bloqueada(String origen, String imei, String marca, LocalDateTime cerradaEn, String reservado, LocalDateTime sinStockEn) {
        when(jdbc.query(eq(BLOQUEO), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("MARCA")).thenReturn(marca);
            when(rs.getTimestamp("CERRADA_EN")).thenReturn(cerradaEn == null ? null : Timestamp.valueOf(cerradaEn));
            when(rs.getString("IMEI_RESERVADO")).thenReturn(reservado);
            when(rs.getTimestamp("SIN_STOCK_EN")).thenReturn(sinStockEn == null ? null : Timestamp.valueOf(sinStockEn));
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    /** Viva, sin IMEI, sin marca, sin reserva y sin «sin stock». */
    private void esperando() {
        bloqueada("SHIPPYPRO", null, null, null, null, null);
    }

    private void noEscribe() {
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    // ---------------------------------------------------------------- conReservaOEsperando

    @Test void conReservaOEsperandoPideLasVivasSinMarcaNiImeiYLasReservadas() {
        dao.conReservaOEsperando();
        verify(jdbc).query(eq(FilasDAO.SQL_FILAS + " WHERE (ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NULL AND MARCA IS NULL"
                + " AND (IMEI IS NULL OR IMEI = '')) OR IMEI_RESERVADO IS NOT NULL ORDER BY ID_FILA"), any(RowMapper.class));
    }

    // ---------------------------------------------------------------- reservar

    @Test void reservarBloqueaPoneLaReservaBorraSinStockYApuntaReservado() {
        esperando();
        assertTrue(dao.reservar(5, IMEI_1, "Puesto 1 · Caja · Listos · A-", AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update(RESERVAR, IMEI_1, T, 5);
        orden.verify(jdbc).update(APUNTE, 5, null, T, null, IMEI_1, CambioImei.RESERVADO, "Puesto 1 · Caja · Listos · A-");
    }

    @Test void reservarGuardaElImeiSinEspaciosYCortaElPorque() {
        esperando();
        assertTrue(dao.reservar(5, " " + IMEI_1 + " ", "x".repeat(300), AHORA));
        verify(jdbc).update(RESERVAR, IMEI_1, T, 5);
        verify(jdbc).update(APUNTE, 5, null, T, null, IMEI_1, CambioImei.RESERVADO, "x".repeat(250));
    }

    @Test void reservarNoHaceNadaSiLaFilaNoExiste() {
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA));
        noEscribe();
    }

    @Test void reservarNoHaceNadaSiLaFilaYaNoEsperaTelefono() {
        bloqueada("HISTORICO", null, null, null, null, null);
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA), "del historico");
        bloqueada("SHIPPYPRO", IMEI_2, null, null, null, null);
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA), "ya tiene IMEI");
        bloqueada("SHIPPYPRO", null, "CANCELADO", null, null, null);
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA), "con marca");
        bloqueada("SHIPPYPRO", null, null, AHORA.minusHours(1), null, null);
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA), "cerrada");
        bloqueada("SHIPPYPRO", null, null, null, IMEI_2, null);
        assertFalse(dao.reservar(5, IMEI_1, "p", AHORA), "ya reservada");
        noEscribe();
    }

    @Test void reservarConImeiVacioCuentaComoSinImei() {
        bloqueada("SHIPPYPRO", " ", null, null, null, null);
        assertTrue(dao.reservar(5, IMEI_1, "p", AHORA));
    }

    @Test void reservarNoAdmiteUnImeiQueNoCabeEnLaColumna() {
        esperando();
        assertFalse(dao.reservar(5, "A".repeat(21), "p", AHORA));
        assertFalse(dao.reservar(5, " ", "p", AHORA));
        assertFalse(dao.reservar(5, null, "p", AHORA));
        noEscribe();
    }

    // ---------------------------------------------------------------- soltarReserva

    @Test void soltarReservaBorraLaReservaYApuntaElMotivoConElReservado() {
        bloqueada("SHIPPYPRO", null, "CANCELADO", null, IMEI_1, null);
        dao.soltarReserva(5, Reservas.CANCELADA, AHORA);
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update(QUITAR, 5);
        orden.verify(jdbc).update(APUNTE, 5, null, T, IMEI_1, null, CambioImei.RESERVA_QUITADA, "CANCELADA");
    }

    @Test void soltarReservaSinReservaNoHaceNada() {
        esperando();
        dao.soltarReserva(5, Reservas.NO_DISPONIBLE, AHORA);
        dao.soltarReserva(6, Reservas.NO_DISPONIBLE, AHORA); // no existe
        noEscribe();
    }

    // ---------------------------------------------------------------- resolverReservaPuesta

    @Test void resolverConElMismoImeiConfirmaConElUsuarioYBorraLaReserva() {
        bloqueada("SHIPPYPRO", IMEI_1.toLowerCase() + " ", null, null, IMEI_1, null);
        dao.resolverReservaPuesta(5, 9, AHORA);
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update(QUITAR, 5);
        orden.verify(jdbc).update(APUNTE, 5, 9, T, IMEI_1, IMEI_1.toLowerCase() + " ", CambioImei.RESERVA_CONFIRMADA, null);
    }

    @Test void resolverConOtroImeiSustituyeConElReservadoComoAnterior() {
        bloqueada("SHIPPYPRO", IMEI_2, null, null, IMEI_1, null);
        dao.resolverReservaPuesta(5, null, AHORA);
        verify(jdbc).update(QUITAR, 5);
        verify(jdbc).update(APUNTE, 5, null, T, IMEI_1, IMEI_2, CambioImei.RESERVA_SUSTITUIDA, null);
    }

    @Test void resolverSinImeiOSinReservaNoHaceNada() {
        bloqueada("SHIPPYPRO", null, null, null, IMEI_1, null);
        dao.resolverReservaPuesta(5, null, AHORA);
        bloqueada("SHIPPYPRO", "", null, null, IMEI_1, null);
        dao.resolverReservaPuesta(5, null, AHORA);
        bloqueada("SHIPPYPRO", IMEI_2, null, null, null, null);
        dao.resolverReservaPuesta(5, null, AHORA);
        dao.resolverReservaPuesta(6, null, AHORA); // no existe
        noEscribe();
    }

    // ---------------------------------------------------------------- apuntarSinStock

    @Test void apuntarSinStockPoneLaHoraYApuntaSinStockUnaVez() {
        esperando();
        dao.apuntarSinStock(5, AHORA);
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update(SIN_STOCK, T, 5);
        orden.verify(jdbc).update(APUNTE_TIPO, 5, null, T, CambioImei.SIN_STOCK);
    }

    @Test void apuntarSinStockNoRepiteNiApuntaEnUnaFilaQueYaNoEspera() {
        bloqueada("SHIPPYPRO", null, null, null, null, AHORA.minusDays(1));
        dao.apuntarSinStock(5, AHORA);
        bloqueada("SHIPPYPRO", null, null, null, IMEI_1, null);
        dao.apuntarSinStock(5, AHORA);
        bloqueada("SHIPPYPRO", IMEI_1, null, null, null, null);
        dao.apuntarSinStock(5, AHORA);
        bloqueada("SHIPPYPRO", null, "ESPERA_STOCK", null, null, null);
        dao.apuntarSinStock(5, AHORA);
        bloqueada("SHIPPYPRO", null, null, AHORA, null, null);
        dao.apuntarSinStock(5, AHORA);
        dao.apuntarSinStock(6, AHORA); // no existe
        noEscribe();
    }
}
