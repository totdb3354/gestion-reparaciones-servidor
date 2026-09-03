package com.reparaciones.servidor.util;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PuntosCalculoTest {

    private static final Map<String, Double> VALORES = Map.of(
            "bateria", 1.00, "camara", 0.70, "chasis", 2.00, "marco", 0.50,
            "pantalla", 1.00, "glass", 0.50, "otro", 0.50, "pulido", 0.25);

    // ── claveDeTipo ───────────────────────────────────────────────────────────
    @Test void mapeaPrefijosSku() {
        assertEquals("bateria",  PuntosCalculo.claveDeTipo("bat14pro"));
        assertEquals("chasis",   PuntosCalculo.claveDeTipo("cha12"));
        assertEquals("camara",   PuntosCalculo.claveDeTipo("cam11"));
        assertEquals("pantalla", PuntosCalculo.claveDeTipo("lcdx"));
        assertEquals("marco",    PuntosCalculo.claveDeTipo("mc13mini"));
        assertEquals("glass",    PuntosCalculo.claveDeTipo("g15promax"));
    }

    @Test void tipoDesconocidoNuloOtroEsOtro() {
        assertEquals("otro", PuntosCalculo.claveDeTipo("otro tornilleria"));
        assertEquals("otro", PuntosCalculo.claveDeTipo("XYZ"));
        assertEquals("otro", PuntosCalculo.claveDeTipo(null));
    }

    @Test void mayusculasNoImportan() {
        assertEquals("glass", PuntosCalculo.claveDeTipo("G15PROMAX"));
    }

    // ── puntosDeReparacion ────────────────────────────────────────────────────
    @Test void cadaPiezaPuntuaUnaVezSinMultiplicarPorCantidad() {
        // Decisión smoke 2026-09-03: cantidad>1 suele ser pieza rota o venida defectuosa —
        // multiplicar premiaba la rotura. Cada fila de pieza puntúa una sola vez.
        double p = PuntosCalculo.puntosDeReparacion("R20260901_1",
                List.of(new PuntosCalculo.Pieza("lcd14", 1), new PuntosCalculo.Pieza("bat14", 2)), VALORES);
        assertEquals(2.00, p, 0.001); // pantalla 1,00 + bateria 1,00 (la cantidad 2 no multiplica)
    }

    @Test void glassConPiezasSumaIgualQueNormal() {
        double p = PuntosCalculo.puntosDeReparacion("G20260901_1",
                List.of(new PuntosCalculo.Pieza("g14", 1), new PuntosCalculo.Pieza("mc14", 1)), VALORES);
        assertEquals(1.00, p, 0.001); // glass 0,50 + marco 0,50
    }

    @Test void sinPiezasPuntuaOtroFijo() {
        assertEquals(0.50, PuntosCalculo.puntosDeReparacion("R20260901_2", List.of(), VALORES), 0.001);
    }

    @Test void piezaSinTipoPuntuaOtro() {
        double p = PuntosCalculo.puntosDeReparacion("R20260901_3",
                List.of(new PuntosCalculo.Pieza(null, 1)), VALORES);
        assertEquals(0.50, p, 0.001);
    }

    @Test void filaConCantidadCeroPuntuaComoUnaUnidad() {
        // Las "otras acciones" del formulario se guardan como fila real con CANTIDAD=0
        // (neutras en stock): son trabajo hecho y puntúan como una unidad, no como cero
        // (bug cazado en smoke 2026-09-03: bajar el valor 'otro' no movía nada).
        double p = PuntosCalculo.puntosDeReparacion("R20260901_4",
                List.of(new PuntosCalculo.Pieza("otro tornilleria", 0)), VALORES);
        assertEquals(0.50, p, 0.001);
    }

    @Test void pulidoEsFijoAunqueTuvieraPiezas() {
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("P20260901_1", List.of(), VALORES), 0.001);
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("P20260901_2",
                List.of(new PuntosCalculo.Pieza("bat14", 1)), VALORES), 0.001);
    }

    // ── agregar ───────────────────────────────────────────────────────────────
    @Test void agregaPorTecnicoYPeriodoConDesglose() {
        LocalDate d = LocalDate.of(2026, 9, 1);
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_1", "lcd14", 1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_1", "bat14", 1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_2", null,    null), // sin piezas
                new PuntosCalculo.FilaPuntos("Marcos", d, "G20260901_1", "g14",   1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "P20260901_1", null,    null),
                new PuntosCalculo.FilaPuntos("Zara",   d, "R20260901_3", "cha12", 1));
        List<PuntoEstadisticaPuntos> out = PuntosCalculo.agregar(filas, VALORES, f -> "2026-09");

        assertEquals(2, out.size());
        PuntoEstadisticaPuntos marcos = out.stream()
                .filter(p -> p.getNombreTecnico().equals("Marcos")).findFirst().orElseThrow();
        assertEquals("2026-09", marcos.getPeriodo());
        assertEquals(3.25, marcos.getPuntos(), 0.001);         // 2,0 + 0,5 + 0,5 + 0,25
        assertEquals(2.50, marcos.getPuntosNormales(), 0.001); // R: 2,0 + 0,5
        assertEquals(0.50, marcos.getPuntosGlass(), 0.001);
        assertEquals(0.25, marcos.getPuntosPulidos(), 0.001);
        assertEquals(2, marcos.getnNormales());
        assertEquals(1, marcos.getnGlass());
        assertEquals(1, marcos.getnPulidos());
        assertEquals(1, marcos.getnSinPiezas());
        PuntoEstadisticaPuntos zara = out.stream()
                .filter(p -> p.getNombreTecnico().equals("Zara")).findFirst().orElseThrow();
        assertEquals(2.00, zara.getPuntos(), 0.001);
    }

    @Test void unaFilaSinPiezaNoDuplicaLaReparacion() {
        // La query trae UNA fila por reparación sin piezas (LEFT JOIN con NULL):
        // no debe contarse como pieza Y como reparación aparte.
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Marcos", LocalDate.of(2026, 9, 1), "R20260901_9", null, null));
        List<PuntoEstadisticaPuntos> out = PuntosCalculo.agregar(filas, VALORES, f -> "2026-09");
        assertEquals(0.50, out.get(0).getPuntos(), 0.001);
        assertEquals(1, out.get(0).getnSinPiezas());
    }

    @Test void valoresQueFaltanCaenAlValorPorDefecto() {
        // Tabla vacía o clave borrada a mano: nunca NullPointerException.
        double p = PuntosCalculo.puntosDeReparacion("R20260901_1",
                List.of(new PuntosCalculo.Pieza("bat14", 1)), Map.of());
        assertEquals(0.0, p, 0.001);
    }
}
