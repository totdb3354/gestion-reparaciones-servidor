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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada ruta exige el rol que la alcanza en pantalla (spec sp7b §4.3). */
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
class RolesRutasDeUnRolTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionComponenteDAO solicitudDao;
    @MockBean DificultadPuntosDAO dificultadDao;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;
    @MockBean TipoCambioDAO tipoCambioDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void laCampanaEsDelSupertecnico() throws Exception {
        mvc.perform(get("/api/solicitudes").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitudes/count").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitudes").header("Authorization", supertecnico())).andExpect(status().isOk());
    }

    @Test void losValoresDeDificultadSonDelAdmin() throws Exception {
        mvc.perform(get("/api/valores-dificultad").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/valores-dificultad").header("Authorization", supertecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/valores-dificultad").header("Authorization", admin())).andExpect(status().isOk());
    }

    @Test void elTipoDeCambioEsDelSupertecnico() throws Exception {
        mvc.perform(get("/api/tipo-cambio/USD").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/tipo-cambio/USD").header("Authorization", supertecnico())).andExpect(status().isOk());
    }

    /** El ADMIN reutiliza la vista de asignaciones en solo lectura: completadas-hoy lleva los dos roles. */
    @Test void completadasHoyEsDelSupertecnicoYDelAdmin() throws Exception {
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", supertecnico()))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", admin()))
           .andExpect(status().isOk());
    }

    @Test void tieneAsignacionYReferenciadoraSonDelSupertecnico() throws Exception {
        mvc.perform(get("/api/reparaciones/imei/355400000000111/tiene-asignacion?tecnico=1").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reparaciones/R20260928_1/referenciadora").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reparaciones/imei/355400000000111/tiene-asignacion?tecnico=1").header("Authorization", supertecnico()))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reparaciones/R20260928_1/referenciadora").header("Authorization", supertecnico()))
           .andExpect(status().isOk());
    }

    /** La gestión de una solicitud (cambiar su estado o limpiarla del listado) es del supertécnico. */
    @Test void gestionarSolicitudEsDelSupertecnico() throws Exception {
        String cuerpo = "{\"estado\":\"RECHAZADA\"}";
        mvc.perform(patch("/api/solicitudes/701/estado").header("Authorization", tecnico())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
           .andExpect(status().isForbidden());
        mvc.perform(patch("/api/solicitudes/701/limpiar").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());

        mvc.perform(patch("/api/solicitudes/701/estado").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
           .andExpect(status().isOk());
        mvc.perform(patch("/api/solicitudes/701/limpiar").header("Authorization", supertecnico()))
           .andExpect(status().isOk());
    }
}
