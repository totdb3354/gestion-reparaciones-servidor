package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.AsignacionLoteService;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Guardado por lotes del modal "Asignar trabajos" (spec 3b §4.2). Los endpoints de alta sueltos siguen para el JavaFX. */
@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    static final String MSG_SIN_CLAVE = "Falta la clave de idempotencia";
    private static final Set<String> CATEGORIAS = Set.of("R", "G", "P");

    private final AsignacionLoteService servicio;
    private final ReparacionDAO dao;
    private final TelefonoDAO telefonoDao;
    private final TecnicoDAO tecnicoDao;
    private final ImeiLookupService imeiLookupService;
    private final LogDAO logDao;
    private final RegistroIdempotencia idempotencia;

    public AsignacionController(AsignacionLoteService servicio, ReparacionDAO dao, TelefonoDAO telefonoDao,
                                TecnicoDAO tecnicoDao, ImeiLookupService imeiLookupService, LogDAO logDao,
                                RegistroIdempotencia idempotencia) {
        this.servicio = servicio;
        this.dao = dao;
        this.telefonoDao = telefonoDao;
        this.tecnicoDao = tecnicoDao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/lote")
    public Respuesta guardarLote(@RequestBody Peticion req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal,
                                 @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_SIN_CLAVE);
        validar(req);
        return idempotencia.ejecutar(principal.getIdUsu(), "lote", claveIdempotencia, req,
                // El lookup externo va aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardar(conModeloDePulido(req), principal.getIdTec(), principal.getIdUsu()),
                resultado -> registrarLogs(req, resultado, principal.getIdUsu()));
    }

    private void validar(Peticion req) {
        if (req.asignaciones() == null || req.asignaciones().isEmpty())
            throw regla("El lote no tiene asignaciones");
        Map<String, TelefonoDelLote> telefonos = new HashMap<>();
        if (req.telefonos() != null) for (TelefonoDelLote t : req.telefonos()) {
            if (t == null) throw regla("Teléfono nulo en el lote");
            if (t.imei() == null || !t.imei().matches("\\d{15}")) throw regla("IMEI no válido: " + t.imei());
            telefonos.put(t.imei(), t);
        }
        Set<Integer> activos = tecnicoDao.getAllActivos().stream().map(Tecnico::getIdTec).collect(Collectors.toSet());
        for (AsignacionDelLote a : req.asignaciones()) {
            if (a == null) throw regla("Asignación nula en el lote");
            // Set.of(...).contains(null) lanzaría NullPointerException: una categoría null es simplemente inválida.
            if (a.categoria() == null || !CATEGORIAS.contains(a.categoria())) throw regla("Categoría no válida: " + a.categoria());
            if (a.imei() == null || !a.imei().matches("\\d{15}")) throw regla("IMEI no válido: " + a.imei());
            if (!activos.contains(a.idTec())) throw regla("Técnico no activo: " + a.idTec());
            TelefonoDelLote t = telefonos.get(a.imei());
            if (t == null) throw regla("Falta el teléfono del IMEI " + a.imei());
            if (!"P".equals(a.categoria()) && (t.modelo() == null || t.modelo().isBlank()))
                throw regla("Falta el modelo del IMEI " + a.imei());
        }
    }

    private static ResponseStatusException regla(String msg) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    /** Como hoy POST /pulidos/asignaciones: un IMEI que solo va a pulido y no tiene modelo ni en el lote ni en BD
     *  lo busca en el servicio de IMEI. Lento y externo: fuera de la transacción. */
    private Peticion conModeloDePulido(Peticion req) {
        Set<String> conRepOGlass = req.asignaciones().stream().filter(a -> !"P".equals(a.categoria()))
                .map(AsignacionDelLote::imei).collect(Collectors.toSet());
        List<TelefonoDelLote> telefonos = new ArrayList<>();
        for (TelefonoDelLote t : req.telefonos()) {
            boolean sinModelo = t.modelo() == null || t.modelo().isBlank();
            if (sinModelo && !conRepOGlass.contains(t.imei())) {
                String enBd = telefonoDao.getModelo(t.imei());
                if (enBd == null || enBd.isBlank()) {
                    String encontrado = imeiLookupService.lookupModeloInterno(t.imei());
                    if (encontrado != null) t = new TelefonoDelLote(t.imei(), encontrado, t.idCli(), t.clienteExplicito());
                }
            }
            telefonos.add(t);
        }
        return new Peticion(telefonos, req.asignaciones());
    }

    /** Los mismos logs que POST /telefonos y los tres endpoints de alta sueltos. */
    private void registrarLogs(Peticion req, Respuesta r, int idUsu) {
        for (TelefonoDelLote t : req.telefonos()) {
            if (t.idCli() != null) logDao.insertar(idUsu, "ASIGNAR_CLIENTE", "IMEI: " + t.imei() + ", ID_CLI: " + t.idCli());
            else if (t.clienteExplicito()) logDao.insertar(idUsu, "QUITAR_CLIENTE", "IMEI: " + t.imei());
        }
        for (Creada c : r.creadas()) {
            switch (c.categoria()) {
                case "P" -> logDao.insertar(idUsu, "CREAR_ASIGNACION_PULIDO",
                        "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", ID_TEC: " + c.idTec());
                case "G" -> logDao.insertar(idUsu, "CREAR_ASIGNACION_GLASS",
                        "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", MODELO: " + dao.getModeloByImei(c.imei())
                        + ", TECNICO: " + dao.getNombreTecnicoById(c.idTec()));
                default -> {
                    boolean chasis = req.asignaciones().stream().anyMatch(a -> "R".equals(a.categoria())
                            && a.imei().equals(c.imei()) && a.idTec() == c.idTec() && a.esChasis());
                    String detalle = "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", MODELO: "
                            + dao.getModeloByImei(c.imei()) + ", TECNICO: " + dao.getNombreTecnicoById(c.idTec());
                    if (chasis) detalle += ", CHASIS: true";
                    logDao.insertar(idUsu, "CREAR_ASIGNACION", detalle);
                }
            }
        }
    }
}
