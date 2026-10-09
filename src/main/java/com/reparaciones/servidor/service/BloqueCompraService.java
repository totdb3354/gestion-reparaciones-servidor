package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones sobre bloques de pedido (0.9.9, spec «Pedidos por bloques» §5). Cada método es una transacción: un
 *  fallo a mitad no deja nada a medias (ni un bloque vacío, ni una línea fuera de su bloque). */
@Service
public class BloqueCompraService {

    /** Una línea que cambió de bloque: de dónde a dónde, si el destino es nuevo y si el origen se quedó vacío (y se
     *  borró). `de` es nulo para una línea que no tenía bloque (hueco de un despliegue). */
    public record Movimiento(int idCompra, String estado, Integer de, int a, boolean bloqueNuevo, int idProv,
                             boolean origenBorrado) {}

    private final BloqueCompraDAO bloqueDao;
    private final CompraComponenteDAO compraDao;
    private final DestinoBloque destinoBloque;

    public BloqueCompraService(BloqueCompraDAO bloqueDao, CompraComponenteDAO compraDao, DestinoBloque destinoBloque) {
        this.bloqueDao = bloqueDao;
        this.compraDao = compraDao;
        this.destinoBloque = destinoBloque;
    }

    /** POST /api/compras (alta suelta; la web usa el lote): regla por defecto y alta en la misma transacción. */
    @Transactional
    public DestinoBloque.Destino altaSuelta(int idCom, int idProv, int cantidad, boolean esUrgente,
                                            double precioUnidad, String divisa, double precioEur) {
        DestinoBloque.Destino d = destinoBloque.resolver(idProv, null, true);
        compraDao.insertar(idCom, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, d.idBloque());
        return d;
    }
}
