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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pulidos")
public class PulidoController {

    private final ReparacionDAO dao;
    private final LogDAO        logDao;
    private final TelefonoDAO   telefonoDAO;
    private final ImeiLookupService imeiLookupService;

    public PulidoController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ImeiLookupService imeiLookupService) {
        this.dao    = dao;
        this.logDao = logDao;
        this.telefonoDAO = telefonoDAO;
        this.imeiLookupService = imeiLookupService;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(
            @RequestParam(required = false) Integer tecnico) {
        return dao.getAsignacionesPulido(tecnico);
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(
            @RequestParam(required = false) Integer tecnico) {
        return dao.getHistorialPulido(tecnico);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertarAsignacion(@RequestBody AsignacionPulidoRequest req,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        String modeloExistente = telefonoDAO.getModelo(req.imei());
        if (modeloExistente == null || modeloExistente.isBlank()) {
            String modelo = imeiLookupService.lookupModeloInterno(req.imei());
            if (modelo != null) {
                telefonoDAO.insertar(req.imei(), modelo);
            }
        }
        String idRep = dao.insertarAsignacionPulido(req.imei(), req.idTec(), req.comentario(), principal.getIdTec(), principal.getIdUsu());
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION_PULIDO",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", ID_TEC: " + req.idTec());
        return Map.of("value", idRep);
    }

    @PostMapping("/asignaciones/completar-lote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completarLote(@RequestBody LoteRequest req,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.completarPulidoLote(req.ids());
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_PULIDO_LOTE",
                "IDS: " + String.join(",", req.ids()));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idAP}")
    public void actualizarAsignacion(@PathVariable String idAP,
                                      @RequestBody ActualizarPulidoRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarAsignacionPulido(idAP, req.idTec(), req.comentario(), req.updatedAt(), principal.getIdTec());
        String imei = dao.getImeiByIdRep(idAP);
        logDao.insertar(principal.getIdUsu(), "ACTUALIZAR_ASIGNACION_PULIDO",
                "ID_REP: " + idAP + (imei != null ? ", IMEI: " + imei : ""));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/asignaciones/{idAP}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsignacion(@PathVariable String idAP,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String imei = dao.getImeiByIdRep(idAP);   // antes del borrado: luego la fila ya no existe
        dao.eliminarAsignacionPulido(idAP);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_ASIGNACION_PULIDO",
                "ID_REP: " + idAP + (imei != null ? ", IMEI: " + imei : ""));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/historial/{idP}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPulido(@PathVariable String idP,
                                @RequestBody(required = false) MotivoRequest req,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        String imei = dao.getImeiByIdRep(idP);   // antes del borrado: luego la fila ya no existe
        dao.eliminarPulido(idP);
        String motivo = req != null ? req.motivo() : null;
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_PULIDO",
                "ID_REP: " + idP + (imei != null ? ", IMEI: " + imei : "")
                + (motivo != null && !motivo.isBlank() ? ", MOTIVO: " + motivo : ""));
    }

    // ── request records ───────────────────────────────────────────────────────

    private record MotivoRequest(String motivo) {}
    private record AsignacionPulidoRequest(String imei, int idTec, String comentario) {}
    private record LoteRequest(List<String> ids) {}
    private record ActualizarPulidoRequest(int idTec, String comentario, LocalDateTime updatedAt) {}
}
