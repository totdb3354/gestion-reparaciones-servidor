package com.reparaciones.servidor.nsys;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Estado de un móvil en el ERP según NSYS (spec pieza 1 §4.6). */
public enum EstadoMovil {
    STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO, BAJA;

    /** Los Status de NSYS que cuentan como móvil en la empresa (regla del Excel de stock). */
    public static final Set<String> VIVOS = Set.of("Available", "InProcess", "Repair");

    /** ¿Es un Status de móvil en la empresa? Sin Status, no (como en {@link #de}). */
    public static boolean vivo(String status) {
        return status != null && VIVOS.contains(status);
    }

    /** Released → VENDIDO; otro Status no vivo → BAJA; vivo: «Pedidos realizados» → DE_PEDIDO, caja que no se trae (o
     *  desconocida) → BAJA, caja que no cuenta → NO_CUENTA y el resto → STOCK. */
    public static EstadoMovil de(String status, UbicacionNsys caja) {
        if ("Released".equals(status)) return VENDIDO;
        if (status == null || !VIVOS.contains(status)) return BAJA;
        if (caja != null && caja.esPedido()) return DE_PEDIDO;
        if (caja == null || caja.ubicacion() == null) return BAJA;
        return caja.cuentaStock() ? STOCK : NO_CUENTA;
    }

    /** La caja de NSYS de cada grado que no se vende («Fuera de su caja»): no vendible, desguace y devolución al proveedor, en el
     *  orden en que salen sus grupos. Los grados, en mayúsculas; las cajas, como las escribe NSYS. */
    public static final Map<String, String> CAJA_DE_GRADO = cajaDeGrado();

    private static Map<String, String> cajaDeGrado() {
        Map<String, String> cajas = new LinkedHashMap<>();
        cajas.put("F", "CF021");
        cajas.put("FD", "Desguace");
        cajas.put("RMA", "Funcionalidad pendiente");
        return Collections.unmodifiableMap(cajas);
    }

    /** Los grados que no se venden estén en la caja que estén (0.9.30): los que tienen caja propia. En mayúsculas; ID y n/a sí
     *  cuentan. */
    public static final Set<String> NO_VENDIBLES = CAJA_DE_GRADO.keySet();

    /** La caja que le toca a un móvil con este grado si no está en ella («Fuera de su caja»): el grado, sin espacios a los lados
     *  y sin mirar mayúsculas; {@code location}, la caja de NSYS sin espacios a los lados y tal cual (null o vacía: sin caja).
     *  Vacío si el grado no tiene caja propia o ya está en la suya. No mira el estado ni los trabajos del taller. */
    public static Optional<String> cajaQueToca(String grado, String location) {
        if (grado == null) return Optional.empty();
        String suya = CAJA_DE_GRADO.get(grado.strip().toUpperCase(Locale.ROOT));
        if (suya == null || suya.equals(Normalizacion.caja(location))) return Optional.empty();
        return Optional.of(suya);
    }

    /** El estado que se enseña, con el grado que vale (0.9.30): un STOCK con un grado no vendible no cuenta como stock.
     *  Sin espacios a los lados y sin mirar mayúsculas («Fd» es FD). Los demás estados, y un grado vacío, no cambian. */
    public EstadoMovil conGrado(String grado) {
        if (this != STOCK || grado == null) return this;
        return NO_VENDIBLES.contains(grado.strip().toUpperCase(Locale.ROOT)) ? NO_CUENTA : this;
    }

    /** ¿Lo tiene NSYS en la empresa (en stock, de pedido o en una caja que no cuenta)? Un vendido o de baja, no. */
    public boolean enEmpresa() {
        return this == STOCK || this == DE_PEDIDO || this == NO_CUENTA;
    }
}
