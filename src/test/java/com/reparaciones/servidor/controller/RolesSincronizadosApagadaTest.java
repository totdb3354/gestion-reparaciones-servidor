package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin pedidos.sincronizados.clave (aunque haya clave de vendidos) la ruta de escritura esta apagada: 404 para todos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-solo-lectura-inventada-000001",
        "pedidos.sincronizados.clave="
})
class RolesSincronizadosApagadaTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean FilasDAO dao;

    @Test void conElAjusteVacioEsUn404ParaTodos() throws Exception {
        String admin = "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        String cuerpo = "{\"canal\":\"PARTICULARES\",\"pedidos\":[]}";
        mvc.perform(post("/api/pedidos/sincronizados").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        mvc.perform(post("/api/pedidos/sincronizados").header("X-API-Key", "clave-solo-lectura-inventada-000001")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        mvc.perform(post("/api/pedidos/sincronizados").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        verifyNoInteractions(dao);
    }
}
