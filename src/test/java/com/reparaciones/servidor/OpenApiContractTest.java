package com.reparaciones.servidor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Hace de red de seguridad del arranque (el proyecto no tenía test de contexto) y del contrato
 * que consume la app web: levanta el contexto completo de Spring sin tocar la BD —nada consulta
 * al arrancar: el único @Scheduled siempre activo corre a medianoche y las tareas de NSYS (0.9.17) solo
 * existen con nsys.importacion.activa o nsys.historico.activo— y comprueba que /v3/api-docs exige sesión
 * y publica las rutas y esquemas de la Task 4. Deja el documento en target/openapi.json para que
 * la web genere de ahí sus tipos TypeScript.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
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
                "/api/auth/login", "/api/reparaciones/pendientes/contadores", "/api/glass/prediccion",
                "/api/asignaciones/lote")) {
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
        assertTrue(resumen.path("properties").path("clienteTelefono").path("nullable").asBoolean(false),
                "clienteTelefono nullable (spec 0.9.8 §5)");
        assertFalse(resumen.path("properties").path("idRep").path("nullable").asBoolean(false), "idRep no nullable");
        // 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.3): capacidad y color de NSYS, nullables y al final.
        assertNullable(esquemas, "ReparacionResumen", "capacidad", "color");
        assertEquals(List.of("capacidad", "color"), propiedades.subList(propiedades.size() - 2, propiedades.size()),
                "capacidad y color van al final de ReparacionResumen");
        assertTrue(esquemas.path("LoginResponse").path("properties").path("idTec").path("nullable").asBoolean(false), "idTec nullable");
        assertTrue(esquemas.path("LoginResponse").path("required").toString().contains("\"token\""));
        assertTrue(esquemas.path("ReparacionMotivoRequest").path("properties").path("motivo").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoClienteRequest").path("properties").path("idCli").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoImeiRequest").path("properties").path("clienteExplicito").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("ValorTexto").path("properties").path("value").path("nullable").asBoolean(false));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/referenciadora", "get", "200").endsWith("/ValorTexto"));
        assertTrue(paths.has("/api/reparaciones/imei/{imei}/cerrados"), "falta GET /imei/{imei}/cerrados (0.9.8)");
        assertTrue(paths.path("/api/reparaciones/cliente").has("patch"), "falta PATCH /api/reparaciones/cliente (0.9.8)");
        assertTrue(esquemas.has("TrabajoCerrado"), "falta el esquema TrabajoCerrado (0.9.8)");

        // Fix tras revisión (spec §5.3): comentario/motivo genuinamente opcionales en los cuerpos
        // de petición, verificado contra el DAO caso a caso (ver task-3-report.md).
        assertTrue(esquemas.path("TelefonoEstadoRequest").path("properties").path("motivo").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("PulidoAsignacionPulidoRequest").path("properties").path("comentario").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("ReparacionAsignacionRequest").path("properties").path("comentario").path("nullable").asBoolean(false), "ReparacionAsignacionRequest.comentario nullable");
        assertTrue(esquemas.path("PulidoActualizarPulidoRequest").path("properties").path("comentario").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("ReparacionActualizarAsignacionRequest").path("properties").path("comentarioAsignacion").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("GlassGlassAsignacionRequest").path("properties").path("comentario").path("nullable").asBoolean(false));

        // ── Sub-proyecto 2 (spec web-formulario §5.5): formulario de reparación y campana ──
        for (String esquema : List.of("Componente", "FilaReparacion", "Reparacion", "SolicitudResumen",
                "SolicitudStock", "ReparacionDAODetalleEdicion", "ReparacionDAOAsignacionActiva",
                "ReparacionInsertarCompletaRequest", "ReparacionGuardarFilaRequest", "ReparacionAgotarRequest",
                "ReparacionEditarRequest", "ReparacionBorradorRequest", "SolicitudStockInsertarRequest",
                "SolicitudEstadoRequest", "SolicitudStockEstadoRequest", "ValorEntero", "ValorTexto",
                "ContenidoBorrador")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }

        for (String ruta : List.of(
                // formulario
                "/api/componentes/agrupados",
                "/api/reparaciones/imei/{imei}",
                "/api/reparaciones/imei/{imei}/incidencia-activa",
                "/api/reparaciones/imei/{imei}/asignaciones-activas",
                "/api/reparaciones/imei/{imei}/ya-reparados",
                "/api/reparaciones/imei/{imei}/acciones",
                "/api/reparaciones/asignaciones/{idAsignacion}/solicitudes",
                "/api/reparaciones/{idRep}/borrador",
                "/api/reparaciones/{idRep}/detalle-edicion",
                "/api/reparaciones/{idRep}",
                "/api/reparaciones/{idAsignacion}/filas",
                "/api/reparaciones/{idAsignacion}/agotar-componente",
                "/api/reparaciones/completa",
                "/api/telefonos/{imei}/modelo",
                // campana
                "/api/componentes/gestionados",
                "/api/solicitudes", "/api/solicitudes/count",
                "/api/solicitudes/{idRc}/estado", "/api/solicitudes/{idRc}/limpiar",
                "/api/solicitudes-stock", "/api/solicitudes-stock/count",
                "/api/solicitudes-stock/{idSol}/estado", "/api/solicitudes-stock/{idSol}")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        // Nullabilidad campo a campo (todo lo demás es required y no nulo)
        assertNullable(esquemas, "FilaReparacion", "observacion", "prefijo", "descripcionSolicitud", "estadoSolicitud");
        assertNullable(esquemas, "Componente", "ultimoPedido", "idComMaster");
        assertNullable(esquemas, "Componente", "consumoDiario", "pedir60", "autoPedido");
        assertTrue(paths.has("/api/componentes/{idCom}/auto-pedido"), "falta /api/componentes/{idCom}/auto-pedido en el contrato");
        assertTrue(paths.has("/api/parametros/prevision"), "falta /api/parametros/prevision en el contrato");
        assertNoNullable(esquemas, "PesosPrevision", "peso1", "peso2", "peso3");
        assertNullable(esquemas, "SolicitudResumen", "tipoComponente", "descripcion");
        assertNullable(esquemas, "SolicitudStock", "descripcion");
        assertNullable(esquemas, "Reparacion", "fechaFin");
        assertNullable(esquemas, "ReparacionDAODetalleEdicion", "observacion");
        assertNullable(esquemas, "ReparacionInsertarCompletaRequest", "idRepAnterior", "idAsignacion", "categoria");
        assertNullable(esquemas, "ReparacionGuardarFilaRequest", "idRepAnterior");
        assertNullable(esquemas, "ReparacionAgotarRequest", "descripcion");
        assertNullable(esquemas, "ReparacionEditarRequest", "observacionNueva");
        assertNullable(esquemas, "SolicitudStockInsertarRequest", "descripcion");
        assertNullable(esquemas, "ContenidoBorrador", "contenido");
        assertNoNullable(esquemas, "FilaReparacion", "idCom", "cantidad", "reutilizado", "esSolicitud", "enCamino");
        assertNoNullable(esquemas, "Componente", "idCom", "tipo", "stock", "stockMinimo", "activo", "enCamino");
        assertNoNullable(esquemas, "SolicitudResumen", "idRc", "idRep", "imei", "nombreTecnico", "estado", "fechaSolicitud");
        assertNoNullable(esquemas, "SolicitudStock", "idSol", "idCom", "tipoComponente", "nombreUsuario", "estado", "fecha");
        assertNoNullable(esquemas, "ReparacionDAOAsignacionActiva", "idRep", "nombreTecnico", "idTec");
        assertNoNullable(esquemas, "ReparacionInsertarCompletaRequest", "filas", "imei", "idTec");
        assertNoNullable(esquemas, "ReparacionGuardarFilaRequest", "filas", "imei", "idTec");
        assertNoNullable(esquemas, "ReparacionBorradorRequest", "contenido");

        assertEquals("integer", esquemas.path("ValorEntero").path("properties").path("value").path("type").asText(),
                "ValorEntero.value debe ser integer");
        assertEquals("integer", esquemas.path("ReparacionInsertarCompletaRequest").path("properties").path("idTec")
                .path("type").asText(), "el idTec del cuerpo se conserva en el contrato (compatibilidad)");
        assertEquals("string", esquemas.path("SolicitudEstadoRequest").path("properties").path("estado")
                .path("type").asText());
        assertEquals("string", esquemas.path("SolicitudStockEstadoRequest").path("properties").path("estado")
                .path("type").asText());

        // Las ocho respuestas que dejan de ser Map y los dos cuerpos de estado
        assertTrue(refDeLaRespuesta(paths, "/api/solicitudes/count", "get", "200").endsWith("/ValorEntero"));
        assertTrue(refDeLaRespuesta(paths, "/api/solicitudes-stock/count", "get", "200").endsWith("/ValorEntero"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/imei/{imei}/incidencia-activa", "get", "200")
                .endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/telefonos/{imei}/modelo", "get", "200").endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idAsignacion}/filas", "post", "201")
                .endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/borrador", "get", "200")
                .endsWith("/ContenidoBorrador"));
        assertTrue(refDelCuerpo(paths, "/api/solicitudes/{idRc}/estado", "patch").endsWith("/SolicitudEstadoRequest"));
        assertTrue(refDelCuerpo(paths, "/api/solicitudes-stock/{idSol}/estado", "patch")
                .endsWith("/SolicitudStockEstadoRequest"));
        assertTrue(refDelCuerpo(paths, "/api/reparaciones/{idRep}/borrador", "put").endsWith("/ReparacionBorradorRequest"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/detalle-edicion", "get", "200")
                .endsWith("/ReparacionDAODetalleEdicion"));
        assertTrue(refDeLaRespuesta(paths, "/api/compras/cantidad-en-camino/{idCom}", "get", "200").endsWith("/ValorEntero"),
                "cantidad-en-camino debe responder ValorEntero");

        Path destino = Path.of("target", "openapi.json");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(ordenarCodigosDeRespuesta(doc)));
    }

    /** 0.9.9 (spec «Pedidos por bloques» §5): rutas y esquemas de los bloques y los campos nuevos de compras. */
    @Test void elContratoPublicaLosBloquesDePedidos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        assertTrue(paths.path("/api/compras/{idCompra}/bloque").has("patch"), "falta PATCH /api/compras/{idCompra}/bloque");
        for (String accion : List.of("confirmar", "recibir", "cancelar", "borrar", "separar", "juntar")) {
            assertTrue(paths.path("/api/bloques-compra/{idBloque}/" + accion).has("post"), () -> "falta POST …/" + accion);
        }
        assertTrue(paths.path("/api/bloques-compra/{idBloque}/nota").has("patch"), "falta PATCH …/nota");
        assertTrue(refDelCuerpo(paths, "/api/compras/{idCompra}/bloque", "patch").endsWith("/BloqueCompraMoverRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/recibir", "post").endsWith("/BloqueCompraLineasRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/juntar", "post").endsWith("/BloqueCompraJuntarRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/nota", "patch").endsWith("/BloqueCompraNotaRequest"));
        assertTrue(refDeLaRespuesta(paths, "/api/bloques-compra/{idBloque}/separar", "post", "200").endsWith("/BloqueCompraSepararRespuesta"));

        assertNullable(esquemas, "CompraComponente", "idBloque", "fechaBloque", "notaBloque", "bloqueUpdatedAt");
        assertNoNullable(esquemas, "CompraComponente", "bloqueAntiguo");
        assertNullable(esquemas, "LoteComprasPeticion", "destinos");
        assertNullable(esquemas, "LoteComprasDestino", "idBloque");
        assertNoNullable(esquemas, "LoteComprasDestino", "idProv");
        // destino es una $ref: OpenAPI 3.0 ignora "nullable" junto a un $ref, así que el contrato lo publica como
        // DestinoPedido requerido aunque el servidor acepta nulo (regla por defecto). La web debe enviar {idBloque: null}.
        assertTrue(esquemas.path("CompraEditarRequest").path("properties").path("destino").path("$ref").asText().endsWith("/DestinoPedido"));
        assertNullable(esquemas, "DestinoPedido", "idBloque");
        assertNoNullable(esquemas, "LineaVista", "idCompra", "updatedAt");
        assertNullable(esquemas, "BloqueCompraMoverRequest", "idBloque");
        assertNoNullable(esquemas, "BloqueCompraJuntarRequest", "destino", "lineas");
        assertNullable(esquemas, "BloqueCompraNotaRequest", "nota");
        assertNoNullable(esquemas, "BloqueCompraSepararRespuesta", "idBloque");
    }

    /** 0.9.15 (spec «Añadir otro» §4): los otros pedidos dentro del bloque de su proveedor. */
    @Test void elContratoPublicaLosOtrosEnBloques() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        assertTrue(paths.path("/api/compras-otros/{id}/bloque").has("patch"), "falta PATCH /api/compras-otros/{id}/bloque");
        assertTrue(refDelCuerpo(paths, "/api/compras-otros/{id}/bloque", "patch").endsWith("/BloqueCompraMoverRequest"));

        assertNullable(esquemas, "CompraOtro", "idBloque", "fechaBloque", "notaBloque", "bloqueUpdatedAt");
        assertNoNullable(esquemas, "CompraOtro", "bloqueAntiguo");

        assertNullable(esquemas, "LoteComprasPeticion", "otros");
        assertTrue(esquemas.path("LoteComprasPeticion").path("properties").path("otros").path("items").path("$ref").asText()
                .endsWith("/LoteComprasOtrosLinea"), "otros es una lista de LoteComprasOtrosLinea");
        assertNoNullable(esquemas, "LoteComprasRespuesta", "idsCreados", "idsOtrosCreados");
        assertEquals("integer", esquemas.path("LoteComprasRespuesta").path("properties").path("idsOtrosCreados")
                .path("items").path("type").asText(), "idsOtrosCreados debe ser una lista de enteros");

        // tipo: required como todas las propiedades (OpenApiConfig) y nullable; el servidor también lo acepta ausente.
        assertNullable(esquemas, "LineaVista", "tipo");
        assertTrue(esquemas.path("LineaVista").path("required").toString().contains("\"tipo\""), "LineaVista.tipo debe ser required");
        List<String> valores = new ArrayList<>();
        esquemas.path("LineaVista").path("properties").path("tipo").path("enum").forEach(v -> valores.add(v.asText()));
        assertEquals(List.of("PIEZA", "OTRO"), valores);

        // destino es una $ref (OpenAPI 3.0 ignora "nullable" junto a un $ref), como CompraEditarRequest.destino.
        assertTrue(esquemas.path("CompraOtroEditarRequest").path("properties").path("destino").path("$ref").asText()
                .endsWith("/DestinoPedido"), "CompraOtroEditarRequest.destino referencia DestinoPedido");
    }

    /** 0.9.9 (ampliación): piezas de cada proveedor y lo pendiente en el listado de Stock (spec 2026-10-11 §4 y 2026-10-12 §4.3). */
    @Test void elContratoPublicaLasPiezasDeCadaProveedor() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        JsonNode piezas = paths.path("/api/proveedores/{idProv}/piezas");
        assertTrue(piezas.has("get"), "falta GET /api/proveedores/{idProv}/piezas");
        assertTrue(piezas.has("put"), "falta PUT /api/proveedores/{idProv}/piezas");
        assertFalse(paths.has("/api/proveedores/{idProv}/componentes"), "el PUT por referencias ya no existe");
        assertTrue(refDelCuerpo(paths, "/api/proveedores/{idProv}/piezas", "put").endsWith("/ProveedorPiezasRequest"));
        assertNoNullable(esquemas, "ProveedorPiezasRequest", "tipos");
        assertNoNullable(esquemas, "ProveedorPiezasTipoRequest", "prefijo", "excluidos");
        assertNoNullable(esquemas, "ProveedorPiezasTipo", "prefijo", "vende", "modelos", "excluidos");
        assertTrue(piezas.path("get").path("responses").path("200").toString().contains("/ProveedorPiezasTipo\""),
                "el GET devuelve una lista de ProveedorPiezasTipo");
        assertNullable(esquemas, "Componente", "pendiente", "proveedores");
        JsonNode items = esquemas.path("Componente").path("properties").path("proveedores").path("items");
        assertEquals("integer", items.path("type").asText());
        assertFalse(items.path("nullable").asBoolean(), "los ids de proveedor no son nulos");
    }

    /**
     * Sub-proyecto 4b (spec §4.5): los dos lotes de pedidos con sus esquemas, los nulos que el servidor ya mandaba en
     * los pedidos (el tipo generado mentía: inventario de Pedidos §22) y el precioEur ignorado de las cuatro peticiones.
     */
    @Test void elContratoPublicaLosLotesYLosNulosDePedidos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/compras/lote", "/api/compras-otros/lote")) {
            assertTrue(paths.path(ruta).has("post"), () -> "falta POST " + ruta + " en el contrato");
        }
        for (String esquema : List.of("LoteComprasPeticion", "LoteComprasLinea", "LoteComprasSolicitudes",
                "LoteComprasRespuesta", "LoteComprasOtrosPeticion", "LoteComprasOtrosLinea")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }
        assertTrue(refDelCuerpo(paths, "/api/compras/lote", "post").endsWith("/LoteComprasPeticion"));
        assertTrue(refDelCuerpo(paths, "/api/compras-otros/lote", "post").endsWith("/LoteComprasOtrosPeticion"));
        assertTrue(refDeLaRespuesta(paths, "/api/compras/lote", "post", "200").endsWith("/LoteComprasRespuesta"));
        assertTrue(refDeLaRespuesta(paths, "/api/compras-otros/lote", "post", "200").endsWith("/LoteComprasRespuesta"));

        assertNullable(esquemas, "LoteComprasLinea", "idCom", "idProv");
        assertNoNullable(esquemas, "LoteComprasLinea", "cantidad", "esUrgente", "precioUnidad");
        assertNoNullable(esquemas, "LoteComprasPeticion", "lineas", "solicitudes");
        assertNoNullable(esquemas, "LoteComprasSolicitudes", "urgentes", "preventivas");
        assertNoNullable(esquemas, "LoteComprasRespuesta", "idsCreados");
        assertEquals("integer", esquemas.path("LoteComprasRespuesta").path("properties").path("idsCreados")
                .path("items").path("type").asText(), "idsCreados debe ser una lista de enteros");
        assertNullable(esquemas, "LoteComprasOtrosLinea", "idProv", "concepto");
        assertNoNullable(esquemas, "LoteComprasOtrosLinea", "cantidad", "esUrgente", "precioUnidad");
        assertNoNullable(esquemas, "LoteComprasOtrosPeticion", "lineas");

        assertNullable(esquemas, "CompraComponente", "cantidadRecibida", "fechaLlegada");
        assertNullable(esquemas, "CompraOtro", "cantidadRecibida", "fechaLlegada");
        assertNoNullable(esquemas, "CompraComponente", "idCompra", "cantidad", "estado", "precioEur", "updatedAt");
        assertNoNullable(esquemas, "CompraOtro", "idCompraOtro", "concepto", "cantidad", "estado", "precioEur");

        for (String peticion : List.of("CompraInsertarRequest", "CompraEditarRequest",
                "CompraOtroInsertarRequest", "CompraOtroEditarRequest")) {
            assertNullable(esquemas, peticion, "precioEur");
            assertEquals("number", esquemas.path(peticion).path("properties").path("precioEur").path("type").asText(),
                    () -> peticion + ".precioEur debe seguir siendo number");
        }
    }

    /**
     * Sub-proyecto 6 (spec §4.6): la lista de acciones y el límite del log, el idUsu opcional del borrado de técnicos,
     * los nulos de LogActividad y los códigos reales del alta, el borrado, activar/desactivar y cambiar contraseña
     * (antes el contrato decía 200 con un objeto vacío donde el servidor responde 201/204).
     */
    @Test void elContratoPublicaLaGestion() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/logs", "/api/logs/acciones", "/api/usuarios/tecnicos",
                "/api/usuarios/tecnicos/{idTec}", "/api/usuarios/tecnicos/{idTec}/tiene-reparaciones",
                "/api/auth/cambiar-password")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        JsonNode acciones = paths.path("/api/logs/acciones").path("get").path("responses").path("200")
                .path("content").elements().next().path("schema");
        assertEquals("array", acciones.path("type").asText(), "GET /api/logs/acciones devuelve una lista");
        assertEquals("string", acciones.path("items").path("type").asText(), "…de textos");

        JsonNode limite = parametro(paths, "/api/logs", "get", "limite");
        assertEquals("query", limite.path("in").asText());
        assertFalse(limite.path("required").asBoolean(true), "limite es opcional (el JavaFX no lo manda)");
        assertEquals("integer", limite.path("schema").path("type").asText());

        JsonNode idUsu = parametro(paths, "/api/usuarios/tecnicos/{idTec}", "delete", "idUsu");
        assertFalse(idUsu.path("required").asBoolean(true), "idUsu del DELETE pasa a opcional (se ignora)");

        assertNullable(esquemas, "LogActividad", "detalle", "motivo");
        assertNoNullable(esquemas, "LogActividad", "idLog", "fecha", "nombreUsuario", "accion");

        assertCodigos(paths, "/api/usuarios/tecnicos", "post", "201", "409", "422");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}", "delete", "204", "404", "409");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}/activar", "patch", "204", "404");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}/desactivar", "patch", "204", "404");
        assertCodigos(paths, "/api/auth/cambiar-password", "patch", "204", "422", "429");
    }

    /** 0.9.17, pieza 1 del módulo de teléfonos: el stock de teléfonos y el rastro (spec 2026-10-16 §8). */
    @Test void elContratoPublicaElStockDeTelefonos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/stock-telefonos", "/api/stock-telefonos/estado", "/api/stock-telefonos/buscar",
                "/api/stock-telefonos/{imeiSn}", "/api/stock-telefonos/{imeiSn}/rastro", "/api/stock-telefonos/importaciones")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/estado", "get", "200").endsWith("/StockTelefonosEstadoImportacion"));
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/{imeiSn}", "get", "200").endsWith("/StockTelefonosFichaMovil"));
        JsonNode imei = parametro(paths, "/api/stock-telefonos/buscar", "get", "imei");
        assertEquals("query", imei.path("in").asText());

        assertNullable(esquemas, "StockTelefonosMovilStock", "modelo", "modeloMaestro", "capacidad", "color", "sku", "ubicacion", "dias");
        assertNoNullable(esquemas, "StockTelefonosMovilStock", "imeiSn", "lote", "grado", "bateriaNueva", "esEsim", "estado",
                "trabajos");
        // 0.9.18: los trabajos abiertos de cada móvil (spec 2026-10-19 §6.1).
        assertNullable(esquemas, "TrabajoAbierto", "entregado");
        assertNoNullable(esquemas, "TrabajoAbierto", "categoria", "tecnico", "desde");
        assertNullable(esquemas, "StockTelefonosEstadoImportacion", "ultimaExportacion", "ultimoFallo", "motivoFallo");
        assertNoNullable(esquemas, "StockTelefonosEstadoImportacion", "nivel");
        assertNullable(esquemas, "StockTelefonosMovilBuscado", "modelo", "modeloMaestro", "capacidad", "color", "ubicacion", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosMovilBuscado", "imeiSn", "estado");
        assertNullable(esquemas, "StockTelefonosFichaMovil", "modelo", "modeloMaestro", "capacidad", "color", "gradoAnterior",
                "sku", "ubicacion", "dias", "fechaVenta", "revisadoEl", "pendienteRevisionDesde");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "imeiSn", "lote", "grado", "bateriaNueva", "estado", "entradas",
                "revisadoDevolucion");
        assertNullable(esquemas, "EventoRastro", "lote", "de", "a", "aNsys", "idRep", "tecnico", "categoria", "revision");
        assertNoNullable(esquemas, "EventoRastro", "fecha", "soloDia", "fuente", "tipo");
        // 0.9.21 (spec «SKU del taller» §4): lo que vale, con lo de NSYS al lado cuando el taller lo cambia.
        assertNullable(esquemas, "StockTelefonosMovilStock", "colorNsys", "gradoNsys", "skuNsys");
        assertNullable(esquemas, "StockTelefonosFichaMovil", "colorNsys", "gradoNsys", "skuNsys", "ponerProducto", "ponerGrado");
        assertNoNullable(esquemas, "StockTelefonosFichaMovil", "porPasar");
        assertNullable(esquemas, "StockTelefonosMovilBuscado", "colorNsys");
        assertNullable(esquemas, "StockTelefonosMovilComprobado", "colorNsys", "gradoNsys");
        assertNullable(esquemas, "EventoRastro", "dato", "origen", "motivo");

        // 0.9.18: comprobar una lista de IMEIs (spec 2026-10-19 §6.2).
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/comprobar", "post").endsWith("/StockTelefonosPeticionComprobar"));
        assertNoNullable(esquemas, "StockTelefonosPeticionComprobar", "imeis");
        assertNullable(esquemas, "StockTelefonosMovilComprobado", "modelo", "modeloMaestro", "capacidad", "color", "grado",
                "ubicacion", "estado", "fechaVenta");
        assertNoNullable(esquemas, "StockTelefonosMovilComprobado", "imei", "encontrado", "bateriaNueva", "trabajos");

        // 0.9.18: lista de importaciones de la píldora (spec 2026-10-19 §6.5). `historico` siempre viene (con total = 0
        // si no hay ninguna importación HISTORICO), para no depender de un objeto nullable junto a un $ref.
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/importaciones", "get", "200")
                .endsWith("/StockTelefonosListaImportaciones"));
        assertEquals("query", parametro(paths, "/api/stock-telefonos/importaciones", "get", "limite").path("in").asText());
        assertNoNullable(esquemas, "StockTelefonosListaImportaciones", "importaciones", "historico");
        assertNullable(esquemas, "StockTelefonosFilaImportacion", "segundos", "motivo");
        assertNoNullable(esquemas, "StockTelefonosFilaImportacion", "fichero", "fechaExportacion", "resultado", "filas", "cambios");
        assertNullable(esquemas, "StockTelefonosProgresoHistorico", "ultimo", "hasta");
        assertNoNullable(esquemas, "StockTelefonosProgresoHistorico", "total", "hechos");

        // 0.9.21 (spec «SKU del taller» §4): la corrección a mano, la lista «Por pasar a NSYS» y las opciones del diálogo.
        for (String ruta : List.of("/api/stock-telefonos/por-pasar", "/api/stock-telefonos/{imeiSn}/colores",
                "/api/stock-telefonos/{imeiSn}/taller", "/api/stock-telefonos/por-pasar/tarjetas",
                "/api/stock-telefonos/por-pasar/hecho", "/api/stock-telefonos/por-pasar/avisos/{idAviso}/revisado",
                "/api/stock-telefonos/por-pasar/plantilla-color", "/api/stock-telefonos/por-pasar/plantilla-altas",
                "/api/stock-telefonos/por-pasar/descarte")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }
        for (String esquema : List.of("TarjetasPorPasar", "FilaColorNsys", "FilaGradoNsys", "FilaAltaNsys", "FilaRevisarNsys",
                "PorPasarPeticionHecho", "PorPasarPeticionDeshacer", "FilaDescartadaNsys", "PorPasarPeticionDescarte",
                "PorPasarPeticionDeshacerDescarte")) {
            assertTrue(esquemas.has(esquema), () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/{imeiSn}/taller", "put").endsWith("/StockTelefonosPeticionTaller"));
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/{imeiSn}/taller", "delete").endsWith("/StockTelefonosPeticionVolver"));
        assertTrue(refDeLaRespuesta(paths, "/api/stock-telefonos/{imeiSn}/colores", "get", "200").endsWith("/OpcionesTaller"));
        assertNullable(esquemas, "StockTelefonosPeticionTaller", "color", "grado");
        assertNoNullable(esquemas, "StockTelefonosPeticionTaller", "motivo");
        assertNullable(esquemas, "StockTelefonosPeticionVolver", "motivo");
        assertNullable(esquemas, "FilaPorPasar", "modelo", "modeloMaestro", "capacidad", "productoNsys", "gradoNsys",
                "ponerProducto", "ponerGrado", "sku");
        assertNoNullable(esquemas, "FilaPorPasar", "imeiSn", "porQue", "desde");
        assertNullable(esquemas, "PorQueTaller", "usuario", "motivo", "idRep");
        assertNoNullable(esquemas, "PorQueTaller", "dato", "origen");
        assertNullable(esquemas, "OpcionesTaller", "productoActual");
        assertNoNullable(esquemas, "OpcionesTaller", "colores", "skus");
        assertNullable(esquemas, "OpcionColor", "producto");
        assertNoNullable(esquemas, "OpcionColor", "color");
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/por-pasar/descarte", "put").endsWith("/PorPasarPeticionDescarte"));
        assertTrue(refDelCuerpo(paths, "/api/stock-telefonos/por-pasar/descarte", "delete").endsWith("/PorPasarPeticionDeshacerDescarte"));
        assertNoNullable(esquemas, "PorPasarPeticionDescarte", "imeiSn", "motivo");
        assertNoNullable(esquemas, "PorPasarPeticionDeshacerDescarte", "imeiSn");
        assertNullable(esquemas, "FilaAltaNsys", "descartadoAntesPor", "descartadoAntesEn", "descartadoAntesMotivo");
        assertNullable(esquemas, "FilaDescartadaNsys", "modelo", "descartadoPor");
        assertNoNullable(esquemas, "FilaDescartadaNsys", "imeiSn", "ultimoTrabajo", "categoria", "descartadoEn", "motivo");
        assertNoNullable(esquemas, "TarjetasPorPasar", "descartadas");
        assertNoNullable(esquemas, "SkuMaestro", "color", "grado", "sku");
        assertNullable(esquemas, "FilaColorNsys", "colorDe", "colorA", "ponerProducto", "hechoPor", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaGradoNsys", "gradoDe", "gradoA", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaAltaNsys", "modelo", "tecnico", "cliente", "hechoEn", "hechoVolvio");
        assertNullable(esquemas, "FilaRevisarNsys", "modelo", "causa", "usuario");
        assertNullable(esquemas, "FilaPorPasar", "ponerProducto");
        assertTrue(esquemas.path("FilaPorPasar").path("properties").has("ponerCompruebalo"));
        assertTrue(esquemas.path("OpcionColor").path("properties").has("compruebalo"));
        assertTrue(esquemas.path("StockTelefonosFichaMovil").path("properties").has("ponerCompruebalo"));
    }

    /** 0.9.21 (spec «SKU del taller», pieza 2, §2.4): la banda del formulario. */
    @Test void elContratoPublicaLaPreviaDelSkuDelTaller() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");
        String ruta = "/api/reparaciones/sku-taller/previsualizar";
        assertTrue(paths.has(ruta), "falta la ruta de la previsualización del SKU del taller");
        assertTrue(refDelCuerpo(paths, ruta, "post").endsWith("/PeticionPreviaTaller"));
        assertTrue(refDeLaRespuesta(paths, ruta, "post", "200").endsWith("/PreviaTaller"));
        assertNoNullable(esquemas, "PeticionPreviaTaller", "imei", "filas");
        assertNullable(esquemas, "FilaPreviaTaller", "idRep");
        assertNoNullable(esquemas, "FilaPreviaTaller", "idCom", "reutilizada");
        assertNoNullable(esquemas, "PreviaTaller", "enNsys", "antes", "despues", "colorSinDeducir", "gradoSinCalcular");
        assertNullable(esquemas, "ValoresTaller", "color", "grado");
    }

    /** Un parámetro (query o path) de una operación, por nombre. */
    private static JsonNode parametro(JsonNode paths, String ruta, String metodo, String nombre) {
        for (JsonNode p : paths.path(ruta).path(metodo).path("parameters")) {
            if (nombre.equals(p.path("name").asText())) return p;
        }
        return fail(metodo + " " + ruta + " no declara el parámetro " + nombre);
    }

    /** Los códigos de respuesta de una operación son exactamente estos (sin el 200 genérico de ResponseEntity<?>). */
    private static void assertCodigos(JsonNode paths, String ruta, String metodo, String... codigos) {
        List<String> declarados = nombres(paths.path(ruta).path(metodo).path("responses"));
        assertEquals(Set.of(codigos), Set.copyOf(declarados), () -> metodo + " " + ruta + " declara " + declarados);
    }

    /**
     * Reintentos seguros (tarea añadida al cierre 2026-09-19): las cuatro escrituras no repetibles del
     * formulario, más POST /api/asignaciones/lote (sub-proyecto 3b), publican {@code Idempotency-Key}
     * como cabecera opcional, igual que las altas sueltas de solicitud de stock, proveedor, cliente, técnico
     * e incidencia (0.8.5); ninguna otra operación la declara.
     */
    @Test void lasEscriturasDelFormularioAdmitenClaveDeIdempotencia() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        assertNotNull(paths, "el contrato no trae paths");

        Set<String> conCabecera = Set.of(
                "post /api/reparaciones/completa",
                "post /api/reparaciones/{idAsignacion}/filas",
                "post /api/reparaciones/{idAsignacion}/agotar-componente",
                "put /api/reparaciones/{idRep}",
                "post /api/asignaciones/lote",
                "post /api/compras/lote",
                "post /api/compras-otros/lote",
                "post /api/solicitudes-stock",
                "post /api/proveedores",
                "post /api/clientes",
                "post /api/usuarios/tecnicos",
                "post /api/reparaciones/{idRep}/incidencia");

        for (String operacion : conCabecera) {
            String[] partes = operacion.split(" ", 2);
            assertTrue(admiteCabeceraIdempotencia(paths, partes[1], partes[0]),
                    () -> operacion + " no declara " + RegistroIdempotencia.CABECERA + " como cabecera opcional");
        }

        Set<String> metodosHttp = Set.of("get", "post", "put", "patch", "delete");
        paths.fields().forEachRemaining(entradaRuta -> {
            String ruta = entradaRuta.getKey();
            entradaRuta.getValue().fields().forEachRemaining(entradaOp -> {
                String metodo = entradaOp.getKey();
                if (!metodosHttp.contains(metodo)) return;
                if (conCabecera.contains(metodo + " " + ruta)) return;
                assertFalse(admiteCabeceraIdempotencia(paths, ruta, metodo),
                        () -> metodo + " " + ruta + " no debería declarar " + RegistroIdempotencia.CABECERA);
            });
        });
    }

    private static boolean admiteCabeceraIdempotencia(JsonNode paths, String ruta, String metodo) {
        JsonNode parametros = paths.path(ruta).path(metodo).path("parameters");
        if (!parametros.isArray()) return false;
        for (JsonNode p : parametros) {
            if ("header".equals(p.path("in").asText())
                    && RegistroIdempotencia.CABECERA.equals(p.path("name").asText())
                    && !p.path("required").asBoolean(true)) {
                return true;
            }
        }
        return false;
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

    private static void assertNullable(JsonNode esquemas, String esquema, String... campos) {
        for (String campo : campos) {
            JsonNode propiedad = esquemas.path(esquema).path("properties").path(campo);
            assertFalse(propiedad.isMissingNode(), () -> esquema + " sin el campo " + campo);
            assertTrue(propiedad.path("nullable").asBoolean(false), () -> esquema + "." + campo + " debe ser nullable");
        }
    }

    private static void assertNoNullable(JsonNode esquemas, String esquema, String... campos) {
        for (String campo : campos) {
            JsonNode propiedad = esquemas.path(esquema).path("properties").path(campo);
            assertFalse(propiedad.isMissingNode(), () -> esquema + " sin el campo " + campo);
            assertFalse(propiedad.path("nullable").asBoolean(false), () -> esquema + "." + campo + " no debe ser nullable");
            assertTrue(esquemas.path(esquema).path("required").toString().contains("\"" + campo + "\""),
                    () -> esquema + "." + campo + " debe ser required");
        }
    }

    private static List<String> nombres(JsonNode objeto) {
        var lista = new ArrayList<String>();
        objeto.fieldNames().forEachRemaining(lista::add);
        return lista;
    }
}
