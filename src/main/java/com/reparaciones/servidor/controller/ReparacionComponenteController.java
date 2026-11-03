package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reparacion-componentes")
public class ReparacionComponenteController {

    private final ReparacionComponenteDAO dao;
    private final LogDAO logDao;

    public ReparacionComponenteController(ReparacionComponenteDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idRep}/incidencia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarIncidencia(@PathVariable String idRep,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrarIncidencia(idRep);
        logDao.insertar(principal.getIdUsu(), "BORRAR_INCIDENCIA", "ID_REP: " + idRep);
    }
}
