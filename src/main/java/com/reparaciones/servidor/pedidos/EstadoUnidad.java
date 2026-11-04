package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Si el IMEI tecleado de una unidad cuadra con su pedido (spec pieza 2 §6.4.3), con lo que vale del taller (0.9.21). Es puro. */
public final class EstadoUnidad {

    public record Resultado(String estado, String motivo) {}

    private static final Set<String> NO_VENDIBLES = Set.of("F", "FD", "RMA");
    private static final Pattern C_DE_NSYS = Pattern.compile("(?i)(Ms)?C(100)?");

    private EstadoUnidad() {}

    public static Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal) {
        if (m == null) return new Resultado("NO_EN_NSYS", null);
        if (ac == null) return new Resultado("NO_CUADRA", "SKU_DESCONOCIDO");
        Atributos pide = ac.pide();
        if (!igual(pide.modelo(), m.modeloMaestro()) || !igual(pide.capacidad(), m.capacidad()) || pide.esEsim() != m.esEsim())
            return new Resultado("NO_CUADRA", "MODELO");
        String grado = v.grade() == null ? "" : v.grade().strip().toUpperCase(Locale.ROOT);
        if (NO_VENDIBLES.contains(grado) || (canal.esAmazon() && grado.equals("ID")))
            return new Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE");
        if (trabajos != null && !trabajos.isEmpty()) return new Resultado("EN_TALLER", null);
        if (m.grade() != null && C_DE_NSYS.matcher(m.grade().strip()).matches() && v.gradoNsys() == null)
            return new Resultado("NECESITA_TALLER", "C_SIN_REPARAR");
        boolean ms = grado.startsWith("MS");
        boolean aceptado = v.sku() != null ? ac.acepta(v.sku()) : ac.aceptaColorYGrado(v.color(), v.grade());
        if (aceptado) return ms ? new Resultado("CON_AVISO", "MS") : new Resultado("CUADRA", null);
        if (ms && ac.aceptaColorYGrado(v.color(), v.grade().strip().substring(2))) return new Resultado("CON_AVISO", "MS");
        if (v.sku() != null && ac.aceptaBporA(v.sku())) return new Resultado("CON_AVISO", "B_POR_A");
        return new Resultado("NECESITA_TALLER", "COLOR_O_GRADO");
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a.strip()).equalsIgnoreCase(b == null ? "" : b.strip());
    }
}
