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
 *  y precioEur calculados por el controlador ANTES de entrar aquí, como el lookup de IMEI en asignaciones). Desde la
 *  0.9.15 (spec «Añadir otro» §4.2) los otros van al bloque de su proveedor, el mismo que sus piezas. */
@Service
public class CompraLoteService {

    static final String GESTIONADA = "GESTIONADA";

    public record LineaCompra(int idCom, int idProv, int cantidad, boolean esUrgente,
                              double precioUnidad, String divisa, double precioEur) {}

    public record LineaOtro(int idProv, String concepto, int cantidad, boolean esUrgente,
                            double precioUnidad, String divisa, double precioEur) {}

    public record BloqueCreado(int idBloque, int idProv) {}

    /** Respuesta de la API, bloques nuevos del lote (0.9.9) y el bloque de cada proveedor (0.9.15, para el «BLOQUE: n»
     *  del log de cada otro). Sus logs van tras la transacción, en el controlador. */
    public record ResultadoLote(LoteCompras.Respuesta respuesta, List<BloqueCreado> bloquesCreados,
                                Map<Integer, Integer> bloquePorProveedor) {
        /** Sin el bloque de cada proveedor (los tests que no miran los logs de los otros). */
        public ResultadoLote(LoteCompras.Respuesta respuesta, List<BloqueCreado> bloquesCreados) {
            this(respuesta, bloquesCreados, Map.of());
        }
    }

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

    /** «Nuevo pedido»: un alta por pieza (insertar resuelve al master) y por otro, en el bloque de su proveedor, y después
     *  cada solicitud a GESTIONADA con los mismos métodos que los PATCH de la campana. El bloque de cada proveedor se
     *  resuelve una vez y vale para sus piezas y sus otros: el de `destinos` si viene; si no, su abierto más reciente o
     *  uno nuevo. */
    @Transactional
    public ResultadoLote guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes, List<Integer> preventivas,
                                       Map<Integer, DestinoPedido> destinos, List<LineaOtro> otros) {
        BloquesDelLote bloques = new BloquesDelLote(destinos);
        List<Integer> ids = new ArrayList<>();
        for (LineaCompra l : lineas) {
            ids.add(compraDao.insertar(l.idCom(), l.idProv(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur(), bloques.de(l.idProv())));
        }
        List<Integer> idsOtros = insertarOtros(otros, bloques);
        for (Integer idRc : urgentes) reparacionComponenteDao.actualizarEstadoSolicitud(idRc, GESTIONADA);
        for (Integer idSol : preventivas) solicitudStockDao.actualizarEstado(idSol, GESTIONADA);
        return new ResultadoLote(new LoteCompras.Respuesta(ids, idsOtros), bloques.creados, bloques.porProveedor);
    }

    /** El lote de otros (la web 0.9.14): un alta por línea, en orden, cada una en el abierto más reciente de su
     *  proveedor o en uno nuevo (0.9.15); sin solicitudes. `idsCreados` son los otros. */
    @Transactional
    public ResultadoLote guardarOtros(List<LineaOtro> lineas) {
        BloquesDelLote bloques = new BloquesDelLote(Map.of());
        List<Integer> ids = insertarOtros(lineas, bloques);
        return new ResultadoLote(new LoteCompras.Respuesta(ids), bloques.creados, bloques.porProveedor);
    }

    private List<Integer> insertarOtros(List<LineaOtro> otros, BloquesDelLote bloques) {
        List<Integer> ids = new ArrayList<>();
        for (LineaOtro o : otros) {
            ids.add(compraOtroDao.insertar(o.idProv(), o.concepto(), o.cantidad(), o.esUrgente(),
                    o.precioUnidad(), o.divisa(), o.precioEur(), bloques.de(o.idProv())));
        }
        return ids;
    }

    /** El bloque de cada proveedor del lote, resuelto una sola vez (0.9.9) para sus piezas y sus otros (0.9.15). */
    private final class BloquesDelLote {
        private final Map<Integer, DestinoPedido> destinos;
        private final Map<Integer, Integer> porProveedor = new HashMap<>();
        private final List<BloqueCreado> creados = new ArrayList<>();

        BloquesDelLote(Map<Integer, DestinoPedido> destinos) {
            this.destinos = destinos;
        }

        int de(int idProv) {
            Integer idBloque = porProveedor.get(idProv);
            if (idBloque != null) return idBloque;
            DestinoBloque.Destino d = destinoBloque.resolver(idProv, destinos.get(idProv), true);
            porProveedor.put(idProv, d.idBloque());
            if (d.nuevo()) creados.add(new BloqueCreado(d.idBloque(), idProv));
            return d.idBloque();
        }
    }
}
