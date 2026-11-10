package com.reparaciones.servidor.nsys;

import java.util.Locale;
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

    /** Los grados que no se venden estén en la caja que estén (0.9.30): no vendible, desguace y devolución al proveedor.
     *  En mayúsculas; ID y n/a sí cuentan. */
    public static final Set<String> NO_VENDIBLES = Set.of("F", "FD", "RMA");

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
