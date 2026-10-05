package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.EstadoUsuarioService;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import com.reparaciones.servidor.model.ValorTexto;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** UsuarioController (spec 6 §4.1 y §4.2): 422 del alta en orden, trim guardado, 409 de duplicados, 404 de ids
 *  inexistentes y 409 del borrado con referencias. Mockito sin Spring. */
class UsuarioControllerTest {

    private final UsuarioDAO dao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final EstadoUsuarioService estado = mock(EstadoUsuarioService.class);
    private final UsuarioController ctl = new UsuarioController(dao, logDao, new com.reparaciones.servidor.idempotencia.RegistroIdempotencia(), estado);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null);

    private static UsuarioController.RegistrarTecnicoRequest alta(String tecnico, String usuario, String rol) {
        return new UsuarioController.RegistrarTecnicoRequest(tecnico, usuario, rol);
    }

    /** Un 422 nunca escribe ni registra log (ni siquiera consulta duplicados). */
    private String falla422(UsuarioController.RegistrarTecnicoRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.registrarTecnico(req, admin, null));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verifyNoInteractions(dao, logDao);
        return e.getReason();
    }

    // ── alta: los cinco 422 en orden ──
    @Test void altaConCampoVacioEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", "usuario-a", "TECNICO")));
    }

    @Test void altaConNombresEnBlancoONulosEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("   ", "usuario-a", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", null, "TECNICO")));
    }

    @Test void altaConUsuarioDeMasDe50Es422() {
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta("tecnico-a", "u".repeat(51), "TECNICO")));
    }

    @Test void altaConTecnicoDeMasDe100Es422() {
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta("t".repeat(101), "usuario-a", "TECNICO")));
    }

    @Test void altaConRolNoPermitidoEs422EnVezDe400() {
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "ADMIN")));
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "")));
    }

    /** Parando en la primera: con todo mal a la vez sale el primero de la lista, y así sucesivamente. */
    @Test void elOrdenEsCamposCincuentaCienRol() {
        String largo51 = "u".repeat(51);
        String largo101 = "t".repeat(101);
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", largo51, "ADMIN")));
        assertEquals("El nombre de usuario no puede superar 50 caracteres.", falla422(alta(largo101, largo51, "ADMIN")));
        assertEquals("El nombre del técnico no puede superar 100 caracteres.", falla422(alta(largo101, "usuario-a", "ADMIN")));
    }

    /** Los límites exactos pasan: 50 y 100 (tras el trim). */
    @Test void losLimitesExactosSonValidos() {
        String usuario50 = "u".repeat(50);
        String tecnico100 = "t".repeat(100);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" " + tecnico100 + " ", " " + usuario50 + " ", "TECNICO"), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(eq(tecnico100), eq(usuario50), anyString(), eq("TECNICO"));
    }

    // ── alta: trim guardado, rol por defecto, 201 con log ──
    /** La contraseña la genera el servidor, se guarda (marcada temporal por el DAO) y se devuelve una sola vez. */
    @Test void altaValidaGeneraLaTemporalLaDevuelveYRegistraLogSinElla() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("  tecnico-a ", " usuario-a  ", "SUPERTECNICO"), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        ArgumentCaptor<String> guardada = ArgumentCaptor.forClass(String.class);
        verify(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), guardada.capture(), eq("SUPERTECNICO"));
        assertEquals(10, guardada.getValue().length());
        assertEquals(new ValorTexto(guardada.getValue()), resp.getBody());
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: SUPERTECNICO, TECNICO: tecnico-a");
    }

    @Test void dosAltasRecibenTemporalesDistintas() {
        var r1 = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "TECNICO"), admin, null);
        var r2 = ctl.registrarTecnico(alta("tecnico-b", "usuario-b", "TECNICO"), admin, null);
        assertNotEquals(r1.getBody(), r2.getBody());
    }

    @Test void altaSinRolGuardaTecnico() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", null), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: TECNICO, TECNICO: tecnico-a");
    }

    // ── alta: los dos 409 de siempre ──
    @Test void tecnicoDuplicadoEs409SinEscribir() {
        when(dao.existeNombreTecnico("tecnico-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" tecnico-a ", "usuario-a", "TECNICO"), admin, null);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ya existe un técnico con ese nombre."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void usuarioDuplicadoEs409SinEscribir() {
        when(dao.existeNombreUsuario("usuario-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a ", "TECNICO"), admin, null);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void violacionDeIntegridadSigueSiendo409SinLog() {
        doThrow(new DataIntegrityViolationException("duplicado"))
                .when(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "TECNICO"), admin, null);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verifyNoInteractions(logDao);
    }

    // ── 404 de idTec inexistente (spec 6 §4.2) ──
    private static void noEncontrado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Técnico no encontrado.", e.getReason());
    }

    @Test void activarUnInexistenteEs404SinEscribir() {
        noEncontrado(() -> ctl.activarTecnico(99, admin));
        verify(dao, never()).activarTecnico(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void desactivarUnInexistenteEs404SinEscribir() {
        noEncontrado(() -> ctl.desactivarTecnico(99, admin));
        verify(dao, never()).desactivarTecnico(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void tieneReparacionesDeUnInexistenteEs404() {
        noEncontrado(() -> ctl.tieneReparaciones(99));
        verify(dao, never()).tieneReferencias(anyInt());
    }

    @Test void eliminarUnInexistenteEs404SinBorrar() {
        noEncontrado(() -> ctl.eliminarTecnico(99, 20, admin));
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }

    // ── activar / desactivar como hoy ──
    @Test void activarYDesactivarEscribenYRegistranLog() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        ctl.desactivarTecnico(7, admin);
        ctl.activarTecnico(7, admin);
        verify(dao).desactivarTecnico(7);
        verify(dao).activarTecnico(7);
        verify(logDao).insertar(1, "DESACTIVAR_USUARIO", "ID_TEC: 7, NOMBRE: tecnico-a");
        verify(logDao).insertar(1, "ACTIVAR_USUARIO", "ID_TEC: 7, NOMBRE: tecnico-a");
    }

    // ── tiene-reparaciones mira todas las referencias ──
    @Test void tieneReparacionesDevuelveTieneReferencias() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.tieneReferencias(7)).thenReturn(true);
        assertEquals(Map.of("value", true), ctl.tieneReparaciones(7));
        when(dao.tieneReferencias(7)).thenReturn(false);
        assertEquals(Map.of("value", false), ctl.tieneReparaciones(7));
    }

    // ── eliminar: 409 con referencias, idUsu resuelto e ignorado ──
    @Test void eliminarConReferenciasEs409SinBorrarNiRegistrar() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.tieneReferencias(7)).thenReturn(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.eliminarTecnico(7, 20, admin));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("\"tecnico-a\" tiene reparaciones asociadas.", e.getReason());
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarResuelveElUsuarioDesdeElTecnicoEIgnoraElDeLaQuery() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.eliminarTecnico(7, 999, admin);
        verify(dao).eliminarTecnico(7, 20);
        verify(logDao).insertar(1, "ELIMINAR_USUARIO", "ID_TEC: 7, ID_USU: 20, NOMBRE: tecnico-a");
    }

    @Test void eliminarSinIdUsuEnLaQueryTambienBorra() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.eliminarTecnico(7, null, admin);
        verify(dao).eliminarTecnico(7, 20);
    }

    /** Un Tecnico sin fila en Usuario no sale en la tabla (JOIN) y no se borra por aquí: 404. */
    @Test void eliminarUnTecnicoSinUsuarioEs404() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(null);   // un mock devuelve 0 para Integer si no se dice
        noEncontrado(() -> ctl.eliminarTecnico(7, 20, admin));
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }

    // ── el estado cacheado del usuario se olvida al cambiarlo (EstadoUsuarioService guarda 30 s) ──
    // Siempre DESPUÉS de escribir: si se olvidara antes, una petición de ese usuario entre medias volvería a
    // cachear el estado viejo durante otros 30 s.

    @Test void desactivarOlvidaElEstadoDelUsuarioDespuesDeEscribir() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.desactivarTecnico(7, admin);
        InOrder orden = inOrder(dao, estado);
        orden.verify(dao).desactivarTecnico(7);
        orden.verify(estado).invalidar(20);
    }

    /** Quien se reactiva puede operar en su siguiente petición, sin esperar a que caduque la caché. */
    @Test void activarOlvidaElEstadoDelUsuarioDespuesDeEscribir() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.activarTecnico(7, admin);
        InOrder orden = inOrder(dao, estado);
        orden.verify(dao).activarTecnico(7);
        orden.verify(estado).invalidar(20);
    }

    @Test void eliminarOlvidaElEstadoDelUsuarioDespuesDeBorrar() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.eliminarTecnico(7, null, admin);
        InOrder orden = inOrder(dao, estado);
        orden.verify(dao).eliminarTecnico(7, 20);
        orden.verify(estado).invalidar(20);
    }

    /** La marca de contraseña temporal surte efecto en la siguiente petición del usuario, no a los 30 s. */
    @Test void restablecerOlvidaElEstadoDelUsuarioDespuesDeEscribir() {
        when(dao.fijarPasswordTemporal(eq(8), anyString())).thenReturn(1);
        ctl.entregarPasswordTemporal(8, admin);
        InOrder orden = inOrder(dao, estado);
        orden.verify(dao).fijarPasswordTemporal(eq(8), anyString());
        orden.verify(estado).invalidar(8);
    }

    @Test void restablecerAUnInexistenteNoTocaLaCache() {
        when(dao.fijarPasswordTemporal(eq(8), anyString())).thenReturn(0);
        assertThrows(ResponseStatusException.class, () -> ctl.entregarPasswordTemporal(8, admin));
        verifyNoInteractions(estado);
    }

    @Test void losCuatro404NoTocanLaCache() {
        assertThrows(ResponseStatusException.class, () -> ctl.activarTecnico(99, admin));
        assertThrows(ResponseStatusException.class, () -> ctl.desactivarTecnico(99, admin));
        assertThrows(ResponseStatusException.class, () -> ctl.eliminarTecnico(99, null, admin));
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getIdUsuByIdTec(7)).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> ctl.eliminarTecnico(7, null, admin));
        verifyNoInteractions(estado);
    }

    /** Un técnico sin fila en Usuario no tiene sesión que olvidar: activar y desactivar siguen funcionando. */
    @Test void activarYDesactivarUnTecnicoSinUsuarioNoTocanLaCache() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getIdUsuByIdTec(7)).thenReturn(null);
        ctl.desactivarTecnico(7, admin);
        ctl.activarTecnico(7, admin);
        verify(dao).desactivarTecnico(7);
        verify(dao).activarTecnico(7);
        verifyNoInteractions(estado);
    }

}
