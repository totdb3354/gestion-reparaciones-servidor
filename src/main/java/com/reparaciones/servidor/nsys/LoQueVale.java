package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.function.Supplier;

/** «Lo que vale» de un móvil (spec 0.9.21 «SKU del taller» §2.1): lo del taller vigente para cada dato, si lo hay; si no, lo
 *  de NSYS. Con algo del taller, el SKU sale del maestro con el modelo, la capacidad y la eSIM del espejo y el color y el
 *  grado que valen; sin nada, todo como hoy (el SKU, el de Nsys_movil). Es puro. */
public final class LoQueVale {

    /** Lo de NSYS cuando NSYS no lo sabe («NSYS: —» en la web). */
    public static final String SIN_DATO = "—";

    /** {@code grade}: el grado entero, como lo escribe NSYS (con el «100» y el «Ms»). {@code colorNsys}, {@code gradoNsys} y
     *  {@code skuNsys}: lo de NSYS cuando lo del taller lo cambia; null si es lo mismo. {@code porPasar}: el móvil tiene algo
     *  del taller vigente (sale en «Por pasar a NSYS»). */
    public record Valor(String color, String grade, String sku, String colorNsys, String gradoNsys, String skuNsys,
                        boolean porPasar) {

        /** El grado sin la batería, como lo enseña la web («A100» → «A»). */
        public String grado() {
            return Normalizacion.gradoSinBateria(grade);
        }

        public boolean bateriaNueva() {
            return Normalizacion.bateriaNueva(grade);
        }
    }

    private LoQueVale() {}

    /** {@code vigentes}: los cambios VIGENTES de ese IMEI (como mucho uno por dato). {@code maestro}: solo se pide si hay
     *  alguno. */
    public static Valor de(MovilNsys m, List<CambioTaller> vigentes, Supplier<Maestro> maestro) {
        if (vigentes.isEmpty()) return new Valor(m.color(), m.grade(), m.sku(), null, null, null, false);
        String color = valor(vigentes, DatoTaller.COLOR, m);
        String grade = valor(vigentes, DatoTaller.GRADO, m);
        String sku = maestro.get().sku(m, color, grade);
        return new Valor(color, grade, sku, siCambia(color, m.color()), siCambia(grade, m.grade()), siCambia(sku, m.sku()),
                true);
    }

    /** Lo que vale de un dato: lo del taller vigente o, si no hay, lo de NSYS. */
    static String valor(List<CambioTaller> vigentes, DatoTaller dato, MovilNsys m) {
        return vigentes.stream().filter(c -> c.dato() == dato && c.valor() != null).map(CambioTaller::valor).findFirst()
                .orElse(dato.deNsys(m));
    }

    /** Lo de NSYS si lo del taller lo cambia (una raya si NSYS no lo sabe); null si es lo mismo. */
    static String siCambia(String taller, String nsys) {
        if (Normalizacion.mismoTexto(taller, nsys)) return null;
        return nsys == null || nsys.isBlank() ? SIN_DATO : nsys;
    }
}
