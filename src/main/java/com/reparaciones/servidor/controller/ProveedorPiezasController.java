package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Piezas que vende un proveedor (0.9.9, spec 2026-10-11 §4.3). El registro va tras la transaccion del servicio. */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorPiezasController {

    private final ProveedorPiezasService servicio;
    private final LogDAO logDao;

    public ProveedorPiezasController(ProveedorPiezasService servicio, LogDAO logDao) {
        this.servicio = servicio;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}/componentes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reemplazar(@PathVariable int idProv, @RequestBody Request req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorPiezasService.Cambios c = servicio.reemplazar(idProv, req.idsCom());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PIEZAS_PROVEEDOR",
                "ID_PROV: " + idProv + ", PIEZAS: +" + c.anadidas() + " -" + c.quitadas());
    }

    record Request(List<Integer> idsCom) {}
}
