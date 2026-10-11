package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReparacionControllerCorregirClienteTest {

    private static final String IMEI = "355400000000111";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class),
            mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class));
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    private int statusDe(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void corrigeYDejaUnaLineaPorTrabajoConAnteriorYNuevo() {
        Map<String, Integer> antes = new HashMap<>();
        antes.put("R20261002_1", 19);
        antes.put("A20261002_9", null);
        when(dao.existeCliente(7)).thenReturn(true);
        when(dao.getClienteGuardado(List.of("R20261002_1", "A20261002_9"))).thenReturn(antes);
        ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1", "A20261002_9"), 7), admin);
        verify(dao).corregirCliente(IMEI, List.of("R20261002_1", "A20261002_9"), 7);
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: R20261002_1, IMEI: " + IMEI + ", ID_CLI: 19 → 7");
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: A20261002_9, IMEI: " + IMEI + ", ID_CLI: — → 7");
    }

    @Test void aSinClienteSeApuntaConGuion() {
        when(dao.getClienteGuardado(List.of("R20261002_1"))).thenReturn(Map.of("R20261002_1", 19));
        ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1"), null), admin);
        verify(dao).corregirCliente(IMEI, List.of("R20261002_1"), null);
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: R20261002_1, IMEI: " + IMEI + ", ID_CLI: 19 → —");
    }

    @Test void listaVaciaOClienteInexistenteEs400SinTocarNada() {
        assertEquals(400, statusDe(() -> ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of(), 7), admin)));
        when(dao.existeCliente(99)).thenReturn(false);
        assertEquals(400, statusDe(() -> ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1"), 99), admin)));
        verify(dao, never()).corregirCliente(anyString(), anyList(), any());
        verifyNoInteractions(logDao);
    }

    @Test void soloSupertecnicoYAdmin() throws Exception {
        for (String metodo : List.of("getCerradosPorImei", "corregirCliente")) {
            PreAuthorize p = java.util.Arrays.stream(ReparacionController.class.getMethods())
                    .filter(m -> m.getName().equals(metodo)).findFirst().orElseThrow().getAnnotation(PreAuthorize.class);
            assertEquals("hasAnyRole('SUPERTECNICO','ADMIN')", p.value(), metodo);
        }
    }
}
