package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOARevisarTest {

    private static final String IMEI = "351111112222333";

    @SuppressWarnings("unchecked")
    private JdbcTemplate conTelefono(String estado, int abiertos) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(estado));   // singletonList admite null (List.of no)
        when(jdbc.queryForObject(contains("COUNT(*) FROM Reparacion"), eq(Integer.class), eq(IMEI))).thenReturn(abiertos);
        return jdbc;
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate sinTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        return jdbc;
    }

    private void verificarSinCambios(JdbcTemplate jdbc) {
        verify(jdbc, never()).update(contains("UPDATE Telefono SET ESTADO"), eq(IMEI));
        verify(jdbc, never()).update(contains("INSERT INTO Revision"), eq(IMEI));
    }

    @Test void recibidoPasaYCreaRevision() {
        JdbcTemplate jdbc = conTelefono("RECIBIDO", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.PASADO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ?", IMEI);
        verify(jdbc).update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", IMEI);
    }

    @Test void okPasaConAvisoYCreaRevisionNueva() {
        JdbcTemplate jdbc = conTelefono("OK", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ?", IMEI);
        verify(jdbc).update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", IMEI);
    }

    @Test void yaEnRevisionNoTocaNada() {
        JdbcTemplate jdbc = conTelefono("EN_REVISION", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.YA_ESTABA, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void conTrabajoAbiertoRechazado() {
        JdbcTemplate jdbc = conTelefono("RECIBIDO", 2);
        assertEquals(RevisionDAO.ResultadoARevisar.EN_REPARACION, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void bloqueadoRechazado() {
        JdbcTemplate jdbc = conTelefono("BLOQUEADO", 0);
        assertEquals(RevisionDAO.ResultadoARevisar.BLOQUEADO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void enviadoYDesguaceFuera() {
        assertEquals(RevisionDAO.ResultadoARevisar.FUERA, new RevisionDAO(conTelefono("ENVIADO", 0)).pasarARevisar(IMEI));
        assertEquals(RevisionDAO.ResultadoARevisar.FUERA, new RevisionDAO(conTelefono("DESGUACE", 0)).pasarARevisar(IMEI));
    }

    @Test void historicoSinEstadoRechazado() {
        JdbcTemplate jdbc = conTelefono(null, 0);
        assertEquals(RevisionDAO.ResultadoARevisar.HISTORICO, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }

    @Test void imeiDesconocidoNoExiste() {
        JdbcTemplate jdbc = sinTelefono();
        assertEquals(RevisionDAO.ResultadoARevisar.NO_EXISTE, new RevisionDAO(jdbc).pasarARevisar(IMEI));
        verificarSinCambios(jdbc);
    }
}
