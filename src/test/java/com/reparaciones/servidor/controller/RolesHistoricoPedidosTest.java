package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.CargaHistorico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La carga del histórico de la hoja de pedidos es solo de ADMIN: TECNICO y SUPERTECNICO reciben 403 sin llegar al servicio. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000"
})
class RolesHistoricoPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean CargaHistorico carga;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void tecnicoYSupertecnicoRecibenElTresCientosTresSinLlegarAlServicio() throws Exception {
        for (String token : java.util.List.of(tecnico(), supertecnico())) {
            mvc.perform(post("/api/pedidos/historico").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/pedidos/historico").header("Authorization", token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(carga);
    }

    @Test void sinSesionNoSePuede() throws Exception {
        mvc.perform(post("/api/pedidos/historico")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/pedidos/historico")).andExpect(status().is4xxClientError());
        verifyNoInteractions(carga);
    }

    @Test void adminArrancaLaCargaConUn202YLeeElEstado() throws Exception {
        var enCurso = new CargaHistorico.EstadoCarga(CargaHistorico.Estado.EN_CURSO, LocalDateTime.of(2026, 11, 11, 10, 0), null, null, null);
        when(carga.iniciar()).thenReturn(enCurso);
        when(carga.estado()).thenReturn(enCurso);
        mvc.perform(post("/api/pedidos/historico").header("Authorization", admin()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("EN_CURSO"));
        mvc.perform(get("/api/pedidos/historico").header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_CURSO"));
    }

    @Test void adminVeUn409ConOtraEnMarchaYUn400SinElAjuste() throws Exception {
        when(carga.iniciar()).thenThrow(new CargaHistorico.YaEnCurso("Ya hay una carga del histórico en marcha"))
                .thenThrow(new CargaHistorico.NoConfigurada("No está configurada la hoja del histórico (pedidos.historico.hoja)"));
        mvc.perform(post("/api/pedidos/historico").header("Authorization", admin())).andExpect(status().isConflict());
        mvc.perform(post("/api/pedidos/historico").header("Authorization", admin())).andExpect(status().isBadRequest());
    }
}
