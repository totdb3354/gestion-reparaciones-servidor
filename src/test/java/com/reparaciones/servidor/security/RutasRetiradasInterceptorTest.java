package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las rutas retiradas responden 403 y dejan constancia, y las vivas siguen pasando (spec sp7b §4.1). */
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
class RutasRetiradasInterceptorTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean LogDAO logDao;
    @MockBean ComponenteDAO componenteDao;

    private String tecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
    }

    @Test void rutaRetiradaEs403YSeAnota() throws Exception {
        mvc.perform(get("/api/componentes").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        verify(logDao).insertar(eq(8), eq(RutasRetiradas.ACCION_LOG), contains("GET /api/componentes"));
        verifyNoInteractions(componenteDao);
    }

    @Test void rutaRetiradaConVariableEs403() throws Exception {
        mvc.perform(get("/api/telefonos/355400000000111/exists").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
    }

    /**
     * Sin cabecera Authorization, la cadena de seguridad rechaza la petición antes de llegar al
     * interceptor (mismo 403 documentado en OpenApiContractTest, sin AuthenticationEntryPoint
     * propio); lo que distingue este caso es que el registro de actividad no se toca.
     */
    @Test void sinTokenLaRechazaLaSeguridadYNoSeAnota() throws Exception {
        mvc.perform(get("/api/componentes")).andExpect(status().isForbidden());
        verifyNoInteractions(logDao);
    }
}
