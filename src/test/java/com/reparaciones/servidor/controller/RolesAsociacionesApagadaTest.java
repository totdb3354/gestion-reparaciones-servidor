package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.AsociacionAmazon;
import com.reparaciones.servidor.pedidos.EstadoAsociaciones;
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

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin pedidos.asociaciones.clave (aunque haya otras claves de maquina) el POST esta apagado: 404 para todos. El GET sigue con su token. */
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
        "pedidos.sincronizados.clave=clave-del-plugin-inventada-0000002",
        "pedidos.asociaciones.clave="
})
class RolesAsociacionesApagadaTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean AsociacionAmazon asociacion;

    @Test void conElAjusteVacioElPostEsUn404ParaTodosYElGetSigue() throws Exception {
        String admin = "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        String cuerpo = "{\"canal\":\"AMAZON\",\"pares\":[]}";
        mvc.perform(post("/api/pedidos/asociaciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        mvc.perform(post("/api/pedidos/asociaciones").header("X-API-Key", "clave-del-plugin-inventada-0000002")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        mvc.perform(post("/api/pedidos/asociaciones").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        when(asociacion.estado()).thenReturn(new EstadoAsociaciones(List.of()));
        mvc.perform(get("/api/pedidos/asociaciones").header("Authorization", admin)).andExpect(status().isOk());
        verify(asociacion).estado();
        verifyNoMoreInteractions(asociacion);
    }
}
