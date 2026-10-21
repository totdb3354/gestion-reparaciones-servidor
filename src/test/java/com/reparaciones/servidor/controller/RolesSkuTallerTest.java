package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.nsys.FilaPreviaTaller;
import com.reparaciones.servidor.nsys.PeticionPreviaTaller;
import com.reparaciones.servidor.nsys.PreviaTaller;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** La previsualización del SKU del taller (spec 0.9.21 pieza 2 §2.4): es el formulario del técnico, así que la piden técnico,
 *  supertécnico y admin; sin sesión, 403. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesSkuTallerTest {

    private static final String RUTA = "/api/reparaciones/sku-taller/previsualizar";
    private static final String CUERPO =
            "{\"imei\":\"350000000000001\",\"filas\":[{\"idRep\":null,\"idCom\":131,\"reutilizada\":false}]}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TallerReparaciones taller;

    private String token(String rol, Integer idTec) {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(9, "usuario_prueba", "", rol, idTec));
    }

    @Test void tecnicoSupertecnicoYAdminPrevisualizan() throws Exception {
        when(taller.previsualizar(any())).thenReturn(PreviaTaller.FUERA_DE_NSYS);
        for (String auth : List.of(token("TECNICO", 4), token("SUPERTECNICO", 3), token("ADMIN", null))) {
            MockHttpServletResponse res = mvc.perform(post(RUTA).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andReturn().getResponse();
            assertEquals(200, res.getStatus(), auth);
            assertTrue(res.getContentAsString().contains("\"enNsys\":false"), res.getContentAsString());
        }
        verify(taller, times(3)).previsualizar(new PeticionPreviaTaller("350000000000001",
                List.of(new FilaPreviaTaller(null, 131, false))));
    }

    @Test void sinSesionEs403SinLlegarAlServicio() throws Exception {
        int status = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(taller);
    }
}
