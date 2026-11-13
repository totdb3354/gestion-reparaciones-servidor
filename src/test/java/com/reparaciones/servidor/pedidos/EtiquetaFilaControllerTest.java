package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EtiquetaFilaControllerTest {

    private final FilasDAO dao = mock(FilasDAO.class);
    private final EtiquetaShippyPro etiquetas = mock(EtiquetaShippyPro.class);
    private final EtiquetaFilaController controller = new EtiquetaFilaController(dao, etiquetas);

    private static FilaPedido fila(Long envioId) {
        return new FilaPedido(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null, "402-0000000-0000005",
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, null, null, null, null, null, envioId, (LocalDateTime) null);
    }

    @Test void unaFilaQueNoExisteEs404() {
        when(dao.porId(9)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.etiqueta(9)).getStatusCode().value());
        verifyNoInteractions(etiquetas);
    }

    @Test void unaFilaSinEnvioNoTieneEtiquetaYEs404ConSuMensaje() {
        when(dao.porId(5)).thenReturn(Optional.of(fila(null)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.etiqueta(5));
        assertEquals(404, e.getStatusCode().value());
        assertEquals("Este pedido no tiene etiqueta generada", e.getReason());
        verifyNoInteractions(etiquetas);
    }

    @Test void conEnvioDevuelveElPdfConSusCabeceras() {
        byte[] pdf = "%PDF-1.4 x".getBytes();
        when(dao.porId(5)).thenReturn(Optional.of(fila(123456L)));
        when(etiquetas.pdf(5, 123456L)).thenReturn(pdf);

        ResponseEntity<byte[]> r = controller.etiqueta(5);

        assertEquals(200, r.getStatusCode().value());
        assertArrayEquals(pdf, r.getBody());
        assertEquals("application/pdf", r.getHeaders().getContentType().toString());
        assertEquals("inline; filename=\"etiqueta.pdf\"", r.getHeaders().getFirst("Content-Disposition"));
        assertEquals("no-store", r.getHeaders().getCacheControl());
    }

    @Test void unFalloDeLaDescargaSePropagaComo502() {
        when(dao.porId(5)).thenReturn(Optional.of(fila(1L)));
        when(etiquetas.pdf(5, 1L)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, EtiquetaShippyPro.MSG_FALLO));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> controller.etiqueta(5)).getStatusCode().value());
    }
}
