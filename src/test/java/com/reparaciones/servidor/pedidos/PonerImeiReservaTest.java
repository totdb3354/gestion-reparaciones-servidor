package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec reserva §7: poner el IMEI desde la celda en una fila con reserva la confirma o la sustituye, con el usuario y en la misma transaccion. */
class PonerImeiReservaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final Timestamp T = Timestamp.valueOf(AHORA);
    private static final String IMEI_1 = "350000000000001";
    private static final String IMEI_2 = "350000000000002";
    private static final String APUNTE = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String QUITAR = "UPDATE Pedido_fila SET IMEI_RESERVADO = NULL, RESERVADA_EN = NULL WHERE ID_FILA = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    @SuppressWarnings("unchecked")
    private void fila(String imei, String reservado) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
            when(rs.getString("IMEI_RESERVADO")).thenReturn(reservado);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void poneElReservadoLoConfirmaConElUsuario() {
        fila(null, IMEI_1);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, IMEI_1, null, 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", IMEI_1, 5);
        orden.verify(jdbc).update(startsWith("INSERT INTO Pedido_fila_cambio"), eq(5), eq(9), eq(T), isNull(), eq(IMEI_1));
        orden.verify(jdbc).update(QUITAR, 5);
        orden.verify(jdbc).update(APUNTE, 5, 9, T, IMEI_1, IMEI_1, CambioImei.RESERVA_CONFIRMADA, null);
    }

    @Test void poneOtroLaSustituyeConElUsuario() {
        fila(null, IMEI_1);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, IMEI_2, null, 9, AHORA));
        verify(jdbc).update(QUITAR, 5);
        verify(jdbc).update(APUNTE, 5, 9, T, IMEI_1, IMEI_2, CambioImei.RESERVA_SUSTITUIDA, null);
    }

    @Test void sinReservaSoloEscribeLoDeSiempre() {
        fila(null, null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, IMEI_2, null, 9, AHORA));
        verify(jdbc, never()).update(eq(QUITAR), any(Object[].class));
        verify(jdbc, never()).update(eq(APUNTE), any(Object[].class));
    }

    @Test void quitarElImeiNoTocaLaReserva() {
        fila(IMEI_1, null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, null, IMEI_1, 9, AHORA));
        verify(jdbc, never()).update(eq(QUITAR), any(Object[].class));
        verify(jdbc, never()).update(eq(APUNTE), any(Object[].class));
    }

    @Test void siElCambioNoSeHaceLaReservaSeQueda() {
        fila(IMEI_2, IMEI_1);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerImei(5, IMEI_1, "", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
