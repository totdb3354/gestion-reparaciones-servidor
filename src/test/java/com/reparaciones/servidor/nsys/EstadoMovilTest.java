package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadoMovilTest {

    private static final UbicacionNsys LISTOS = new UbicacionNsys("CAJA LISTOS", "Listos", true, false);
    private static final UbicacionNsys PIEZA = new UbicacionNsys("AFaltaRepuestos", "Esperando pieza", false, false);
    private static final UbicacionNsys PEDIDOS = new UbicacionNsys("Pedidos realizados", null, false, true);
    private static final UbicacionNsys DESGUACE = new UbicacionNsys("Desguace", null, false, false);

    @Test void releasedEsVendido() {
        assertEquals(EstadoMovil.VENDIDO, EstadoMovil.de("Released", LISTOS));
    }

    @Test void losQueNoEstanVivosSonBaja() {
        for (String status : new String[]{"Removed", "RMA", "ReturnedToSupplier", null}) {
            assertEquals(EstadoMovil.BAJA, EstadoMovil.de(status, LISTOS));
        }
    }

    @Test void vivosSegunSuCaja() {
        for (String status : new String[]{"Available", "InProcess", "Repair"}) {
            assertEquals(EstadoMovil.STOCK, EstadoMovil.de(status, LISTOS));
            assertEquals(EstadoMovil.NO_CUENTA, EstadoMovil.de(status, PIEZA));
            assertEquals(EstadoMovil.DE_PEDIDO, EstadoMovil.de(status, PEDIDOS));
            assertEquals(EstadoMovil.BAJA, EstadoMovil.de(status, DESGUACE));
        }
    }

    @Test void unaCajaDesconocidaEsBaja() {
        assertEquals(EstadoMovil.BAJA, EstadoMovil.de("Available", null));
    }
}
