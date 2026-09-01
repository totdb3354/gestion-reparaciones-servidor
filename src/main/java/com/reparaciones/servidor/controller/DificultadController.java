package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ValorDificultad;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Valores de dificultad de las estadísticas por puntos (spec 2026-09-01). */
@RestController
@RequestMapping("/api/valores-dificultad")
public class DificultadController {

    private final DificultadPuntosDAO dao;
    private final LogDAO logDao;

    public DificultadController(DificultadPuntosDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<ValorDificultad> getValores() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    @PutMapping
    public void actualizarValores(@RequestBody List<ValorDificultad> valores,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        Map<String, Double> actuales = dao.getValores();
        // Validación completa ANTES de escribir nada
        for (ValorDificultad v : valores) {
            if (v.getClave() == null || !actuales.containsKey(v.getClave()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Clave desconocida: " + v.getClave());
            if (v.getPuntos() < 0 || v.getPuntos() > 99.99)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Puntos fuera de rango para " + v.getClave());
        }
        StringBuilder detalle = new StringBuilder();
        for (ValorDificultad v : valores) {
            double antes = actuales.get(v.getClave());
            if (Math.abs(antes - v.getPuntos()) < 0.001) continue;
            dao.actualizar(v.getClave(), v.getPuntos());
            if (detalle.length() > 0) detalle.append(", ");
            detalle.append(v.getClave()).append(": ")
                   .append(formatear(antes)).append(" -> ").append(formatear(v.getPuntos()));
        }
        if (detalle.length() > 0)
            logDao.insertar(principal.getIdUsu(), "EDITAR_PUNTOS", detalle.toString());
    }

    private static String formatear(double v) {
        String s = java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        if (!s.contains(".")) s += ".0";
        return s.replace('.', ',');
    }
}
