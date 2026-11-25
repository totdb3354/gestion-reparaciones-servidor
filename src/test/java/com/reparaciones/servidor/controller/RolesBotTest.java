package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.bot.Devoluciones;
import com.reparaciones.servidor.bot.InformeMovimientos;
import com.reparaciones.servidor.bot.ListaDevoluciones;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/bot/**: solo con la clave del bot (su propio ajuste); la de vendidos y los tokens de usuario no valen. */
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
        "pedidos.bot.clave=clave-del-bot-de-stock-inventada-01,corta"
})
class RolesBotTest {

    private static final String BOT = "clave-del-bot-de-stock-inventada-01";
    private static final String VENDIDOS = "clave-solo-lectura-inventada-000001";
    private static final String CUERPO = "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DESCONTADO\","
            + "\"resultado\":\"OK\",\"origen\":\"CAJA LISTOS\",\"destino\":\"Pedidos realizados\",\"motivo\":null,\"fecha\":\"2026-11-25T10:12:03\"}]}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean Devoluciones devoluciones;
    @MockBean FilasDAO filas;

    private String admin() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void conLaClaveDelBotLeeLaListaYGuarda() throws Exception {
        when(devoluciones.lista()).thenReturn(new ListaDevoluciones(List.of(new ListaDevoluciones.Grupo("CAJA LISTOS",
                List.of(new ListaDevoluciones.ImeiADevolver("350000000000001", "IMEI_CORREGIDO"))))));
        mvc.perform(get("/api/bot/devoluciones").header("X-API-Key", BOT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].destino").value("CAJA LISTOS"))
                .andExpect(jsonPath("$.grupos[0].imeis[0].motivo").value("IMEI_CORREGIDO"));
        when(devoluciones.registrar(any())).thenReturn(new InformeMovimientos(1, 0));
        mvc.perform(post("/api/bot/movimientos").header("X-API-Key", BOT).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guardados").value(1)).andExpect(jsonPath("$.repetidos").value(0));
    }

    @Test void sinClaveConOtraOConLaDeVendidosEs401() throws Exception {
        for (String clave : new String[] {null, "otra-clave", "corta", VENDIDOS}) {
            var g = get("/api/bot/devoluciones");
            var p = post("/api/bot/movimientos").contentType(MediaType.APPLICATION_JSON).content(CUERPO);
            if (clave != null) { g.header("X-API-Key", clave); p.header("X-API-Key", clave); }
            mvc.perform(g).andExpect(status().isUnauthorized());
            mvc.perform(p).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(devoluciones);
    }

    @Test void unTokenDeUsuarioNoVale() throws Exception {
        mvc.perform(get("/api/bot/devoluciones").header("Authorization", admin())).andExpect(status().isUnauthorized());
        verifyNoInteractions(devoluciones);
    }

    @Test void laClaveDelBotNoAbreOtrasRutas() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", BOT)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/filas").header("X-API-Key", BOT)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("X-API-Key", BOT)).andExpect(status().isForbidden());
    }

    @Test void unCuerpoMaloEs400() throws Exception {
        for (String cuerpo : List.of("{\"run\":\"\",\"movimientos\":[]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"VENDIDO\",\"resultado\":\"OK\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DEVUELTO\",\"resultado\":\"QUIZA\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"\",\"accion\":\"DEVUELTO\",\"resultado\":\"OK\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DEVUELTO\",\"resultado\":\"OK\"}]}"))
            mvc.perform(post("/api/bot/movimientos").header("X-API-Key", BOT).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(devoluciones);
    }
}
