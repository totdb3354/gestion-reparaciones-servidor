package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.model.SolicitudResumen;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private final ReparacionComponenteDAO dao;

    public SolicitudController(ReparacionComponenteDAO dao) {
        this.dao = dao;
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
                                  @RequestBody Map<String, String> body) {
        dao.actualizarEstadoSolicitud(idRc, body.get("estado"));
    }

    @PatchMapping("/{idRc}/limpiar")
    public void limpiar(@PathVariable int idRc) {
        dao.limpiarSolicitud(idRc);
    }
}
