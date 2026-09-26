package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST y DELETE /api/tecnicos (spec 6 §4.3): solo ADMIN, con la cadena de seguridad real. Hasta el sub-proyecto 6
 *  no tenían @PreAuthorize y cualquier sesión podía crear o borrar un Tecnico. */
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
class RolesUsuarioTecnicoTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TecnicoDAO tecnicoDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "usuario-b", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null)); }

    private static final String CUERPO = """
            {"nombre":"tecnico-a"}""";

    @Test void crearTecnicoYSupertecnicoReciben403() throws Exception {
        for (String token : new String[] { tecnico(), supertecnico() }) {
            mvc.perform(post("/api/tecnicos").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
        }
        verify(tecnicoDao, never()).insertar(anyString());
    }

    @Test void crearAdminEs201() throws Exception {
        mvc.perform(post("/api/tecnicos").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated());
        verify(tecnicoDao).insertar("tecnico-a");
    }

    @Test void borrarTecnicoYSupertecnicoReciben403() throws Exception {
        for (String token : new String[] { tecnico(), supertecnico() }) {
            mvc.perform(delete("/api/tecnicos/9").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        verify(tecnicoDao, never()).eliminar(anyInt());
    }

    @Test void borrarAdminEs204() throws Exception {
        when(tecnicoDao.getNombreById(9)).thenReturn("tecnico-a");
        mvc.perform(delete("/api/tecnicos/9").header("Authorization", admin()))
                .andExpect(status().isNoContent());
        verify(tecnicoDao).eliminar(9);
    }
}
