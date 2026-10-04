package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.EstadoUsuarioService;
import com.reparaciones.servidor.security.IntentosFallidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.MedidorFuerza;
import com.reparaciones.servidor.security.PoliticaPassword;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** PATCH /api/auth/cambiar-password (spec 6 §4.4): 422 "Rellena todos los campos." y 422 de longitud antes de tocar
 *  la BD (sustituyen al 400 sin cuerpo), el 422 "Contraseña actual incorrecta." de siempre con su registro
 *  (spec sp7b §5.2) y 204 con log. */
class AuthControllerCambiarPasswordTest {

    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final EstadoUsuarioService estadoUsuario = mock(EstadoUsuarioService.class);
    private final List<List<String>> palabrasMedidas = new ArrayList<>();
    private int notaFija = 4;
    private final PoliticaPassword politica = new PoliticaPassword(
            (p, w) -> { palabrasMedidas.add(w); return new MedidorFuerza.Medida(notaFija, List.of("Añade otra palabra.")); }, "");
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao, new IntentosFallidos(), estadoUsuario, politica);
    private final UsuarioPrincipal usuario = new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4);

    private static AuthController.CambiarPasswordRequest cambio(String actual, String nueva) {
        return new AuthController.CambiarPasswordRequest(actual, nueva);
    }

    /** Un 422 de validación no cambia la contraseña ni registra log. */
    private String falla422(AuthController.CambiarPasswordRequest req) {
        return falla422(usuario, req);
    }

    private String falla422(UsuarioPrincipal quien, AuthController.CambiarPasswordRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(quien, req));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verify(usuarioDao, never()).cambiarPassword(anyInt(), any(), any());
        verifyNoInteractions(logDao);
        return e.getReason();
    }

    @Test void cambioValidoEs204YRegistraLog() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123"));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "nueva-larga-123");
        verify(logDao).insertar(8, "CAMBIAR_PASSWORD", "");
    }

    /** Tras cambiarla, la entrada cacheada del filtro se retira: el usuario puede operar de inmediato en vez
     *  de esperar hasta 30 s a que caduque (spec sp7b, arreglo E3-2). */
    @Test void unCambioValidoRetiraLaEntradaDeLaCache() {
        ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123"));
        verify(estadoUsuario).invalidar(8);
    }

    /** Un intento fallido no retira nada: la marca de contraseña temporal sigue como estaba. */
    @Test void unCambioFallidoNoTocaLaCache() {
        doThrow(new IllegalArgumentException("Contraseña actual incorrecta."))
                .when(usuarioDao).cambiarPassword(8, "otra-cosa", "nueva-larga-123");
        ctl.cambiarPassword(usuario, cambio("otra-cosa", "nueva-larga-123"));
        verifyNoInteractions(estadoUsuario);
    }

    @Test void camposVaciosOAusentesSon422() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "nueva-larga-123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", "")));
        assertEquals("Rellena todos los campos.", falla422(cambio(null, "nueva-larga-123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", null)));
    }

    @Test void nuevaDeMenosDeDiezEs422() {
        assertEquals("La contraseña debe tener al menos 10 caracteres.", falla422(cambio("secreta1", "123456789")));
    }

    @Test void elOrdenEsCamposYDespuesLongitud() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "123")));
    }

    /** Sin trim: diez espacios son una contraseña de 10 caracteres (la nota la pone el medidor). */
    @Test void losEspaciosCuentanComoCaracteres() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "          "));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "          ");
    }

    /** Un rechazo por la regla no es un fallo de la contraseña actual: ni registra CAMBIAR_PASSWORD_FALLIDO ni gasta
     *  intentos del freno (seis rechazos seguidos y una buena después entra sin 429). */
    @Test void unaPocoSeguraEs422ConElConsejoYSinGastarIntentos() {
        notaFija = 2;
        assertEquals("La contraseña es poco segura. Añade otra palabra.", falla422(cambio("secreta1", "nueva-larga-123")));
        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123")));
        }
        notaFija = 4;
        assertEquals(204, ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123")).getStatusCode().value());
    }

    @Test void igualALaActualEs422() {
        assertEquals("La nueva contraseña tiene que ser distinta de la actual.",
                falla422(cambio("misma-de-antes-1", "misma-de-antes-1")));
    }

    @Test void elAdministradorNecesitaNotaCuatro() {
        notaFija = 3;
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);
        assertEquals("La contraseña es poco segura. Añade otra palabra.", falla422(admin, cambio("secreta1", "nueva-larga-123")));
    }

    @Test void elNombreDelTecnicoPenaliza() {
        when(usuarioDao.getNombreByIdTec(4)).thenReturn("Tecnico Prueba");
        ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123"));
        assertTrue(palabrasMedidas.get(0).containsAll(List.of("usuario-a", "tecnico prueba", "prueba")));
    }

    @Test void actualIncorrectaSigueSiendo422ConSuTextoYRegistraElFallo() {
        doThrow(new IllegalArgumentException("Contraseña actual incorrecta."))
                .when(usuarioDao).cambiarPassword(8, "otra-cosa", "nueva-larga-123");
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("otra-cosa", "nueva-larga-123"));
        assertEquals(422, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Contraseña actual incorrecta."), resp.getBody());
        verify(logDao).insertar(8, "CAMBIAR_PASSWORD_FALLIDO", "INTENTOS: 1");
    }
}
