package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.model.ReparacionComponente;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reparacion-componentes")
public class ReparacionComponenteController {

    private final ReparacionComponenteDAO dao;

    public ReparacionComponenteController(ReparacionComponenteDAO dao) {
        this.dao = dao;
    }

    @GetMapping("/{idRep}")
    public List<ReparacionComponente> getByReparacion(@PathVariable String idRep) {
        return dao.getByReparacion(idRep);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req) {
        dao.insertar(req.idRep(), req.idCom(), req.esReutilizado(),
                req.esIncidencia(), req.esResuelto(), req.incidencia(),
                req.observaciones(), req.esSolicitud(),
                req.descripcionSolicitud(), req.cantidad());
    }

    @DeleteMapping("/{idRep}/{idCom}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String idRep, @PathVariable int idCom) {
        dao.eliminar(idRep, idCom);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idRep}/incidencia")
    public void marcarIncidencia(@PathVariable String idRep,
                                  @RequestBody Map<String, String> body) {
        dao.marcarIncidencia(idRep, body.get("comentario"));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idRep}/incidencia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarIncidencia(@PathVariable String idRep) {
        dao.borrarIncidencia(idRep);
    }

    private record InsertarRequest(String idRep, Integer idCom,
                                   boolean esReutilizado, boolean esIncidencia,
                                   boolean esResuelto, String incidencia,
                                   String observaciones, boolean esSolicitud,
                                   String descripcionSolicitud, int cantidad) {}
}
