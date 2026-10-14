package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
import com.reparaciones.servidor.model.TipoLinea;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Bloques de pedido (0.9.9, spec «Pedidos por bloques» §5.3): mover líneas y acciones sobre un bloque. Solo
 *  SUPERTECNICO, como el resto de escrituras de compras. Los logs van después de la transacción del servicio. */
@RestController
@RequestMapping("/api")
public class BloqueCompraController {

    private final BloqueCompraService servicio;
    private final RegistroBloques registro;
    private final LogDAO logDao;

    public BloqueCompraController(BloqueCompraService servicio, RegistroBloques registro, LogDAO logDao) {
        this.servicio = servicio;
        this.registro = registro;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/compras/{idCompra}/bloque")
    public void mover(@PathVariable int idCompra, @RequestBody MoverRequest req,
                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Movimiento m = servicio.mover(idCompra, new DestinoPedido(req.idBloque()), req.updatedAt());
        registro.movimiento(principal.getIdUsu(), m);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/confirmar")
    public void confirmar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.CONFIRMAR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/recibir")
    public void recibir(@PathVariable int idBloque, @RequestBody LineasRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.RECIBIR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/cancelar")
    public void cancelar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.CANCELAR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/borrar")
    public void borrar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.BORRAR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/separar")
    public SepararRespuesta separar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Separacion s = servicio.separar(idBloque, req.lineas());
        int idUsu = principal.getIdUsu();
        registro.creado(idUsu, s.nuevo(), s.idProv());
        logDao.insertar(idUsu, "SEPARAR_BLOQUE", conIds("DE: " + s.origen() + " A: " + s.nuevo(), s.ids(), s.idsOtros()));
        return new SepararRespuesta(s.nuevo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/juntar")
    public void juntar(@PathVariable int idBloque, @RequestBody JuntarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Union u = servicio.juntar(idBloque, req.destino(), req.lineas());
        logDao.insertar(principal.getIdUsu(), "JUNTAR_BLOQUE",
                conIds("DE: " + u.origen() + " A: " + u.destino(), u.ids(), u.idsOtros()));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/bloques-compra/{idBloque}/nota")
    public void nota(@PathVariable int idBloque, @RequestBody NotaRequest req,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        servicio.cambiarNota(idBloque, req.nota(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_NOTA_BLOQUE", "BLOQUE: " + idBloque);
    }

    /** Un log por línea (el de su transición de siempre, de pieza o de otro, para rastrear el stock línea a línea) y
     *  otro del bloque con los ids por tipo (0.9.15). */
    private void aplicar(int idBloque, BloqueCompraService.Accion accion, LineasRequest req, UsuarioPrincipal principal) {
        BloqueCompraService.Aplicada a = servicio.aplicar(idBloque, accion, req.lineas());
        int idUsu = principal.getIdUsu();
        for (BloqueCompraDAO.LineaBloque l : a.lineas()) {
            logDao.insertar(idUsu, logDeLinea(accion, l.tipoLinea()), detalleDeLinea(accion, l));
        }
        logDao.insertar(idUsu, accion.log(), conIds("BLOQUE: " + a.idBloque(),
                idsDe(a.lineas(), TipoLinea.PIEZA), idsDe(a.lineas(), TipoLinea.OTRO)));
    }

    /** La acción de siempre de la línea: CONFIRMAR_PEDIDO, RECIBIR_PEDIDO…; la de un otro con «_OTRO», como
     *  CompraOtroController (0.9.15). */
    static String logDeLinea(BloqueCompraService.Accion accion, TipoLinea tipo) {
        String base = switch (accion) {
            case CONFIRMAR -> "CONFIRMAR_PEDIDO";
            case RECIBIR -> "RECIBIR_PEDIDO";
            case CANCELAR -> "CANCELAR_PEDIDO";
            case BORRAR -> "BORRAR_PEDIDO";
        };
        return tipo == TipoLinea.OTRO ? base + "_OTRO" : base;
    }

    /** Los mismos detalles que las transiciones por línea de CompraController y CompraOtroController: recibir lleva el
     *  componente (o el concepto) y la cantidad. */
    static String detalleDeLinea(BloqueCompraService.Accion accion, BloqueCompraDAO.LineaBloque l) {
        boolean otro = l.tipoLinea() == TipoLinea.OTRO;
        String id = (otro ? "ID_COMPRA_OTRO: " : "ID_COMPRA: ") + l.idCompra();
        if (accion != BloqueCompraService.Accion.RECIBIR) return id;
        return id + (otro ? ", CONCEPTO: " : ", COMPONENTE: ") + l.tipo() + ", CANT: " + l.cantidad();
    }

    static List<Integer> idsDe(List<BloqueCompraDAO.LineaBloque> lineas, TipoLinea tipo) {
        return lineas.stream().filter(l -> l.tipoLinea() == tipo).map(BloqueCompraDAO.LineaBloque::idCompra).toList();
    }

    /** «{cabecera}, ID_COMPRA: 12, 13, ID_COMPRA_OTRO: 88» (0.9.15): la parte de otros solo si hay otros, y la de piezas
     *  si hay piezas o si no hay nada (el texto de siempre). */
    static String conIds(String cabecera, List<Integer> piezas, List<Integer> otros) {
        StringBuilder s = new StringBuilder(cabecera);
        if (!piezas.isEmpty() || otros.isEmpty()) s.append(", ID_COMPRA: ").append(unir(piezas));
        if (!otros.isEmpty()) s.append(", ID_COMPRA_OTRO: ").append(unir(otros));
        return s.toString();
    }

    static String unir(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    record LineasRequest(List<LineaVista> lineas) {}

    record MoverRequest(@Schema(nullable = true, description = "Nulo = bloque nuevo") Integer idBloque,
                        LocalDateTime updatedAt) {}

    record JuntarRequest(int destino, List<LineaVista> lineas) {}

    record NotaRequest(@Schema(nullable = true, description = "Vacía o nula = sin nota") String nota,
                       LocalDateTime updatedAt) {}

    record SepararRespuesta(int idBloque) {}
}
