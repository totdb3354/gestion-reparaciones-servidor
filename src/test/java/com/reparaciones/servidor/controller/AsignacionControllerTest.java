package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.AsignacionLoteService;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsignacionControllerTest {
    private static final String IMEI = "111111111111111";
    private static final String CLAVE = "clave-1";
    private final AsignacionLoteService servicio = mock(AsignacionLoteService.class);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TelefonoDAO telefonoDao = mock(TelefonoDAO.class);
    private final TecnicoDAO tecnicoDao = mock(TecnicoDAO.class);
    private final ImeiLookupService lookup = mock(ImeiLookupService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AsignacionController controller = new AsignacionController(
            servicio, dao, telefonoDao, tecnicoDao, lookup, logDao, new RegistroIdempotencia());
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    AsignacionControllerTest() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(new Tecnico(3, "Técnico A", true, false, false)));
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new Respuesta(List.of(), List.of()));
    }

    private static Peticion rep(String modelo) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, modelo, null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, false)));
    }

    private static Peticion pulido(String modelo) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, modelo, null, false)),
                List.of(new AsignacionDelLote(IMEI, "P", 3, null, false)));
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void sinClaveEs400() {
        assertEquals(400, estado(() -> controller.guardarLote(rep("13pro"), super_, null)));
        assertEquals(400, estado(() -> controller.guardarLote(rep("13pro"), super_, "  ")));
        verifyNoInteractions(servicio);
    }

    @Test void elReintentoConLaMismaClaveNoRepiteLaEscritura() {
        Respuesta primera = controller.guardarLote(rep("13pro"), super_, CLAVE);
        Respuesta segunda = controller.guardarLote(rep("13pro"), super_, CLAVE);
        assertSame(primera, segunda);
        verify(servicio, times(1)).guardar(any(), eq(3), eq(7));
    }

    @Test void validaciones422() {
        assertEquals(422, estado(() -> controller.guardarLote(rep(""), super_, CLAVE)));            // R sin modelo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "X", 3, null, false))), super_, CLAVE)));      // categoría
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, null, 3, null, false))), super_, CLAVE)));     // categoría null
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 99, null, false))), super_, CLAVE)));     // técnico no activo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote("123", "13pro", null, false)),
                List.of(new AsignacionDelLote("123", "R", 3, null, false))), super_, CLAVE)));     // IMEI
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(), List.of(new AsignacionDelLote(IMEI, "R", 3, null, false))), super_, CLAVE))); // sin teléfono
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(List.of(), List.of()), super_, CLAVE))); // vacío
        verifyNoInteractions(servicio);
    }

    @Test void validacionesDeTelefonosYElementosNulos422() {
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                Arrays.asList(new TelefonoDelLote(IMEI, "13pro", null, false), null),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, false))), super_, CLAVE)));     // teléfono nulo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false), new TelefonoDelLote(null, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, false))), super_, CLAVE)));     // IMEI de teléfono nulo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false), new TelefonoDelLote("123", "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, false))), super_, CLAVE)));     // IMEI de teléfono mal formado
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                Arrays.asList(new AsignacionDelLote(IMEI, "R", 3, null, false), null)), super_, CLAVE))); // asignación nula
        verifyNoInteractions(servicio);
    }

    @Test void pulidoSinModeloEnBdHaceElLookupYLoMandaAlServicio() {
        when(telefonoDao.getModelo(IMEI)).thenReturn(null);
        when(lookup.lookupModeloInterno(IMEI)).thenReturn("12");
        controller.guardarLote(pulido(null), super_, CLAVE);
        ArgumentCaptor<Peticion> enviada = ArgumentCaptor.forClass(Peticion.class);
        verify(servicio).guardar(enviada.capture(), eq(3), eq(7));
        assertEquals("12", enviada.getValue().telefonos().get(0).modelo());
    }

    @Test void sinLookupSiLaBdYaTieneModeloOSiHayReparacion() {
        when(telefonoDao.getModelo(IMEI)).thenReturn("12");
        controller.guardarLote(pulido(null), super_, "clave-a");
        controller.guardarLote(rep("13pro"), super_, "clave-b");
        verifyNoInteractions(lookup);
    }

    @Test void logsComoLosEndpointsSueltos() {
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new Respuesta(
                List.of(new Creada("A1", IMEI, 3, "R")), List.of()));
        when(dao.getModeloByImei(IMEI)).thenReturn("13pro");
        when(dao.getNombreTecnicoById(3)).thenReturn("Técnico A");
        controller.guardarLote(new Peticion(List.of(new TelefonoDelLote(IMEI, "13pro", 12, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, true))), super_, CLAVE);
        verify(logDao).insertar(7, "ASIGNAR_CLIENTE", "IMEI: " + IMEI + ", ID_CLI: 12");
        verify(logDao).insertar(7, "CREAR_ASIGNACION",
                "ID_REP: A1, IMEI: " + IMEI + ", MODELO: 13pro, TECNICO: Técnico A, CHASIS: true");
    }
}
