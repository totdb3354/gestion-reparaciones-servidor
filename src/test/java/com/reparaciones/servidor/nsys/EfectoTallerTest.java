package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.QUITADO;
import static com.reparaciones.servidor.nsys.CambioTaller.Estado.VIGENTE;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.PiezaTaller.Tipo.*;
import static com.reparaciones.servidor.nsys.TallerDePrueba.movil;
import static org.junit.jupiter.api.Assertions.*;

/** Lo que hace un guardado (spec 0.9.21 pieza 2 §2.1): fila a fila, desde lo que vale en ese momento. */
class EfectoTallerTest {

    private static final String IMEI = "350000000000001";
    private static final MovilNsys AZUL_B = movil(IMEI, "Azul", "B");
    /** El color de cada SKU del iPhone 13 de prueba: rojo y azul se deducen; «chai13xxx», no. */
    private static final Function<String, Optional<String>> COLORES = sku -> switch (sku) {
        case "chai13red", "tapai13red" -> Optional.of("Rojo");
        case "chai13blue", "tapai13blue" -> Optional.of("Azul");
        default -> Optional.empty();
    };

    private static PiezaTaller pieza(String idRep, String sku, PiezaTaller.Tipo tipo, boolean nueva) {
        return new PiezaTaller(idRep, sku, tipo, nueva);
    }

    @Test void unChasisDeOtroColorCambiaElColorYPoneLaA() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "chai13red", CHASIS, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE), new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)),
                r.pasos().get(0).nuevos());
        assertEquals("Rojo", r.colorDespues());
        assertEquals("A", r.gradoDespues());
        assertFalse(r.colorSinDeducir());
        assertFalse(r.gradoSinCalcular());
    }

    @Test void msbConChasisYBateriaNuevosQuedaMsA100YCadaFilaConLoSuyo() {
        MovilNsys msb = movil(IMEI, "Azul", "MsB");
        EfectoTaller.Resultado r = EfectoTaller.calcular(msb, "Azul", "MsB",
                List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE), new TallerDAO.Nuevo(GRADO, "MsA", "MsB", VIGENTE)),
                r.pasos().get(0).nuevos());
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "MsA100", "MsB", VIGENTE)), r.pasos().get(1).nuevos());
        assertEquals("MsA100", r.gradoDespues());
    }

    @Test void siChasisYTapaDeducenColoresDistintosMandaElChasisSeaCualSeaElOrden() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B",
                List.of(pieza("R1", "tapai13blue", TAPA, true), pieza("R2", "chai13red", CHASIS, true)), COLORES);
        assertEquals(List.of(), r.pasos().get(0).nuevos(), "la tapa no toca el color (manda el chasis) ni el grado");
        assertFalse(r.pasos().get(0).colorSinDeducir());
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE), new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)),
                r.pasos().get(1).nuevos());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void unaTapaSolaCambiaElColorPeroNoElGrado() {
        // 0.9.21.1: solo el chasis pone la A; la tapa trasera cambia el color y nada más.
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "tapai13red", TAPA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(0).nuevos());
        assertEquals("Rojo", r.colorDespues());
        assertEquals("B", r.gradoDespues());
    }

    @Test void unaTapaSobreUnGradoSinFormaNoAvisaDelGrado() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID", List.of(pieza("R1", "tapai13red", TAPA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(0).nuevos());
        assertNull(r.pasos().get(0).gradoSinCalcular());
        assertFalse(r.gradoSinCalcular());
    }

    @Test void unColorQueNoSeDeduceNoCambiaPeroElGradoSi() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(pieza("R1", "chai13xxx", CHASIS, true)), COLORES);
        assertTrue(r.pasos().get(0).colorSinDeducir());
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), r.pasos().get(0).nuevos());
        assertEquals("Azul", r.colorDespues());
        assertTrue(r.colorSinDeducir());
    }

    @Test void unGradoSinLaFormaDeNsysNoCambiaConNingunaPiezaYSeAvisa() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID",
                List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(0).nuevos(), "el color sí");
        assertEquals("ID", r.pasos().get(0).gradoSinCalcular());
        assertTrue(r.pasos().get(1).nuevos().isEmpty());
        assertEquals("ID", r.pasos().get(1).gradoSinCalcular());
        assertEquals("ID", r.gradoDespues());
        assertTrue(r.gradoSinCalcular());
    }

    @Test void sinGradoSeAvisaConUnaRaya() {
        MovilNsys sin = movil(IMEI, "Azul", null);
        assertEquals("—", EfectoTaller.calcular(sin, "Azul", null, List.of(pieza("R1", "bati13", BATERIA, true)), COLORES)
                .pasos().get(0).gradoSinCalcular());
    }

    @Test void volverAlValorDeNsysQuitaLoDelTaller() {
        // NSYS dice Azul · B; el taller tenía Rojo · A: un chasis azul vuelve el color a lo de NSYS.
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Rojo", "A", List.of(pieza("R1", "chai13blue", CHASIS, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Azul", "Azul", QUITADO)), r.pasos().get(0).nuevos(), "el grado ya era A");
    }

    @Test void igualALoQueValeNoCreaNada() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Rojo", "A", List.of(pieza("R1", "chai13red", CHASIS, false)), COLORES);
        assertTrue(r.pasos().get(0).nuevos().isEmpty());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void laBateriaReutilizadaQuitaEl100YLaNuevaNoLoRepite() {
        MovilNsys cien = movil(IMEI, "Azul", "B100");
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "B", "B100", VIGENTE)),
                EfectoTaller.calcular(cien, "Azul", "B100", List.of(pieza("R1", "bati13", BATERIA, false)), COLORES).pasos().get(0).nuevos());
        assertTrue(EfectoTaller.calcular(cien, "Azul", "B100", List.of(pieza("R1", "bati13", BATERIA, true)), COLORES)
                .pasos().get(0).nuevos().isEmpty());
    }

    @Test void sinPiezasElMovilQuedaIgual() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B", List.of(), COLORES);
        assertEquals("Azul", r.colorDespues());
        assertEquals("B", r.gradoDespues());
        assertTrue(r.pasos().isEmpty());
    }

    @Test void siElChasisNoDeduceLaTapaPoneElColorYSeAvisaDelChasis() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B",
                List.of(pieza("R1", "chai13xxx", CHASIS, true), pieza("R2", "tapai13red", TAPA, true)), COLORES);
        assertTrue(r.pasos().get(0).colorSinDeducir());
        assertFalse(r.pasos().get(1).colorSinDeducir());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void siLaTapaNoDeduceYElChasisSiMandaElChasisYLaTapaNoAvisa() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(AZUL_B, "Azul", "B",
                List.of(pieza("R1", "tapai13xxx", TAPA, true), pieza("R2", "chai13red", CHASIS, true)), COLORES);
        assertFalse(r.pasos().get(0).colorSinDeducir(), "la tapa no avisa: manda el chasis");
        assertFalse(r.pasos().get(1).colorSinDeducir());
        assertEquals("Rojo", r.colorDespues());
    }

    @Test void laBateriaReutilizadaSobreGradoIdAvisaDelGrado() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID", List.of(pieza("R1", "bati13", BATERIA, false)), COLORES);
        assertTrue(r.pasos().get(0).nuevos().isEmpty());
        assertEquals("ID", r.pasos().get(0).gradoSinCalcular());
    }
}
