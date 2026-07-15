package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/colores/equivalencias")
public class ColorEquivalenciaController {

    private final ColorEquivalenciaDAO dao;

    public ColorEquivalenciaController(ColorEquivalenciaDAO dao) { this.dao = dao; }

    @GetMapping
    public List<Map<String, String>> getAll() { return dao.getAll(); }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardar(@RequestBody EquivalenciaRequest req) {
        dao.guardar(req.textoExterno(), req.colorOficial());
    }

    private record EquivalenciaRequest(String textoExterno, String colorOficial) {}
}
