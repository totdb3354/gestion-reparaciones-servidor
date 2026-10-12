package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Operaciones sobre bloques de pedido (0.9.9, spec «Pedidos por bloques» §5). Cada método es una transacción: un
 *  fallo a mitad no deja nada a medias (ni un bloque vacío, ni una línea fuera de su bloque). */
@Service
public class BloqueCompraService {

    public static final String MSG_MODIFICADO = "Este bloque fue modificado por otro usuario.";
    static final String MSG_SIN_LINEAS = "No hay líneas a las que aplicar la acción.";
    static final String MSG_NO_EXISTE_LINEA = "El pedido no existe";
    static final String MSG_LINEA_MODIFICADA = "Dato modificado por otro usuario";
    static final String MSG_YA_EN_ESE = "La línea ya está en ese bloque.";
    static final String MSG_YA_SOLA = "La línea ya está sola en su bloque.";
    static final String MSG_DEJA_UNA = "Deja al menos una línea en el bloque.";
    static final String MSG_OTRO_BLOQUE = "Elige otro bloque.";
    static final String MSG_LINEA_CERRADA = "Una línea recibida o cancelada no se puede mover.";
    static final String MSG_NOTA_LARGA = "La nota no puede superar los 200 caracteres.";
    static final int NOTA_MAX = 200; // VARCHAR(200) de Bloque_compra.NOTA

    /** Una línea que cambió de bloque: de dónde a dónde, si el destino es nuevo y si el origen se quedó vacío (y se
     *  borró). `de` es nulo para una línea que no tenía bloque (hueco de un despliegue). */
    public record Movimiento(int idCompra, String estado, Integer de, int a, boolean bloqueNuevo, int idProv,
                             boolean origenBorrado) {}

    /** Acción de estado de un bloque: estado que tienen que tener las líneas y nombre del log del bloque. */
    public enum Accion {
        CONFIRMAR("pendiente", "CONFIRMAR_BLOQUE"),
        RECIBIR("en_camino", "RECIBIR_BLOQUE"),
        CANCELAR("en_camino", "CANCELAR_BLOQUE"),
        BORRAR("pendiente", "BORRAR_BLOQUE");

        private final String estado;
        private final String log;

        Accion(String estado, String log) {
            this.estado = estado;
            this.log = log;
        }

        public String log() {
            return log;
        }
    }

    /** Las líneas a las que se aplicó una acción, para sus logs. */
    public record Aplicada(int idBloque, List<BloqueCompraDAO.LineaBloque> lineas) {}

    public record Separacion(int origen, int nuevo, int idProv, List<Integer> ids) {}

    public record Union(int origen, int destino, List<Integer> ids) {}

    private record Comprobadas(BloqueCompraDAO.Bloque bloque, List<BloqueCompraDAO.LineaBloque> elegidas, int total) {}

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

