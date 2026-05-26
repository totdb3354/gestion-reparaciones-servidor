package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PulidoControllerTest extends BaseIntegrationTest {

    @Test
    void getAsignaciones_conSuperTecnico_devuelve200() throws Exception {
        mockMvc.perform(get("/api/pulidos/asignaciones")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getHistorial_conAdmin_devuelve200() throws Exception {
        mockMvc.perform(get("/api/pulidos/historial")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void postAsignacion_superTecnico_devuelve201ConId() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_SUPER, "comentario", "Pulido test"));
        mockMvc.perform(post("/api/pulidos/asignaciones")
                        .header("Authorization", "Bearer " + tokenSuperTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(notNullValue()));
    }

    @Test
    void postAsignacion_tecnico_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_TEC, "comentario", ""));
        mockMvc.perform(post("/api/pulidos/asignaciones")
                        .header("Authorization", "Bearer " + tokenTecnico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void postAsignacion_admin_devuelve403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("imei", "999000000000000", "idTec", ID_TEC_SUPER, "comentario", ""));
        mockMvc.perform(post("/api/pulidos/asignaciones")
                        .header("Authorization", "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAsignacion_idInexistente_superTecnico_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/pulidos/asignaciones/ID_NO_EXISTE")
                        .header("Authorization", "Bearer " + tokenSuperTecnico()))
                .andExpect(status().isNoContent());
    }
}
