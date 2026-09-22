package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** GET /api/reparaciones/carga-tecnicos (spec 3a §5): la carga diaria por técnico que antes
 *  calculaba el cliente JavaFX, con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class CargaTecnicosControllerTest {

    private static final String IMEI_1 = "000000000000001";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean TecnicoDAO tecnicoDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    /** Asignación abierta con los campos que usa el cálculo (mismo helper que CargaTecnicosTest). */
    private ReparacionResumen asig(String idRep, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, IMEI_1, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null, null, 0, false, null, null, null);
        r.setCliente(cliente);
        return r;
    }

    private void mockearTecnicosActivos() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(
                new Tecnico(1, "T1", true, false, false),
                new Tecnico(2, "T2", true, false, false)));
    }

    @Test void supertecnicoRecibeLosDosAlcances() throws Exception {
        mockearTecnicosActivos();
        when(dao.getAsignaciones(null)).thenReturn(List.of(asig("A_1", 1, "CLI")));
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(List.of());

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.pedidos.length()").value(2))
                .andExpect(jsonPath("$.total.length()").value(2));
    }

    @Test void adminTambienPuedeConsultarla() throws Exception {
        mockearTecnicosActivos();
        when(dao.getAsignaciones(null)).thenReturn(List.of());
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(List.of());

        int status = mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", admin()))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    @Test void tecnicoRecibe403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", tecnico()))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test void siFallanLasCompletadasHoyDegradaASoloPendiente() throws Exception {
        mockearTecnicosActivos();
        when(dao.getAsignaciones(null)).thenReturn(List.of(asig("A_1", 1, "CLI")));
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenThrow(new RuntimeException("BD caída"));

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.pedidos[*].pctHecho", everyItem(is(0.0))))
                .andExpect(jsonPath("$.total[*].pctHecho", everyItem(is(0.0))));
    }
}
