package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.SkuNa;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Si el IMEI tecleado de una unidad cuadra con su pedido (spec pieza 2 §6.4.3), con lo que vale del taller (0.9.21). Es puro. */
public final class EstadoUnidad {

    /** estado y motivo como antes; sim: null, ESIM (el móvil es eSIM y el pedido no) o SIM_FISICA (al revés); na: el grado que
     *  cuenta es n/a; ms: es un Ms; dato: lo que es el móvil para el motivo (modelo, capacidad, color o grado), o null. */
    public record Resultado(String estado, String motivo, String sim, boolean na, boolean ms, String dato) {
        public Resultado(String estado, String motivo) {
            this(estado, motivo, null, false, false, null);
        }
    }

    private static final Pattern C_DE_NSYS = Pattern.compile("(?i)(Ms)?C(100)?");

    private EstadoUnidad() {}

    public static Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal) {
        if (m == null) return new Resultado("NO_EN_NSYS", null);
        if (ac == null) return new Resultado("NO_CUADRA", "SKU_DESCONOCIDO");
        Atributos pide = ac.pide();
        String sim = pide.esEsim() == m.esEsim() ? null : m.esEsim() ? "ESIM" : "SIM_FISICA";
        if (!igual(pide.modelo(), m.modeloMaestro())) return new Resultado("NO_CUADRA", "MODELO", sim, false, false, m.modeloMaestro());
        if (!igual(pide.capacidad(), m.capacidad())) return new Resultado("NO_CUADRA", "CAPACIDAD", sim, false, false, m.capacidad());
        String original = v.grade() == null ? "" : v.grade().strip();
        String grado = original.toUpperCase(Locale.ROOT);
        if (EstadoMovil.NO_VENDIBLES.contains(grado) || (canal.esAmazon() && grado.equals("ID")))
            return new Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE", sim, false, false, original);
        // Con un técnico, el color y el grado aún pueden cambiar: no se miran (ni n/a ni Ms).
        if (trabajos != null && !trabajos.isEmpty()) return new Resultado("EN_TALLER", null, sim, false, false, null);
        if (cSinReparar(v)) return new Resultado("NECESITA_TALLER", "GRADO", sim, false, false, "C");
        if (SkuNa.esNa(v.grade())) {
            // Sin grado: solo se puede comprobar el color.
            if (ac.aceptaColor(v.color())) return new Resultado("CON_AVISO", null, sim, true, false, null);
            return new Resultado("NECESITA_TALLER", "COLOR", sim, true, false, v.color());
        }
        boolean ms = grado.startsWith("MS");
        String base = ms ? original.substring(2) : original;
        // El SKU que vale o, si no está entre los del pedido, el color y el grado: desde la 0.9.29 un n/a lleva SKU (el «SKU sin
        // grado» + «NA»), que no es el SKU web de ninguna fila del maestro.
        boolean aceptado = (v.sku() != null && ac.acepta(v.sku())) || ac.aceptaColorYGrado(v.color(), v.grade())
                || (ms && ac.aceptaColorYGrado(v.color(), base));
        if (aceptado) return ms || sim != null ? new Resultado("CON_AVISO", null, sim, false, ms, null) : new Resultado("CUADRA", null);
        if (v.sku() != null && ac.aceptaBporA(v.sku())) return new Resultado("CON_AVISO", "B_POR_A", sim, false, ms, null);
        boolean colorVale = ac.aceptaColor(v.color());
        boolean gradoVale = ac.aceptaGrado(base);
        if (colorVale && gradoVale) return new Resultado("NECESITA_TALLER", "COLOR_O_GRADO", sim, false, ms, null);
        return colorVale ? new Resultado("NECESITA_TALLER", "GRADO", sim, false, ms, base)
                : new Resultado("NECESITA_TALLER", "COLOR", sim, false, ms, v.color());
    }

    /** Un C (con o sin Ms, con o sin «100») que el taller no ha convertido en A: solo el chasis lo hace A, la bateria nueva solo añade «100». Mira el grado que cuenta ahora. */
    static boolean cSinReparar(LoQueVale.Valor v) {
        return v.grade() != null && C_DE_NSYS.matcher(v.grade().strip()).matches();
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a.strip()).equalsIgnoreCase(b == null ? "" : b.strip());
    }
}
