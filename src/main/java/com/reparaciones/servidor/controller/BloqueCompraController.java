package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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

    record MoverRequest(@Schema(nullable = true, description = "Nulo = bloque nuevo") Integer idBloque,
                        LocalDateTime updatedAt) {}
}
