package com.reparaciones.servidor.nsys;

/** El cierre automático de un cambio del taller tras una importación (spec 0.9.21 §2.2), dato a dato: con lo que NSYS dice
 *  ahora (la fila más reciente del IMEI en el espejo) y lo que decía al hacer el cambio. Es puro. */
public final class CierreTaller {

    public enum Decision { SIGUE, PASADO, MANDA_NSYS }

    private CierreTaller() {}

    /** NSYS ahora = lo del taller: PASADO. NSYS ahora distinto de lo que tenía al hacer el cambio: MANDA_NSYS (alguien lo
     *  cambió después a otra cosa: manda lo más reciente). Si no, SIGUE. Si NSYS ahora no sabe el dato (un producto que el
     *  maestro no traduce deja el color vacío), no se sabe si coincide: SIGUE. */
    public static Decision decidir(CambioTaller c, String nsysAhora) {
        if (nsysAhora == null || nsysAhora.isBlank()) return Decision.SIGUE;
        if (Normalizacion.mismoTexto(nsysAhora, c.valor())) return Decision.PASADO;
        if (!Normalizacion.mismoTexto(nsysAhora, c.valorNsys())) return Decision.MANDA_NSYS;
        return Decision.SIGUE;
    }
}
