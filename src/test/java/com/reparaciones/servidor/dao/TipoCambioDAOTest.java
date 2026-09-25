package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Si Frankfurter falla, 503 con un mensaje que el usuario entiende (spec 4b §4.3); antes era un 500 genérico. */
class TipoCambioDAOTest {

    private static final String MSG_USD = "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HttpClient http = mock(HttpClient.class);
    private final TipoCambioDAO dao = new TipoCambioDAO(jdbc, http);

    @SuppressWarnings("unchecked")
    private void frankfurterResponde(String cuerpo) throws Exception {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body()).thenReturn(cuerpo);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
    }

    private void falla503() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.getTasa("USD"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals(MSG_USD, e.getReason());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test void eurEsUnoSinConsultarNada() {
        assertEquals(1.0, dao.getTasa("EUR"));
        verifyNoInteractions(jdbc, http);
    }

    @Test void conLaTasaDeHoyEnCacheNoLlamaAFrankfurter() {
        when(jdbc.query(contains("FROM TipoCambio"), any(RowMapper.class), eq("USD"), any())).thenReturn(List.of(1.1367));
        assertEquals(1.1367, dao.getTasa("USD"));
        verifyNoInteractions(http);
    }

    @Test void frankfurterBienGuardaLaTasaDelDia() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{\"USD\":1.1367}}");
        assertEquals(1.1367, dao.getTasa("usd"));
        verify(jdbc).update(contains("INSERT INTO TipoCambio"), eq("USD"), any(), eq(1.1367), eq(1.1367));
    }

    @Test void frankfurterCaidoEs503ConElMensaje() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("sin red"));
        falla503();
    }

    @Test void respuestaSinLaDivisaEs503() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{}}");
        falla503();
    }

    /** Important 1 de la revisión final: una tasa 0 (o no numérica) no se cachea y sale como 503, no como
     *  una división por cero silenciosa el resto del día. */
    @Test void tasaCeroEs503SinCachear() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{\"USD\":0}}");
        falla503();
    }

    @Test void tasaNoNumericaEs503SinCachear() throws Exception {
        frankfurterResponde("{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{\"USD\":\"x\"}}");
        falla503();
    }
}
