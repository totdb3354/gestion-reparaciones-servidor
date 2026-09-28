package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un usuario que ya no puede operar recibe 401, que es lo que expulsa en los dos clientes (spec sp7b §5.1 y D4). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class JwtAuthFilterEstadoTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean EstadoUsuarioService estado;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;
    @MockBean AuthenticationManager authManager;

    private String token() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
    }

    @Test void unUsuarioOperativoPasa() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isOk());
    }

    @Test void unUsuarioDesactivadoOBorradoEs401NoEs403() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isUnauthorized());
    }

    /** El inicio de sesión no pasa por el filtro: aunque el usuario diera negativo en la comprobación,
     *  la petición llega al controlador sin bloquearse, y es el propio login el que decide su 401. */
    @Test void elLoginNoPasaPorLaComprobacion() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("credenciales incorrectas"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x\",\"password\":\"y\"}"))
           .andExpect(status().isUnauthorized());
    }
}
