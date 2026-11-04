package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.VistaPedidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Pedidos» (0.9.26, spec pieza 2 §3): SUPERTECNICO y ADMIN; el TECNICO recibe 403 sin llegar a la vista. */
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
class RolesPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean VistaPedidos vista;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final List<String> RUTAS = List.of("/api/pedidos", "/api/pedidos?viejos=true", "/api/pedidos/fuentes");

    @Test void elTecnicoRecibe403SinLlegarALaVista() throws Exception {
        for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(vista);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        when(vista.pedidos(anyBoolean())).thenReturn(List.of());
        when(vista.fuentes()).thenReturn(List.of());
        for (String token : List.of(supertecnico(), admin()))
            for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk());
    }
}
