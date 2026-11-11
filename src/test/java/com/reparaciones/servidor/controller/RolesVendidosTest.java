package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.pedidos.Vendido;
import com.reparaciones.servidor.pedidos.VistaFilas;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/pedidos/vendidos con la clave de maquina puesta: solo la clave la abre, y la clave no abre nada mas (cadena de seguridad real). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-de-prueba-inventada"
})
class RolesVendidosTest {

    private static final String CLAVE = "clave-de-prueba-inventada";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean FilasDAO dao;
    @MockBean VistaFilas vista;

    private String admin() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void conLaClaveBuenaDevuelveLaListaEnLaRaiz() throws Exception {
        when(dao.vendidosDesde(any(), anyInt())).thenReturn(List.of(new Vendido("350000000000001", "i13128AzulA", "AMZ", "AMAZON",
                "402-0000000-0000001", LocalDateTime.of(2026, 11, 11, 9, 30))));
        mvc.perform(get("/api/pedidos/vendidos?dias=3").header("X-API-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imei").value("350000000000001"))
                .andExpect(jsonPath("$[0].sku").value("i13128AzulA"))
                .andExpect(jsonPath("$[0].plataforma").value("AMZ"))
                .andExpect(jsonPath("$[0].canal").value("AMAZON"))
                .andExpect(jsonPath("$[0].pedido").value("402-0000000-0000001"))
                .andExpect(jsonPath("$[0].fechaImei").exists());
        verify(dao).vendidosDesde(any(), anyInt());
    }

    @Test void sinClaveOConOtraClaveEsUn401SinTocarLaBase() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "otra-clave")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", "")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", CLAVE + "x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", CLAVE.substring(1))).andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    @Test void unTokenDeAdminSinClaveEsUn401() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos").header("Authorization", admin())).andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    @Test void unTokenDeAdminConUnaClaveMalaTambienEsUn401() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos").header("Authorization", admin()).header("X-API-Key", "otra-clave"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    /** Sin sesion el resto de la API da 403 (como antes de esta ruta); la clave no cambia nada. */
    @Test void laClaveNoAbreNingunaOtraRuta() throws Exception {
        mvc.perform(get("/api/pedidos/filas").header("X-API-Key", CLAVE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/fuentes").header("X-API-Key", CLAVE)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/historico").header("X-API-Key", CLAVE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/vendidos/otra").header("X-API-Key", CLAVE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("X-API-Key", CLAVE)).andExpect(status().isForbidden());
        verifyNoInteractions(dao, vista);
    }

    @Test void diasFueraDeRangoOQueNoEsNumeroEsUn400() throws Exception {
        for (String dias : List.of("0", "32", "-1", "abc"))
            mvc.perform(get("/api/pedidos/vendidos?dias=" + dias).header("X-API-Key", CLAVE)).andExpect(status().isBadRequest());
        verifyNoInteractions(dao);
    }

    @Test void diasPorDefectoSiete() throws Exception {
        when(dao.vendidosDesde(any(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", CLAVE)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/pedidos/vendidos?dias=1").header("X-API-Key", CLAVE)).andExpect(status().isOk());
        mvc.perform(get("/api/pedidos/vendidos?dias=31").header("X-API-Key", CLAVE)).andExpect(status().isOk());
    }

    @Test void soloSeLee() throws Exception {
        mvc.perform(post("/api/pedidos/vendidos").header("X-API-Key", CLAVE)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(dao);
    }
}
