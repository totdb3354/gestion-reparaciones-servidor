package com.reparaciones.servidor.pedidos;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** «Pedidos» (spec pieza 2 §8): solo lectura, SUPERTECNICO y ADMIN (no hay jerarquía de roles: van los dos nombrados). */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class PedidosController {

    private final VistaPedidos vista;

    public PedidosController(VistaPedidos vista) {
        this.vista = vista;
    }

    /** Los pedidos abiertos, del más antiguo al más nuevo; {@code viejos}, también los del Simulador fuera de la ventana. */
    @GetMapping
    public List<PedidoVista> pedidos(@RequestParam(defaultValue = "false") boolean viejos) {
        return vista.pedidos(viejos);
    }

    @GetMapping("/fuentes")
    public List<FuentePedidosVista> fuentes() {
        return vista.fuentes();
    }
}
