package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.model.SolicitudResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@PreAuthorize("hasRole('SUPERTECNICO')")
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private final ReparacionComponenteDAO dao;
    private final LogDAO                  logDao;

    public SolicitudController(ReparacionComponenteDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping("/count")
    public Map<String, Object> count() {
        return Map.of("value", dao.contarSolicitudesPendientes());
    }

    @GetMapping
    public List<SolicitudResumen> getSolicitudes(
            @RequestParam(required = false) String estado) {
        return dao.getSolicitudes(estado);
    }

    @PatchMapping("/{idRc}/estado")
    public void actualizarEstado(@PathVariable int idRc,
                                  @RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.get("estado");
        dao.actualizarEstadoSolicitud(idRc, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado) ? "RECHAZAR_SOLICITUD" : "GESTIONAR_SOLICITUD";
        logDao.insertar(principal.getIdUsu(), accion, "ID_RC: " + idRc + ", ESTADO: " + estado);
    }

    @PatchMapping("/{idRc}/limpiar")
    public void limpiar(@PathVariable int idRc) {
        dao.limpiarSolicitud(idRc);
    }
}
