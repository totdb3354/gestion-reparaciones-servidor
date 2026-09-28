package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo el supertécnico borra teléfonos, y el borrado queda registrado (spec sp7b §4.4 y §5.7). */
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
class TelefonoControllerEliminarTest {

    private static final String IMEI = "355400000000111";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO dao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }

    @Test void unTecnicoNoBorraTelefonos() throws Exception {
        mvc.perform(delete("/api/telefonos/" + IMEI).header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        verify(dao, never()).eliminar(IMEI);
    }

    @Test void elSupertecnicoBorraYQuedaRegistrado() throws Exception {
        mvc.perform(delete("/api/telefonos/" + IMEI).header("Authorization", supertecnico()))
           .andExpect(status().isNoContent());
        verify(dao).eliminar(IMEI);
        verify(logDao).insertar(eq(7), eq("ELIMINAR_TELEFONO"), contains(IMEI));
    }
}
