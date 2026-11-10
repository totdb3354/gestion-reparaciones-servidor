package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;

/** Lo que el ERP dice del IMEI puesto en una fila. nivel: OK, AVISO o MAL. motivo: null con OK. skuMovil y caja: null si el movil no esta en NSYS. */
public record AvisoImei(String nivel, String motivo, String skuMovil, String caja) {

    public static final String OK = "OK";
    public static final String AVISO = "AVISO";
    public static final String MAL = "MAL";

    public static AvisoImei de(Aceptacion ac, Canal canal, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos,
                               boolean enOtraFilaViva) {
        if (m == null) return new AvisoImei(MAL, "NO_EN_NSYS", null, null);
        String sku = v.sku();
        String caja = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (enOtraFilaViva) return new AvisoImei(MAL, "EN_OTRA_FILA", sku, caja);
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, trabajos, canal);
        return switch (r.estado()) {
            case "NO_CUADRA" -> new AvisoImei(MAL, r.motivo(), sku, caja);
            case "NECESITA_TALLER" -> new AvisoImei(MAL, "COLOR_O_GRADO", sku, caja);
            case "EN_TALLER" -> new AvisoImei(AVISO, "EN_TALLER", sku, caja);
            case "CON_AVISO" -> new AvisoImei(AVISO, r.motivo(), sku, caja);
            default -> new AvisoImei(OK, null, sku, caja);
        };
    }
}
