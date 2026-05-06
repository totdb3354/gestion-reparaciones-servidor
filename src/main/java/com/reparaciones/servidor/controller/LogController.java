package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.LogActividad;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    public List<LogActividad> getAll() {
        return logDao.getAll();
    }
}
