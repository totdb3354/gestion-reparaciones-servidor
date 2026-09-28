package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.Usuario;
import com.reparaciones.servidor.model.ValorTexto;
import com.reparaciones.servidor.security.PasswordTemporal;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    /** Nombre de la operación en {@link RegistroIdempotencia}. */
    static final String OP_ALTA = "alta-tecnico";

    private final UsuarioDAO           dao;
    private final LogDAO               logDao;
    private final RegistroIdempotencia idempotencia;
    /** Clave de la huella de la contraseña: aleatoria, creada al arrancar y solo en memoria de este proceso. */
    private final byte[]               claveHuella;

    public UsuarioController(UsuarioDAO dao, LogDAO logDao, RegistroIdempotencia idempotencia) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.idempotencia = idempotencia;
        this.claveHuella  = claveHuellaNueva();
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
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content),
        @ApiResponse(responseCode = "422", content = @Content)
    })
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal,
                                               @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                               String claveIdempotencia) {
        String nombreTecnico = recortar(req.nombreTecnico());
        String nombreUsuario = recortar(req.nombreUsuario());
        ValidacionUsuarios.validarAlta(nombreTecnico, nombreUsuario, req.password(), req.rol());
        String rol = req.rol() != null ? req.rol() : "TECNICO";
        // Con clave, un reintento con el mismo cuerpo devuelve el 201 de la primera vez en vez del 409 de
        // duplicado. Los 409 no quedan registrados: salen de la escritura como excepción y se responden aquí.
        var peticion = new PeticionAltaTecnico(nombreTecnico, nombreUsuario, huella(claveHuella, req.password()), rol);
        try {
            return idempotencia.ejecutar(principal.getIdUsu(), OP_ALTA, claveIdempotencia, peticion,
                    () -> {
                        if (dao.existeNombreTecnico(nombreTecnico)) {
                            throw new Duplicado("Ya existe un técnico con ese nombre.");
                        }
                        if (dao.existeNombreUsuario(nombreUsuario)) {
                            throw new Duplicado("Ese nombre de usuario ya existe.");
                        }
                        try {
                            dao.registrarTecnico(nombreTecnico, nombreUsuario, req.password(), rol);
                        } catch (DataIntegrityViolationException e) {
                            throw new Duplicado("Ese nombre de usuario ya existe.");
                        }
                        return ResponseEntity.status(HttpStatus.CREATED).build();
                    },
                    ignorado -> logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                            "NOMBRE_USUARIO: " + nombreUsuario + ", ROL: " + rol + ", TECNICO: " + nombreTecnico));
        } catch (Duplicado d) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", d.getMessage()));
        }
    }

    /** Lo que identifica un alta de técnico en el registro de reintentos: la contraseña entra solo como huella. */
    private record PeticionAltaTecnico(String nombreTecnico, String nombreUsuario, String huellaPassword, String rol) {}

    /** 409 de duplicado dentro de la escritura del alta: no se registra y se responde con su mensaje. */
    private static final class Duplicado extends RuntimeException {
        Duplicado(String mensaje) {
            super(mensaje, null, false, false);
        }
    }

    /** 32 bytes aleatorios para la huella; cada instancia (cada arranque del servidor) tiene los suyos. */
    static byte[] claveHuellaNueva() {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        return clave;
    }

    /** HMAC-SHA256 del texto con la clave dada: sirve para comparar dos peticiones dentro del mismo proceso. */
    static String huella(byte[] clave, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String s) {
        return s == null ? null : s.trim();
    }

    @PatchMapping("/tecnicos/{idTec}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content)
    })
    public void activarTecnico(@PathVariable int idTec,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
        String nombre = dao.getNombreByIdTec(idTec);
        dao.activarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "ACTIVAR_USUARIO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content)
    })
    public void desactivarTecnico(@PathVariable int idTec,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
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
        exigirTecnico(idTec);
        return Map.of("value", dao.tieneReferencias(idTec));
    }

    /** Spec 6 §4.2 (G8): el servidor vuelve a comprobar todas las referencias (409 sin borrar nada) y resuelve el
     *  usuario desde idTec; el idUsu de la query se conserva en el contrato (opcional) para el JavaFX y se ignora. */
    @DeleteMapping("/tecnicos/{idTec}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    public void eliminarTecnico(@PathVariable int idTec,
                                @io.swagger.v3.oas.annotations.Parameter(
                                        description = "Ignorado: el servidor lo resuelve desde idTec")
                                @RequestParam(required = false) Integer idUsu,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
        String nombre = dao.getNombreByIdTec(idTec);
        if (dao.tieneReferencias(idTec)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ValidacionUsuarios.msgTieneReferencias(nombre));
        }
        Integer idUsuReal = dao.getIdUsuByIdTec(idTec);
        if (idUsuReal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ValidacionUsuarios.MSG_NO_ENCONTRADO);
        }
        dao.eliminarTecnico(idTec, idUsuReal);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_USUARIO",
                "ID_TEC: " + idTec + ", ID_USU: " + idUsuReal + ", NOMBRE: " + nombre);
    }

    /** 404 {message: "Técnico no encontrado."} en vez del 500 de getNombreByIdTec (spec 6 §4.2). */
    private void exigirTecnico(int idTec) {
        if (!dao.existeTecnico(idTec)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ValidacionUsuarios.MSG_NO_ENCONTRADO);
        }
    }

    /**
     * Entrega una contraseña temporal para otro usuario. La genera el servidor, se devuelve una sola vez para
     * que el administrador la comunique, y el usuario está obligado a cambiarla al entrar (spec sp7b §5.4).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{idUsu}/password-temporal")
    public ValorTexto entregarPasswordTemporal(@PathVariable int idUsu,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String password = PasswordTemporal.generar();
        dao.fijarPasswordTemporal(idUsu, password);
        logDao.insertar(principal.getIdUsu(), "RESTABLECER_PASSWORD", "ID_USU: " + idUsu);
        return new ValorTexto(password);
    }

    /** Package-private (no private) para que los tests lo construyan; springdoc lo publica con el mismo nombre. */
    record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password, String rol) {}
}
