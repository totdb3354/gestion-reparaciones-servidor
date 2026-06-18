package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Telefono;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telefonos")
public class TelefonoController {

    private final TelefonoDAO dao;
    private final ImeiLookupService imeiLookupService;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
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
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarObservacion(@PathVariable String imei,
                                      @RequestBody ObservacionRequest req) {
        dao.actualizarObservacion(imei, req.observacion());
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
                                            @RequestBody RevisionLogisticaRequest req) {
        if (dao.tieneAsignacionesActivas(imei)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El IMEI tiene asignaciones activas");
        }
        dao.actualizarRevisionLogistica(imei, req.revisado());
    }

    private record ImeiRequest(String imei, String modelo) {}
    private record ObservacionRequest(String observacion) {}
    private record RevisionLogisticaRequest(boolean revisado) {}
}
