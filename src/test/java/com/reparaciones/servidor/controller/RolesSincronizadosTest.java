package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.pedidos.VistaFilas;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/** POST /api/pedidos/sincronizados: la clave de ESCRITURA (su propio ajuste) frente a la de solo lectura de los vendidos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-solo-lectura-inventada-000001,clave-del-plugin-inventada-0000002",
        "pedidos.sincronizados.clave=clave-del-plugin-inventada-0000002,corta"
})
class RolesSincronizadosTest {

    private static final String ESCRITURA = "clave-del-plugin-inventada-0000002";
    private static final String SOLO_LECTURA = "clave-solo-lectura-inventada-000001";
    private static final String CUERPO = "{\"canal\":\"PARTICULARES\",\"pedidos\":[{\"pedido\":\"37352\",\"imeis\":[\"350000000000001\"]}]}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean FilasDAO dao;
    @MockBean VistaFilas vista;

    private String admin() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private MockHttpServletRequestBuilder aviso(String clave) {
        MockHttpServletRequestBuilder b = post("/api/pedidos/sincronizados").contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        return clave == null ? b : b.header("X-API-Key", clave);
    }

    @Test void conLaClaveDeEscrituraDevuelveLosRecuentos() throws Exception {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(2, 1, 3));
        mvc.perform(aviso(ESCRITURA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.filasSincronizadas").value(2))
                .andExpect(jsonPath("$.marcasQuitadas").value(1))
                .andExpect(jsonPath("$.sinFila").value(3));
        verify(dao).sincronizar(any(), any(), any());
    }

    @Test void laClaveSoloDeVendidosNoEscribeEs401() throws Exception {
        mvc.perform(aviso(SOLO_LECTURA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    @Test void sinClaveOConClaveMalaOCortaEs401() throws Exception {
        mvc.perform(aviso(null)).andExpect(status().isUnauthorized());
        mvc.perform(aviso("otra-clave")).andExpect(status().isUnauthorized());
        mvc.perform(aviso("corta")).andExpect(status().isUnauthorized());
        mvc.perform(aviso("")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    @Test void unTokenDeUsuarioNoVale() throws Exception {
        mvc.perform(aviso(null).header("Authorization", admin())).andExpect(status().isUnauthorized());
        mvc.perform(aviso("otra-clave").header("Authorization", admin())).andExpect(status().isUnauthorized());
        verifyNoInteractions(dao);
    }

    @Test void laClaveDeEscrituraNoAbreNingunaOtraRutaSalvoLaDeVendidosQueTambienTiene() throws Exception {
        mvc.perform(get("/api/pedidos/filas").header("X-API-Key", ESCRITURA)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/historico").header("X-API-Key", ESCRITURA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/sincronizados/otra").header("X-API-Key", ESCRITURA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("X-API-Key", ESCRITURA)).andExpect(status().isForbidden());
        verifyNoInteractions(dao, vista);
        when(dao.vendidosDesde(any(), any(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", ESCRITURA)).andExpect(status().isOk());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", SOLO_LECTURA)).andExpect(status().isOk());
    }

    @Test void conLaClaveDeEscrituraSoloSePuedeHacerPost() throws Exception {
        mvc.perform(get("/api/pedidos/sincronizados").header("X-API-Key", ESCRITURA)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(dao);
    }

    @Test void canalNoWebOImeiMaloEs400() throws Exception {
        for (String cuerpo : List.of("{\"canal\":\"AMAZON\",\"pedidos\":[]}",
                "{\"canal\":\"PARTICULARES\",\"pedidos\":[{\"pedido\":\"1\",\"imeis\":[\"123\"]}]}"))
            mvc.perform(post("/api/pedidos/sincronizados").header("X-API-Key", ESCRITURA)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        verifyNoInteractions(dao);
    }
}
