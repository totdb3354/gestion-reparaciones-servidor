package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.TipoCambioDAO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tipo-cambio")
public class TipoCambioController {

    private final TipoCambioDAO dao;

    public TipoCambioController(TipoCambioDAO dao) {
        this.dao = dao;
    }

    @GetMapping("/{divisa}")
    public Map<String, Double> getTasa(@PathVariable String divisa) {
        return Map.of("value", dao.getTasa(divisa));
    }
}
