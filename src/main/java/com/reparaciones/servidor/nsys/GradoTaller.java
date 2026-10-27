package com.reparaciones.servidor.nsys;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** El grado de un móvil con una pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.1, y 0.9.22 pieza 4 §3). El grado se escribe
 *  como en NSYS, [Ms]<letra>[100], y cada pieza toca su parte: chasis, la letra pasa a A (Ms y 100 se quedan); batería nueva,
 *  con 100; reutilizada, sin 100. La tapa trasera no lo toca (0.9.21.1). «Ms» nunca cambia solo. Desde la pieza 4, tres grupos:
 *  con forma (A+ cuenta como A y, al escribir, se escribe A; «Ms» sin mirar mayúsculas y escrito «Ms»), ignorados (F, Fd, FD,
 *  RMA, ID: ninguna pieza los cambia) y como «n/a» (n/a, vacío y cualquier otro código: el chasis lo deja en A; la batería no
 *  puede). Es puro. */
public final class GradoTaller {

    /** Los tres grupos de grado (spec pieza 4 §3). */
    public enum Grupo { CON_FORMA, IGNORADO, COMO_NA }

    /** La forma del grado de NSYS: «A+» y «A-» antes que «A» para leerlo entero; «Ms» sin mirar mayúsculas. */
    static final Pattern FORMA = Pattern.compile("((?i:ms))?(A\\+|A-|A|B|C)(100)?");
    /** Los grados que se siguen usando y el taller no toca (sin mirar mayúsculas): no vendible, desguace, bloqueo de operador y
     *  falla de Face ID. */
    static final Set<String> IGNORADOS = Set.of("F", "FD", "RMA", "ID");

    private GradoTaller() {}

    /** El grupo de un grado de NSYS (o del que vale con el taller); null cuenta como «n/a». */
    public static Grupo grupo(String grado) {
        if (grado == null) return Grupo.COMO_NA;
        String g = grado.strip();
        if (FORMA.matcher(g).matches()) return Grupo.CON_FORMA;
        if (IGNORADOS.contains(g.toUpperCase(Locale.ROOT))) return Grupo.IGNORADO;
        return Grupo.COMO_NA;
    }

    /** El grado tras la pieza: con forma, la cuenta de siempre (escrito como NSYS: «Ms», la letra con A+ como A y el «100»); un
     *  ignorado o una tapa, el mismo (sin espacios a los lados); como «n/a», «A» con un chasis y vacío con lo demás (no hay letra
     *  a la que poner o quitar el 100). */
    public static Optional<String> conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva) {
        Grupo g = grupo(grado);
        if (g == Grupo.COMO_NA) return tipo == PiezaTaller.Tipo.CHASIS ? Optional.of("A") : Optional.empty();
        if (g == Grupo.IGNORADO || tipo == PiezaTaller.Tipo.TAPA) return Optional.of(grado.strip());
        Matcher f = FORMA.matcher(grado.strip());
        if (!f.matches()) throw new IllegalStateException("Grado con forma que no casa: " + grado);
        String ms = f.group(1) == null ? "" : "Ms";
        String letra = tipo == PiezaTaller.Tipo.CHASIS || "A+".equals(f.group(2)) ? "A" : f.group(2);
        boolean cien = tipo == PiezaTaller.Tipo.BATERIA ? nueva : f.group(3) != null;
        return Optional.of(ms + letra + (cien ? "100" : ""));
    }

    /** NSYS aún no le ha puesto grado: «n/a» o vacío. */
    static boolean sinGrado(String grado) {
        return grado == null || grado.isBlank() || grado.strip().equalsIgnoreCase("n/a");
    }
}
