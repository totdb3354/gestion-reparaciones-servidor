package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SeguridadTest extends BaseIntegrationTest {

    // ── 401 sin token ─────────────────────────────────────────────────────────

    @Test
    void sinToken_tecnicosGet_devuelve401() throws Exception {
        mockMvc.perform(get("/api/tecnicos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_reparacionesHistorial_devuelve401() throws Exception {
        mockMvc.perform(get("/api/reparaciones/historial"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_componentesGet_devuelve401() throws Exception {
        mockMvc.perform(get("/api/componentes"))
                .andExpect(status().isUnauthorized());
    }

    // ── 403 TECNICO intenta endpoints de SUPERTECNICO ─────────────────────────

    @Test
    void tecnico_postAsignacion_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_TEC, "comentario", ""));
        mockMvc.perform(post("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void tecnico_postComponente_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tipo", "Componente X", "stock", 5, "stockMinimo", 1));
        mockMvc.perform(post("/api/componentes")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void tecnico_postPulidoAsignacion_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_TEC, "comentario", ""));
        mockMvc.perform(post("/api/pulidos/asignaciones")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── 403 ADMIN intenta endpoints de SUPERTECNICO ───────────────────────────

    @Test
    void admin_postAsignacion_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_SUPER, "comentario", ""));
        mockMvc.perform(post("/api/reparaciones/asignaciones")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_postComponente_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tipo", "Componente Y", "stock", 3, "stockMinimo", 1));
        mockMvc.perform(post("/api/componentes")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── 403 SUPERTECNICO / TECNICO intentan endpoints solo de ADMIN ───────────

    @Test
    void superTecnico_getUsuariosTecnicos_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tecnico_getUsuariosTecnicos_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuarios/tecnicos")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isForbidden());
    }
}
