package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Datos inventados del SKU del taller (repos públicos): un iPhone 13 128GB y sus cambios. El SKU de NSYS y el del maestro
 *  se escriben igual, i13128<color><grado>, para que las pruebas se lean de un vistazo. */
final class TallerDePrueba {

    static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 21, 10, 0);

    private TallerDePrueba() {}

    /** Un iPhone 13 128GB en Listos con este color y este grado de NSYS (y su SKU, i13128<color><grado>). */
    static MovilNsys movil(String imei, String color, String grade) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128" + color + grade, "13", "13", "128GB", color, grade, false, "Listos",
                "Listos", EstadoMovil.STOCK, HOY.minusDays(20), HOY.minusDays(1));
    }

    /** Un móvil cuyo producto no está en el maestro: sin modelo, color ni SKU. */
    static MovilNsys sinMaestro(String imei, String grade) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, null, null, null, null, null, null, false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY.minusDays(20), HOY.minusDays(1));
    }

    /** Un cambio VIGENTE a mano de «Técnico 1» (ID_USU 7), de ayer, que no sustituye a ninguno. */
    static CambioTaller vigente(int id, String imei, DatoTaller dato, String valor, String valorNsys) {
        return vigente(id, imei, dato, valor, valorNsys, HOY.minusDays(1));
    }

    static CambioTaller vigente(int id, String imei, DatoTaller dato, String valor, String valorNsys, LocalDateTime fecha) {
        return new CambioTaller(id, imei, dato, valor, valorNsys, valorNsys, CambioTaller.MANO, null, 7, "Técnico 1", "prueba",
                fecha, CambioTaller.Estado.VIGENTE, null, null, null);
    }

    /** Un producto del maestro del iPhone 13 128GB: SKU i13128<color><grado>. */
    static ProductoMaestro producto(String goodsColor, String color, String grado) {
        return new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB " + goodsColor + " " + grado), "13", "13",
                "128GB", color, grado, "i13128" + color + grado, null, "i13128" + color, false);
    }
}
