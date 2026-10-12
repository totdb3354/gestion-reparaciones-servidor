package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tipos de pieza que vende un proveedor (0.9.9, spec 2026-10-12 §4.3). El registro va tras la transacción del
 *  servicio. */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorPiezasController {

    private final ProveedorPiezasService servicio;
    private final LogDAO logDao;

    public ProveedorPiezasController(ProveedorPiezasService servicio, LogDAO logDao) {
        this.servicio = servicio;
        this.logDao = logDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/{idProv}/piezas")
    public List<Tipo> leer(@PathVariable int idProv) {
        return servicio.leer(idProv).stream().map(t -> new Tipo(t.prefijo(), t.vende(), t.modelos(), t.excluidos())).toList();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}/piezas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reemplazar(@PathVariable int idProv, @RequestBody Request req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        // Los nulos llegan tal cual al servicio, que responde 422 (Stream.toList admite nulos).
        List<ProveedorPiezasService.Entrada> tipos = req.tipos() == null ? null : req.tipos().stream()
                .map(t -> t == null ? null : new ProveedorPiezasService.Entrada(t.prefijo(), t.excluidos())).toList();
        String resumen = servicio.reemplazar(idProv, tipos);
        logDao.insertar(principal.getIdUsu(), "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: " + idProv + ", PIEZAS: " + resumen);
    }

    /** Un tipo que existe: sus modelos (en el orden de la tienda), si el proveedor lo vende y qué modelos excluye. */
    record Tipo(String prefijo, boolean vende, List<String> modelos, List<String> excluidos) {}

    record Request(List<TipoRequest> tipos) {}

    record TipoRequest(String prefijo, List<String> excluidos) {}
}
