package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerTest extends BaseIntegrationTest {

    @Test
    void login_credencialesCorrectas_devuelve200ConToken() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usuario", "admin_test", "password", "test1234"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.nombreUsuario").value("admin_test"));
    }

    @Test
    void login_passwordIncorrecta_devuelve401() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usuario", "admin_test", "password", "wrong_pass"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_usuarioInexistente_devuelve401() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usuario", "no_existe", "password", "test1234"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_superTecnico_devuelveRolCorrecto() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usuario", "super_test", "password", "test1234"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPERTECNICO"))
                .andExpect(jsonPath("$.idTec").value(ID_TEC_SUPER));
    }
}
