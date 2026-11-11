package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricoControllerTest {

    private final CargaHistorico carga = mock(CargaHistorico.class);
    private final HistoricoController controller = new HistoricoController(carga);
    private static final CargaHistorico.EstadoCarga EN_CURSO = new CargaHistorico.EstadoCarga(CargaHistorico.Estado.EN_CURSO,
            LocalDateTime.of(2026, 11, 11, 10, 0), null, null, null);

    @Test void postArrancaLaCargaYResponde202ConElEstado() {
        when(carga.iniciar()).thenReturn(EN_CURSO);
        ResponseEntity<CargaHistorico.EstadoCarga> r = controller.cargar();
        assertEquals(202, r.getStatusCode().value());
        assertSame(EN_CURSO, r.getBody());
    }

    @Test void postConOtraEnMarchaEs409ConUnTextoClaro() {
        when(carga.iniciar()).thenThrow(new CargaHistorico.YaEnCurso(CargaHistorico.MSG_EN_CURSO));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, controller::cargar);
        assertEquals(409, e.getStatusCode().value());
        assertEquals(CargaHistorico.MSG_EN_CURSO, e.getReason());
    }

    @Test void postSinElAjusteDeLaHojaEs400ConUnTextoClaro() {
        when(carga.iniciar()).thenThrow(new CargaHistorico.NoConfigurada(CargaHistorico.MSG_NO_CONFIGURADA));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, controller::cargar);
        assertEquals(400, e.getStatusCode().value());
        assertEquals(CargaHistorico.MSG_NO_CONFIGURADA, e.getReason());
    }

    @Test void getDevuelveElEstadoDeLaUltimaCarga() {
        when(carga.estado()).thenReturn(EN_CURSO);
        assertSame(EN_CURSO, controller.estado());
    }
}
