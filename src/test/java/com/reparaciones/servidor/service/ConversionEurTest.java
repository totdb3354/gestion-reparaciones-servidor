package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.TipoCambioDAO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** P3 (spec 4b §4.3): Frankfurter da unidades de divisa por 1 EUR, así que el importe en euros es precio / tasa,
 *  redondeado a 2 decimales HALF_UP (el cliente JavaFX multiplicaba). */
class ConversionEurTest {

    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final ConversionEur conversion = new ConversionEur(tipoCambio);

    @Test void enEurosDevuelveElPrecioSinConsultarLaTasa() {
        assertEquals(12.345, conversion.aEuros(12.345, "EUR"));
        assertEquals(12.345, conversion.aEuros(12.345, " eur "));
        verifyNoInteractions(tipoCambio);
    }

    @Test void enDolaresDivideEntreLaTasaYRedondeaADosDecimales() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        assertEquals(8.80, conversion.aEuros(10.0, "USD"));   // 8.7974… → 8.80
        assertEquals(0.0, conversion.aEuros(0.0, "USD"));
    }

    @Test void elRedondeoEsHalfUpYLaDivisaSeNormaliza() {
        when(tipoCambio.getTasa("USD")).thenReturn(2.0);
        assertEquals(0.13, conversion.aEuros(0.25, " usd "));  // 0.125 → 0.13 (HALF_EVEN daría 0.12)
        verify(tipoCambio).getTasa("USD");
    }

    @Test void sinTasaPropagaEl503() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> conversion.aEuros(10.0, "USD"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }
}
