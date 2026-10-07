package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
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
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Mínimo y pesos de la previsión: solo ADMIN (spec 0.9.5 §4.2), con la cadena de seguridad real. */
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
class RolesParametrosPrevisionTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ParametroDAO parametroDao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean LogDAO logDao;
    /** El PUT es @Transactional y este test no tiene base de datos: sin esto no abre conexión. */
    @MockBean PlatformTransactionManager transactionManager;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int status(MockHttpServletRequestBuilder peticion, String autorizacion) throws Exception {
        return mvc.perform(peticion.header("Authorization", autorizacion)).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test void parametrosSoloAdmin() throws Exception {
        when(parametroDao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        String cuerpo = "{\"peso1\":40,\"peso2\":35,\"peso3\":25}";
        for (String quien : new String[] { tecnico(), supertecnico() }) {
            assertEquals(403, status(get("/api/parametros/prevision"), quien));
            assertEquals(403, status(json(put("/api/parametros/prevision"), cuerpo), quien));
        }
        verify(parametroDao, never()).guardarPesosPrevision(any());
        assertEquals(200, status(get("/api/parametros/prevision"), admin()));
        assertEquals(200, status(json(put("/api/parametros/prevision"), cuerpo), admin()));
        verify(parametroDao).guardarPesosPrevision(new Pesos(40, 35, 25));
    }

    @Test void stockMinimoSoloAdmin() throws Exception {
        String cuerpo = "{\"stockMinimo\":4}";
        assertEquals(403, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), supertecnico()));
        assertEquals(403, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), tecnico()));
        verify(componenteDao, never()).setStockMinimo(anyInt(), anyInt());
        assertEquals(200, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), admin()));
        verify(componenteDao).setStockMinimo(5, 4);
    }
}
