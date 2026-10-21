package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.aMano;
import static com.reparaciones.servidor.nsys.TallerDePrueba.deReparacion;
import static org.junit.jupiter.api.Assertions.*;

/** Qué se deshace al borrar o editar una reparación (spec 0.9.21 pieza 2 §2.3): solo lo que sigue mandando. */
class PlanDeshacerTest {

    private static final String IMEI = "350000000000001";
    private static final String R = "R20261021_3";
    private static final Predicate<String> TODAS = idRep -> true;

    @Test void unVigenteSinAnteriorSeDeshaceYValeLoDeNsys() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(c), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, null)), plan.deshechos());
        assertTrue(plan.sinDeshacer().isEmpty());
        assertTrue(plan.vigentes(List.of(c)).isEmpty());
    }

    @Test void unVigenteQueSustituyoAOtroLoDevuelve() {
        CambioTaller a = aMano(4, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, 4);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(a, c), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, a)), plan.deshechos());
        assertEquals(List.of(a), plan.vigentes(List.of(c)));
    }

    @Test void loQueYaNoMandaSoloSeAvisa() {
        CambioTaller sustituido = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, null);
        CambioTaller posterior = aMano(6, IMEI, COLOR, "Verde", "Azul", VIGENTE, 5);
        CambioTaller pasado = deReparacion(7, IMEI, GRADO, "A", "B", R, PASADO, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(sustituido, posterior, pasado), R, TODAS);
        assertTrue(plan.deshechos().isEmpty());
        assertEquals(List.of(sustituido, pasado), plan.sinDeshacer());
        assertFalse(plan.vacio());
    }

    @Test void mandaNsysYQuitadoTampocoSeDeshacen() {
        CambioTaller manda = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, MANDA_NSYS, null);
        CambioTaller quitado = deReparacion(6, IMEI, GRADO, "B", "B", R, QUITADO, null);
        assertEquals(List.of(manda, quitado), PlanDeshacer.de(List.of(manda, quitado), R, TODAS).sinDeshacer());
    }

    @Test void unoYaDeshechoEnUnaEdicionAnteriorNoVuelveAAvisar() {
        // SUSTITUIDO y ningún cambio apunta a él: lo deshizo una edición de la propia reparación.
        CambioTaller deshecho = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, null);
        assertTrue(PlanDeshacer.de(List.of(deshecho), R, TODAS).vacio());
    }

    @Test void deCadaDatoCuentaElUltimoCambioDeLaReparacion() {
        CambioTaller a = aMano(3, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller primero = deReparacion(4, IMEI, COLOR, "Rojo", "Azul", R, SUSTITUIDO, 3);   // deshecho al editar
        CambioTaller segundo = deReparacion(5, IMEI, COLOR, "Rosa", "Azul", R, VIGENTE, 3);
        assertEquals(List.of(new PlanDeshacer.Deshecho(segundo, a)), PlanDeshacer.de(List.of(a, primero, segundo), R, TODAS).deshechos());
    }

    @Test void elAnteriorDeUnaReparacionBorradaSeSaltaYVuelveElDeAntes() {
        CambioTaller mano = aMano(3, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller deOtra = deReparacion(4, IMEI, COLOR, "Rojo", "Azul", "R20261020_1", SUSTITUIDO, 3);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rosa", "Azul", R, VIGENTE, 4);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(mano, deOtra, c), R, idRep -> !idRep.equals("R20261020_1"));
        assertEquals(List.of(new PlanDeshacer.Deshecho(c, mano)), plan.deshechos());
    }

    @Test void colorYGradoVanCadaUnoPorSuLado() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R, PASADO, null);
        PlanDeshacer.Plan plan = PlanDeshacer.de(List.of(color, grado), R, TODAS);
        assertEquals(List.of(new PlanDeshacer.Deshecho(color, null)), plan.deshechos());
        assertEquals(List.of(grado), plan.sinDeshacer());
    }

    @Test void sinCambiosDeLaReparacionNoHayNada() {
        CambioTaller deOtra = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", "R20261020_1", VIGENTE, null);
        assertTrue(PlanDeshacer.de(List.of(deOtra), R, TODAS).vacio());
    }
}
