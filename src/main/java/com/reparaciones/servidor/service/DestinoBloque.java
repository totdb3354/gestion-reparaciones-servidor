package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** A qué bloque va una línea nueva o que cambia de proveedor (0.9.9, spec «Pedidos por bloques» §5.2). No abre
 *  transacción: la pone quien lo llama (el lote, el alta suelta, editar y mover). */
@Component
public class DestinoBloque {

    static final String MSG_OTRO_PROVEEDOR = "El bloque %d es de otro proveedor.";
    static final String MSG_NO_EXISTE = "El bloque %d ya no existe; elige otro destino.";
    static final String MSG_TERMINADO = "El bloque %d ya está terminado.";

    public record Destino(int idBloque, boolean nuevo) {}

    private final BloqueCompraDAO dao;

    public DestinoBloque(BloqueCompraDAO dao) {
        this.dao = dao;
    }

    /** `pedido` nulo = regla por defecto: con `preferirAbierto`, el abierto más reciente del proveedor o uno nuevo; sin
     *  él, siempre uno nuevo. `pedido` con `idBloque` nulo = bloque nuevo; con número, ese bloque (409 si ya no existe,
     *  422 si es de otro proveedor o si está terminado, 2026-10-12). Un destino que dejó de estar abierto se acepta
     *  mientras no esté terminado: añadir líneas pendientes siempre es seguro (es lo que hace «Añadir líneas…»). */
    public Destino resolver(int idProv, DestinoPedido pedido, boolean preferirAbierto) {
        if (pedido == null) {
            if (preferirAbierto) {
                Optional<Integer> abierto = dao.abiertoMasReciente(idProv);
                if (abierto.isPresent()) return new Destino(abierto.get(), false);
            }
            return new Destino(dao.crear(idProv), true);
        }
        if (pedido.idBloque() == null) return new Destino(dao.crear(idProv), true);
        int idBloque = pedido.idBloque();
        BloqueCompraDAO.Bloque b = dao.getById(idBloque).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, MSG_NO_EXISTE.formatted(idBloque)));
        if (b.idProv() != idProv) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_OTRO_PROVEEDOR.formatted(idBloque));
        }
        if (dao.estaTerminado(idBloque)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_TERMINADO.formatted(idBloque));
        }
        return new Destino(idBloque, false);
    }
}
