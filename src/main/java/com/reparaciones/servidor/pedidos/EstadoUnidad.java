package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Si el IMEI tecleado de una unidad cuadra con su pedido (spec pieza 2 §6.4.3), con lo que vale del taller (0.9.21). Es puro. */
public final class EstadoUnidad {

    public record Resultado(String estado, String motivo) {}

    private static final Pattern C_DE_NSYS = Pattern.compile("(?i)(Ms)?C(100)?");

    private EstadoUnidad() {}

    public static Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal) {
        if (m == null) return new Resultado("NO_EN_NSYS", null);
        if (ac == null) return new Resultado("NO_CUADRA", "SKU_DESCONOCIDO");
        Atributos pide = ac.pide();
        if (!igual(pide.modelo(), m.modeloMaestro()) || !igual(pide.capacidad(), m.capacidad()) || pide.esEsim() != m.esEsim())
            return new Resultado("NO_CUADRA", "MODELO");
        String grado = v.grade() == null ? "" : v.grade().strip().toUpperCase(Locale.ROOT);
        if (EstadoMovil.NO_VENDIBLES.contains(grado) || (canal.esAmazon() && grado.equals("ID")))
            return new Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE");
        if (trabajos != null && !trabajos.isEmpty()) return new Resultado("EN_TALLER", null);
        if (cSinReparar(v))
            return new Resultado("NECESITA_TALLER", "C_SIN_REPARAR");
        boolean ms = grado.startsWith("MS");
        // El SKU que vale o, si no está entre los del pedido, el color y el grado: desde la 0.9.29 un n/a lleva SKU (el «SKU sin
        // grado» + «NA»), que no es el SKU web de ninguna fila del maestro.
        boolean aceptado = (v.sku() != null && ac.acepta(v.sku())) || ac.aceptaColorYGrado(v.color(), v.grade());
        if (aceptado) return ms ? new Resultado("CON_AVISO", "MS") : new Resultado("CUADRA", null);
        if (ms && ac.aceptaColorYGrado(v.color(), v.grade().strip().substring(2))) return new Resultado("CON_AVISO", "MS");
        if (v.sku() != null && ac.aceptaBporA(v.sku())) return new Resultado("CON_AVISO", "B_POR_A");
        return new Resultado("NECESITA_TALLER", "COLOR_O_GRADO");
    }

    /** Un C (con o sin Ms, con o sin «100») que el taller no ha convertido en A: solo el chasis lo hace A, la bateria nueva solo añade «100». Mira el grado que cuenta ahora. */
    static boolean cSinReparar(LoQueVale.Valor v) {
        return v.grade() != null && C_DE_NSYS.matcher(v.grade().strip()).matches();
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a.strip()).equalsIgnoreCase(b == null ? "" : b.strip());
    }
}
