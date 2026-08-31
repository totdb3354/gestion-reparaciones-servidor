package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
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
    private final ComponenteDAO         componenteDao;

    public ReparacionController(ReparacionDAO dao, ReparacionComponenteDAO rcDao, LogDAO logDao,
                                com.reparaciones.servidor.dao.BorradorDAO borradorDao, ComponenteDAO componenteDao) {
        this.dao         = dao;
        this.rcDao       = rcDao;
        this.logDao      = logDao;
        this.borradorDao = borradorDao;
        this.componenteDao = componenteDao;
    }

    // Glass ≈ reparación: completar/editar/borrar se reutilizan; la acción de log
    // se distingue por el prefijo del ID (G = glass terminado, AG = asignación de glass).
    private static boolean esGlass(String idRep)      { return idRep != null && idRep.startsWith("G"); }
    private static boolean esGlassAsig(String idAsig) { return idAsig != null && idAsig.startsWith("AG"); }

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

    /** Asignaciones completadas hoy (corte = inicio de hoy en Madrid) — "hecho hoy" de la carga v2. */
    @GetMapping("/asignaciones/completadas-hoy")
    public List<ReparacionResumen> getAsignacionesCompletadasHoy() {
        return dao.getAsignacionesCompletadasHoy(
                com.reparaciones.servidor.job.UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(
                        java.time.Clock.system(java.time.ZoneId.of("Europe/Madrid"))));
    }

    @GetMapping("/asignaciones/imei/{imei}")
    public List<ReparacionResumen> getAsignacionesPorImei(@PathVariable String imei) {
        return dao.getAsignacionesPorImei(imei);
    }

    @GetMapping("/asignaciones/{idRep}")
    public ReparacionResumen getAsignacionById(@PathVariable String idRep) {
        return dao.getAsignacionAnyById(idRep)
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

    /** Acciones "otro" ya guardadas del IMEI (se precargan bloqueadas al editar). */
    @GetMapping("/imei/{imei}/acciones")
    public List<String> getAccionesOtro(@PathVariable String imei,
                                        @RequestParam(required = false) String categoria,
                                        @RequestParam(required = false) String excluir) {
        return dao.getAccionesOtro(imei, categoria, excluir);
    }

    @GetMapping("/imei/{imei}/incidencia-activa")
    public Map<String, Object> getIncidenciaActivaPorImei(@PathVariable String imei,
            @RequestParam(defaultValue = "R") String tipo) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("value", dao.getIncidenciaActivaPorImei(imei, tipo));
        return resp;
    }

    @GetMapping("/imei/{imei}/tiene-asignacion")
    public Map<String, Object> existeAsignacionParaTecnico(
            @PathVariable String imei, @RequestParam int tecnico,
            @RequestParam(defaultValue = "R") String tipo) {
        return Map.of("value", dao.existeAsignacionParaTecnico(imei, tecnico, tipo));
    }

    @GetMapping("/imei/{imei}/tecnicos-asignados")
    public List<Integer> getTecnicosConAsignacionActiva(@PathVariable String imei) {
        return dao.getTecnicosConAsignacionActiva(imei);
    }

    @GetMapping("/imei/{imei}/asignaciones-activas")
    public List<ReparacionDAO.AsignacionActiva> getAsignacionesActivasPorImei(@PathVariable String imei) {
        return dao.getAsignacionesActivasPorImei(imei);
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
        String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario(), req.urgente(), req.esChasis(), principal.getIdTec(), principal.getIdUsu());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        String detalleLog = "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
                ", TECNICO: " + tecnico;
        if (req.urgente()) detalleLog += ", URGENTE: true";
        if (req.esChasis()) detalleLog += ", CHASIS: true";
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION", detalleLog);
        return Map.of("value", idRep);
    }

    @PostMapping("/completa")
    @ResponseStatus(HttpStatus.CREATED)
    public void insertarCompleta(@RequestBody InsertarCompletaRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertarCompleta(req.filas(), req.imei(), req.idTec(),
                req.idRepAnterior(), req.idAsignacion(), req.categoria());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(req.idAsignacion()) || "G".equals(req.categoria())
                        ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION",
                "ID_REP: " + req.idAsignacion() + ", IMEI: " + req.imei() +
                ", MODELO: " + modelo + ", TECNICO: " + tecnico + componentesDe(req.filas()));
    }

    @PatchMapping("/{idRep}/completar")
    public void completar(@PathVariable String idRep,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen rep = dao.getAsignacionAnyById(idRep).orElse(null);
        dao.completar(idRep);
        String detalle = rep != null
                ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idRep;
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idRep) ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}/urgente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarUrgente(@PathVariable String idRep, @RequestBody UrgenteRequest req,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarUrgente(idRep, req.urgente());
        String imei = dao.getImeiByIdRep(idRep);
        logDao.insertar(principal.getIdUsu(), "MARCAR_URGENTE",
                "ID_REP: " + idRep + (imei != null ? ", IMEI: " + imei : "") + ", URGENTE: " + req.urgente());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}/chasis")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarChasis(@PathVariable String idRep, @RequestBody ChasisRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarChasis(idRep, req.esChasis());
        String imei = dao.getImeiByIdRep(idRep);
        logDao.insertar(principal.getIdUsu(), "MARCAR_CHASIS",
                "ID_REP: " + idRep + (imei != null ? ", IMEI: " + imei : "") + ", CHASIS: " + req.esChasis());
    }

    /** Marca "por cerrar": solo el dueño de la asignación (técnico o supertécnico); nunca ajenas. */
    @PatchMapping("/asignaciones/{idRep}/por-cerrar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarPorCerrar(@PathVariable String idRep, @RequestBody PorCerrarRequest req,
                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean esRepNormal = idRep != null && idRep.startsWith("A")
                && !idRep.startsWith("AG") && !idRep.startsWith("AP");
        if (!esRepNormal) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de reparación");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        // Solo el dueño: es algo que solo sabe el que repara (spec por-cerrar-carga §2, ajuste smoke 2026-07-08)
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes marcar tus propias asignaciones");
        }
        dao.actualizarPorCerrar(idRep, req.porCerrar());
        logDao.insertar(principal.getIdUsu(),
                req.porCerrar() ? "MARCAR_POR_CERRAR" : "QUITAR_POR_CERRAR",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei());
    }

    /**
     * Entrega del teléfono a la glass abierta del IMEI (spec entrega-glass 2026-08-28 §4).
     * Solo el dueño de la reparación normal; sella (o limpia) ENTREGADO_AT/ENTREGADO_POR en
     * TODAS las AG abiertas del IMEI. Sin lock optimista (toggle, último gana).
     */
    @PatchMapping("/asignaciones/{idRep}/entrega-glass")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarEntregaGlass(@PathVariable String idRep, @RequestBody EntregaGlassRequest req,
                                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean esRepNormal = idRep != null && idRep.startsWith("A")
                && !idRep.startsWith("AG") && !idRep.startsWith("AP");
        if (!esRepNormal) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de reparación");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes entregar tus propias asignaciones");
        }
        List<ReparacionDAO.GlassAbierta> glass = dao.getGlassAbiertas(asig.getImei());
        if (glass.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Sin glass abierta para este IMEI");
        }
        if (!req.entregado()) {
            // Quien marca, desmarca (decisión 2026-08-31): la firma ENTREGADO_POR manda.
            if (asig.getGlassEntregadoAt() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No hay entrega que deshacer");
            }
            if (!principal.getIdTec().equals(asig.getGlassEntregadoPor())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo quien registró la entrega puede deshacerla");
            }
        }
        if (req.entregado()) dao.entregarGlass(asig.getImei(), principal.getIdTec());
        else                 dao.deshacerEntregaGlass(asig.getImei());

        String ids  = glass.stream().map(ReparacionDAO.GlassAbierta::idRep)
                .collect(java.util.stream.Collectors.joining(","));
        String tecs = glass.stream().map(ReparacionDAO.GlassAbierta::nombreTecnico).distinct()
                .collect(java.util.stream.Collectors.joining(","));
        logDao.insertar(principal.getIdUsu(),
                req.entregado() ? "ENTREGAR_GLASS" : "DESHACER_ENTREGA_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", GLASS: " + ids + ", TECNICO_GLASS: " + tecs);
    }

    /**
     * Llegada registrada por el propio técnico de glass (válvula de escape del gate "sin teléfono
     * no hay glass" — spec 2026-08-28 §2): sella la entrega en TODAS las AG abiertas del IMEI con
     * él como ENTREGADO_POR. Solo el dueño de la AG y solo si aún no hay entrega registrada.
     */
    @PatchMapping("/asignaciones/{idRep}/llegada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLlegadaGlass(@PathVariable String idRep,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (idRep == null || !idRep.startsWith("AG")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de glass");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes registrar la llegada de tus propias asignaciones");
        }
        if (asig.getEntregadoAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La entrega ya está registrada");
        }
        dao.entregarGlass(asig.getImei(), principal.getIdTec());
        logDao.insertar(principal.getIdUsu(), "ENTREGAR_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", LLEGADA registrada por el tecnico de glass");
    }

    /** Deshacer la llegada: solo el dueño de la AG y solo si la firma (ENTREGADO_POR) es suya. */
    @DeleteMapping("/asignaciones/{idRep}/llegada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerLlegadaGlass(@PathVariable String idRep,
                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (idRep == null || !idRep.startsWith("AG")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de glass");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes deshacer la llegada de tus propias asignaciones");
        }
        if (asig.getEntregadoAt() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No hay entrega que deshacer");
        }
        if (!principal.getIdTec().equals(asig.getEntregadoPor())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo quien registró la entrega puede deshacerla");
        }
        dao.deshacerEntregaGlass(asig.getImei());
        logDao.insertar(principal.getIdUsu(), "DESHACER_ENTREGA_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", LLEGADA deshecha por el tecnico de glass");
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/asignaciones/{idRep}")
    public void actualizarAsignacion(@PathVariable String idRep,
                                      @RequestBody ActualizarAsignacionRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen ant = dao.getAsignacionAnyById(idRep).orElse(null);
        dao.actualizarAsignacion(idRep, req.idTec(), req.comentarioAsignacion(), req.updatedAt(), principal.getIdTec());

        List<String> cambios = new ArrayList<>();
        cambios.add("ID_REP: " + idRep);
        if (ant != null) {
            cambios.add("IMEI: " + ant.getImei());
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
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idRep) ? "ACTUALIZAR_ASIGNACION_GLASS" : "ACTUALIZAR_ASIGNACION",
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
            cambios.add("IMEI: " + ant.getImei());
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
        logDao.insertar(principal.getIdUsu(),
                esGlass(idRep) ? "EDITAR_GLASS" : "EDITAR_REPARACION",
                String.join(", ", cambios));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/{idRep}/incidencia")
    @ResponseStatus(HttpStatus.CREATED)
    public void marcarIncidenciaYAsignar(@PathVariable String idRep,
                                          @RequestBody IncidenciaRequest req,
                                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec(), principal.getIdTec(), principal.getIdUsu());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnicoNue = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(),
                esGlass(idRep) ? "MARCAR_INCIDENCIA_GLASS" : "MARCAR_INCIDENCIA",
                "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo +
                ", TECNICO_NUE: " + tecnicoNue);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/imei/{imei}/incidencia-activa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarIncidenciaPorImei(@PathVariable String imei,
            @RequestParam(defaultValue = "R") String tipo) {
        dao.borrarIncidenciaPorImei(imei, tipo);
    }

    @PostMapping("/{idAsignacion}/agotar-componente")
    @ResponseStatus(HttpStatus.CREATED)
    public void agotarComponente(@PathVariable String idAsignacion,
                                  @RequestBody AgotarRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.agotarComponente(idAsignacion, req.idCom(), req.cantidad(), req.descripcion());
        String tipo = dao.getTipoComponenteById(req.idCom());
        String imei = dao.getImeiByIdRep(idAsignacion);
        logDao.insertar(principal.getIdUsu(), "AGOTAR_COMPONENTE",
                "ID_ASIG: " + idAsignacion + (imei != null ? ", IMEI: " + imei : "") + ", TIPO: " + tipo + ", CANT: " + req.cantidad());
    }

    @PostMapping("/{idAsignacion}/filas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> guardarFilaIndividual(@PathVariable String idAsignacion,
                                                     @RequestBody GuardarFilaRequest req,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        String idRep = dao.guardarFilaIndividual(req.filas(), req.imei(), req.idTec(),
                req.idRepAnterior(), idAsignacion);
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idAsignacion) ? "GUARDAR_FILA_INDIVIDUAL_GLASS" : "GUARDAR_FILA_INDIVIDUAL",
                "ID_REP: " + idRep + ", ID_ASIG: " + idAsignacion +
                ", IMEI: " + req.imei() + ", TECNICO: " + tecnico + componentesDe(req.filas()));
        return Map.of("value", idRep);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/asignaciones/{idAsig}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsignacion(@PathVariable String idAsig,
                                   @RequestBody(required = false) MotivoRequest req,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReparacionResumen rep = dao.getAsignacionAnyById(idAsig).orElse(null);
        dao.eliminarAsignacion(idAsig);
        String detalle = rep != null
                ? "ID_REP: " + idAsig + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idAsig;
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idAsig) ? "ELIMINAR_ASIGNACION_GLASS" : "ELIMINAR_ASIGNACION", detalle,
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
        logDao.insertar(principal.getIdUsu(),
                esGlass(idRep) ? "ELIMINAR_GLASS" : "ELIMINAR_REPARACION", detalle,
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
    private record AsignacionRequest(String imei, int idTec, String comentario, boolean urgente, boolean esChasis) {}
    private record InsertarCompletaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                           String idRepAnterior, String idAsignacion, String categoria) {}
