package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Telefono;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telefonos")
public class TelefonoController {

    private final TelefonoDAO dao;

    public TelefonoController(TelefonoDAO dao) {
        this.dao = dao;
    }

    @GetMapping
    public List<Telefono> getAll() {
        return dao.getAll();
    }

    @GetMapping("/{imei}/exists")
    public Map<String, Boolean> exists(@PathVariable String imei) {
        return Map.of("value", dao.exists(imei));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody ImeiRequest req) {
        dao.insertar(req.imei());
    }

    @DeleteMapping("/{imei}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String imei) {
        dao.eliminar(imei);
    }

    private record ImeiRequest(String imei) {}
}
