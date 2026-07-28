package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EnvioDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.MovimientoDAO;
import com.reparaciones.servidor.dao.RevisionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Telefono;
import com.reparaciones.servidor.model.TelefonoInventario;
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
    private final RevisionDAO revisionDao;
    private final EnvioDAO envioDao;
    private final MovimientoDAO movimientoDao;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService, LogDAO logDao, RevisionDAO revisionDao,
                             EnvioDAO envioDao, MovimientoDAO movimientoDao) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
        this.revisionDao = revisionDao;
        this.envioDao = envioDao;
        this.movimientoDao = movimientoDao;
    }

    @GetMapping
    public List<Telefono> getAll() {
        return dao.getAll();
    }

    @GetMapping("/inventario")
    public List<TelefonoInventario> getInventario() {
        return dao.getInventario();
    }

    @GetMapping("/{imei}/exists")
    public Map<String, Boolean> exists(@PathVariable String imei) {
        return Map.of("value", dao.exists(imei));
    }

    @GetMapping("/{imei}/cliente")
    public Map<String, String> getClienteId(@PathVariable String imei) {
        Integer id = dao.getClienteId(imei);
        return Map.of("value", id == null ? "" : id.toString());
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
    public void insertar(@RequestBody ImeiRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean explicito = Boolean.TRUE.equals(req.clienteExplicito());
        boolean cambiaCliente = req.idCli() != null || explicito;   // asignar o quitar
        if (cambiaCliente && !"SUPERTECNICO".equals(principal.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERTECNICO puede cambiar cliente");
        }
        dao.insertar(req.imei(), req.modelo(), req.idCli(), explicito);
        if (req.idCli() != null) {
            logDao.insertar(principal.getIdUsu(), "ASIGNAR_CLIENTE",
                    "IMEI: " + req.imei() + ", ID_CLI: " + req.idCli());
        } else if (explicito) {
            logDao.insertar(principal.getIdUsu(), "QUITAR_CLIENTE", "IMEI: " + req.imei());
        }
    }

    @PatchMapping("/{imei}/observacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarObservacion(@PathVariable String imei,
                                      @RequestBody ObservacionRequest req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarObservacion(imei, req.observacion(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_OBSERVACION", "IMEI: " + imei);
    }

    @PatchMapping("/{imei}/cliente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarCliente(@PathVariable String imei,
                                  @RequestBody ClienteRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarCliente(imei, req.idCli(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CAMBIAR_CLIENTE",
                "IMEI: " + imei + ", ID_CLI: " + (req.idCli() == null ? "—" : req.idCli()));
    }

    @PatchMapping("/{imei}/atributos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarAtributos(@PathVariable String imei,
                                    @RequestBody AtributosRequest req,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.actualizarAtributos(imei, req.modelo(), req.storageGb(), req.color(),
                req.gradoProveedor(), req.gradoPropio(), req.esEsim(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_ATRIBUTOS",
                "IMEI: " + imei + ", MODELO: " + req.modelo());
    }

    @DeleteMapping("/{imei}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String imei) {
        dao.eliminar(imei);
    }

    /**
     * F2c: el check antiguo de revisión ya no existe (lo sustituye el ciclo de F2b).
     * Se mantiene como no-op tolerante para clientes ≤v0.16 durante la ventana de
     * actualización; ELIMINAR en F3 (pasada de autorización).
     */
    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req) {
        // no-op
    }

    /** F2b: escaneo masivo "a revisar" — clasifica cada IMEI y pasa a EN_REVISION los que tocan. */
    @PostMapping("/a-revisar")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public List<ResultadoARevisarResponse> aRevisar(@RequestBody ImeisRequest req,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<ResultadoARevisarResponse> out = new java.util.ArrayList<>();
        for (String imei : new java.util.LinkedHashSet<>(req.imeis())) {
            RevisionDAO.ResultadoARevisar r = revisionDao.pasarARevisar(imei, principal.getIdUsu());
            if (r == RevisionDAO.ResultadoARevisar.PASADO || r == RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK) {
                logDao.insertar(principal.getIdUsu(), "A_REVISAR", "IMEI: " + imei
                        + (r == RevisionDAO.ResultadoARevisar.PASADO_ESTABA_OK ? ", ESTABA_OK" : ""));
            }
            out.add(new ResultadoARevisarResponse(imei, r.name()));
        }
        return out;
    }

    /** F2b: guarda la parte estética de la revisión vigente (sella autor+fecha, espeja grado). */
    @PatchMapping("/{imei}/revision/estetica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardarRevisionEstetica(@PathVariable String imei, @RequestBody EsteticaRequest req,
                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        revisionDao.guardarEstetica(imei, req.grado(), req.pant(), principal.getIdUsu());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_REVISION", "IMEI: " + imei + ", PARTE: ESTETICA");
    }

    /** F2b: guarda la parte funcional; con bloqueo de operador marcado, el teléfono pasa a BLOQUEADO. */
    @PatchMapping("/{imei}/revision/funcional")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardarRevisionFuncional(@PathVariable String imei, @RequestBody FuncionalRequest req,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        com.reparaciones.servidor.model.RevisionFuncional f = new com.reparaciones.servidor.model.RevisionFuncional(
                req.bateriaPct(), b(req.pantTactil()), b(req.pantQuemada()), b(req.pantMal()),
                b(req.camMancha()), b(req.camLente()), b(req.altSup()), b(req.altInf()), b(req.mic()),
                b(req.faceId()), b(req.ms()), req.msTexto(), b(req.bloqueoOp()), req.observacion());
        revisionDao.guardarFuncional(imei, f, principal.getIdUsu());
        logDao.insertar(principal.getIdUsu(), "GUARDAR_REVISION", "IMEI: " + imei + ", PARTE: FUNCIONAL");
        if (f.bloqueoOp() && revisionDao.bloquearPorRevision(imei, principal.getIdUsu())) {
            logDao.insertar(principal.getIdUsu(), "BLOQUEAR_TELEFONO", "IMEI: " + imei,
                    "Bloqueo de operador detectado en revisión");
        }
    }

    /** F2b: revisión vigente (última pasada) para la ficha; existe=false si nunca hubo. */
    @GetMapping("/{imei}/revision")
    public RevisionResponse getRevision(@PathVariable String imei) {
        com.reparaciones.servidor.model.Revision r = revisionDao.getVigente(imei);
        return new RevisionResponse(r != null, r);
    }

    /** F2c: registro masivo de devoluciones — cada teléfono vuelve al almacén marcado. */
    @PostMapping("/devoluciones")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public List<com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion> devoluciones(
            @RequestBody DevolucionesRequest req, @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion> out = new java.util.ArrayList<>();
        for (DevolucionItem item : req.items()) {
            com.reparaciones.servidor.dao.EnvioDAO.ItemDevolucion r = envioDao.devolver(item.imei(), item.motivo(), principal.getIdUsu());
            if ("DEVUELTO".equals(r.resultado())) {
                logDao.insertar(principal.getIdUsu(), "DEVOLUCION_TELEFONO",
                        "IMEI: " + item.imei() + (r.envio() != null ? ", ENVIO: " + r.envio() : ""), item.motivo());
            }
            out.add(r);
        }
        return out;
    }

    /** F2c: línea de vida del teléfono para el historial de la ficha. */
    @GetMapping("/{imei}/movimientos")
    public List<com.reparaciones.servidor.model.MovimientoTelefono> getMovimientos(@PathVariable String imei) {
        return movimientoDao.getPorImei(imei);
    }

    /** F2b: acciones de estado de la revisión. OK/BLOQUEAR/DESBLOQUEAR/DESGUACE (motivo obligatorio). */
    @PostMapping("/{imei}/estado")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accionEstado(@PathVariable String imei, @RequestBody EstadoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        switch (req.accion() == null ? "" : req.accion()) {
            case "OK" -> {
                revisionDao.marcarOk(imei, principal.getIdUsu());
                logDao.insertar(principal.getIdUsu(), "TELEFONO_OK", "IMEI: " + imei);
            }
            case "BLOQUEAR" -> {
                revisionDao.bloquear(imei, principal.getIdUsu(), req.motivo());
                logDao.insertar(principal.getIdUsu(), "BLOQUEAR_TELEFONO", "IMEI: " + imei, req.motivo());
            }
            case "DESBLOQUEAR" -> {
                revisionDao.desbloquear(imei, principal.getIdUsu());
                logDao.insertar(principal.getIdUsu(), "DESBLOQUEAR_TELEFONO", "IMEI: " + imei);
            }
            case "DESGUACE" -> {
                if (req.motivo() == null || req.motivo().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El desguace requiere motivo");
                revisionDao.desguace(imei, principal.getIdUsu(), req.motivo());
                logDao.insertar(principal.getIdUsu(), "DESGUACE_TELEFONO", "IMEI: " + imei, req.motivo());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción desconocida");
        }
    }

    private static boolean b(Boolean v) { return Boolean.TRUE.equals(v); }

    private record ImeisRequest(java.util.List<String> imeis) {}
    private record ResultadoARevisarResponse(String imei, String resultado) {}
    private record ImeiRequest(String imei, String modelo, Integer idCli, Boolean clienteExplicito) {}
    private record ObservacionRequest(String observacion, java.time.LocalDateTime updatedAt) {}
    private record ClienteRequest(Integer idCli, java.time.LocalDateTime updatedAt) {}
    private record RevisionLogisticaRequest(boolean revisado, java.time.LocalDateTime updatedAt) {}
    private record AtributosRequest(String modelo, Integer storageGb, String color,
                                    String gradoProveedor, String gradoPropio, Boolean esEsim,
                                    java.time.LocalDateTime updatedAt) {}
    private record EsteticaRequest(String grado, String pant) {}
    private record FuncionalRequest(Integer bateriaPct, Boolean pantTactil, Boolean pantQuemada, Boolean pantMal,
                                    Boolean camMancha, Boolean camLente, Boolean altSup, Boolean altInf,
                                    Boolean mic, Boolean faceId, Boolean ms, String msTexto,
                                    Boolean bloqueoOp, String observacion) {}
    private record RevisionResponse(boolean existe, com.reparaciones.servidor.model.Revision revision) {}
    private record EstadoRequest(String accion, String motivo) {}
    private record DevolucionesRequest(java.util.List<DevolucionItem> items) {}
    private record DevolucionItem(String imei, String motivo) {}
}
