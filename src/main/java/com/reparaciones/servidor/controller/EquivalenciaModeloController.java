package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EquivalenciaModeloDAO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modelos/equivalencias")
public class EquivalenciaModeloController {

    private final EquivalenciaModeloDAO dao;

    public EquivalenciaModeloController(EquivalenciaModeloDAO dao) { this.dao = dao; }

    @GetMapping
    public List<Map<String, String>> getAll() { return dao.getAll(); }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardar(@RequestBody EquivalenciaRequest req) {
        dao.guardar(req.textoExterno(), req.modeloInterno());
    }

    private record EquivalenciaRequest(String textoExterno, String modeloInterno) {}
}
