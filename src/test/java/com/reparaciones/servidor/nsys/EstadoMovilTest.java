package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void enEmpresaSoloSiNoEsVendidoNiBaja() {
        assertTrue(EstadoMovil.STOCK.enEmpresa());
        assertTrue(EstadoMovil.DE_PEDIDO.enEmpresa());
        assertTrue(EstadoMovil.NO_CUENTA.enEmpresa());
        assertFalse(EstadoMovil.VENDIDO.enEmpresa());
        assertFalse(EstadoMovil.BAJA.enEmpresa());
    }

    // ---------------------------------------------------------------- 0.9.30: los grados F, FD y RMA no cuentan como stock

    @Test void unStockConGradoNoVendibleNoCuenta() {
        for (String grado : new String[]{"F", "FD", "RMA", "Fd", "f", " rma "})
            assertEquals(EstadoMovil.NO_CUENTA, EstadoMovil.STOCK.conGrado(grado), grado);
    }

    @Test void unStockConCualquierOtroGradoSigueContando() {
        for (String grado : new String[]{"ID", "n/a", "A", "A-", "B", "Ms", "MsA", "", null})
            assertEquals(EstadoMovil.STOCK, EstadoMovil.STOCK.conGrado(grado), String.valueOf(grado));
    }

    @Test void elGradoSoloCambiaAlQueEsStock() {
        for (EstadoMovil estado : new EstadoMovil[]{EstadoMovil.DE_PEDIDO, EstadoMovil.NO_CUENTA, EstadoMovil.VENDIDO, EstadoMovil.BAJA})
            for (String grado : new String[]{"F", "FD", "RMA", "A"})
                assertEquals(estado, estado.conGrado(grado), estado + " con " + grado);
    }

    // ---------------------------------------------------------------- «Fuera de su caja»: cada grado no vendible tiene su caja

    @Test void cadaGradoNoVendibleEnSuCajaNoTocaNada() {
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("F", "CF021"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("FD", "Desguace"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("RMA", "Funcionalidad pendiente"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca(" rma ", " Funcionalidad pendiente "), "sin espacios a los lados");
    }

    @Test void fueraDeSuCajaOSinCajaLeTocaLaSuya() {
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("F", "CAJA REP. OFICINA"));
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("f", null));
        assertEquals(Optional.of("Desguace"), EstadoMovil.cajaQueToca("Fd", "Funcionalidad pendiente"));
        assertEquals(Optional.of("Funcionalidad pendiente"), EstadoMovil.cajaQueToca("RMA", ""));
        assertEquals(Optional.of("Funcionalidad pendiente"), EstadoMovil.cajaQueToca("RMA", "CF021"));
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("F", "cf021"), "la caja se compara tal cual");
    }

    @Test void unGradoVendibleNoTieneCaja() {
        for (String grado : new String[]{"A", "A-", "B", "ID", "n/a", "Ms", "", null})
            assertEquals(Optional.empty(), EstadoMovil.cajaQueToca(grado, "CAJA LISTOS"), String.valueOf(grado));
    }

    @Test void losNoVendiblesSonLosGradosConCajaYEnEseOrden() {
        assertEquals(Set.of("F", "FD", "RMA"), EstadoMovil.NO_VENDIBLES);
        assertEquals(List.of("CF021", "Desguace", "Funcionalidad pendiente"), List.copyOf(EstadoMovil.CAJA_DE_GRADO.values()));
    }
}
