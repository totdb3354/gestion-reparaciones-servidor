package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.AsignacionLoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/asignaciones/lote (spec 3b §4.2): la cadena de seguridad real, la clave de idempotencia
 *  obligatoria y el wiring del controller y el servicio nuevos (mismo patrón que PrediccionGlassControllerTest). */
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
class RolesAsignacionLoteTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean AsignacionLoteService servicio;
    @MockBean TecnicoDAO tecnicoDao;
    @MockBean LogDAO logDao;
    @MockBean ReparacionDAO dao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final String CUERPO = """
            {"telefonos":[{"imei":"111111111111111","modelo":"13pro","idCli":null,"clienteExplicito":false}],
             "asignaciones":[{"imei":"111111111111111","categoria":"R","idTec":3,"comentario":null,"esChasis":false}]}""";

    private ResultActions lote(String token, String clave) throws Exception {
        var peticion = post("/api/asignaciones/lote").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        if (clave != null) peticion = peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion);
    }

    private void tecnicoActivo() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(new Tecnico(3, "Técnico A", true, false, false)));
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        lote(tecnico(), "k1").andExpect(status().isForbidden());
        lote(admin(), "k2").andExpect(status().isForbidden());
    }

    @Test void supertecnicoGuarda() throws Exception {
        tecnicoActivo();
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new LoteAsignaciones.Respuesta(
                List.of(new LoteAsignaciones.Creada("A1", "111111111111111", 3, "R")), List.of()));
        lote(supertecnico(), "k3").andExpect(status().isOk()).andExpect(jsonPath("$.creadas[0].idRep").value("A1"));
    }

    @Test void sinClaveEs400() throws Exception {
        tecnicoActivo();
        lote(supertecnico(), null).andExpect(status().isBadRequest());
    }
}
