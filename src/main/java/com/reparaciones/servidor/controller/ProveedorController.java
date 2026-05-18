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
    public List<Proveedor> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/activos")
    public List<Proveedor> getActivos() {
        return dao.getActivos();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/{idProv}/tiene-pedidos")
    public Map<String, Boolean> tienePedidos(@PathVariable int idProv) {
        return Map.of("value", dao.tienePedidos(idProv));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody NombreRequest req) {
        dao.insertar(req.nombre());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idProv}/activo")
    public void setActivo(@PathVariable int idProv, @RequestBody ActivoRequest req) {
        dao.setActivo(idProv, req.activo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idProv}/divisa")
    public void setDivisa(@PathVariable int idProv, @RequestBody DivisaRequest req) {
        dao.setDivisa(idProv, req.divisa());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idProv}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idProv) {
        dao.borrar(idProv);
    }

    private record NombreRequest(String nombre) {}
    private record ActivoRequest(boolean activo) {}
    private record DivisaRequest(String divisa) {}
}
