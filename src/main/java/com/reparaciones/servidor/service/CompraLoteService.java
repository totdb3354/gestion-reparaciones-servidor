package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.LoteCompras;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** La transacción de los dos lotes de pedidos (spec 4b §4.4, P5): todas las altas del lote y el marcado de sus
 *  solicitudes juntos; cualquier excepción deshace el lote entero. Recibe líneas ya resueltas (divisa del proveedor
 *  y precioEur calculados por el controlador ANTES de entrar aquí, como el lookup de IMEI en asignaciones). */
@Service
public class CompraLoteService {

    static final String GESTIONADA = "GESTIONADA";

    public record LineaCompra(int idCom, int idProv, int cantidad, boolean esUrgente,
                              double precioUnidad, String divisa, double precioEur) {}

    public record LineaOtro(int idProv, String concepto, int cantidad, boolean esUrgente,
                            double precioUnidad, String divisa, double precioEur) {}

    private final CompraComponenteDAO compraDao;
    private final CompraOtroDAO compraOtroDao;
    private final ReparacionComponenteDAO reparacionComponenteDao;
    private final SolicitudStockDAO solicitudStockDao;

    public CompraLoteService(CompraComponenteDAO compraDao, CompraOtroDAO compraOtroDao,
                             ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao) {
        this.compraDao = compraDao;
        this.compraOtroDao = compraOtroDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
    }

    /** Un alta por línea (insertar resuelve al master) y después cada solicitud a GESTIONADA con los mismos métodos
     *  que los PATCH de la campana (solo actúan si no estaba ya gestionada). */
    @Transactional
    public LoteCompras.Respuesta guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes,
                                                List<Integer> preventivas) {
        List<Integer> ids = new ArrayList<>();
        for (LineaCompra l : lineas) {
            ids.add(compraDao.insertar(l.idCom(), l.idProv(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur()));
        }
        for (Integer idRc : urgentes) reparacionComponenteDao.actualizarEstadoSolicitud(idRc, GESTIONADA);
        for (Integer idSol : preventivas) solicitudStockDao.actualizarEstado(idSol, GESTIONADA);
        return new LoteCompras.Respuesta(ids);
    }

    /** Un alta por línea, en orden; sin solicitudes (los otros pedidos no tienen). */
    @Transactional
    public LoteCompras.Respuesta guardarOtros(List<LineaOtro> lineas) {
        List<Integer> ids = new ArrayList<>();
        for (LineaOtro l : lineas) {
            ids.add(compraOtroDao.insertar(l.idProv(), l.concepto(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur()));
        }
        return new LoteCompras.Respuesta(ids);
    }
}
