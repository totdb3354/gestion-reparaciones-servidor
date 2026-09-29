package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EquivalenciaModeloDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modelos/equivalencias")
public class EquivalenciaModeloController {

    private final EquivalenciaModeloDAO dao;
    private final LogDAO logDao;

    public EquivalenciaModeloController(EquivalenciaModeloDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    // Equivalencias de modelo no las usa la tienda: sus lecturas exigen el mismo rol que sus escrituras (spec sp7b §4.2).
    @GetMapping
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public List<Map<String, String>> getAll() { return dao.getAll(); }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardar(@RequestBody EquivalenciaRequest req,
                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.guardar(req.textoExterno(), req.modeloInterno());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_EQUIVALENCIA_MODELO",
                "TEXTO: " + req.textoExterno() + ", MODELO: " + req.modeloInterno());
    }

    private record EquivalenciaRequest(String textoExterno, String modeloInterno) {}
}
