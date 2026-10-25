package com.reparaciones.servidor.nsys;

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

    /** ¿Lo tiene NSYS en la empresa (en stock, de pedido o en una caja que no cuenta)? Un vendido o de baja, no. */
    public boolean enEmpresa() {
        return this == STOCK || this == DE_PEDIDO || this == NO_CUENTA;
    }
}
