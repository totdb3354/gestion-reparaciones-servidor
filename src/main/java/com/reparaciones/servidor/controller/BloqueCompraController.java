package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
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
        logDao.insertar(idUsu, "SEPARAR_BLOQUE", "DE: " + s.origen() + " A: " + s.nuevo() + ", ID_COMPRA: " + unir(s.ids()));
        return new SepararRespuesta(s.nuevo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/juntar")
    public void juntar(@PathVariable int idBloque, @RequestBody JuntarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Union u = servicio.juntar(idBloque, req.destino(), req.lineas());
        logDao.insertar(principal.getIdUsu(), "JUNTAR_BLOQUE",
                "DE: " + u.origen() + " A: " + u.destino() + ", ID_COMPRA: " + unir(u.ids()));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/bloques-compra/{idBloque}/nota")
    public void nota(@PathVariable int idBloque, @RequestBody NotaRequest req,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        servicio.cambiarNota(idBloque, req.nota(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_NOTA_BLOQUE", "BLOQUE: " + idBloque);
    }

    /** Un log por línea (el de su transición de siempre, para rastrear el stock línea a línea) y otro del bloque. */
    private void aplicar(int idBloque, BloqueCompraService.Accion accion, LineasRequest req, UsuarioPrincipal principal) {
        BloqueCompraService.Aplicada a = servicio.aplicar(idBloque, accion, req.lineas());
        int idUsu = principal.getIdUsu();
        for (BloqueCompraDAO.LineaBloque l : a.lineas()) logDao.insertar(idUsu, logDeLinea(accion), detalleDeLinea(accion, l));
        logDao.insertar(idUsu, accion.log(), "BLOQUE: " + a.idBloque() + ", ID_COMPRA: "
                + unir(a.lineas().stream().map(BloqueCompraDAO.LineaBloque::idCompra).toList()));
    }

    static String logDeLinea(BloqueCompraService.Accion accion) {
        return switch (accion) {
            case CONFIRMAR -> "CONFIRMAR_PEDIDO";
            case RECIBIR -> "RECIBIR_PEDIDO";
            case CANCELAR -> "CANCELAR_PEDIDO";
            case BORRAR -> "BORRAR_PEDIDO";
        };
    }

    /** Los mismos detalles que las transiciones por línea de CompraController (RECIBIR_PEDIDO lleva componente y cantidad). */
    static String detalleDeLinea(BloqueCompraService.Accion accion, BloqueCompraDAO.LineaBloque l) {
        return accion == BloqueCompraService.Accion.RECIBIR
                ? "ID_COMPRA: " + l.idCompra() + ", COMPONENTE: " + l.tipo() + ", CANT: " + l.cantidad()
                : "ID_COMPRA: " + l.idCompra();
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
