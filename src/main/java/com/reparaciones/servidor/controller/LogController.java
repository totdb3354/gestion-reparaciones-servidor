package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.LogActividad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogDAO logDao;

    public LogController(LogDAO logDao) {
        this.logDao = logDao;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LogActividad> getAll(
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return logDao.getFiltered(accion, tecnico, desde, hasta);
    }
}
