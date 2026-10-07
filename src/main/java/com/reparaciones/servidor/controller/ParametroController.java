package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.PesosPrevision;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Parámetros que edita el administrador (spec 0.9.5 §4.2): los pesos de la previsión de pedidos de Stock. */
@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    static final String MSG_PESOS = "Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.";

    private final ParametroDAO dao;
    private final LogDAO logDao;

    public ParametroController(ParametroDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/prevision")
    public PesosPrevision getPrevision() {
        Pesos p = dao.getPesosPrevision();
        return new PesosPrevision(p.p1(), p.p2(), p.p3());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @PutMapping("/prevision")
    public void guardarPrevision(@RequestBody PesosPrevision req, @AuthenticationPrincipal UsuarioPrincipal principal) {
        Pesos nuevos = new Pesos(req.peso1(), req.peso2(), req.peso3());
        if (!nuevos.validos()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_PESOS);
        Pesos antes = dao.getPesosPrevision();
        dao.guardarPesosPrevision(nuevos);
        if (!antes.equals(nuevos))
            logDao.insertar(principal.getIdUsu(), "EDITAR_PARAMETROS", "PREVISION: " + antes.texto() + " → " + nuevos.texto());
    }
}
