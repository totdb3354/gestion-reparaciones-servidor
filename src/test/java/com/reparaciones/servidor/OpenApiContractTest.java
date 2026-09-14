package com.reparaciones.servidor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Hace de red de seguridad del arranque (el proyecto no tenía test de contexto) y del contrato
 * que consume la app web: levanta el contexto completo de Spring sin tocar la BD —nada consulta
 * al arrancar y el único @Scheduled corre a medianoche— y comprueba que /v3/api-docs exige sesión
 * y publica las rutas y esquemas de la Task 4. Deja el documento en target/openapi.json para que
 * la web genere de ahí sus tipos TypeScript.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Sin BD: Hikari es perezoso, así que el contexto arranca aunque no haya MariaDB delante.
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        // application.properties no está en git (credenciales), así que el test trae sus propias claves.
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000"
})
class OpenApiContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    /**
     * Sin cabecera Authorization el servidor responde 403 (SecurityConfig no registra ningún
     * mecanismo de login, así que Spring Security usa Http403ForbiddenEntryPoint); con un token
     * inválido o caducado es el JwtAuthFilter el que responde 401. Se documentan los dos códigos
     * porque la web tiene que tratar ambos como "sin sesión".
     */
    @Test void elContextoArrancaYElContratoExigeSesion() throws Exception {
        var sinToken = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertEquals(403, sinToken.getStatus(), "el contrato OpenAPI no puede ser público");

        var tokenMalo = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer no-es-un-jwt"))
                .andReturn().getResponse();
        assertEquals(401, tokenMalo.getStatus(), "un token inválido debe dar 401");
    }

    @Test void elContratoPublicaLosEsquemasDeLaWeb() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));

        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());

        JsonNode paths = doc.get("paths");
        assertNotNull(paths, "el contrato no trae paths");
        for (String ruta : List.of("/api/clientes", "/api/clientes/activos", "/api/clientes/{idCli}",
                "/api/clientes/{idCli}/tiene-telefonos", "/api/clientes/{idCli}/activo",
                "/api/auth/login")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        JsonNode esquemas = doc.path("components").path("schemas");
        assertFalse(esquemas.isMissingNode(), "el contrato no trae components.schemas");
        for (String esquema : List.of("LoginResponse", "ValorBooleano", "Cliente", "NombreRequest",
                "EditarRequest", "ActivoRequest", "LoginRequest")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }

        JsonNode loginResponse = esquemas.path("LoginResponse").path("properties");
        for (String campo : List.of("idUsu", "nombreUsuario", "rol", "idTec", "token")) {
            assertTrue(loginResponse.has(campo),
                    () -> "LoginResponse sin el campo " + campo + "; tiene: " + nombres(loginResponse));
        }

        assertEquals("boolean", esquemas.path("ValorBooleano").path("properties").path("value")
                .path("type").asText(), "ValorBooleano.value debe ser boolean");

        Path destino = Path.of("target", "openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
    }

    private static List<String> nombres(JsonNode objeto) {
        var lista = new ArrayList<String>();
        objeto.fieldNames().forEachRemaining(lista::add);
        return lista;
    }
}
