package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.PrediccionGlassRespuesta;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.FiltroTecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import com.reparaciones.servidor.service.ImeiLookupService;
import com.reparaciones.servidor.service.PrediccionGlass;
import com.reparaciones.servidor.service.TipoTrabajo;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final TecnicoDAO tecnicoDao;
    private final CargaAsignacionesService cargaAsignaciones;

    public GlassController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ImeiLookupService imeiLookupService,
                           TecnicoDAO tecnicoDao, CargaAsignacionesService cargaAsignaciones) {
        this.dao = dao;
        this.logDao = logDao;
        this.telefonoDAO = telefonoDAO;
        this.imeiLookupService = imeiLookupService;
        this.tecnicoDao = tecnicoDao;
        this.cargaAsignaciones = cargaAsignaciones;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(@RequestParam(required = false) Integer tecnico,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getAsignacionesGlass(FiltroTecnico.efectivo(principal, tecnico));
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(@RequestParam(required = false) Integer tecnico,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getHistorialGlass(FiltroTecnico.efectivo(principal, tecnico));
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
        String idRep = dao.insertarAsignacionGlass(req.imei(), req.idTec(), req.comentario(), req.urgente(), principal.getIdTec(), principal.getIdUsu());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(req.idTec());
        String detalleLog = "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", MODELO: " + modelo + ", TECNICO: " + tecnico;
        if (req.urgente()) detalleLog += ", URGENTE: true";
        logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION_GLASS", detalleLog);
        return Map.of("value", idRep);
    }

    private record GlassAsignacionRequest(String imei, int idTec, @Schema(nullable = true) String comentario, boolean urgente) {}

    /** Glass automática del modal "Asignar trabajos" (spec 3b §4.1). POST porque lleva las verdes del modal,
     *  pero no escribe nada. Carga lo mismo que /reparaciones/carga-tecnicos: las tres categorías abiertas y las
     *  cerradas hoy (si esa consulta falla, degrada a solo lo abierto). */
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/prediccion")
    public PrediccionGlassRespuesta predecir(@RequestBody PrediccionRequest req) {
        if (req.imei() == null || !req.imei().matches("\\d{15}"))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "IMEI no válido: " + req.imei());
        CargaAsignacionesService.Estado estado = cargaAsignaciones.cargar();
        List<PrediccionGlass.VerdeEnModal> verdes = req.verdes() == null ? List.of() : req.verdes().stream()
                .filter(v -> v != null)
                .map(v -> new PrediccionGlass.VerdeEnModal(v.imei(), v.idTec(), v.tipo(), v.esChasis(), v.conCliente()))
                .toList();
        Tecnico t = PrediccionGlass.elegir(tecnicoDao.getAllActivos(), estado.abiertas(), estado.cerradasHoy(), verdes,
                req.imei(), req.conCliente());
        return t == null ? new PrediccionGlassRespuesta(null, null)
                         : new PrediccionGlassRespuesta(t.getIdTec(), t.getNombre());
    }

    private record PrediccionRequest(String imei, boolean conCliente, List<VerdeRequest> verdes) {}
    private record VerdeRequest(String imei, int idTec, TipoTrabajo tipo, boolean esChasis, boolean conCliente) {}
}
