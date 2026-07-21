package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorDAO dao;

    public ProveedorController(ProveedorDAO dao) {
        this.dao = dao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<Proveedor> getAll(@RequestParam(required = false) String tipo) {
        return dao.getAll(tipo);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/activos")
    public List<Proveedor> getActivos(@RequestParam(required = false) String tipo) {
        return dao.getActivos(tipo);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/{idProv}/tiene-pedidos")
    public Map<String, Boolean> tienePedidos(@PathVariable int idProv) {
        return Map.of("value", dao.tienePedidos(idProv));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody AltaRequest req) {
        dao.insertar(req.nombre(), req.divisa(), req.tipo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idProv}/activo")
    public void setActivo(@PathVariable int idProv, @RequestBody ActivoRequest req) {
        dao.setActivo(idProv, req.activo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}")
    public void editar(@PathVariable int idProv, @RequestBody EditarRequest req) {
        dao.editar(idProv, req.nombre(), req.divisa(), req.comentario());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idProv}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idProv) {
        dao.borrar(idProv);
    }

    private record AltaRequest(String nombre, String divisa, String tipo) {}
    private record ActivoRequest(boolean activo) {}
    private record EditarRequest(String nombre, String divisa, String comentario) {}
}
