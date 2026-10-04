package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.EvaluacionPassword;
import com.reparaciones.servidor.model.LoginResponse;
import com.reparaciones.servidor.security.EstadoUsuarioService;
import com.reparaciones.servidor.security.IntentosFallidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.PoliticaPassword;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final JwtUtil               jwtUtil;
    private final LogDAO                logDao;
    private final UsuarioDAO            usuarioDao;
    private final IntentosFallidos      intentos;
    private final EstadoUsuarioService  estadoUsuario;
    private final PoliticaPassword      politica;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil,
                          LogDAO logDao, UsuarioDAO usuarioDao, IntentosFallidos intentos,
                          EstadoUsuarioService estadoUsuario, PoliticaPassword politica) {
        this.authManager  = authManager;
        this.jwtUtil      = jwtUtil;
        this.logDao       = logDao;
        this.usuarioDao   = usuarioDao;
        this.intentos     = intentos;
        this.estadoUsuario = estadoUsuario;
        this.politica      = politica;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            content = @io.swagger.v3.oas.annotations.media.Content(
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req,
                                               jakarta.servlet.http.HttpServletRequest http) {
        // Sin nombre no hay cuenta que frenar ni intento que auditar: 401 antes de tocar nada más (spec sp7b §5.2).
        if (req.usuario() == null || req.usuario().isBlank()) {
            return ResponseEntity.status(401).build();
        }
        intentos.comprobar(req.usuario());
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            intentos.limpiar(req.usuario());
            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            return ResponseEntity.ok(new LoginResponse(
                    principal.getIdUsu(), principal.getUsername(), principal.getRol(),
                    principal.getIdTec(), token, usuarioDao.tienePasswordTemporal(principal.getIdUsu())));
        } catch (BadCredentialsException e) {
            // Deja constancia del intento con el nombre tal cual se escribió, aunque no exista ese usuario
            // (spec sp7b §5.2).
            int fallos = intentos.registrarFallo(req.usuario());
            anotarIntentoFallido(req.usuario(), fallos, http);
            return ResponseEntity.status(401).build();
        }
    }

    /** Registrar el intento no puede cambiar el 401 de una autenticación fallida: si la escritura de auditoría
     *  falla (p. ej. un nombre demasiado largo para la columna), la traza va al log de la aplicación y el intento
     *  sigue respondiendo 401, igual que RutasRetiradasInterceptor.anotar con las rutas retiradas. */
    private void anotarIntentoFallido(String usuario, int fallos, jakarta.servlet.http.HttpServletRequest http) {
        try {
            logDao.insertarIntento(usuario, "LOGIN_FALLIDO", "INTENTOS: " + fallos + origenDe(http));
        } catch (RuntimeException e) {
            log.warn("No se pudo anotar el intento fallido de login: {}", e.toString());
        }
    }

    @PatchMapping("/cambiar-password")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<?> cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody CambiarPasswordRequest req) {
        intentos.comprobar(principal.getUsername());
        // 422 con los textos del cliente antes de BCrypt (spec 6 §4.4): sustituye al 400 sin cuerpo y evita el
        // "rawPassword cannot be null" de matches(null, …). Lanza ResponseStatusException: el catch de abajo no la ve.
        ValidacionUsuarios.validarCambioPassword(req.passwordActual(), req.passwordNueva());
        // La regla de la nueva antes de comprobar la actual: un rechazo por la regla no es un intento fallido.
        EvaluacionPassword evaluacion = politica.evaluar(req.passwordNueva(), req.passwordActual(),
                principal.getUsername(), nombreTecnico(principal), principal.getRol());
        if (!evaluacion.aceptable()) throw ValidacionUsuarios.regla(evaluacion.mensaje());
        try {
            usuarioDao.cambiarPassword(principal.getIdUsu(), req.passwordActual(), req.passwordNueva());
            // La marca ya se limpió en la base: retira también la entrada cacheada para que pueda operar de
            // inmediato, sin esperar a que caduque la caché del filtro (spec sp7b, arreglo E3-2).
            estadoUsuario.invalidar(principal.getIdUsu());
            intentos.limpiar(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            int fallos = intentos.registrarFallo(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD_FALLIDO", "INTENTOS: " + fallos);
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Nota de una contraseña propuesta, para la barra de la web mientras se escribe: la misma regla que al guardar,
     * con el usuario, el técnico y el rol de la sesión (nunca del cuerpo). No conoce la actual, así que no comprueba
     * que sea distinta (eso lo hace el guardado). No escribe, no registra actividad y no deja la contraseña en ningún
     * log. Se permite también con la contraseña temporal (JwtAuthFilter), porque se usa en el cambio obligatorio.
     */
    @PostMapping("/evaluar-password")
    public EvaluacionPassword evaluarPassword(@AuthenticationPrincipal UsuarioPrincipal principal,
                                              @RequestBody EvaluarPasswordRequest req) {
        return politica.evaluar(req.password(), null, principal.getUsername(), nombreTecnico(principal),
                principal.getRol());
    }

    /** Nombre visible del técnico de la sesión, para que la regla lo penalice; null sin técnico o si no se encuentra. */
    private String nombreTecnico(UsuarioPrincipal principal) {
        if (principal.getIdTec() == null) return null;
        try {
            return usuarioDao.getNombreByIdTec(principal.getIdTec());
        } catch (org.springframework.dao.DataAccessException e) {
            return null;
        }
    }

    /** La dirección que ve la aplicación, para el registro; vacío si no hay petición (tests). */
    private static String origenDe(jakarta.servlet.http.HttpServletRequest http) {
        if (http == null) return "";
        String reenviada = http.getHeader("X-Real-IP");
        if (reenviada == null || reenviada.isBlank()) reenviada = http.getRemoteAddr();
        return reenviada == null || reenviada.isBlank() ? "" : ", ORIGEN: " + reenviada;
    }

    record LoginRequest(String usuario, String password) {}
    record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
    record EvaluarPasswordRequest(String password) {}
}
