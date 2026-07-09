package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Lote;
import com.reparaciones.servidor.model.VerificacionImei;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.LoteImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteDAO loteDao;
    private final TelefonoDAO telefonoDao;
    private final LoteImportService importService;

    public LoteController(LoteDAO loteDao, TelefonoDAO telefonoDao, LoteImportService importService) {
        this.loteDao = loteDao;
        this.telefonoDao = telefonoDao;
        this.importService = importService;
    }

    @GetMapping
    public List<Lote> getAll() { return loteDao.getAll(); }

    @PostMapping("/verificar")
    public List<VerificacionImei> verificar(@RequestBody VerificarRequest req) {
        return telefonoDao.verificar(req.imeis());
    }

    @PostMapping("/importar")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public com.reparaciones.servidor.model.ImportacionRequest.Respuesta importar(
            @RequestBody com.reparaciones.servidor.model.ImportacionRequest req,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return importService.importar(req, principal.getIdUsu());
    }

    public record VerificarRequest(List<String> imeis) {}
}
