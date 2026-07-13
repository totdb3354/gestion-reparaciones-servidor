package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UbicacionDerivadorTest {

    @Test void recibidoSinTrabajosEstaEnAlmacen() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 0, 0, null);
        assertEquals("RECIBIDO", r.estadoEfectivo());
        assertEquals("ALMACEN", r.ubicacion());
        assertTrue(r.subUbicaciones().isEmpty());
    }

    @Test void cualquierTrabajoAbiertoMandaAReparaciones() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 0, 1, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals("REPARACIONES", r.ubicacion());
        assertEquals(List.of("NORMAL"), r.subUbicaciones());
    }

    @Test void telefonoDivididoPantallaYCuerpo() {
        // pulido + glass + normal abiertos: la parte pantalla muestra el PRIMER
        // trabajo de pantalla (pulido antes que glass), la parte cuerpo el normal
        var r = UbicacionDerivador.derivar("RECIBIDO", 1, 1, 1, null);
        assertEquals(List.of("PULIDO", "NORMAL"), r.subUbicaciones());
    }

    @Test void alCompletarPulidoSaltaAGlass() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 1, 1, null);
        assertEquals(List.of("GLASS", "NORMAL"), r.subUbicaciones());
    }

    @Test void soloTrabajoDePantalla() {
        var r = UbicacionDerivador.derivar(null, 1, 0, 0, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals(List.of("PULIDO"), r.subUbicaciones());
    }

    @Test void historicoSinTrabajosEstaFueraDelCiclo() {
        var r = UbicacionDerivador.derivar(null, 0, 0, 0, null);
        assertNull(r.estadoEfectivo());
        assertNull(r.ubicacion());
    }

    @Test void bloqueadoMandaSobreTrabajos() {
        var r = UbicacionDerivador.derivar("BLOQUEADO", 0, 0, 1, null);
        assertEquals("BLOQUEADO", r.estadoEfectivo());
        assertEquals("BLOQUEO", r.ubicacion());
    }

    @Test void enviadoYDesguaceEstanFuera() {
        assertNull(UbicacionDerivador.derivar("ENVIADO", 0, 0, 0, null).ubicacion());
        assertNull(UbicacionDerivador.derivar("DESGUACE", 0, 0, 0, null).ubicacion());
    }

    @Test void enRevisionVaAParaRevisar() {
        assertEquals("PARA_REVISAR", UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null).ubicacion());
    }

    @Test void okConClienteEsPedidoYSinClienteEsListo() {
        // "Es pedido" = tiene cliente asignado. Punto de enchufe de la futura entidad Pedido.
        assertEquals("PEDIDOS", UbicacionDerivador.derivar("OK", 0, 0, 0, 7).ubicacion());
        assertEquals("LISTOS",  UbicacionDerivador.derivar("OK", 0, 0, 0, null).ubicacion());
    }

    @Test void okConTrabajoNuevoVuelveSoloAReparaciones() {
        var r = UbicacionDerivador.derivar("OK", 1, 0, 0, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals("REPARACIONES", r.ubicacion());
    }
}
