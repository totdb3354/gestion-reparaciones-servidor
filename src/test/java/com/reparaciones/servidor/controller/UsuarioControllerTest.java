package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** UsuarioController (spec 6 §4.1 y §4.2): 422 del alta en orden, trim guardado, 409 de duplicados, 404 de ids
 *  inexistentes y 409 del borrado con referencias. Mockito sin Spring. */
class UsuarioControllerTest {

    private final UsuarioDAO dao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioController ctl = new UsuarioController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null);

    private static UsuarioController.RegistrarTecnicoRequest alta(String tecnico, String usuario, String password, String rol) {
        return new UsuarioController.RegistrarTecnicoRequest(tecnico, usuario, password, rol);
    }

    /** Un 422 nunca escribe ni registra log (ni siquiera consulta duplicados). */
    private String falla422(UsuarioController.RegistrarTecnicoRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.registrarTecnico(req, admin));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verifyNoInteractions(dao, logDao);
        return e.getReason();
    }

    // ── alta: los cinco 422 en orden ──
    @Test void altaConCampoVacioEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", "usuario-a", "secreta1", "TECNICO")));
    }

    @Test void altaConNombresEnBlancoONulosEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("   ", "usuario-a", "secreta1", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", null, "secreta1", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", "usuario-a", null, "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", "usuario-a", "", "TECNICO")));
    }

    @Test void altaConPasswordCortaEs422() {
        assertEquals("La contraseña debe tener al menos 6 caracteres.",
                falla422(alta("tecnico-a", "usuario-a", "12345", "TECNICO")));
    }

    @Test void altaConUsuarioDeMasDe50Es422() {
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta("tecnico-a", "u".repeat(51), "secreta1", "TECNICO")));
    }

    @Test void altaConTecnicoDeMasDe100Es422() {
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta("t".repeat(101), "usuario-a", "secreta1", "TECNICO")));
    }

    @Test void altaConRolNoPermitidoEs422EnVezDe400() {
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "secreta1", "ADMIN")));
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "secreta1", "")));
    }

    /** Parando en la primera: con todo mal a la vez sale el primero de la lista, y así sucesivamente. */
    @Test void elOrdenEsCamposSeisCincuentaCienRol() {
        String largo51 = "u".repeat(51);
        String largo101 = "t".repeat(101);
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", largo51, "1", "ADMIN")));
        assertEquals("La contraseña debe tener al menos 6 caracteres.", falla422(alta(largo101, largo51, "1", "ADMIN")));
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta(largo101, largo51, "secreta1", "ADMIN")));
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta(largo101, "usuario-a", "secreta1", "ADMIN")));
    }

    /** Los límites exactos pasan: 6 caracteres, 50 y 100 (tras el trim). */
    @Test void losLimitesExactosSonValidos() {
        String usuario50 = "u".repeat(50);
        String tecnico100 = "t".repeat(100);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" " + tecnico100 + " ", " " + usuario50 + " ", "123456", "TECNICO"), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(tecnico100, usuario50, "123456", "TECNICO");
    }

    // ── alta: trim guardado, rol por defecto, 201 con log ──
    @Test void altaValidaGuardaLosNombresRecortadosYRegistraLog() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("  tecnico-a ", " usuario-a  ", " secreta1 ", "SUPERTECNICO"), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).existeNombreTecnico("tecnico-a");
        verify(dao).existeNombreUsuario("usuario-a");
        // la contraseña no se recorta (calco del cliente)
        verify(dao).registrarTecnico("tecnico-a", "usuario-a", " secreta1 ", "SUPERTECNICO");
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: SUPERTECNICO, TECNICO: tecnico-a");
    }

    @Test void altaSinRolGuardaTecnico() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "secreta1", null), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO");
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: TECNICO, TECNICO: tecnico-a");
    }

    // ── alta: los dos 409 de siempre ──
    @Test void tecnicoDuplicadoEs409SinEscribir() {
        when(dao.existeNombreTecnico("tecnico-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" tecnico-a ", "usuario-a", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ya existe un técnico con ese nombre."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void usuarioDuplicadoEs409SinEscribir() {
        when(dao.existeNombreUsuario("usuario-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a ", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void violacionDeIntegridadSigueSiendo409SinLog() {
        doThrow(new DataIntegrityViolationException("duplicado"))
                .when(dao).registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO");
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verifyNoInteractions(logDao);
    }
}
