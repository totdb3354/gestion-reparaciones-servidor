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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/glass/prediccion (spec 3b §4.1): la glass automática del modal "Asignar trabajos" elige
 * al técnico de menor carga entre los habilitados para glass, con el mismo cálculo que
 * /reparaciones/carga-tecnicos (mismo patrón de test que {@link CargaTecnicosControllerTest}).
 */
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
class PrediccionGlassControllerTest {

    private static final String IMEI = "111111111111111";
    private static final String CUERPO = """
            {"imei":"111111111111111","conCliente":true,"verdes":[]}""";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean TecnicoDAO tecnicoDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private ReparacionResumen asig(String idRep, String imei, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null, null, 0, false, null, null, null);
        r.setCliente(cliente);
        return r;
    }

    private void dosHabilitados() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(
                new Tecnico(1, "Técnico A", true, false, true),
                new Tecnico(2, "Técnico B", true, false, true)));
    }

    private ResultActions predecir(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/api/glass/prediccion").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        predecir(tecnico(), CUERPO).andExpect(status().isForbidden());
        predecir(admin(), CUERPO).andExpect(status().isForbidden());
    }

    @Test void eligeAlDeMenorCarga() throws Exception {
        dosHabilitados();
        when(dao.getAsignacionesGlass(null)).thenReturn(List.of(asig("AG1", "222222222222222", 1, "CLI")));
        predecir(supertecnico(), CUERPO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTec").value(2))
                .andExpect(jsonPath("$.nombre").value("Técnico B"));
    }

    @Test void lasVerdesDelCuerpoCuentan() throws Exception {
        dosHabilitados();
        String cuerpo = """
                {"imei":"111111111111111","conCliente":true,"verdes":[
                  {"imei":"333333333333333","idTec":1,"tipo":"GLASS","esChasis":false,"conCliente":true}]}""";
        predecir(supertecnico(), cuerpo).andExpect(jsonPath("$.idTec").value(2));
    }

    @Test void sinCandidatoDevuelveNulos() throws Exception {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(new Tecnico(3, "Técnico C", true, false, false)));
        predecir(supertecnico(), CUERPO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTec").value(nullValue()))
                .andExpect(jsonPath("$.nombre").value(nullValue()));
    }

    @Test void siFallanLasCompletadasHoyCalculaSoloConLoAbierto() throws Exception {
        dosHabilitados();
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenThrow(new RuntimeException("BD caída"));
        when(dao.getAsignacionesGlass(null)).thenReturn(List.of(asig("AG1", "222222222222222", 1, "CLI")));
        predecir(supertecnico(), CUERPO).andExpect(status().isOk()).andExpect(jsonPath("$.idTec").value(2));
    }
}
