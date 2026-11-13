package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.FilasDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El ajuste admite varias claves separadas por comas (con espacios): cada una abre; las de menos de 32 caracteres se ignoran. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-de-prueba-uno-000000000001, clave-de-prueba-dos-000000000002 ,corta-de-prueba"
})
class RolesVendidosVariasClavesTest {

    @Autowired MockMvc mvc;
    @MockBean FilasDAO dao;

    @Test void cadaClaveValidaDa200() throws Exception {
        when(dao.vendidosDesde(any(), any(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "clave-de-prueba-uno-000000000001")).andExpect(status().isOk());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "clave-de-prueba-dos-000000000002")).andExpect(status().isOk());
    }

    @Test void laCortaSeIgnoraYLaListaEnteraNoEsUnaClave() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "corta-de-prueba")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key",
                "clave-de-prueba-uno-000000000001,clave-de-prueba-dos-000000000002")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "otra")).andExpect(status().isUnauthorized());
    }
}
