package com.reparaciones.servidor.util;

import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import com.reparaciones.servidor.util.PrevisionPedido.Resultado;
import com.reparaciones.servidor.util.PrevisionPedido.Tramos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regla de la previsión de pedidos (spec 0.9.5 §3.1): los casos de la tabla de la spec, en el mismo orden. */
class PrevisionPedidoTest {

    private static final Pesos P = Pesos.POR_DEFECTO;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);

    @Test void bateriaQueSeMueve() {
        assertEquals(new Resultado(0.31, 16), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 3, 0));
    }

    @Test void bienSurtidaNoPide() {
        assertEquals(new Resultado(0.31, 0), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 19, 0));
    }

    @Test void casiParadaPideElMinimo() {
        assertEquals(new Resultado(0.02, 2), PrevisionPedido.calcular(new Tramos(1, 0, 0), P, 2, 0, 0));
    }

    @Test void loQueEstaEnCaminoCuenta() {
        assertEquals(new Resultado(0.31, 11), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 3, 5));
    }

    @Test void piezaNuevaSinTratoEspecial() {
        assertEquals(new Resultado(0.10, 6), PrevisionPedido.calcular(new Tramos(6, 0, 0), P, 2, 0, 0));
    }

    @Test void sinConsumoPideElMinimo() {
        assertEquals(new Resultado(0.0, 2), PrevisionPedido.calcular(Tramos.CERO, P, 2, 0, 0));
    }

    @Test void conMinimoCeroYSinConsumoNoPide() {
        assertEquals(new Resultado(0.0, 0), PrevisionPedido.calcular(Tramos.CERO, P, 0, 0, 0));
    }

    /** 31 unidades en el tramo reciente = 0,5166… al día; × 60 en coma flotante es 31,000000000000004 y redondeando
     *  hacia arriba daría 32. En enteros da exactamente 31. */
    @Test void elRedondeoNoSeEquivocaPorDecimales() {
        assertEquals(31, PrevisionPedido.calcular(new Tramos(31, 0, 0), P, 0, 0, 0).pedir60());
    }

    @Test void otrosPesos() {
        // 40·12 + 35·8 + 25·5 = 885 → 885/3000 = 0,295 → 0,30; 60 d: 53100 − 9000 = 44100 → 14,7 → 15
        assertEquals(new Resultado(0.30, 15), PrevisionPedido.calcular(new Tramos(12, 8, 5), new Pesos(40, 35, 25), 2, 3, 0));
    }

    @Test void pesosValidosSoloSiSonDe0a100YSuman100() {
        assertTrue(P.validos());
        assertTrue(new Pesos(100, 0, 0).validos());
        assertFalse(new Pesos(50, 30, 30).validos());
        assertFalse(new Pesos(-10, 60, 50).validos());
        assertFalse(new Pesos(101, -1, 0).validos());
        assertEquals("50/30/20", P.texto());
    }

    @Test void agruparRepartePorTramosYExcluyeHoyYLoDeMasDe90Dias() {
        List<ConsumoDia> consumos = List.of(
                new ConsumoDia(1, HOY, 100),                  // hoy: fuera
                new ConsumoDia(1, HOY.minusDays(1), 1),       // tramo 1
                new ConsumoDia(1, HOY.minusDays(30), 2),      // tramo 1 (límite)
                new ConsumoDia(1, HOY.minusDays(31), 4),      // tramo 2
                new ConsumoDia(1, HOY.minusDays(60), 8),      // tramo 2 (límite)
                new ConsumoDia(1, HOY.minusDays(61), 16),     // tramo 3
                new ConsumoDia(1, HOY.minusDays(90), 32),     // tramo 3 (límite)
                new ConsumoDia(1, HOY.minusDays(91), 1000),   // fuera
                new ConsumoDia(7, HOY.minusDays(5), 3));      // otro master
        Map<Integer, Tramos> t = PrevisionPedido.agrupar(consumos, HOY);
        assertEquals(new Tramos(3, 12, 48), t.get(1));
        assertEquals(new Tramos(3, 0, 0), t.get(7));
        assertEquals(2, t.size());
    }
}
