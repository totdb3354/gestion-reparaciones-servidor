package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;

/** Lo que el ERP dice del IMEI puesto en una fila. nivel: OK, AVISO o MAL. motivo: null con OK. skuMovil y caja: null si el movil no esta en NSYS. */
public record AvisoImei(String nivel, String motivo, String skuMovil, String caja, DetalleAviso detalle) {

    public static final String OK = "OK";
    public static final String AVISO = "AVISO";
    public static final String MAL = "MAL";
    /** Los motivos de SKU, los unicos que una persona puede dar por buenos; NO_EN_NSYS y EN_OTRA_FILA se arreglan cambiando el IMEI. */
    public static final java.util.Set<String> MOTIVOS_SKU = java.util.Set.of("MODELO", "CAPACIDAD", "COLOR", "GRADO", "COLOR_O_GRADO",
            "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO");

    public AvisoImei(String nivel, String motivo, String skuMovil, String caja) {
        this(nivel, motivo, skuMovil, caja, null);
    }

    /** Si el motivo es uno de los de SKU. */
    public boolean esDeSku() {
        return motivo != null && MOTIVOS_SKU.contains(motivo);
    }

    public static AvisoImei de(Aceptacion ac, Canal canal, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos,
                               boolean enOtraFilaViva) {
        if (m == null) return new AvisoImei(MAL, "NO_EN_NSYS", null, null);
        String sku = v.sku();
        String caja = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (enOtraFilaViva) return new AvisoImei(MAL, "EN_OTRA_FILA", sku, caja);
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, trabajos, canal);
        boolean enTaller = r.estado().equals("EN_TALLER");
        DetalleAviso d = DetalleAviso.de(r.sim(), r.na(), r.ms(), r.dato(), enTaller ? trabajos : List.of());
        return switch (r.estado()) {
            case "NO_CUADRA", "NECESITA_TALLER" -> new AvisoImei(MAL, r.motivo(), sku, caja, d);
            case "EN_TALLER" -> new AvisoImei(AVISO, "EN_TALLER", sku, caja, d);
            case "CON_AVISO" -> new AvisoImei(AVISO, r.motivo(), sku, caja, d);
            case "CUADRA" -> new AvisoImei(OK, null, sku, caja, d);
            default -> throw new IllegalStateException("Estado de unidad desconocido: " + r.estado());
        };
    }
}
