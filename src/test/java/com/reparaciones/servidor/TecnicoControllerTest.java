package com.reparaciones.servidor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TecnicoControllerTest extends BaseIntegrationTest {

    @Test
    void getTecnicos_conToken_devuelve200YLista() throws Exception {
        mockMvc.perform(get("/api/tecnicos")
                        .header("Authorization", "Bearer " + tokenTecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
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
