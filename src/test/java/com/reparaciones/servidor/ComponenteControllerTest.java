package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ComponenteControllerTest extends BaseIntegrationTest {

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void getGestionados_conToken_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/componentes/gestionados")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // 'Pantalla Test' insertada en data-test.sql
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getAll_conToken_devuelve200() throws Exception {
        mockMvc.perform(get("/api/componentes")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void postComponente_superTecnico_devuelve201() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tipo", "Batería Test", "stock", 5, "stockMinimo", 2));
        mockMvc.perform(post("/api/componentes")
                        .header("Authorization", "Bearer " + tokenSuperTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void postComponente_tecnico_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tipo", "Batería Test 2", "stock", 5, "stockMinimo", 2));
        mockMvc.perform(post("/api/componentes")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void postComponente_admin_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tipo", "Batería Test 3", "stock", 5, "stockMinimo", 2));
        mockMvc.perform(post("/api/componentes")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── PATCH stock ───────────────────────────────────────────────────────────

    @Test
    void patchStock_componente1_devuelve200() throws Exception {
        // ID_COM=1 ('Pantalla Test') insertado en data-test.sql
        String body = objectMapper.writeValueAsString(Map.of("delta", 1));
        mockMvc.perform(patch("/api/componentes/1/stock")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
