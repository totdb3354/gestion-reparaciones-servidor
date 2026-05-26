package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReparacionControllerTest extends BaseIntegrationTest {

    // ── GET historial / asignaciones ─────────────────────────────────────────

    @Test
    void getHistorial_conSuperTecnico_devuelve200() throws Exception {
        mockMvc.perform(get("/api/reparaciones/historial")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getHistorial_conAdmin_devuelve200() throws Exception {
        mockMvc.perform(get("/api/reparaciones/historial")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAsignaciones_conTecnico_devuelve200() throws Exception {
        mockMvc.perform(get("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── GET asignacion por ID ─────────────────────────────────────────────────

    @Test
    void getAsignacionById_idInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/reparaciones/asignaciones/ID_QUE_NO_EXISTE")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isNotFound());
    }

    // ── POST asignación ───────────────────────────────────────────────────────

    @Test
    void postAsignacion_superTecnico_devuelve201ConId() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_SUPER, "comentario", "Test"));
        mockMvc.perform(post("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenSuperTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(notNullValue()));
    }

    @Test
    void postAsignacion_tecnico_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_TEC, "comentario", "Test"));
        mockMvc.perform(post("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void postAsignacion_admin_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_SUPER, "comentario", "Test"));
        mockMvc.perform(post("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
