package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
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

    public PulidoController(ReparacionDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
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
        String idRep = dao.insertarAsignacionPulido(req.imei(), req.idTec(), req.comentario());
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
        dao.actualizarAsignacionPulido(idAP, req.idTec(), req.comentario(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "ACTUALIZAR_ASIGNACION_PULIDO", "ID_REP: " + idAP);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/asignaciones/{idAP}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsignacion(@PathVariable String idAP,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminarAsignacionPulido(idAP);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_ASIGNACION_PULIDO", "ID_REP: " + idAP);
    }

    // ── request records ───────────────────────────────────────────────────────

    private record AsignacionPulidoRequest(String imei, int idTec, String comentario) {}
    private record LoteRequest(List<String> ids) {}
    private record ActualizarPulidoRequest(int idTec, String comentario, LocalDateTime updatedAt) {}
}
