package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints propios de glass (prefijos AG/G). Glass ≈ reparación: completar,
 * editar y borrar se hacen vía {@link ReparacionController} (operan por ID).
 * Aquí solo la lectura y el alta de asignaciones de glass.
 */
@RestController
@RequestMapping("/api/glass")
public class GlassController {

    private final ReparacionDAO dao;
    private final LogDAO logDao;
    private final TelefonoDAO telefonoDAO;
    private final ImeiLookupService imeiLookupService;

    public GlassController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ImeiLookupService imeiLookupService) {
        this.dao = dao;
        this.logDao = logDao;
        this.telefonoDAO = telefonoDAO;
        this.imeiLookupService = imeiLookupService;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(@RequestParam(required = false) Integer tecnico) {
        return dao.getAsignacionesGlass(tecnico);
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(@RequestParam(required = false) Integer tecnico) {
        return dao.getHistorialGlass(tecnico);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertarAsignacion(@RequestBody GlassAsignacionRequest req,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String modeloExistente = telefonoDAO.getModelo(req.imei());
        if (modeloExistente == null || modeloExistente.isBlank()) {
            String modelo = imeiLookupService.lookupModeloInterno(req.imei());
            if (modelo != null) telefonoDAO.insertar(req.imei(), modelo);
        }
        String idRep = dao.insertarAsignacionGlass(req.imei(), req.idTec(), req.comentario(), req.urgente(), principal.getIdTec());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        String detalleLog = "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo + ", TECNICO: " + tecnico;
        if (req.urgente()) detalleLog += ", URGENTE: true";
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION_GLASS", detalleLog);
        return Map.of("value", idRep);
    }

    private record GlassAsignacionRequest(String imei, int idTec, String comentario, boolean urgente) {}
}
