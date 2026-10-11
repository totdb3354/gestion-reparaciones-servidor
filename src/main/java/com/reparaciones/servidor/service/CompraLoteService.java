package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LoteCompras;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public record BloqueCreado(int idBloque, int idProv) {}

    /** Respuesta de la API y bloques nuevos del lote (0.9.9): sus logs van tras la transacción, en el controlador. */
    public record ResultadoLote(LoteCompras.Respuesta respuesta, List<BloqueCreado> bloquesCreados) {}

    private final CompraComponenteDAO compraDao;
    private final CompraOtroDAO compraOtroDao;
    private final ReparacionComponenteDAO reparacionComponenteDao;
    private final SolicitudStockDAO solicitudStockDao;
    private final DestinoBloque destinoBloque;

    public CompraLoteService(CompraComponenteDAO compraDao, CompraOtroDAO compraOtroDao,
                             ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao,
                             DestinoBloque destinoBloque) {
        this.compraDao = compraDao;
        this.compraOtroDao = compraOtroDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
        this.destinoBloque = destinoBloque;
    }

    /** Un alta por línea (insertar resuelve al master) en el bloque de su proveedor y después cada solicitud a
     *  GESTIONADA con los mismos métodos que los PATCH de la campana. El destino de cada proveedor se resuelve una vez
     *  (0.9.9): el de `destinos` si viene; si no, su abierto más reciente o uno nuevo. */
    @Transactional
    public ResultadoLote guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes, List<Integer> preventivas,
                                       Map<Integer, DestinoPedido> destinos) {
        Map<Integer, Integer> bloquePorProveedor = new HashMap<>();
        List<BloqueCreado> creados = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (LineaCompra l : lineas) {
            Integer idBloque = bloquePorProveedor.get(l.idProv());
            if (idBloque == null) {
                DestinoBloque.Destino d = destinoBloque.resolver(l.idProv(), destinos.get(l.idProv()), true);
                idBloque = d.idBloque();
                bloquePorProveedor.put(l.idProv(), idBloque);
                if (d.nuevo()) creados.add(new BloqueCreado(idBloque, l.idProv()));
            }
            ids.add(compraDao.insertar(l.idCom(), l.idProv(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur(), idBloque));
        }
        for (Integer idRc : urgentes) reparacionComponenteDao.actualizarEstadoSolicitud(idRc, GESTIONADA);
        for (Integer idSol : preventivas) solicitudStockDao.actualizarEstado(idSol, GESTIONADA);
        return new ResultadoLote(new LoteCompras.Respuesta(ids), creados);
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
