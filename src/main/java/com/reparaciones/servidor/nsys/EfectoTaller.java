package com.reparaciones.servidor.nsys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Lo que hace un guardado de reparaciones en el color y el grado de un móvil (spec 0.9.21 «SKU del taller», pieza 2, §2.1):
 *  fila a fila y en su orden, cada una desde lo que vale tras la anterior. Chasis: el color deducido (si es otro) y la
 *  letra A; tapa trasera: solo el color (0.9.21.1: no toca el grado ni avisa de él); batería: el 100. Si el valor nuevo es
 *  el de NSYS, nace QUITADO; si es el que ya vale, no se crea nada (la misma regla que la corrección a mano de la pieza 1).
 *  En un guardado con un chasis cuyo color se deduce, la tapa no toca el color. El chasis se aplica antes que las demás
 *  piezas, sea cual sea el orden de las filas (0.9.21.1: n/a + batería nueva + chasis → A100, sin aviso de la batería).
 *  La misma cuenta para guardar y para la previsualización. Es puro.
 *  Desde la 0.9.22 pieza 4, un chasis que da letra a un grado «como n/a» aplica la batería recordada del móvil. */
public final class EfectoTaller {

    /** Lo que hace una fila: sus cambios (0, 1 o 2) y sus avisos ({@code gradoSinCalcular}: el grado que no se pudo
     *  calcular, «—» si no había, o null). */
    public record Paso(PiezaTaller pieza, List<TallerDAO.Nuevo> nuevos, boolean colorSinDeducir, String gradoSinCalcular) {}

    public record Resultado(String colorAntes, String gradoAntes, String colorDespues, String gradoDespues, List<Paso> pasos,
                            boolean recordadaUsada) {

        public boolean colorSinDeducir() {
            return pasos.stream().anyMatch(Paso::colorSinDeducir);
        }

        public boolean gradoSinCalcular() {
            return pasos.stream().anyMatch(p -> p.gradoSinCalcular() != null);
        }
    }

    private EfectoTaller() {}

    /** {@code colorVale} y {@code gradoVale}: lo que vale ahora (LoQueVale). {@code colorDeChasis}: SKU → color del maestro
     *  (ColorChasis). Sin batería recordada. */
    public static Resultado calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
                                     Function<String, Optional<String>> colorDeChasis) {
        return calcular(m, colorVale, gradoVale, guardado, colorDeChasis, null);
    }

    /** Como el de arriba, con la batería recordada del móvil (0.9.22 pieza 4 §5.2; null si no hay): si un chasis da letra a un
     *  grado «como n/a», se le aplica (nueva → con 100, reutilizada → sin 100) y el resultado lo dice ({@code recordadaUsada}). */
    public static Resultado calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
                                     Function<String, Optional<String>> colorDeChasis, BateriaRecordada recordada) {
        boolean chasisConColor = guardado.stream()
                .anyMatch(p -> p.tipo() == PiezaTaller.Tipo.CHASIS && colorDeChasis.apply(p.sku()).isPresent());
        String color = colorVale;
        String grado = gradoVale;
        boolean usada = false;
        List<Paso> pasos = new ArrayList<>();
        // Primero el chasis (estable: el resto conserva su orden). Los pasos salen en este orden, cada uno con su pieza.
        List<PiezaTaller> enOrden = guardado.stream()
                .sorted(Comparator.comparing((PiezaTaller p) -> p.tipo() != PiezaTaller.Tipo.CHASIS)).toList();
        for (PiezaTaller p : enOrden) {
            List<TallerDAO.Nuevo> nuevos = new ArrayList<>();
            boolean sinColor = false;
            if (p.tipo().conColor() && !(p.tipo() == PiezaTaller.Tipo.TAPA && chasisConColor)) {
                Optional<String> deducido = colorDeChasis.apply(p.sku());
                if (deducido.isEmpty()) sinColor = true;
                else {
                    Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.COLOR, deducido.get(), color, m);
                    if (n.isPresent()) {
                        nuevos.add(n.get());
                        color = n.get().valor();
                    }
                }
            }
            String gradoMal = null;
            if (p.tipo().conGrado()) {
                boolean sinLetra = GradoTaller.grupo(grado) == GradoTaller.Grupo.COMO_NA;
                Optional<String> nuevoGrado = GradoTaller.conPieza(grado, p.tipo(), p.nueva());
                if (nuevoGrado.isPresent() && sinLetra && recordada != null && !usada && p.tipo() == PiezaTaller.Tipo.CHASIS) {
                    nuevoGrado = GradoTaller.conPieza(nuevoGrado.get(), PiezaTaller.Tipo.BATERIA, recordada.nueva());
                    usada = true;
                }
                if (nuevoGrado.isEmpty()) gradoMal = grado == null || grado.isBlank() ? LoQueVale.SIN_DATO : grado;
                else {
                    Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.GRADO, nuevoGrado.get(), grado, m);
                    if (n.isPresent()) {
                        nuevos.add(n.get());
                        grado = n.get().valor();
                    }
                }
            }
            pasos.add(new Paso(p, List.copyOf(nuevos), sinColor, gradoMal));
        }
        return new Resultado(colorVale, gradoVale, color, grado, List.copyOf(pasos), usada);
    }

    /** Un dato que pasa a {@code nuevo}: nada si ya es ese; QUITADO si es lo de NSYS; si no, VIGENTE. */
    static Optional<TallerDAO.Nuevo> cambio(DatoTaller dato, String nuevo, String actual, MovilNsys m) {
        if (Normalizacion.mismoTexto(nuevo, actual)) return Optional.empty();
        String nsys = dato.deNsys(m);
        CambioTaller.Estado estado = Normalizacion.mismoTexto(nuevo, nsys) ? CambioTaller.Estado.QUITADO
                : CambioTaller.Estado.VIGENTE;
        return Optional.of(new TallerDAO.Nuevo(dato, nuevo.strip(), nsys, estado));
    }
}
