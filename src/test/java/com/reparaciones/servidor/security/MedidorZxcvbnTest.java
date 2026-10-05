package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Con la librería real: lo obvio sale bajo, una frase de palabras sueltas sale alto, las palabras del usuario bajan la
 *  nota y los consejos están en español. */
class MedidorZxcvbnTest {

    private final MedidorZxcvbn medidor = new MedidorZxcvbn();

    @Test void unaSerieDeDigitosSacaNotaMinima() {
        assertTrue(medidor.medir("123456789012", List.of()).nota() <= 1);
    }

    @Test void unaFraseDePalabrasSueltasSacaLaNotaMaxima() {
        assertEquals(4, medidor.medir("tortuga violeta lampara nube 47", List.of()).nota());
    }

    @Test void lasPalabrasDelUsuarioBajanLaNota() {
        int sin = medidor.medir("zapateriagonzalez", List.of()).nota();
        int con = medidor.medir("zapateriagonzalez", List.of("zapateria", "gonzalez")).nota();
        assertTrue(con < sin, () -> "sin palabras " + sin + ", con palabras " + con);
    }

    @Test void losConsejosEstanEnEspanol() {
        List<String> consejos = medidor.medir("password", List.of()).consejos();
        assertFalse(consejos.isEmpty());
        assertNotEquals("This is a top-10 common password.", consejos.get(0));
        assertNotEquals("This is a top-10 common password", consejos.get(0));
        // El texto exacto del Step 2 (aviso de contraseña muy común en messages_es.properties):
        assertTrue(consejos.get(0).toLowerCase().contains("contraseña"), () -> "consejo: " + consejos.get(0));
    }

    @Test void laNotaVaDeCeroACuatro() {
        for (String p : List.of("a", "password", "Tr0ub4dor&3", "tortuga violeta lampara nube 47")) {
            int nota = medidor.medir(p, List.of()).nota();
            assertTrue(nota >= 0 && nota <= 4, () -> p + " → " + nota);
        }
    }
}
