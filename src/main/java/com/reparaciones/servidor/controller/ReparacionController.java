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
    private final com.reparaciones.servidor.dao.BorradorDAO borradorDao;

    public ReparacionController(ReparacionDAO dao, ReparacionComponenteDAO rcDao, LogDAO logDao,
                                com.reparaciones.servidor.dao.BorradorDAO borradorDao) {
        this.dao         = dao;
        this.rcDao       = rcDao;
        this.logDao      = logDao;
        this.borradorDao = borradorDao;
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

    @GetMapping("/asignaciones/imei/{imei}")
    public List<ReparacionResumen> getAsignacionesPorImei(@PathVariable String imei) {
        return dao.getAsignacionesPorImei(imei);
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
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "CREAR_REPARACION",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
                ", TECNICO: " + tecnico);
        return Map.of("value", idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> insertarAsignacion(@RequestBody AsignacionRequest req,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
                ", TECNICO: " + tecnico);
        return Map.of("value", idRep);
    }

    @PostMapping("/completa")
    @ResponseStatus(HttpStatus.CREATED)
    public void insertarCompleta(@RequestBody InsertarCompletaRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertarCompleta(req.filas(), req.imei(), req.idTec(),
                req.idRepAnterior(), req.idAsignacion());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION",
                "ID_REP: " + req.idAsignacion() + ", IMEI: " + req.imei() +
                ", MODELO: " + modelo + ", TECNICO: " + tecnico);
    }

    @PatchMapping("/{idRep}/completar")
    public void completar(@PathVariable String idRep,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen rep = dao.getAsignacionById(idRep).orElse(null);
        dao.completar(idRep);
        String detalle = rep != null
                ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idRep;
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_REPARACION", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}/urgente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarUrgente(@PathVariable String idRep, @RequestBody UrgenteRequest req,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarUrgente(idRep, req.urgente());
        logDao.insertar(principal.getIdUsu(), "MARCAR_URGENTE",
                "ID_REP: " + idRep + ", URGENTE: " + req.urgente());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}")
    public void actualizarAsignacion(@PathVariable String idRep,
                                      @RequestBody ActualizarAsignacionRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen ant = dao.getAsignacionById(idRep).orElse(null);
        dao.actualizarAsignacion(idRep, req.idTec(), req.comentarioAsignacion(), req.updatedAt());

        List<String> cambios = new ArrayList<>();
        cambios.add("ID_REP: " + idRep);
        if (ant != null) {
            if (ant.getIdTec() != req.idTec()) {
                String nomAnt = dao.getNombreTecnicoById(ant.getIdTec());
                String nomNue = dao.getNombreTecnicoById(req.idTec());
                cambios.add("TEC_ANT: " + nomAnt + " → TEC_NUE: " + nomNue);
            }
            String comAnt = ant.getComentarioAsignacion() != null ? ant.getComentarioAsignacion() : "";
            String comNue = req.comentarioAsignacion() != null ? req.comentarioAsignacion() : "";
            if (!comAnt.equals(comNue)) {
                cambios.add("COM_ANT: '" + comAnt + "' → COM_NUE: '" + comNue + "'");
            }
        }
        logDao.insertar(principal.getIdUsu(), "ACTUALIZAR_ASIGNACION",
                String.join(", ", cambios));
    }

    @PutMapping("/{idRep}")
    public void editarReparacion(@PathVariable String idRep, @RequestBody EditarRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen ant = dao.getResumenById(idRep).orElse(null);
        dao.editarReparacion(idRep, req.idComNuevo(), req.esReutilizadoNuevo(),
                req.observacionNueva(), req.nNuevas(), req.updatedAt());

        List<String> cambios = new ArrayList<>();
        cambios.add("ID_REP: " + idRep);
        if (ant != null) {
            String comAnt = ant.getTipoComponente() != null ? ant.getTipoComponente() : "";
            String comNue = req.idComNuevo() > 0 ? dao.getTipoComponenteById(req.idComNuevo()) : comAnt;
            if (!comAnt.equals(comNue)) {
                cambios.add("COM_ANT: " + comAnt + " → COM_NUE: " + comNue);
            }
            String obsAnt = ant.getObservaciones() != null ? ant.getObservaciones() : "";
            String obsNue = req.observacionNueva() != null ? req.observacionNueva() : "";
            if (!obsAnt.equals(obsNue)) {
                cambios.add("OBS_ANT: '" + obsAnt + "' → OBS_NUE: '" + obsNue + "'");
            }
        }
        logDao.insertar(principal.getIdUsu(), "EDITAR_REPARACION",
                String.join(", ", cambios));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/{idRep}/incidencia")
    @ResponseStatus(HttpStatus.CREATED)
    public void marcarIncidenciaYAsignar(@PathVariable String idRep,
                                          @RequestBody IncidenciaRequest req,
                                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnicoNue = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "MARCAR_INCIDENCIA",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
                ", TECNICO_NUE: " + tecnicoNue);
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
        String tipo = dao.getTipoComponenteById(req.idCom());
        logDao.insertar(principal.getIdUsu(), "AGOTAR_COMPONENTE",
                "ID_ASIG: " + idAsignacion + ", TIPO: " + tipo + ", CANT: " + req.cantidad());
    }

    @PostMapping("/{idAsignacion}/filas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> guardarFilaIndividual(@PathVariable String idAsignacion,
                                                     @RequestBody GuardarFilaRequest req,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        String idRep = dao.guardarFilaIndividual(req.filas(), req.imei(), req.idTec(),
                req.idRepAnterior(), idAsignacion);
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_FILA_INDIVIDUAL",
                "ID_REP: " + idRep + ", ID_ASIG: " + idAsignacion +
                ", IMEI: " + req.imei() + ", TECNICO: " + tecnico);
        return Map.of("value", idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/asignaciones/{idAsig}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsignacion(@PathVariable String idAsig,
                                   @RequestBody(required = false) MotivoRequest req,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen rep = dao.getAsignacionById(idAsig).orElse(null);
        dao.eliminarAsignacion(idAsig);
        String detalle = rep != null
                ? "ID_REP: " + idAsig + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idAsig;
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_ASIGNACION", detalle,
                req != null ? req.motivo() : null);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idRep}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String idRep,
                         @RequestBody(required = false) MotivoRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen rep = dao.getResumenById(idRep).orElse(null);
        dao.eliminar(idRep);
        String detalle = rep != null
                ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idRep;
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_REPARACION", detalle,
                req != null ? req.motivo() : null);
    }

    // ── borrador del modal ─────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @GetMapping("/{idRep}/borrador")
    public Map<String, String> getBorrador(@PathVariable String idRep) {
        return java.util.Collections.singletonMap("contenido", borradorDao.get(idRep));
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @PutMapping("/{idRep}/borrador")
    public void guardarBorrador(@PathVariable String idRep, @RequestBody BorradorRequest req) {
        borradorDao.guardar(idRep, req.contenido());
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @DeleteMapping("/{idRep}/borrador")
    public void eliminarBorrador(@PathVariable String idRep) {
        borradorDao.eliminar(idRep);
    }

    // ── request records ───────────────────────────────────────────────────────

    private record BorradorRequest(String contenido) {}
    private record InsertarRequest(String imei, int idTec,
                                   LocalDateTime fechaAsig, LocalDateTime fechaFin) {}
    private record AsignacionRequest(String imei, int idTec, String comentario) {}
    private record InsertarCompletaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                           String idRepAnterior, String idAsignacion) {}
private record ActualizarAsignacionRequest(int idTec, String comentarioAsignacion, LocalDateTime updatedAt) {}
    private record EditarRequest(int idComNuevo, boolean esReutilizadoNuevo,
                                 String observacionNueva, int nNuevas,
                                 LocalDateTime updatedAt) {}
    private record IncidenciaRequest(String comentario, String imei, int idTec) {}
    private record AgotarRequest(int idCom, int cantidad, String descripcion) {}
    private record UrgenteRequest(boolean urgente) {}
    private record GuardarFilaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                      String idRepAnterior) {}
    private record MotivoRequest(String motivo) {}
}
