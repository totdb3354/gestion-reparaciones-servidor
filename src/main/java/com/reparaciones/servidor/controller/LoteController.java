package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Lote;
import com.reparaciones.servidor.model.VerificacionImei;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteDAO loteDao;
    private final TelefonoDAO telefonoDao;

    public LoteController(LoteDAO loteDao, TelefonoDAO telefonoDao) {
        this.loteDao = loteDao;
        this.telefonoDao = telefonoDao;
    }

    @GetMapping
    public List<Lote> getAll() { return loteDao.getAll(); }

    @PostMapping("/verificar")
    public List<VerificacionImei> verificar(@RequestBody VerificarRequest req) {
        return telefonoDao.verificar(req.imeis());
    }

    public record VerificarRequest(List<String> imeis) {}
}
