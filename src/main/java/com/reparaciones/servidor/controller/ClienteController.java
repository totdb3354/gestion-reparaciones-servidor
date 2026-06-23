package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Cliente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteDAO dao;
    private final LogDAO logDao;

    public ClienteController(ClienteDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<Cliente> getAll() { return dao.getAll(); }

    @GetMapping("/activos")
    public List<Cliente> getActivos() { return dao.getActivos(); }

    @GetMapping("/{idCli}/tiene-telefonos")
    public Map<String, Boolean> tieneTelefonos(@PathVariable int idCli) {
        return Map.of("value", dao.tieneTelefonos(idCli));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void insertar(@RequestBody NombreRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.nombre());
        logDao.insertar(principal.getIdUsu(), "CREAR_CLIENTE", "NOMBRE: " + req.nombre());
    }

    @PutMapping("/{idCli}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void editar(@PathVariable int idCli, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        String anterior = dao.getNombreById(idCli);
        dao.editar(idCli, req.nombre(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_CLIENTE",
                "ID_CLI: " + idCli + ", NOMBRE: " + anterior + " → " + req.nombre());
    }

    @PatchMapping("/{idCli}/activo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void setActivo(@PathVariable int idCli, @RequestBody ActivoRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.setActivo(idCli, req.activo(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(),
                req.activo() ? "ALTA_CLIENTE" : "BAJA_CLIENTE", "ID_CLI: " + idCli);
    }

    private record NombreRequest(String nombre) {}
    private record EditarRequest(String nombre, LocalDateTime updatedAt) {}
    private record ActivoRequest(boolean activo, LocalDateTime updatedAt) {}
}
