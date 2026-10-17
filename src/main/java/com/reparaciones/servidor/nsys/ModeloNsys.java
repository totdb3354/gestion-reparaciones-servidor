package com.reparaciones.servidor.nsys;

import org.springframework.stereotype.Service;

/** Modelo de un IMEI según el espejo de NSYS (spec pieza 1 §7). Sustituye a IMEIcheck con la misma firma: null si NSYS no
 *  conoce el IMEI o su modelo no está en el catálogo del ERP. Entonces se escribe a mano, como antes. */
@Service
public class ModeloNsys {

    private final NsysDAO dao;

    public ModeloNsys(NsysDAO dao) {
        this.dao = dao;
    }

    public String lookupModeloInterno(String imei) {
        if (imei == null || imei.isBlank()) return null;
        return dao.modeloDe(imei.strip());
    }
}
