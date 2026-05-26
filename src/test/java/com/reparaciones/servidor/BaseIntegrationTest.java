package com.reparaciones.servidor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc       mockMvc;
    @Autowired protected ObjectMapper  objectMapper;
    @Autowired private   JdbcTemplate  jdbc;

    // BCrypt strength 4 para tests rápidos; misma contraseña "test1234" para todos los usuarios
    private static final String HASH = new BCryptPasswordEncoder(4).encode("test1234");

    // IDs fijos que no chocan con el AUTO_INCREMENT del data-test.sql
    static final int ID_TEC_SUPER =  100;
    static final int ID_TEC_TEC   =  101;
    static final int ID_USU_ADMIN =   10;
    static final int ID_USU_SUPER =   11;
    static final int ID_USU_TEC   =   12;

    @BeforeEach
    void insertarDatosBase() {
        jdbc.update("INSERT INTO Tecnico (ID_TEC, NOMBRE, ACTIVO) VALUES (?, ?, ?)",
                ID_TEC_SUPER, "Técnico SuperTest", true);
        jdbc.update("INSERT INTO Tecnico (ID_TEC, NOMBRE, ACTIVO) VALUES (?, ?, ?)",
                ID_TEC_TEC, "Técnico TecTest", true);
        jdbc.update("INSERT INTO Usuario (ID_USU, NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, ?, ?, ?)",
                ID_USU_ADMIN, "admin_test", HASH, "ADMIN", null);
        jdbc.update("INSERT INTO Usuario (ID_USU, NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, ?, ?, ?)",
                ID_USU_SUPER, "super_test", HASH, "SUPERTECNICO", ID_TEC_SUPER);
        jdbc.update("INSERT INTO Usuario (ID_USU, NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, ?, ?, ?)",
                ID_USU_TEC, "tecnico_test", HASH, "TECNICO", ID_TEC_TEC);
    }

    protected String tokenPara(String usuario, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usuario", usuario, "password", password));
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> resp = objectMapper.readValue(json, new TypeReference<>() {});
        return (String) resp.get("token");
    }

    protected String tokenAdmin()        throws Exception { return tokenPara("admin_test",   "test1234"); }
    protected String tokenSuperTecnico() throws Exception { return tokenPara("super_test",   "test1234"); }
    protected String tokenTecnico()      throws Exception { return tokenPara("tecnico_test", "test1234"); }
}
