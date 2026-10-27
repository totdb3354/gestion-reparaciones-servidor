package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** El grado con una pieza (spec 0.9.21 pieza 2 §2.1): [Ms]<letra>[100]; cada pieza toca su parte y la tapa trasera, ninguna
 *  (0.9.21.1). */
class GradoTallerTest {

    @ParameterizedTest(name = "{0} + {1} (nueva {2}) → {3}")
    @CsvSource({
            // chasis: la letra pasa a A; Ms y 100 se quedan
            "B,       CHASIS,  true,  A",
            "B,       CHASIS,  false, A",
            "MsB,     CHASIS,  true,  MsA",
            "C100,    CHASIS,  false, A100",
            "A,       CHASIS,  true,  A",
            // tapa trasera (0.9.21.1): no toca el grado
            "MsA-100, TAPA,    true,  MsA-100",
            "B,       TAPA,    false, B",
            // batería: nueva pone el 100, reutilizada lo quita; Ms y la letra se quedan
            "B,       BATERIA, true,  B100",
            "MsA-,    BATERIA, true,  MsA-100",
            "B100,    BATERIA, true,  B100",
            "B100,    BATERIA, false, B",
            "A,       BATERIA, false, A",
            "MsB100,  BATERIA, false, MsB",
            // 0.9.22 pieza 4: A+ cuenta como A (y se escribe A); «Ms» sin mirar mayúsculas (MSA- es MsA-)
            "A+,      CHASIS,  true,  A",
            "A+,      BATERIA, true,  A100",
            "A+100,   BATERIA, false, A",
            "MsA+,    BATERIA, true,  MsA100",
            "MSA-,    BATERIA, true,  MsA-100",
            "msB,     CHASIS,  false, MsA",
            "A+,      TAPA,    true,  A+",
            // sin espacios a los lados
            "' B ',   CHASIS,  true,  A"
    })
    void conLaFormaDeNsys(String grado, PiezaTaller.Tipo tipo, boolean nueva, String esperado) {
        assertEquals(Optional.of(esperado), GradoTaller.conPieza(grado, tipo, nueva));
    }

    @ParameterizedTest(name = "«{0}» se ignora: ninguna pieza lo cambia")
    @CsvSource({"ID", "F", "Fd", "FD", "RMA", "rma", "' id '"})
    void losIgnoradosNoCambianConNingunaPieza(String grado) {
        for (PiezaTaller.Tipo tipo : PiezaTaller.Tipo.values()) {
            assertEquals(Optional.of(grado.strip()), GradoTaller.conPieza(grado, tipo, true), grado + " " + tipo);
            assertEquals(Optional.of(grado.strip()), GradoTaller.conPieza(grado, tipo, false), grado + " " + tipo);
        }
    }

    @ParameterizedTest(name = "«{0}» cuenta como n/a: el chasis pone A; la tapa y la batería no pueden")
    @CsvSource({"G", "P", "P+", "AG", "A-P+", "CG", "100", "a", "MsMsA", "—"})
    void loQueNoSeUsaCuentaComoNa(String grado) {
        assertEquals(Optional.of("A"), GradoTaller.conPieza(grado, PiezaTaller.Tipo.CHASIS, true));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, true));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, false));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.TAPA, true));
    }

    @ParameterizedTest(name = "grupo de «{0}»: {1}")
    @CsvSource({
            "A,      CON_FORMA", "MsC100, CON_FORMA", "A+100,  CON_FORMA", "MSA-,   CON_FORMA", "' B ',  CON_FORMA",
            "ID,     IGNORADO",  "Fd,     IGNORADO",  "rma,    IGNORADO",
            "n/a,    COMO_NA",   "N/A,    COMO_NA",   "'',     COMO_NA",   "AG,     COMO_NA", "A-P+,   COMO_NA", "—, COMO_NA"
    })
    void elGrupoDeCadaGrado(String grado, GradoTaller.Grupo grupo) {
        assertEquals(grupo, GradoTaller.grupo(grado));
    }

    @Test void sinGradoNingunoEsComoNa() {
        assertEquals(GradoTaller.Grupo.COMO_NA, GradoTaller.grupo(null));
    }

    @Test void sinGradoTampoco() {
        assertEquals(Optional.empty(), GradoTaller.conPieza(null, PiezaTaller.Tipo.BATERIA, true));
    }

    @ParameterizedTest(name = "sin grado de NSYS («{0}»): el chasis pone A; la tapa y la batería no pueden")
    @CsvSource({"n/a", "N/A", "' n/a '", "''", "' '"})
    void sinGradoDeNsysSoloElChasisPoneLaA(String grado) {
        // 0.9.21.1: un chasis sobre n/a o vacío deja el móvil en A (sin Ms ni 100: no hay nada que conservar).
        assertEquals(Optional.of("A"), GradoTaller.conPieza(grado, PiezaTaller.Tipo.CHASIS, true));
        assertEquals(Optional.of("A"), GradoTaller.conPieza(grado, PiezaTaller.Tipo.CHASIS, false));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, true));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.TAPA, true));
    }

    @Test void sinGradoNingunoElChasisTambienPoneLaA() {
        assertEquals(Optional.of("A"), GradoTaller.conPieza(null, PiezaTaller.Tipo.CHASIS, true));
    }

    @Test void elEjemploDeLaSpec() {
        // MsB + chasis nuevo + batería nueva → MsA100
        String trasChasis = GradoTaller.conPieza("MsB", PiezaTaller.Tipo.CHASIS, true).orElseThrow();
        assertEquals("MsA100", GradoTaller.conPieza(trasChasis, PiezaTaller.Tipo.BATERIA, true).orElseThrow());
    }
}
