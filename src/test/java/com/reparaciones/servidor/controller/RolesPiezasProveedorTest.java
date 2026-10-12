package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT de las piezas de un proveedor (0.9.9): solo SUPERTECNICO, con la cadena de seguridad real y el servicio mockeado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesPiezasProveedorTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ProveedorPiezasService servicio;
    @MockBean LogDAO logDao;

    private String token(String rol, Integer idTec) {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "u", "", rol, idTec));
    }

    private ResultActions put(String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/proveedores/5/componentes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idsCom\":[1,2]}"));
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        put(token("TECNICO", 4)).andExpect(status().isForbidden());
        put(token("ADMIN", null)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test void elSupertecnicoGuardaY204() throws Exception {
        when(servicio.reemplazar(anyInt(), any())).thenReturn(new ProveedorPiezasService.Cambios(2, 0));
        put(token("SUPERTECNICO", 3)).andExpect(status().isNoContent());
        verify(servicio).reemplazar(5, List.of(1, 2));
    }
}
