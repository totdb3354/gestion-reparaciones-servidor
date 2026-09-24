package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    /** Las dos divisas del combo "Editar proveedor" del cliente (sub-proyecto 4a). */
    private static final Set<String> DIVISAS = Set.of("EUR", "USD");
    private static final int NOMBRE_MAX = 100; // VARCHAR(100) de Proveedor.NOMBRE
    static final String MSG_NOMBRE = "El nombre no puede estar vacío.";
    static final String MSG_NOMBRE_LARGO = "El nombre no puede superar los 100 caracteres.";
    static final String MSG_DIVISA = "Divisa no válida (EUR o USD).";
    static final String MSG_TIENE_PEDIDOS = "El proveedor tiene pedidos y no se puede borrar.";

    private final ProveedorDAO dao;
    private final LogDAO logDao;

    public ProveedorController(ProveedorDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
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
        String nombre = nombreValido(req.nombre());
        // Divisa nula o en blanco: se pasa null y el DAO pone EUR (calco del alta del cliente, que no la manda).
        // Solo se valida si viene informada (decisión 4).
        String divisa = req.divisa() == null || req.divisa().isBlank() ? null : divisaValida(req.divisa());
        dao.insertar(nombre, divisa, req.tipo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idProv}/activo")
    public void setActivo(@PathVariable int idProv, @RequestBody ActivoRequest req) {
        dao.setActivo(idProv, req.activo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}")
    public void editar(@PathVariable int idProv, @RequestBody EditarRequest req) {
        dao.editar(idProv, nombreValido(req.nombre()), divisaValida(req.divisa()), req.comentario());
    }

    /** Guard que hasta el 4a solo aplicaba el cliente: sin él, la clave foránea de compras y lotes hacía fallar el
     *  DELETE con 500 y el cliente lo enseñaba como "servidor no disponible". */
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idProv}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idProv, @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tienePedidos(idProv)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_TIENE_PEDIDOS);
        }
        String nombre = nombreOnull(idProv);
        dao.borrar(idProv); // con un id inexistente es un no-op: 204 como antes del 4a
        if (nombre != null) {
            logDao.insertar(principal.getIdUsu(), "BORRAR_PROVEEDOR", "ID_PROV: " + idProv + ", NOMBRE: " + nombre);
        }
    }

    /** getNombreById usa queryForObject, que lanza con un id inexistente; aquí se tolera para no dar 500 (decisión 2).
     *  El DAO no se toca porque CompraController y CompraOtroController dependen de que lance. */
    private String nombreOnull(int idProv) {
        try {
            return dao.getNombreById(idProv);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static String nombreValido(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOMBRE);
        }
        if (n.length() > NOMBRE_MAX) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOMBRE_LARGO);
        }
        return n;
    }

    private static String divisaValida(String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (!DIVISAS.contains(d)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_DIVISA);
        }
        return d;
    }

    record AltaRequest(String nombre, String divisa, String tipo) {}
    record ActivoRequest(boolean activo) {}
    record EditarRequest(String nombre, String divisa, String comentario) {}
}
