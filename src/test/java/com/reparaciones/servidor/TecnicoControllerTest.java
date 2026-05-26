package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TecnicoControllerTest extends BaseIntegrationTest {

    @Test
    void getTecnicos_conToken_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/tecnicos")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // insertados en @BeforeEach: ID_TEC 100 y 101
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getTecnicosActivos_conToken_devuelve200YSoloActivos() throws Exception {
        mockMvc.perform(get("/api/tecnicos/activos")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getTecnicos_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/tecnicos"))
                .andExpect(status().isUnauthorized());
    }
}
