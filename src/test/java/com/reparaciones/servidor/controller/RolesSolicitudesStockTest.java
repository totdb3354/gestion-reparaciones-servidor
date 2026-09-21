package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Roles de /api/solicitudes-stock y del ajuste de stock (spec web-formulario §5.3), con la cadena de seguridad real. */
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
class RolesSolicitudesStockTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean SolicitudStockDAO dao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int status(MockHttpServletRequestBuilder peticion, String autorizacion) throws Exception {
        return mvc.perform(peticion.header("Authorization", autorizacion)).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test void crearTecnicoYSupertecnico201AdminEs403() throws Exception {
        String cuerpo = "{\"idCom\":111,\"descripcion\":null}";
        assertEquals(403, status(json(post("/api/solicitudes-stock"), cuerpo), admin()));
        verifyNoInteractions(dao);
        assertEquals(201, status(json(post("/api/solicitudes-stock"), cuerpo), tecnico()));
        verify(dao).insertar(111, 8, null);
        assertEquals(201, status(json(post("/api/solicitudes-stock"), cuerpo), supertecnico()));
        verify(dao).insertar(111, 7, null);
    }

    @Test void listarYContarSupertecnicoYAdmin200TecnicoEs403() throws Exception {
        assertEquals(403, status(get("/api/solicitudes-stock"), tecnico()));
        assertEquals(403, status(get("/api/solicitudes-stock/count"), tecnico()));
        verifyNoInteractions(dao);
        for (String quien : new String[] { supertecnico(), admin() }) {
            assertEquals(200, status(get("/api/solicitudes-stock?estado=PENDIENTE"), quien));
            assertEquals(200, status(get("/api/solicitudes-stock/count"), quien));
        }
    }

    @Test void cambiarEstadoYBorrarSoloSupertecnico() throws Exception {
        String cuerpo = "{\"estado\":\"RECHAZADA\"}";
        for (String quien : new String[] { tecnico(), admin() }) {
            assertEquals(403, status(json(patch("/api/solicitudes-stock/701/estado"), cuerpo), quien));
            assertEquals(403, status(delete("/api/solicitudes-stock/701"), quien));
        }
        verifyNoInteractions(dao);
        assertEquals(200, status(json(patch("/api/solicitudes-stock/701/estado"), cuerpo), supertecnico()));
        verify(dao).actualizarEstado(701, "RECHAZADA");
        assertEquals(204, status(delete("/api/solicitudes-stock/701"), supertecnico()));
        verify(dao).borrar(701);
    }

    @Test void ajusteDeStockSoloSupertecnico() throws Exception {
        String cuerpo = "{\"delta\":1}";
        assertEquals(403, status(json(patch("/api/componentes/101/stock"), cuerpo), tecnico()));
        assertEquals(403, status(json(patch("/api/componentes/101/stock"), cuerpo), admin()));
        verifyNoInteractions(componenteDao);
        assertEquals(200, status(json(patch("/api/componentes/101/stock"), cuerpo), supertecnico()));
        verify(componenteDao).actualizarStock(101, 1);
    }
}
