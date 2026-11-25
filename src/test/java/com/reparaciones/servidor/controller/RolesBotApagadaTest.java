package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.bot.Devoluciones;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin pedidos.bot.clave (aunque haya clave de vendidos) /api/bot/** esta apagada: 404 para todos. */
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
        "pedidos.bot.clave="
})
class RolesBotApagadaTest {

    @Autowired MockMvc mvc;
    @MockBean Devoluciones devoluciones;

    @Test void conElAjusteVacioEsUn404ParaTodos() throws Exception {
        mvc.perform(get("/api/bot/devoluciones")).andExpect(status().isNotFound());
        mvc.perform(get("/api/bot/devoluciones").header("X-API-Key", "clave-solo-lectura-inventada-000001")).andExpect(status().isNotFound());
        verifyNoInteractions(devoluciones);
    }
}
