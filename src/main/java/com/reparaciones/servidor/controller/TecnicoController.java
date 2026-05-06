package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tecnicos")
public class TecnicoController {

    private final TecnicoDAO dao;
    private final LogDAO     logDao;

    public TecnicoController(TecnicoDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<Tecnico> getAll() {
        return dao.getAll();
    }

    @GetMapping("/activos")
    public List<Tecnico> getAllActivos() {
        return dao.getAllActivos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody NombreRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.nombre());
        logDao.insertar(principal.getIdUsu(), "CREAR_TECNICO", "NOMBRE: " + req.nombre());
    }

    @DeleteMapping("/{idTec}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idTec,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminar(idTec);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_TECNICO", "ID_TEC: " + idTec);
    }

    private record NombreRequest(String nombre) {}
}
