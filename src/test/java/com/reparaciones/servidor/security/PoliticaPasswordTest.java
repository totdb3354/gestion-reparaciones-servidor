package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.EvaluacionPassword;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las reglas y su orden con una nota fija (el medidor real se prueba en MedidorZxcvbnTest). */
class PoliticaPasswordTest {

    private static PoliticaPassword conNota(int nota, String... consejos) {
        return new PoliticaPassword((p, w) -> new MedidorFuerza.Medida(nota, List.of(consejos)), "");
    }

    private static final String BUENA = "abcdefghij";   // 10 caracteres

    @Test void vaciaONulaPideRellenarYNoMide() {
        List<String> medidas = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { medidas.add(p); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertEquals(new EvaluacionPassword(0, false, PoliticaPassword.MSG_RELLENA), politica.evaluar("", null, "u", null, "TECNICO"));
        assertEquals(new EvaluacionPassword(0, false, PoliticaPassword.MSG_RELLENA), politica.evaluar(null, null, "u", null, "TECNICO"));
        assertTrue(medidas.isEmpty());
    }

    @Test void menosDeDiezEsCortaAunqueLaNotaSeaAlta() {
        var ev = conNota(4).evaluar("abcdefghi", null, "u", null, "TECNICO");
        assertEquals(new EvaluacionPassword(4, false, "La contraseña debe tener al menos 10 caracteres."), ev);
    }

    @Test void diezYSesentaYCuatroSonValidos() {
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
        assertTrue(conNota(3).evaluar("a".repeat(64), null, "u", null, "TECNICO").aceptable());
    }

    @Test void masDeSesentaYCuatroCaracteresEsLarga() {
        var ev = conNota(4).evaluar("a".repeat(65), null, "u", null, "TECNICO");
        assertEquals("La contraseña es demasiado larga (máximo 64 caracteres).", ev.mensaje());
        assertFalse(ev.aceptable());
    }

    /** 40 caracteres, 77 bytes: BCrypt solo usa 72. */
    @Test void masDeSetentaYDosBytesEsLargaAunqueTengaMenosDe64Caracteres() {
        String conTildes = "ñ".repeat(37) + "abc";
        assertEquals(40, conTildes.length());
        assertEquals(PoliticaPassword.MSG_LARGA, conNota(4).evaluar(conTildes, null, "u", null, "TECNICO").mensaje());
    }

    @Test void unaCadenaEnormeNoSeMide() {
        List<String> medidas = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { medidas.add(p); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertEquals(PoliticaPassword.MSG_LARGA, politica.evaluar("a".repeat(5000), null, "u", null, "TECNICO").mensaje());
        assertTrue(medidas.isEmpty());
    }

    @Test void igualALaActualSeRechaza() {
        assertEquals("La nueva contraseña tiene que ser distinta de la actual.",
                conNota(4).evaluar(BUENA, BUENA, "u", null, "TECNICO").mensaje());
        assertTrue(conNota(4).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
    }

    @Test void elOrdenEsVaciaCortaLargaIgualNota() {
        assertEquals(PoliticaPassword.MSG_CORTA, conNota(0).evaluar("abc", "abc", "u", null, "TECNICO").mensaje());
        assertEquals(PoliticaPassword.MSG_LARGA, conNota(0).evaluar("a".repeat(65), "a".repeat(65), "u", null, "TECNICO").mensaje());
        assertEquals(PoliticaPassword.MSG_IGUAL, conNota(0).evaluar(BUENA, BUENA, "u", null, "TECNICO").mensaje());
    }

    @Test void pocaSeguraLlevaElPrimerConsejo() {
        var ev = conNota(2, "Añade otra palabra.", "Evita las fechas.").evaluar(BUENA, null, "u", null, "TECNICO");
        assertEquals(new EvaluacionPassword(2, false, "La contraseña es poco segura. Añade otra palabra."), ev);
        assertEquals("La contraseña es poco segura.", conNota(2).evaluar(BUENA, null, "u", null, "TECNICO").mensaje());
    }

    @Test void notaTresValeParaTecnicoYSupertecnicoPeroNoParaAdmin() {
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "SUPERTECNICO").aceptable());
        assertFalse(conNota(3).evaluar(BUENA, null, "u", null, "ADMIN").aceptable());
        assertTrue(conNota(4).evaluar(BUENA, null, "u", null, "ADMIN").aceptable());
        assertNull(conNota(4).evaluar(BUENA, null, "u", null, "ADMIN").mensaje());
    }

    @Test void penalizaUsuarioTecnicoPalabrasGenericasYPropias() {
        List<List<String>> palabras = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { palabras.add(w); return new MedidorFuerza.Medida(4, List.of()); },
                " MarcaUno, otra ,, ");
        politica.evaluar(BUENA, null, "Usuario-A", "Juan Pérez", "TECNICO");
        List<String> w = palabras.get(0);
        assertTrue(w.containsAll(List.of("usuario-a", "juan pérez", "juan", "pérez", "marcauno", "otra", "taller", "reparaciones")),
                () -> "palabras: " + w);
        assertFalse(w.contains(""));
    }

    @Test void sinTecnicoNiPalabrasPropiasTambienFunciona() {
        List<List<String>> palabras = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { palabras.add(w); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertTrue(politica.evaluar(BUENA, null, "admin", null, "ADMIN").aceptable());
        assertTrue(palabras.get(0).contains("admin"));
    }
}
