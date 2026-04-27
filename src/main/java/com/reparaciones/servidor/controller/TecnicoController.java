package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.Tecnico;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tecnicos")
public class TecnicoController {

    private final TecnicoDAO dao;

    public TecnicoController(TecnicoDAO dao) {
        this.dao = dao;
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
    public void insertar(@RequestBody NombreRequest req) {
        dao.insertar(req.nombre());
    }

    @DeleteMapping("/{idTec}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idTec) {
        dao.eliminar(idTec);
    }

    private record NombreRequest(String nombre) {}
}
