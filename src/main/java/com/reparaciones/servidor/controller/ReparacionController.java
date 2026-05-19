package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.*;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reparaciones")
public class ReparacionController {

    private final ReparacionDAO         dao;
    private final ReparacionComponenteDAO rcDao;
    private final LogDAO                logDao;

    public ReparacionController(ReparacionDAO dao, ReparacionComponenteDAO rcDao, LogDAO logDao) {
        this.dao    = dao;
        this.rcDao  = rcDao;
        this.logDao = logDao;
    }

    // ── raw ──────────────────────────────────────────────────────────────────

    @GetMapping
    public List<Reparacion> getAll() {
        return dao.getAll();
    }

    @GetMapping("/imei/{imei}")
    public List<Reparacion> getByImei(@PathVariable String imei) {
        return dao.getByImei(imei);
    }

    @GetMapping("/imei/{imei}/count")
    public Map<String, Object> countByImei(@PathVariable String imei) {
        return Map.of("value", dao.countByImei(imei));
    }

    // ── historial ─────────────────────────────────────────────────────────────

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(
            @RequestParam(required = false) Integer tecnico) {
        return dao.getHistorial(tecnico);
    }

    @GetMapping("/historial/imei/{imei}")
    public List<ReparacionResumen> getHistorialPorImei(@PathVariable String imei) {
        return dao.getHistorialPorImei(imei);
    }

    // ── asignaciones ─────────────────────────────────────────────────────────

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(
            @RequestParam(required = false) Integer tecnico) {
        return dao.getAsignaciones(tecnico);
    }

    @GetMapping("/asignaciones/{idRep}")
    public ReparacionResumen getAsignacionById(@PathVariable String idRep) {
        return dao.getAsignacionById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
    }

    @GetMapping("/asignaciones/{idAsignacion}/solicitudes")
    public List<FilaReparacion> getSolicitudesPorAsignacion(@PathVariable String idAsignacion) {
        return rcDao.getSolicitudesPorAsignacion(idAsignacion);
    }

    // ── auxiliares ────────────────────────────────────────────────────────────

    @GetMapping("/{idRep}/detalle-edicion")
    public ReparacionDAO.DetalleEdicion getDetalleEdicion(@PathVariable String idRep) {
        return dao.getDetalleEdicion(idRep);
    }

    @GetMapping("/{idRep}/referenciadora")
    public Map<String, Object> getReferenciadora(@PathVariable String idRep) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("value", dao.getReferenciadora(idRep));
        return resp;
    }

    @GetMapping("/imei/{imei}/ya-reparados")
    public Set<Integer> getIdComsYaReparados(@PathVariable String imei,
                                              @RequestParam String excluir) {
        return dao.getIdComsYaReparados(imei, excluir);
    }

    @GetMapping("/imei/{imei}/incidencia-activa")
    public Map<String, Object> getIncidenciaActivaPorImei(@PathVariable String imei) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("value", dao.getIncidenciaActivaPorImei(imei));
        return resp;
    }

    @GetMapping("/imei/{imei}/tiene-asignacion")
    public Map<String, Object> existeAsignacionParaTecnico(
            @PathVariable String imei, @RequestParam int tecnico) {
        return Map.of("value", dao.existeAsignacionParaTecnico(imei, tecnico));
    }

    @GetMapping("/imei/{imei}/tecnicos-asignados")
    public List<Integer> getTecnicosConAsignacionActiva(@PathVariable String imei) {
        return dao.getTecnicosConAsignacionActiva(imei);
    }

    @GetMapping("/estadisticas")
    public List<PuntoEstadistica> getEstadisticasPorTecnico(
            @RequestParam String granularidad,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dao.getEstadisticasPorTecnico(granularidad, desde, hasta);
    }

    // ── escritura ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertar(@RequestBody InsertarRequest req,
                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        String idRep = dao.insertar(req.imei(), req.idTec(), req.fechaAsig(), req.fechaFin());
        logDao.insertar(principal.getIdUsu(), "CREAR_REPARACION",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", ID_TEC: " + req.idTec());
        return Map.of("value", idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertarAsignacion(@RequestBody AsignacionRequest req,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        String idRep = dao.insertarAsignacion(req.imei(), req.idTec());
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", ID_TEC: " + req.idTec());
        return Map.of("value", idRep);
    }

    @PostMapping("/completa")
    @ResponseStatus(HttpStatus.CREATED)
    public void insertarCompleta(@RequestBody InsertarCompletaRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertarCompleta(req.filas(), req.imei(), req.idTec(),
                req.idRepAnterior(), req.idAsignacion());
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION",
                "ID_REP: " + req.idAsignacion() + ", IMEI: " + req.imei() + ", ID_TEC: " + req.idTec());
    }

    @PatchMapping("/{idRep}/completar")
    public void completar(@PathVariable String idRep,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.completar(idRep);
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION", "ID_REP: " + idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}/tecnico")
    public void actualizarTecnico(@PathVariable String idRep, @RequestBody TecnicoRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarTecnico(idRep, req.idTec(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "REASIGNAR_TECNICO",
                "ID_REP: " + idRep + ", ID_TEC_NUEVO: " + req.idTec());
    }

    @PutMapping("/{idRep}")
    public void editarReparacion(@PathVariable String idRep, @RequestBody EditarRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.editarReparacion(idRep, req.idComNuevo(), req.esReutilizadoNuevo(),
                req.observacionNueva(), req.nNuevas(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_REPARACION", "ID_REP: " + idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/{idRep}/incidencia")
    @ResponseStatus(HttpStatus.CREATED)
    public void marcarIncidenciaYAsignar(@PathVariable String idRep,
                                          @RequestBody IncidenciaRequest req,
                                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec());
        logDao.insertar(principal.getIdUsu(), "MARCAR_INCIDENCIA",
                "ID_REP: " + idRep + ", IMEI: " + req.imei());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/imei/{imei}/incidencia-activa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarIncidenciaPorImei(@PathVariable String imei) {
        dao.borrarIncidenciaPorImei(imei);
    }

    @PostMapping("/{idAsignacion}/agotar-componente")
    @ResponseStatus(HttpStatus.CREATED)
    public void agotarComponente(@PathVariable String idAsignacion,
                                  @RequestBody AgotarRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.agotarComponente(idAsignacion, req.idCom(), req.cantidad(), req.descripcion());
        logDao.insertar(principal.getIdUsu(), "AGOTAR_COMPONENTE",
                "ID_ASIG: " + idAsignacion + ", ID_COM: " + req.idCom() + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/asignaciones/{idAsig}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsignacion(@PathVariable String idAsig,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminarAsignacion(idAsig);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_ASIGNACION", "ID_REP: " + idAsig);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idRep}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String idRep,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminar(idRep);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_REPARACION", "ID_REP: " + idRep);
    }

    // ── request records ───────────────────────────────────────────────────────

    private record InsertarRequest(String imei, int idTec,
                                   LocalDateTime fechaAsig, LocalDateTime fechaFin) {}
    private record AsignacionRequest(String imei, int idTec) {}
    private record InsertarCompletaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                           String idRepAnterior, String idAsignacion) {}
    private record TecnicoRequest(int idTec, LocalDateTime updatedAt) {}
    private record EditarRequest(int idComNuevo, boolean esReutilizadoNuevo,
                                 String observacionNueva, int nNuevas,
                                 LocalDateTime updatedAt) {}
    private record IncidenciaRequest(String comentario, String imei, int idTec) {}
    private record AgotarRequest(int idCom, int cantidad, String descripcion) {}
}
