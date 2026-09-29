package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo el administrador entrega una contraseña temporal, y queda registrado (spec sp7b §5.4). */
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
class UsuarioControllerResetPasswordTest {

    private static final String RUTA = "/api/usuarios/8/password-temporal";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean UsuarioDAO dao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(9, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void unTecnicoNoRestableceContrasenasAjenas() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verify(dao, never()).fijarPasswordTemporal(anyInt(), anyString());
    }

    @Test void unSupertecnicoTampoco() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", supertecnico())).andExpect(status().isForbidden());
    }

    @Test void elAdminRecibeUnaContrasenaYQuedaRegistrado() throws Exception {
        when(dao.fijarPasswordTemporal(eq(8), anyString())).thenReturn(1);
        mvc.perform(post(RUTA).header("Authorization", admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.value").isString());
        verify(dao).fijarPasswordTemporal(eq(8), anyString());
        verify(logDao).insertar(eq(1), eq("RESTABLECER_PASSWORD"), contains("ID_USU: 8"));
    }

    /** La contraseña entregada no se repite entre llamadas. */
    @Test void cadaLlamadaEntregaUnaDistinta() throws Exception {
        when(dao.fijarPasswordTemporal(eq(8), anyString())).thenReturn(1);
        String una = mvc.perform(post(RUTA).header("Authorization", admin()))
                .andReturn().getResponse().getContentAsString();
        String otra = mvc.perform(post(RUTA).header("Authorization", admin()))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(una, otra);
    }

    /** Si ese usuario no existe, la actualización no afecta a ninguna fila: 404 y sin registrar nada. */
    @Test void unUsuarioInexistenteEs404YNoRegistraNada() throws Exception {
        when(dao.fijarPasswordTemporal(eq(8), anyString())).thenReturn(0);
        mvc.perform(post(RUTA).header("Authorization", admin())).andExpect(status().isNotFound());
        verify(logDao, never()).insertar(anyInt(), anyString(), anyString());
    }
}
