package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lecturas de pedidos y proveedores (0.9.16, spec «El técnico sin Pedidos ni Proveedores» §4): SUPERTECNICO y ADMIN; el
 *  TECNICO recibe 403 sin llegar al DAO. Cadena de seguridad real y DAOs mockeados (sin base de datos). */
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
class RolesLecturasPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean CompraComponenteDAO compraDao;
    @MockBean CompraOtroDAO otroDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    /** `GET /api/compras/en-camino` no va aquí: es una ruta retirada y `RutasRetiradas` la corta con 403 para todos
     *  (`RutasRetiradasInterceptorTest`); su anotación se cambia solo para que diga lo mismo que las demás. */
    private static final List<String> LECTURAS = List.of("/api/compras", "/api/compras-otros", "/api/proveedores?tipo=COMPONENTES");

    @Test void elTecnicoRecibe403SinLlegarAlDao() throws Exception {
        for (String ruta : LECTURAS) {
            mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(compraDao, otroDao, proveedorDao);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        for (String ruta : LECTURAS) {
            mvc.perform(get(ruta).header("Authorization", supertecnico())).andExpect(status().isOk());
            mvc.perform(get(ruta).header("Authorization", admin())).andExpect(status().isOk());
        }
    }
}
