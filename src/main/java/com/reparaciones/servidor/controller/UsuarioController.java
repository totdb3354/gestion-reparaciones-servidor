package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.Usuario;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioDAO dao;

    public UsuarioController(UsuarioDAO dao) {
        this.dao = dao;
    }

    @GetMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> getUsuariosTecnicos() {
        return dao.getUsuariosTecnicos();
    }

    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req) {
        try {
            dao.registrarTecnico(req.nombreTecnico(), req.nombreUsuario(), req.password());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PatchMapping("/tecnicos/{idTec}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activarTecnico(@PathVariable int idTec) {
        dao.activarTecnico(idTec);
    }

    @PatchMapping("/tecnicos/{idTec}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarTecnico(@PathVariable int idTec) {
        dao.desactivarTecnico(idTec);
    }

    @GetMapping("/tecnicos/{idTec}/tiene-reparaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> tieneReparaciones(@PathVariable int idTec) {
        return Map.of("value", dao.tieneReparaciones(idTec));
    }

    @DeleteMapping("/tecnicos/{idTec}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTecnico(@PathVariable int idTec, @RequestParam int idUsu) {
        dao.eliminarTecnico(idTec, idUsu);
    }

    private record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password) {}
}
