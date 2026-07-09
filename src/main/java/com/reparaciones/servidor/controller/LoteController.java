package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.model.Lote;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteDAO loteDao;

    public LoteController(LoteDAO loteDao) { this.loteDao = loteDao; }

    @GetMapping
    public List<Lote> getAll() { return loteDao.getAll(); }
}