private record ActualizarAsignacionRequest(int idTec, String comentarioAsignacion, LocalDateTime updatedAt) {}
    private record EditarRequest(int idComNuevo, boolean esReutilizadoNuevo,
                                 String observacionNueva, int nNuevas,
                                 LocalDateTime updatedAt) {}
    private record IncidenciaRequest(String comentario, String imei, int idTec) {}
    private record AgotarRequest(int idCom, int cantidad, String descripcion) {}
    private record UrgenteRequest(boolean urgente) {}
    private record ChasisRequest(boolean esChasis) {}
    private record PorCerrarRequest(boolean porCerrar) {}
    record EntregaGlassRequest(boolean entregado) {}   // package-private: lo construye el test
    private record GuardarFilaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                      String idRepAnterior) {}
    private record MotivoRequest(String motivo) {}

    /** Tipos de los componentes consumidos, para el detalle del log ("" si no hay filas con pieza). */
    private String componentesDe(List<FilaReparacion> filas) {
        if (filas == null) return "";
        String tipos = filas.stream()
                .filter(f -> f.idCom > 0)
                .map(f -> { try { return componenteDao.getTipoById(f.idCom); } catch (Exception e) { return "?"; } })
                .collect(java.util.stream.Collectors.joining(", "));
        return tipos.isEmpty() ? "" : ", COMPONENTES: " + tipos;
    }
}
