package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.LogActividad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    static final String MSG_LIMITE = "Límite no válido (debe estar entre 1 y 5000).";

    private final LogDAO logDao;

    public LogController(LogDAO logDao) {
        this.logDao = logDao;
    }

    /** {@code limite} es opcional y aditivo (spec 6 G3): sin él, todo el log que cumpla los filtros, como pide el
     *  JavaFX; la web pide las 1.000 filas más recientes. Fuera de 1..5000 → 422 sin consultar. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LogActividad> getAll(
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer limite) {
        if (limite != null && (limite < 1 || limite > LogDAO.LIMITE_MAX)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_LIMITE);
        }
        return logDao.getFiltered(accion, tecnico, desde, hasta, limite);
    }

    /** Lista real de acciones para el filtro "Acción..." (spec 6 G4). */
    @GetMapping("/acciones")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> getAcciones() {
        return logDao.getAcciones();
    }
}
