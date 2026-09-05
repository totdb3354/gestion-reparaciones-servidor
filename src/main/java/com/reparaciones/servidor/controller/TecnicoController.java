package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tecnicos")
public class TecnicoController {

    private final TecnicoDAO dao;
    private final LogDAO     logDao;

    public TecnicoController(TecnicoDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<Tecnico> getAll() {
        return dao.getAll();
    }

    @GetMapping("/activos")
    public List<Tecnico> getAllActivos() {
        return dao.getAllActivos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody NombreRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.nombre());
        logDao.insertar(principal.getIdUsu(), "CREAR_TECNICO", "NOMBRE: " + req.nombre());
    }

    @DeleteMapping("/{idTec}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idTec,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreById(idTec);
        dao.eliminar(idTec);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_TECNICO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    /** Habilita/deshabilita al técnico para la glass automática del modal de asignación
     *  (spec 2026-09-05-glass-prediccion, §3.2). Solo SuperTécnico: Admin ve el diálogo pero no edita (403 aquí). */
    @PatchMapping("/{idTec}/glass")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setGlass(@PathVariable int idTec, @RequestBody GlassRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        int filas = dao.setGlass(idTec, req.habilitado());
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Técnico no encontrado: " + idTec);
        }
        String nombre = dao.getNombreById(idTec);
        logDao.insertar(principal.getIdUsu(),
                req.habilitado() ? "HABILITAR_GLASS" : "DESHABILITAR_GLASS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    /** Package-private (no private) para que el test lo construya; Jackson lo deserializa igual. */
    record GlassRequest(boolean habilitado) {}

    private record NombreRequest(String nombre) {}
}
