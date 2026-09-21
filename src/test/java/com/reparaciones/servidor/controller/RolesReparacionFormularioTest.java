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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_9";

    private static final String FILA_JSON =
            "{\"idCom\":101,\"cantidad\":1,\"reutilizado\":false,\"observacion\":null,\"prefijo\":\"bat\","
            + "\"esSolicitud\":false,\"descripcionSolicitud\":null,\"estadoSolicitud\":null,\"enCamino\":false}";

    private static final String CUERPO_COMPLETA_CON_ASIG =
            "{\"filas\":[" + FILA_JSON + "],\"imei\":\"" + IMEI + "\",\"idTec\":9,"
            + "\"idRepAnterior\":null,\"idAsignacion\":\"" + ASIG + "\",\"categoria\":null}";

    private static final String CUERPO_COMPLETA_SIN_ASIG =
            "{\"filas\":[" + FILA_JSON + "],\"imei\":\"" + IMEI + "\",\"idTec\":4,"
            + "\"idRepAnterior\":null,\"idAsignacion\":null,\"categoria\":\"R\"}";

    private static final String CUERPO_AGOTAR = "{\"idCom\":102,\"cantidad\":1,\"descripcion\":null}";

    private static final String CUERPO_FILAS =
            "{\"filas\":[" + FILA_JSON + "],\"imei\":\"" + IMEI + "\",\"idTec\":9,\"idRepAnterior\":null}";

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
    /** ADMIN cuyo token SÍ trae técnico — para probar que el rol se corta antes de llegar a la propiedad de la asignación. */
    private String adminConIdTec(int idTec) { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", idTec)); }

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

    // ── escrituras del formulario: mismo rol que el borrador (TECNICO o SUPERTECNICO) ──────────

    @Test void completaSiendoAdminEs403() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(5);
        int status = mvc.perform(post("/api/reparaciones/completa")
                        .header("Authorization", adminConIdTec(5))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_COMPLETA_CON_ASIG))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    @Test void filasSiendoAdminEs403() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(5);
        int status = mvc.perform(post("/api/reparaciones/" + ASIG + "/filas")
                        .header("Authorization", adminConIdTec(5))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_FILAS))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    @Test void agotarSiendoAdminEs403() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(5);
        int status = mvc.perform(post("/api/reparaciones/" + ASIG + "/agotar-componente")
                        .header("Authorization", adminConIdTec(5))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_AGOTAR))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    @Test void completarSiendoAdminEs403() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(5);
        int status = mvc.perform(patch("/api/reparaciones/" + ASIG + "/completar")
                        .header("Authorization", adminConIdTec(5)))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    /** Cierra un hueco de cobertura sobre exigirSupertecnico: por la cadena real, no solo la llamada directa. */
    @Test void completaSinAsignacionSiendoTecnicoEs403PorLaCadenaReal() throws Exception {
        int status = mvc.perform(post("/api/reparaciones/completa")
                        .header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_COMPLETA_SIN_ASIG))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verify(dao, never()).insertarCompleta(any(), any(), anyInt(), any(), any(), any());
    }

    /** El técnico dueño de la asignación sigue llegando al DAO: el rol nuevo no bloquea al rol legítimo. */
    @Test void filasSiendoTecnicoLlegaAlDao() throws Exception {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(any(), eq(IMEI), eq(4), isNull(), eq(ASIG))).thenReturn("R20260916_5");
        int status = mvc.perform(post("/api/reparaciones/" + ASIG + "/filas")
                        .header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_FILAS))
                .andReturn().getResponse().getStatus();
        assertEquals(201, status);
        verify(dao).guardarFilaIndividual(any(), eq(IMEI), eq(4), isNull(), eq(ASIG));
    }
}
