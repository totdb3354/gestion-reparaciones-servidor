package com.reparaciones.servidor.security;

import java.util.List;

/** Estima lo difícil que es adivinar una contraseña: nota de 0 (trivial) a 4 (muy difícil) y consejos para mejorarla.
 *  Interfaz para que las reglas de {@link PoliticaPassword} se prueben con una nota fija. */
@FunctionalInterface
public interface MedidorFuerza {

    Medida medir(String password, List<String> palabrasDelUsuario);

    /** {@code consejos}: primero el aviso (si lo hay) y después las sugerencias, ya traducidos. */
    record Medida(int nota, List<String> consejos) {}
}
