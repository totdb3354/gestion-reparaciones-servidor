package com.reparaciones.servidor.nsys;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** El grado de un móvil con una pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.1). El grado se escribe como en NSYS,
 *  [Ms]<letra>[100], y cada pieza toca su parte: chasis, la letra pasa a A (Ms y 100 se quedan); batería nueva, con
 *  100; reutilizada, sin 100. La tapa trasera no lo toca (0.9.21.1). «Ms» nunca cambia solo. Un grado sin esa forma
 *  (ID, F, n/a…) no se puede calcular. Es puro. */
public final class GradoTaller {

    /** La forma del grado de NSYS (la de TallerNsys.GRADO); «A-» antes que «A» para leerlo entero. */
    static final Pattern FORMA = Pattern.compile("(Ms)?(A-|A|B|C)(100)?");

    private GradoTaller() {}

    /** El grado tras la pieza (puede ser el mismo); vacío si el grado no tiene la forma de NSYS. */
    public static Optional<String> conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva) {
        if (grado == null) return Optional.empty();
        Matcher f = FORMA.matcher(grado.strip());
        if (!f.matches()) return Optional.empty();
        String ms = f.group(1) == null ? "" : f.group(1);
        String letra = tipo == PiezaTaller.Tipo.CHASIS ? "A" : f.group(2);
        boolean cien = tipo == PiezaTaller.Tipo.BATERIA ? nueva : f.group(3) != null;
        return Optional.of(ms + letra + (cien ? "100" : ""));
    }
}
