package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.Cliente;
import com.reparaciones.servidor.model.ValorBooleano;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    /** Nombre de la operación en {@link RegistroIdempotencia}. */
    static final String OP_ALTA = "alta-cliente";

    private final ClienteDAO dao;
    private final LogDAO logDao;
    private final RegistroIdempotencia idempotencia;

    public ClienteController(ClienteDAO dao, LogDAO logDao, RegistroIdempotencia idempotencia) {
        this.dao = dao;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
    }

    @GetMapping
    public List<Cliente> getAll() { return dao.getAll(); }

    @GetMapping("/activos")
    public List<Cliente> getActivos() { return dao.getActivos(); }

    @GetMapping("/{idCli}/tiene-telefonos")
    public ValorBooleano tieneTelefonos(@PathVariable int idCli) {
        return new ValorBooleano(dao.tieneTelefonos(idCli));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void insertar(@RequestBody NombreRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal,
                         @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        // Con clave, un reintento con el mismo cuerpo devuelve el 201 de la primera vez sin crear otro cliente.
        idempotencia.ejecutar(principal.getIdUsu(), OP_ALTA, claveIdempotencia, req,
                () -> {
                    dao.insertar(req.nombre());
                    return null;
                },
                ignorado -> logDao.insertar(principal.getIdUsu(), "CREAR_CLIENTE", "NOMBRE: " + req.nombre()));
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

    @DeleteMapping("/{idCli}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void borrar(@PathVariable int idCli,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tieneTelefonos(idCli)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El cliente tiene teléfonos o trabajos asociados; desactívalo en lugar de borrarlo");
        }
        String nombre;
        try {
            nombre = dao.getNombreById(idCli);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idCli);
        }
        dao.borrar(idCli);
        logDao.insertar(principal.getIdUsu(), "BORRAR_CLIENTE",
                "ID_CLI: " + idCli + ", NOMBRE: " + nombre);
    }

    record NombreRequest(String nombre) {}
    record EditarRequest(String nombre, LocalDateTime updatedAt) {}
    record ActivoRequest(boolean activo, LocalDateTime updatedAt) {}
}
