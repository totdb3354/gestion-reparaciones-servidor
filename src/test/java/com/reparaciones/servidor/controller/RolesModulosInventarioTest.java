package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los módulos que la tienda no usa (lotes, inventario, revisión, equivalencias) exigen SUPERTECNICO
 * también en sus lecturas (spec sp7b §4.2).
 */
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
class RolesModulosInventarioTest {

    /** Las siete lecturas que estaban abiertas. Formato: "METODO ruta". */
    private static final List<String> SOLO_SUPERTECNICO = List.of(
            "GET /api/colores/equivalencias",
            "GET /api/modelos/equivalencias",
            "GET /api/lotes",
            "GET /api/telefonos/inventario",
            "GET /api/telefonos/355400000000111/revision",
            "GET /api/telefonos/355400000000111/movimientos");

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO telefonoDao;
    @MockBean LoteDAO loteDao;
    @MockBean LogDAO logDao;
    @MockBean ColorEquivalenciaDAO colorEquivalenciaDao;
    @MockBean EquivalenciaModeloDAO equivalenciaModeloDao;
    @MockBean RevisionDAO revisionDao;
    @MockBean MovimientoDAO movimientoDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }

    @Test void unTecnicoNoLeeLosModulosDeInventario() throws Exception {
        for (String caso : SOLO_SUPERTECNICO) {
            String ruta = caso.split(" ", 2)[1];
            mvc.perform(get(ruta).header("Authorization", tecnico()))
               .andExpect(status().isForbidden());
        }
    }

    @Test void unSupertecnicoSiLosLee() throws Exception {
        for (String caso : SOLO_SUPERTECNICO) {
            String ruta = caso.split(" ", 2)[1];
            mvc.perform(get(ruta).header("Authorization", supertecnico()))
               .andExpect(status().isOk());
        }
    }

    @Test void verificarLoteExigeSupertecnico() throws Exception {
        mvc.perform(post("/api/lotes/verificar").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imeis\":[]}"))
           .andExpect(status().isForbidden());
    }
}
