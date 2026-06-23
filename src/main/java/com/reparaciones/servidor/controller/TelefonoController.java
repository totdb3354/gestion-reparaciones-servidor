package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Telefono;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telefonos")
public class TelefonoController {

    private final TelefonoDAO dao;
    private final ImeiLookupService imeiLookupService;
    private final LogDAO logDao;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService, LogDAO logDao) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
    }

    @GetMapping
    public List<Telefono> getAll() {
        return dao.getAll();
    }

    @GetMapping("/{imei}/exists")
    public Map<String, Boolean> exists(@PathVariable String imei) {
        return Map.of("value", dao.exists(imei));
    }

    @GetMapping("/{imei}/modelo")
    public Map<String, String> getModelo(@PathVariable String imei) {
        String modelo = dao.getModelo(imei);
        if (modelo == null || modelo.isBlank()) {
            modelo = imeiLookupService.lookupModeloInterno(imei);
        }
        return Map.of("value", modelo != null ? modelo : "");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody ImeiRequest req) {
        dao.insertar(req.imei(), req.modelo());
    }

    @PatchMapping("/{imei}/observacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    public void actualizarObservacion(@PathVariable String imei,
                                      @RequestBody ObservacionRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarObservacion(imei, req.observacion(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_OBSERVACION", "IMEI: " + imei);
    }

    @DeleteMapping("/{imei}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String imei) {
        dao.eliminar(imei);
    }

    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tieneAsignacionesActivas(imei)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El IMEI tiene asignaciones activas");
        }
        dao.actualizarRevisionLogistica(imei, req.revisado(), req.updatedAt());
        String modelo = dao.getModelo(imei);
        logDao.insertar(principal.getIdUsu(),
                req.revisado() ? "MARCAR_REVISION" : "QUITAR_REVISION",
                "IMEI: " + imei + ", MODELO: " + (modelo != null ? modelo : "?"));
    }

    private record ImeiRequest(String imei, String modelo) {}
    private record ObservacionRequest(String observacion, java.time.LocalDateTime updatedAt) {}
    private record RevisionLogisticaRequest(boolean revisado, java.time.LocalDateTime updatedAt) {}
}
