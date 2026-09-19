package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BorradorDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** @PreAuthorize de la edición y del borrador (spec web-formulario §5.1 y §5.2), con la cadena de seguridad real. */
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
class RolesReparacionFormularioTest {

    private static final String CUERPO_EDITAR =
            "{\"idComNuevo\":101,\"esReutilizadoNuevo\":false,\"observacionNueva\":null,"
            + "\"nNuevas\":1,\"updatedAt\":\"2026-09-16T07:02:00\"}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean ReparacionComponenteDAO rcDao;
    @MockBean LogDAO logDao;
    @MockBean BorradorDAO borradorDao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean DificultadPuntosDAO dificultadDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int editarComo(String autorizacion) throws Exception {
        return mvc.perform(put("/api/reparaciones/R20260916_5")
                        .header("Authorization", autorizacion)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDITAR))
                .andReturn().getResponse().getStatus();
    }

    @Test void editarSiendoTecnicoEs403() throws Exception {
        assertEquals(403, editarComo(tecnico()));
        verifyNoInteractions(dao);
    }

    @Test void editarSiendoAdminEs403() throws Exception {
        assertEquals(403, editarComo(admin()));
        verifyNoInteractions(dao);
    }

    @Test void editarSiendoSupertecnicoLlegaAlDao() throws Exception {
        assertEquals(200, editarComo(supertecnico()));
        verify(dao).editarReparacion("R20260916_5", 101, false, null, 1, LocalDateTime.of(2026, 9, 16, 7, 2, 0));
    }

    @Test void detalleEdicionSiendoTecnicoEs403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/R20260916_5/detalle-edicion")
                .header("Authorization", tecnico())).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    @Test void borradorSiendoAdminEs403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/A20260916_1/borrador")
                .header("Authorization", admin())).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao, borradorDao);
    }
}
