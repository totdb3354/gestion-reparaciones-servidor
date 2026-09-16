package com.reparaciones.servidor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.TreeMap;

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
        "jwt.expiration=86400000",
        // Para que un application.properties local con springdoc.api-docs.enabled=false no apague
        // el contrato y haga fallar este test de forma confusa.
        "springdoc.api-docs.enabled=true"
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
        // Documenta la ausencia actual de un AuthenticationEntryPoint propio; si se añade uno,
        // este 403 debería pasar a 401 y este test tendría que actualizarse.
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
                "/api/auth/login", "/api/reparaciones/pendientes/contadores")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        JsonNode esquemas = doc.path("components").path("schemas");
        assertFalse(esquemas.isMissingNode(), "el contrato no trae components.schemas");
        // Los records anidados se publican prefijados por su controller (ver OpenApiConfig):
        // ReparacionEditarRequest está en la lista para demostrar que los dos EditarRequest ya no chocan.
        for (String esquema : List.of("LoginResponse", "ValorBooleano", "Cliente", "ClienteNombreRequest",
                "ClienteEditarRequest", "ClienteActivoRequest", "AuthLoginRequest",
                "ReparacionEditarRequest", "ContadoresPendientes", "ValorTexto")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }

        JsonNode clienteEditar = esquemas.path("ClienteEditarRequest").path("properties");
        for (String campo : List.of("nombre", "updatedAt")) {
            assertTrue(clienteEditar.has(campo), () -> "ClienteEditarRequest sin el campo " + campo
                    + "; tiene: " + nombres(clienteEditar));
        }

        JsonNode clienteActivo = esquemas.path("ClienteActivoRequest").path("properties");
        for (String campo : List.of("activo", "updatedAt")) {
            assertTrue(clienteActivo.has(campo), () -> "ClienteActivoRequest sin el campo " + campo
                    + "; tiene: " + nombres(clienteActivo));
        }

        assertTrue(refDelCuerpo(paths, "/api/clientes/{idCli}", "put").endsWith("/ClienteEditarRequest"),
                () -> "PUT /api/clientes/{idCli} referencia "
                        + refDelCuerpo(paths, "/api/clientes/{idCli}", "put"));
        assertTrue(refDelCuerpo(paths, "/api/clientes/{idCli}/activo", "patch")
                        .endsWith("/ClienteActivoRequest"),
                () -> "PATCH /api/clientes/{idCli}/activo referencia "
                        + refDelCuerpo(paths, "/api/clientes/{idCli}/activo", "patch"));

        JsonNode loginResponse = esquemas.path("LoginResponse").path("properties");
        for (String campo : List.of("idUsu", "nombreUsuario", "rol", "idTec", "token")) {
            assertTrue(loginResponse.has(campo),
                    () -> "LoginResponse sin el campo " + campo + "; tiene: " + nombres(loginResponse));
        }

        assertEquals("boolean", esquemas.path("ValorBooleano").path("properties").path("value")
                .path("type").asText(), "ValorBooleano.value debe ser boolean");

        JsonNode contadores = esquemas.path("ContadoresPendientes").path("properties");
        for (String campo : List.of("reparaciones", "glass", "pulidos")) {
            assertEquals("integer", contadores.path(campo).path("type").asText(),
                    () -> "ContadoresPendientes." + campo + " debe ser integer");
        }

        String refTieneTelefonos = refDeLaRespuesta(paths, "/api/clientes/{idCli}/tiene-telefonos",
                "get", "200");
        assertTrue(refTieneTelefonos.endsWith("/ValorBooleano"),
                () -> "GET /api/clientes/{idCli}/tiene-telefonos responde " + refTieneTelefonos);

        // Nullabilidad (spec web-taller §5.3): todo required, nullable explícito
        JsonNode resumen = esquemas.path("ReparacionResumen");
        List<String> requeridos = new ArrayList<>();
        resumen.path("required").forEach(n -> requeridos.add(n.asText()));
        List<String> propiedades = nombres(resumen.path("properties"));
        assertEquals(propiedades.size(), requeridos.size(), "ReparacionResumen: todas las propiedades deben ser required");
        assertTrue(requeridos.containsAll(propiedades));
        assertTrue(resumen.path("properties").path("fechaFin").path("nullable").asBoolean(false), "fechaFin nullable");
        assertTrue(resumen.path("properties").path("glassEntregadoPor").path("nullable").asBoolean(false), "glassEntregadoPor nullable");
        assertFalse(resumen.path("properties").path("idRep").path("nullable").asBoolean(false), "idRep no nullable");
        assertTrue(esquemas.path("LoginResponse").path("properties").path("idTec").path("nullable").asBoolean(false), "idTec nullable");
        assertTrue(esquemas.path("LoginResponse").path("required").toString().contains("\"token\""));
        assertTrue(esquemas.path("ReparacionMotivoRequest").path("properties").path("motivo").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoClienteRequest").path("properties").path("idCli").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoImeiRequest").path("properties").path("clienteExplicito").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("ValorTexto").path("properties").path("value").path("nullable").asBoolean(false));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/referenciadora", "get", "200").endsWith("/ValorTexto"));

        Path destino = Path.of("target", "openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(ordenarCodigosDeRespuesta(doc)));
    }

    /** Ordena por clave los códigos de cada "responses" para que el volcado sea estable entre arranques. */
    private static JsonNode ordenarCodigosDeRespuesta(JsonNode doc) {
        JsonNode paths = doc.get("paths");
        if (paths != null) {
            paths.forEach(operaciones -> operaciones.forEach(operacion -> {
                if (operacion instanceof ObjectNode opNode
                        && opNode.get("responses") instanceof ObjectNode responses) {
                    var ordenadas = new TreeMap<String, JsonNode>();
                    responses.fields().forEachRemaining(e -> ordenadas.put(e.getKey(), e.getValue()));
                    responses.removeAll();
                    ordenadas.forEach(responses::set);
                }
            }));
        }
        return doc;
    }

    /** $ref del esquema del requestBody de una operación (application/json o el único que haya). */
    private static String refDelCuerpo(JsonNode paths, String ruta, String metodo) {
        JsonNode contenido = paths.path(ruta).path(metodo).path("requestBody").path("content");
        assertFalse(contenido.isMissingNode(),
                () -> metodo + " " + ruta + " no declara requestBody en el contrato");
        JsonNode media = contenido.has("application/json")
                ? contenido.path("application/json")
                : contenido.elements().next();
        String ref = media.path("schema").path("$ref").asText("");
        assertFalse(ref.isEmpty(),
                () -> metodo + " " + ruta + " no referencia ningún esquema: " + media);
        return ref;
    }

    /** $ref del esquema de una respuesta (código dado) de una operación. */
    private static String refDeLaRespuesta(JsonNode paths, String ruta, String metodo, String codigo) {
        JsonNode contenido = paths.path(ruta).path(metodo).path("responses").path(codigo).path("content");
        assertFalse(contenido.isMissingNode(),
                () -> metodo + " " + ruta + " no declara el código " + codigo + " en el contrato");
        JsonNode media = contenido.has("application/json")
                ? contenido.path("application/json")
                : contenido.elements().next();
        String ref = media.path("schema").path("$ref").asText("");
        assertFalse(ref.isEmpty(),
                () -> metodo + " " + ruta + " " + codigo + " no referencia ningún esquema: " + media);
        return ref;
    }

    private static List<String> nombres(JsonNode objeto) {
        var lista = new ArrayList<String>();
        objeto.fieldNames().forEachRemaining(lista::add);
        return lista;
    }
}
