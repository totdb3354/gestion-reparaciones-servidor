package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.TipoLinea;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

/** Logs de los bloques (0.9.9, spec «Pedidos por bloques» §5.4) que comparten los controllers de pedidos. Se llaman
 *  después de la transacción, como el resto de logs. */
@Component
public class RegistroBloques {

    private final LogDAO logDao;
    private final ProveedorDAO proveedorDao;

    public RegistroBloques(LogDAO logDao, ProveedorDAO proveedorDao) {
        this.logDao = logDao;
        this.proveedorDao = proveedorDao;
    }

    public void creado(int idUsu, int idBloque, int idProv) {
        logDao.insertar(idUsu, "CREAR_BLOQUE", "BLOQUE: " + idBloque + ", PROVEEDOR: " + nombre(idProv));
    }

    public void vaciado(int idUsu, int idBloque) {
        logDao.insertar(idUsu, "BORRAR_BLOQUE", "BLOQUE: " + idBloque + " (se quedó sin líneas)");
    }

    /** CREAR_BLOQUE si el destino es nuevo, MOVER_LINEA_BLOQUE (con ID_COMPRA_OTRO si es un otro, 0.9.15) y
     *  BORRAR_BLOQUE si el origen se quedó vacío. */
    public void movimiento(int idUsu, BloqueCompraService.Movimiento m) {
        if (m.bloqueNuevo()) creado(idUsu, m.a(), m.idProv());
        String id = (m.tipo() == TipoLinea.OTRO ? "ID_COMPRA_OTRO: " : "ID_COMPRA: ") + m.idCompra();
        logDao.insertar(idUsu, "MOVER_LINEA_BLOQUE", id + ", ESTADO: " + m.estado()
                + ", DE: " + (m.de() == null ? "-" : m.de()) + " A: " + m.a());
        if (m.origenBorrado() && m.de() != null) vaciado(idUsu, m.de());
    }

    /** getNombreById lanza con un id inexistente: un log no debe tumbar la petición. */
    private String nombre(int idProv) {
        try {
            return proveedorDao.getNombreById(idProv);
        } catch (EmptyResultDataAccessException e) {
            return "ID_PROV " + idProv;
        }
    }
}
