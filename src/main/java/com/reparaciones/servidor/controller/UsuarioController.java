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

    /** Alta de usuario y técnico (spec 6 §4.1): los nombres se recortan antes de validar y se guardan recortados;
     *  los cinco 422 de {@link ValidacionUsuarios#validarAlta} van antes que los dos 409 de duplicado de siempre.
     *  Un 422 o un 409 no escriben ni registran log. */
    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombreTecnico = recortar(req.nombreTecnico());
        String nombreUsuario = recortar(req.nombreUsuario());
        ValidacionUsuarios.validarAlta(nombreTecnico, nombreUsuario, req.password(), req.rol());
        if (dao.existeNombreTecnico(nombreTecnico)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya existe un técnico con ese nombre."));
        }
        if (dao.existeNombreUsuario(nombreUsuario)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        String rol = req.rol() != null ? req.rol() : "TECNICO";
        try {
            dao.registrarTecnico(nombreTecnico, nombreUsuario, req.password(), rol);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                "NOMBRE_USUARIO: " + nombreUsuario + ", ROL: " + rol + ", TECNICO: " + nombreTecnico);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static String recortar(String s) {
        return s == null ? null : s.trim();
    }

    @PatchMapping("/tecnicos/{idTec}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activarTecnico(@PathVariable int idTec,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.activarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "ACTIVAR_USUARIO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarTecnico(@PathVariable int idTec,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.desactivarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "DESACTIVAR_USUARIO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/excluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.excluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "EXCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/incluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void incluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.incluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "INCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
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
        String nombre = dao.getNombreByIdTec(idTec);
        dao.eliminarTecnico(idTec, idUsu);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_USUARIO",
                "ID_TEC: " + idTec + ", ID_USU: " + idUsu + ", NOMBRE: " + nombre);
    }

    /** Package-private (no private) para que los tests lo construyan; springdoc lo publica con el mismo nombre. */
    record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password, String rol) {}
}