    /** PATCH /api/compras/{id}/bloque (spec §5.3). `destino.idBloque()` nulo = bloque nuevo. Una línea recibida o
     *  cancelada no se mueve (422); de las demás, mover no toca el stock (spec §2) y la confirmación de una línea ya
     *  pedida la pide la web. */
    @Transactional
    public Movimiento mover(int idCompra, DestinoPedido destino, LocalDateTime updatedAt) {
        CompraComponente l = compraDao.getById(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_EXISTE_LINEA));
        if (!mismaVersion(l.getUpdatedAt(), updatedAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_LINEA_MODIFICADA);
        }
        // Lo recibido y lo cancelado es historial: no cambia de bloque (spec 2026-10-12 §2).
        if ("recibido".equals(l.getEstado()) || "cancelado".equals(l.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_LINEA_CERRADA);
        }
        Integer origen = l.getIdBloque();
        if (destino.idBloque() == null && origen != null && bloqueDao.contarLineas(origen) <= 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_YA_SOLA);
        }
        if (destino.idBloque() != null && destino.idBloque().equals(origen)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_YA_EN_ESE);
        }
        DestinoBloque.Destino d = destinoBloque.resolver(l.getIdProv(), destino, false);
        compraDao.moverABloque(idCompra, origen, d.idBloque());
        boolean borrado = origen != null && bloqueDao.borrarSiVacio(origen);
        return new Movimiento(idCompra, l.getEstado(), origen, d.idBloque(), d.nuevo(), l.getIdProv(), borrado);
    }

    /** PUT /api/compras/{id} con otro proveedor (spec §5.2): edita, lleva la línea a un bloque del proveedor nuevo y
     *  borra el de origen si se queda vacío, todo junto. Sin `destino`, la regla por defecto: pendiente → su abierto más
     *  reciente o uno nuevo; ya pedida → uno nuevo. */
    @Transactional
    public Movimiento editarCambiandoProveedor(int idCompra, int idProv, int cantidad, boolean esUrgente,
                                               double precioUnidad, String divisa, double precioEur,
                                               LocalDateTime updatedAt, DestinoPedido destino) {
        CompraComponente l = compraDao.getById(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_EXISTE_LINEA));
        compraDao.editar(idCompra, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, updatedAt);
        DestinoBloque.Destino d = destinoBloque.resolver(idProv, destino, "pendiente".equals(l.getEstado()));
        Integer origen = l.getIdBloque();
        compraDao.moverABloque(idCompra, origen, d.idBloque());
        boolean borrado = origen != null && bloqueDao.borrarSiVacio(origen);
        return new Movimiento(idCompra, l.getEstado(), origen, d.idBloque(), d.nuevo(), idProv, borrado);
    }

    /** DELETE /api/compras/{id}: borra la línea pendiente y, si era la última, su bloque. Devuelve ese bloque. */
    @Transactional
    public Optional<Integer> borrarLineaPendiente(int idCompra) {
        Integer origen = compraDao.getById(idCompra).map(CompraComponente::getIdBloque).orElse(null);
        compraDao.borrarPendiente(idCompra, origen);
        return origen != null && bloqueDao.borrarSiVacio(origen) ? Optional.of(origen) : Optional.empty();
    }

    /** Confirmar, recibir, cancelar o borrar (spec §5.3): solo las líneas de la lista, todas en el estado de la acción;
     *  si no, 409 sin cambiar nada. Cada línea pasa por su transición de siempre (recibir suma stock como «Confirmar
     *  recibido»). Borrar exige el bloque entero y lo borra. */
    @Transactional
    public Aplicada aplicar(int idBloque, Accion accion, List<LineaVista> vistas) {
        Comprobadas c = comprobar(idBloque, vistas, accion == Accion.BORRAR);
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            if (!accion.estado.equals(l.estado())) throw modificado();
        }
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            switch (accion) {
                case CONFIRMAR -> compraDao.confirmar(l.idCompra(), l.updatedAt());
                case RECIBIR -> compraDao.confirmarRecibido(l.idCompra(), l.updatedAt());
                case CANCELAR -> compraDao.cancelar(l.idCompra(), l.updatedAt());
                case BORRAR -> compraDao.borrarPendiente(l.idCompra(), idBloque);
            }
        }
        // Borrar exige el bloque entero: si no queda vacío, otro escribió entre la lectura y el borrado (409, revierte).
        if (accion == Accion.BORRAR && !bloqueDao.borrarSiVacio(idBloque)) throw modificado();
        return new Aplicada(idBloque, c.elegidas());
    }

    /** «Separar líneas…» (spec §5.3): las de la lista pasan a un bloque nuevo del mismo proveedor; al menos una se
     *  queda. Los estados no cuentan (la web ya pidió confirmación por las ya pedidas). */
    @Transactional
    public Separacion separar(int idBloque, List<LineaVista> vistas) {
        Comprobadas c = comprobar(idBloque, vistas, false);
        if (bloqueDao.estaTerminado(idBloque)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, DestinoBloque.MSG_TERMINADO.formatted(idBloque));
        }
        if (c.elegidas().size() >= c.total()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_DEJA_UNA);
        }
        int nuevo = bloqueDao.crear(c.bloque().idProv());
        List<Integer> ids = new ArrayList<>();
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            compraDao.moverABloque(l.idCompra(), idBloque, nuevo);
            ids.add(l.idCompra());
        }
        // Separar no se lleva todas: si el origen quedó vacío, otro lo cambió entre la lectura y el movimiento (409).
        if (bloqueDao.borrarSiVacio(idBloque)) throw modificado();
        return new Separacion(idBloque, nuevo, c.bloque().idProv(), ids);
    }

    /** «Juntar con…» (spec §5.3): todas las líneas (la lista tiene que ser el bloque entero) pasan a `destino`, del mismo
     *  proveedor; la nota se añade a la del destino y el bloque desaparece. */
    @Transactional
    public Union juntar(int idBloque, int destino, List<LineaVista> vistas) {
        if (destino == idBloque) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_OTRO_BLOQUE);
        Comprobadas c = comprobar(idBloque, vistas, true);
        if (bloqueDao.estaTerminado(idBloque)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, DestinoBloque.MSG_TERMINADO.formatted(idBloque));
        }
        destinoBloque.resolver(c.bloque().idProv(), new DestinoPedido(destino), false); // 409 si no existe, 422 si es de otro proveedor o está terminado
        BloqueCompraDAO.Bloque d = bloqueDao.getById(destino).orElseThrow(BloqueCompraService::modificado);
        List<Integer> ids = new ArrayList<>();
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            compraDao.moverABloque(l.idCompra(), idBloque, destino);
            ids.add(l.idCompra());
        }
        String nota = unirNotas(d.nota(), c.bloque().nota());
        if (!Objects.equals(nota, d.nota())) bloqueDao.cambiarNota(destino, nota);
        // Se movió el bloque entero: si no queda vacío, otro añadió una línea en medio (409, revierte).
        if (!bloqueDao.borrarSiVacio(idBloque)) throw modificado();
        return new Union(idBloque, destino, ids);
    }

    /** Nota del destino con la del absorbido añadida tras « · », recortada a 200 (spec §5.3). */
    static String unirNotas(String destino, String origen) {
        boolean sinOrigen = origen == null || origen.isBlank();
        if (sinOrigen) return destino;
        boolean sinDestino = destino == null || destino.isBlank();
        String unida = sinDestino ? origen : destino + " · " + origen;
        return unida.length() > NOTA_MAX ? unida.substring(0, NOTA_MAX) : unida;
    }

    /** PATCH …/nota: recortada; vacía = sin nota; con la versión del bloque que vio la web. */
    @Transactional
    public void cambiarNota(int idBloque, String nota, LocalDateTime updatedAt) {
        String limpia = nota == null ? "" : nota.trim();
        if (limpia.length() > NOTA_MAX) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOTA_LARGA);
        BloqueCompraDAO.Bloque b = bloqueDao.getById(idBloque).orElseThrow(BloqueCompraService::modificado);
        if (!mismaVersion(b.updatedAt(), updatedAt)) throw modificado();
        bloqueDao.cambiarNota(idBloque, limpia.isEmpty() ? null : limpia);
    }

    /** La lista de la web contra la base (spec §5.3): lista vacía → 422; bloque inexistente, línea que no es del
     *  bloque o con otra versión → 409. `exigirTodas` (borrar y juntar): la lista tiene que ser el bloque entero. */
    private Comprobadas comprobar(int idBloque, List<LineaVista> vistas, boolean exigirTodas) {
        if (vistas == null || vistas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_SIN_LINEAS);
        }
        BloqueCompraDAO.Bloque bloque = bloqueDao.getById(idBloque).orElseThrow(BloqueCompraService::modificado);
        Map<Integer, BloqueCompraDAO.LineaBloque> actuales = new LinkedHashMap<>();
        for (BloqueCompraDAO.LineaBloque l : bloqueDao.lineas(idBloque)) actuales.put(l.idCompra(), l);
        Map<Integer, BloqueCompraDAO.LineaBloque> elegidas = new LinkedHashMap<>();
        for (LineaVista v : vistas) {
            BloqueCompraDAO.LineaBloque l = actuales.get(v.idCompra());
            if (l == null || !mismaVersion(l.updatedAt(), v.updatedAt())) throw modificado();
            elegidas.put(l.idCompra(), l);
        }
        if (exigirTodas && elegidas.size() != actuales.size()) throw modificado();
        return new Comprobadas(bloque, new ArrayList<>(elegidas.values()), actuales.size());
    }

    private static ResponseStatusException modificado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, MSG_MODIFICADO);
    }

    /** Bloqueo optimista a segundos, como CompraComponenteDAO.checkUpdatedAt. */
    static boolean mismaVersion(LocalDateTime bd, LocalDateTime cliente) {
        return bd != null && cliente != null
                && bd.truncatedTo(ChronoUnit.SECONDS).equals(cliente.truncatedTo(ChronoUnit.SECONDS));
    }
}
