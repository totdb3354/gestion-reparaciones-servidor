package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.AsociacionAmazon;
import com.reparaciones.servidor.pedidos.Canal;
import com.reparaciones.servidor.pedidos.EstadoAsociaciones;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.pedidos.PeticionAsociaciones;
import com.reparaciones.servidor.pedidos.RespuestaAsociaciones;
import com.reparaciones.servidor.pedidos.VistaFilas;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/pedidos/asociaciones: el POST solo con la clave de los libros de ventas (su propio ajuste); el GET, con token de SUPERTECNICO o ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-solo-lectura-inventada-000001",
        "pedidos.sincronizados.clave=clave-del-plugin-inventada-0000002",
        "pedidos.asociaciones.clave=clave-de-los-libros-inventada-0003,corta"
})
class RolesAsociacionesTest {

    private static final String LIBROS = "clave-de-los-libros-inventada-0003";
    private static final String VENDIDOS = "clave-solo-lectura-inventada-000001";
    private static final String PLUGIN = "clave-del-plugin-inventada-0000002";
    private static final String CUERPO = "{\"canal\":\"AMAZON\",\"pares\":[{\"pedido\":\"402-0000000-0000001\",\"imei\":\"350000000000001\"}]}";
    private static final String RUTA = "/api/pedidos/asociaciones";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean AsociacionAmazon asociacion;
    @MockBean FilasDAO dao;
    @MockBean VistaFilas vista;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private MockHttpServletRequestBuilder pasada(String clave) {
        MockHttpServletRequestBuilder b = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        return clave == null ? b : b.header("X-API-Key", clave);
    }

    @Test void conLaClaveDeLosLibrosDevuelveLosRecuentos() throws Exception {
        when(asociacion.aplicar(any(), any(), any())).thenReturn(new RespuestaAsociaciones(1, 2, 3, 4, 5, 6));
        mvc.perform(pasada(LIBROS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmadas").value(1))
                .andExpect(jsonPath("$.intercambiadas").value(2))
                .andExpect(jsonPath("$.puestas").value(3))
                .andExpect(jsonPath("$.yaEstaban").value(4))
                .andExpect(jsonPath("$.paraRevisar").value(5))
                .andExpect(jsonPath("$.descartadas").value(6));
        verify(asociacion).aplicar(eq(Canal.AMAZON),
                eq(List.of(new PeticionAsociaciones.Par("402-0000000-0000001", "350000000000001"))), any(LocalDateTime.class));
    }

    @Test void unImeiQueLlegaComoNumeroJsonLlegaAlServicioComoTexto() throws Exception {
        when(asociacion.aplicar(any(), any(), any())).thenReturn(new RespuestaAsociaciones(0, 0, 1, 0, 0, 0));
        mvc.perform(post(RUTA).header("X-API-Key", LIBROS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"AMAZON_STOCKROOM\",\"pares\":[{\"pedido\":\"402-0000000-0000001\",\"imei\":350000000000001}]}"))
                .andExpect(status().isOk());
        verify(asociacion).aplicar(eq(Canal.AMAZON_STOCKROOM),
                eq(List.of(new PeticionAsociaciones.Par("402-0000000-0000001", "350000000000001"))), any(LocalDateTime.class));
    }

    @Test void lasClavesDeLasOtrasRutasDeMaquinaNoValenAqui() throws Exception {
        mvc.perform(pasada(VENDIDOS)).andExpect(status().isUnauthorized());
        mvc.perform(pasada(PLUGIN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(asociacion);
    }

    @Test void sinClaveOConClaveMalaOCortaEs401() throws Exception {
        mvc.perform(pasada(null)).andExpect(status().isUnauthorized());
        mvc.perform(pasada("otra-clave")).andExpect(status().isUnauthorized());
        mvc.perform(pasada("corta")).andExpect(status().isUnauthorized());
        mvc.perform(pasada("")).andExpect(status().isUnauthorized());
        verifyNoInteractions(asociacion);
    }

    @Test void unTokenDeUsuarioNoValeParaElPost() throws Exception {
        mvc.perform(pasada(null).header("Authorization", admin())).andExpect(status().isUnauthorized());
        mvc.perform(pasada("otra-clave").header("Authorization", supertecnico())).andExpect(status().isUnauthorized());
        verifyNoInteractions(asociacion);
    }

    @Test void laClaveDeLosLibrosNoAbreNingunaOtraRutaNiElGet() throws Exception {
        mvc.perform(get(RUTA).header("X-API-Key", LIBROS)).andExpect(status().isForbidden());
        mvc.perform(put(RUTA).header("X-API-Key", LIBROS)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/filas").header("X-API-Key", LIBROS)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/historico").header("X-API-Key", LIBROS)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", LIBROS)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pedidos/sincronizados").header("X-API-Key", LIBROS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"canal\":\"PARTICULARES\",\"pedidos\":[]}")).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA + "/otra").header("X-API-Key", LIBROS)).andExpect(status().isForbidden());
        verifyNoInteractions(asociacion, dao, vista);
    }

    @Test void canalQueNoEsDeAmazonOParesAusentesEs400() throws Exception {
        for (String cuerpo : List.of("{\"canal\":\"PARTICULARES\",\"pares\":[]}", "{\"canal\":\"AMAZON\"}"))
            mvc.perform(post(RUTA).header("X-API-Key", LIBROS).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(asociacion);
    }

    @Test void elGetLoUsanSupertecnicoYAdminConSuToken() throws Exception {
        when(asociacion.estado()).thenReturn(new EstadoAsociaciones(List.of(new EstadoAsociaciones.Pasada("AMAZON",
                LocalDateTime.of(2026, 11, 13, 10, 0), 1, 2, 3, 4, 1, 0, LocalDateTime.of(2026, 11, 13, 9, 15, 7), null, null,
                List.of(new EstadoAsociaciones.Revisar("402-0000000-0000001", "350000000000001", "SIN_HUECO",
                        LocalDateTime.of(2026, 11, 13, 10, 0)))))));
        for (String token : List.of(supertecnico(), admin()))
            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.canales[0].canal").value("AMAZON"))
                    .andExpect(jsonPath("$.canales[0].cuando").value("2026-11-13T10:00:00"))
                    .andExpect(jsonPath("$.canales[0].yaEstaban").value(4))
                    .andExpect(jsonPath("$.canales[0].libroModificado").value("2026-11-13T09:15:07"))
                    .andExpect(jsonPath("$.canales[0].error").value(nullValue()))
                    .andExpect(jsonPath("$.canales[0].revisar[0].pedido").value("402-0000000-0000001"))
                    .andExpect(jsonPath("$.canales[0].revisar[0].imei").value("350000000000001"))
                    .andExpect(jsonPath("$.canales[0].revisar[0].motivo").value("SIN_HUECO"))
                    .andExpect(jsonPath("$.canales[0].revisar[0].vistoEn").value("2026-11-13T10:00:00"));
    }

    @Test void elTecnicoYSinTokenNoLeenElGet() throws Exception {
        mvc.perform(get(RUTA).header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isForbidden());
        verifyNoInteractions(asociacion);
    }
}
