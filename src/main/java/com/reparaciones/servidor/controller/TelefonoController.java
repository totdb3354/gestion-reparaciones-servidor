package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
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

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService, LogDAO logDao) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
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

    @PutMapping("/{imei}/revision-logistica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarRevisionLogistica(@PathVariable String imei,
                                            @RequestBody RevisionLogisticaRequest req,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.tieneAsignacionesActivas(imei)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El IMEI tiene asignaciones activas");
        }
        dao.actualizarRevisionLogistica(imei, req.revisado(), req.updatedAt());
        String modelo = dao.getModelo(imei);
        logDao.insertar(principal.getIdUsu(),
                req.revisado() ? "MARCAR_REVISION" : "QUITAR_REVISION",
                "IMEI: " + imei + ", MODELO: " + (modelo != null ? modelo : "?"));
    }

    private record ImeiRequest(String imei, String modelo, Integer idCli, Boolean clienteExplicito) {}
    private record ObservacionRequest(String observacion, java.time.LocalDateTime updatedAt) {}
    private record ClienteRequest(Integer idCli, java.time.LocalDateTime updatedAt) {}
    private record RevisionLogisticaRequest(boolean revisado, java.time.LocalDateTime updatedAt) {}
    private record AtributosRequest(String modelo, Integer storageGb, String color,
                                    String gradoProveedor, String gradoPropio, Boolean esEsim,
                                    java.time.LocalDateTime updatedAt) {}
}
