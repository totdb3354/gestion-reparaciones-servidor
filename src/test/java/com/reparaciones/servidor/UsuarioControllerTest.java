package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UsuarioControllerTest extends BaseIntegrationTest {

    @Test
    void getUsuariosTecnicos_admin_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getUsuariosTecnicos_superTecnico_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsuariosTecnicos_tecnico_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarTecnico_admin_devuelve201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nombreTecnico", "Técnico Nuevo Test",
                "nombreUsuario", "nuevo_usuario_test",
                "password",      "test1234",
                "rol",           "TECNICO"));
        mockMvc.perform(post("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void registrarTecnico_nombreUsuarioDuplicado_devuelve409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nombreTecnico", "Otro Técnico",
                "nombreUsuario", "admin_test",
                "password",      "test1234",
                "rol",           "TECNICO"));
        mockMvc.perform(post("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void registrarTecnico_superTecnico_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nombreTecnico", "Técnico X",
                "nombreUsuario", "usuario_x",
                "password",      "test1234",
                "rol",           "TECNICO"));
        mockMvc.perform(post("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenSuperTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarTecnico_admin_devuelve204() throws Exception {
        mockMvc.perform(patch("/api/usuarios/tecnicos/" + ID_TEC_TEC + "/desactivar")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());
    }
}
