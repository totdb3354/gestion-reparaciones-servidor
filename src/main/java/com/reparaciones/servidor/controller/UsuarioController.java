package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.Usuario;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioDAO dao;
    private final LogDAO     logDao;

    public UsuarioController(UsuarioDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> getUsuariosTecnicos() {
        return dao.getUsuariosTecnicos();
    }

    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        try {
            dao.registrarTecnico(req.nombreTecnico(), req.nombreUsuario(), req.password());
            logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                    "NOMBRE_USUARIO: " + req.nombreUsuario() + ", TECNICO: " + req.nombreTecnico());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PatchMapping("/tecnicos/{idTec}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activarTecnico(@PathVariable int idTec,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.activarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "ACTIVAR_USUARIO", "ID_TEC: " + idTec);
    }

    @PatchMapping("/tecnicos/{idTec}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarTecnico(@PathVariable int idTec,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desactivarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "DESACTIVAR_USUARIO", "ID_TEC: " + idTec);
    }

    @GetMapping("/tecnicos/{idTec}/tiene-reparaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> tieneReparaciones(@PathVariable int idTec) {
        return Map.of("value", dao.tieneReparaciones(idTec));
    }

    @DeleteMapping("/tecnicos/{idTec}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTecnico(@PathVariable int idTec, @RequestParam int idUsu,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminarTecnico(idTec, idUsu);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_USUARIO",
                "ID_TEC: " + idTec + ", ID_USU: " + idUsu);
    }

    private record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password) {}
}
