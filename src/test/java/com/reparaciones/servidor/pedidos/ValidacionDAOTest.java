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

/** Validar SKU en el DAO (JdbcTemplate simulado; datos inventados). */
@SuppressWarnings("unchecked")
class ValidacionDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final Timestamp T_AHORA = Timestamp.valueOf(AHORA);
    private static final String IMEI = "350000000000001";
    private static final String OTRO = "350000000000002";
    private static final String PONER = "UPDATE Pedido_fila SET VALIDADA_IMEI = ?, VALIDADA_EN = ?, VALIDADA_USU = ? WHERE ID_FILA = ?";
    private static final String QUITAR = "UPDATE Pedido_fila SET VALIDADA_IMEI = NULL, VALIDADA_EN = NULL, VALIDADA_USU = NULL WHERE ID_FILA = ?";
    private static final String INS = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO, DETALLE) VALUES (?, ?, ?, ?, ?)";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private void fila(String origen, String imei, String validadaImei) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_VALIDACION_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            when(rs.getString("VALIDADA_IMEI")).thenReturn(validadaImei);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    private void filaParaPonerImei(String imei, String validadaImei) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
            when(rs.getString("VALIDADA_IMEI")).thenReturn(validadaImei);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void validarBloqueaLaFilaLaEscribeConUsuarioYHoraYApuntaValidadaConElMotivo() {
        for (String motivo : List.of("MODELO", "COLOR_O_GRADO", "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO")) {
            clearInvocations(jdbc);
            fila("SHIPPYPRO", IMEI, null);
            assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerValidacion(5, true, IMEI, motivo, null, 9, AHORA), motivo);
            var orden = inOrder(jdbc);
            orden.verify(jdbc).query(eq(FilasDAO.SQL_FILA_VALIDACION_BLOQUEADA), any(RowMapper.class), eq(5));
            orden.verify(jdbc).update(PONER, IMEI, T_AHORA, 9, 5);
            orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "VALIDADA", motivo);
        }
    }

    @Test void validarLoYaValidadoParaEseImeiNoEscribeNiApunta() {
        fila("SHIPPYPRO", IMEI, IMEI);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void validarConUnaValidacionDeOtroImeiLaSustituye() {
        fila("SHIPPYPRO", IMEI, OTRO);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        verify(jdbc).update(PONER, IMEI, T_AHORA, 9, 5);
    }

    @Test void siElImeiYaNoEsElQueSeMiroEsCambiada() {
        fila("SHIPPYPRO", OTRO, null);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        fila("SHIPPYPRO", null, null);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        verify(jdbc, never()).update(eq(PONER), any(), any(), any(), any());
    }

    @Test void imeiAnteriorQueNoCoincideEsCambiadaYVacioEsperaLaFilaSinImei() {
        fila("SHIPPYPRO", IMEI, null);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerValidacion(5, true, IMEI, "MODELO", OTRO, 9, AHORA));
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerValidacion(5, true, IMEI, "MODELO", "", 9, AHORA));
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerValidacion(5, false, null, null, OTRO, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerValidacion(5, true, IMEI, "MODELO", IMEI, 9, AHORA));
    }

    @Test void quitarBorraLaValidacionYApuntaValidacionQuitada() {
        fila("SHIPPYPRO", IMEI, IMEI);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerValidacion(5, false, null, null, null, 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).update(QUITAR, 5);
        orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "VALIDACION_QUITADA", null);
    }

    @Test void quitarSinValidacionNoHaceNada() {
        fila("SHIPPYPRO", IMEI, null);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerValidacion(5, false, null, null, null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void quitarUnaValidacionDeOtroImeiLaBorraEnSilencio() {
        fila("SHIPPYPRO", IMEI, OTRO);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerValidacion(5, false, null, null, null, 9, AHORA));
        verify(jdbc).update(QUITAR, 5);
        verify(jdbc, never()).update(eq(INS), any(), any(), any(), any(), any());
    }

    @Test void filaInexistenteOHistorico() {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_VALIDACION_BLOQUEADA), any(RowMapper.class), eq(5))).thenReturn(new ArrayList<>());
        assertEquals(FilasDAO.Resultado.NO_EXISTE, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        fila("HISTORICO", IMEI, null);
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerValidacion(5, true, IMEI, "MODELO", null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void cambiarElImeiBorraLaValidacionYLaApuntaConElUsuarioQueLoCambio() {
        filaParaPonerImei(IMEI, IMEI);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, OTRO, null, 9, AHORA));
        verify(jdbc).update(QUITAR, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "VALIDACION_QUITADA", "al cambiar el IMEI");
    }

    @Test void quitarElImeiBorraLaValidacion() {
        filaParaPonerImei(IMEI, IMEI);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, null, null, 9, AHORA));
        verify(jdbc).update(QUITAR, 5);
    }

    @Test void ponerElMismoImeiOUnaFilaSinValidacionNoTocaLaValidacion() {
        filaParaPonerImei(IMEI, IMEI);
        dao.ponerImei(5, IMEI, null, 9, AHORA);
        filaParaPonerImei(IMEI, null);
        dao.ponerImei(5, OTRO, null, 9, AHORA);
        verify(jdbc, never()).update(QUITAR, 5);
    }
}
