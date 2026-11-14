package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST y GET /api/pedidos/asociaciones: canal, tope y la hora de Madrid; la limpieza de los pares la hace el servicio. */
class AsociacionesControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final PeticionAsociaciones.Par PAR = new PeticionAsociaciones.Par("402-0000000-0000001", "350000000000001");
    private final AsociacionAmazon servicio = mock(AsociacionAmazon.class);
    private final AsociacionesController controller = new AsociacionesController(servicio, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private int estado(String canal, List<PeticionAsociaciones.Par> pares) {
        return assertThrows(ResponseStatusException.class,
                () -> controller.asociar(new PeticionAsociaciones(canal, pares))).getStatusCode().value();
    }

    @Test void devuelveLosRecuentosDelServicioYLeDaLaHoraDeMadrid() {
        RespuestaAsociaciones r = new RespuestaAsociaciones(1, 2, 3, 4, 5, 6);
        when(servicio.aplicar(any(), any(), any())).thenReturn(r);
        assertEquals(r, controller.asociar(new PeticionAsociaciones("AMAZON", List.of(PAR))));
        verify(servicio).aplicar(Canal.AMAZON, List.of(PAR), AHORA);
    }

    @Test void losDosCanalesDeAmazonValen() {
        when(servicio.aplicar(any(), any(), any())).thenReturn(new RespuestaAsociaciones(0, 0, 0, 0, 0, 0));
        controller.asociar(new PeticionAsociaciones("AMAZON", List.of()));
        controller.asociar(new PeticionAsociaciones("AMAZON_STOCKROOM", List.of()));
        verify(servicio).aplicar(Canal.AMAZON, List.of(), AHORA);
        verify(servicio).aplicar(Canal.AMAZON_STOCKROOM, List.of(), AHORA);
    }

    @Test void unCanalQueNoEsDeAmazonOQueNoExisteEs400() {
        for (String malo : new String[]{"DISTRIBUIDORES", "PARTICULARES", "BACKIPHONE", "NOEXISTE", "amazon", "", null})
            assertEquals(400, estado(malo, List.of(PAR)), String.valueOf(malo));
        verifyNoInteractions(servicio);
    }

    @Test void sinParesOConMasDe10000Es400YConJusto10000Vale() {
        when(servicio.aplicar(any(), any(), any())).thenReturn(new RespuestaAsociaciones(0, 0, 0, 0, 0, 0));
        assertEquals(400, estado("AMAZON", null));
        List<PeticionAsociaciones.Par> diezMil = new ArrayList<>(Collections.nCopies(10_000, PAR));
        controller.asociar(new PeticionAsociaciones("AMAZON", diezMil));
        diezMil.add(PAR);
        assertEquals(400, estado("AMAZON", diezMil));
        verify(servicio, times(1)).aplicar(any(), any(), any());
    }

    @Test void losParesMalosNoSonUn400SinoQueLosDescartaElServicio() {
        when(servicio.aplicar(any(), any(), any())).thenReturn(new RespuestaAsociaciones(0, 0, 0, 0, 0, 3));
        List<PeticionAsociaciones.Par> malos = new ArrayList<>();
        malos.add(new PeticionAsociaciones.Par("402-0000000-0000001", "CANCELADO"));
        malos.add(new PeticionAsociaciones.Par(null, null));
        malos.add(null);
        assertEquals(3, controller.asociar(new PeticionAsociaciones("AMAZON", malos)).descartadas());
        verify(servicio).aplicar(Canal.AMAZON, malos, AHORA);
    }

    @Test void elGetDevuelveElEstadoDelServicio() {
        EstadoAsociaciones e = new EstadoAsociaciones(List.of());
        when(servicio.estado()).thenReturn(e);
        assertSame(e, controller.estado());
    }
}
