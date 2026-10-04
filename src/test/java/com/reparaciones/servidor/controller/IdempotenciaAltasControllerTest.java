package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Reintentos seguros con {@code Idempotency-Key} en las altas sueltas (solicitud de stock, proveedor, cliente,
 * técnico e incidencia), con la cadena HTTP real: misma clave y mismo cuerpo = una sola escritura y la misma
 * respuesta; sin cabecera, cada petición escribe como siempre; misma clave con otro cuerpo = 422.
 */
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
class IdempotenciaAltasControllerTest {

    private static final String IMEI = "355400000000111";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean SolicitudStockDAO solicitudDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean ClienteDAO clienteDao;
    @MockBean UsuarioDAO usuarioDao;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null)); }

    /** Cada test usa su propia clave: el registro es un bean compartido por todo el contexto. */
    private static String claveNueva() {
        return "clave-" + UUID.randomUUID();
    }

    private MockHttpServletResponse enviar(String ruta, String token, String cuerpo, String clave) throws Exception {
        var peticion = post(ruta).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (clave != null) peticion = peticion.header(RegistroIdempotencia.CABECERA, clave);
        return mvc.perform(peticion).andReturn().getResponse();
    }

    /** Dos envíos con la misma clave y el mismo cuerpo: ambos 201 y con la misma respuesta. */
    private void dosVecesMismaRespuesta(String ruta, String token, String cuerpo, String clave) throws Exception {
        MockHttpServletResponse primera = enviar(ruta, token, cuerpo, clave);
        MockHttpServletResponse segunda = enviar(ruta, token, cuerpo, clave);
        assertEquals(201, primera.getStatus());
        assertEquals(201, segunda.getStatus());
        assertEquals(primera.getContentAsString(), segunda.getContentAsString());
    }

    private void dosVecesSinCabecera(String ruta, String token, String cuerpo) throws Exception {
        assertEquals(201, enviar(ruta, token, cuerpo, null).getStatus());
        assertEquals(201, enviar(ruta, token, cuerpo, null).getStatus());
    }

    /** Misma clave con otro cuerpo: el 422 de {@link RegistroIdempotencia} y la segunda no escribe. */
    private void mismaClaveOtroCuerpo(String ruta, String token, String cuerpo, String otroCuerpo) throws Exception {
        String clave = claveNueva();
        assertEquals(201, enviar(ruta, token, cuerpo, clave).getStatus());
        MockHttpServletResponse segunda = enviar(ruta, token, otroCuerpo, clave);
        assertEquals(422, segunda.getStatus());
        assertEquals(RegistroIdempotencia.MSG_CLAVE_REUTILIZADA, segunda.getErrorMessage());
    }

    // ── POST /api/solicitudes-stock ─────────────────────────────────────────────

    private static final String SOLICITUD = "/api/solicitudes-stock";
    private static final String CUERPO_SOLICITUD = "{\"idCom\":111,\"descripcion\":\"pantalla\"}";

    @Test void solicitudStockConLaMismaClaveSeCreaUnaVez() throws Exception {
        dosVecesMismaRespuesta(SOLICITUD, tecnico(), CUERPO_SOLICITUD, claveNueva());
        verify(solicitudDao, times(1)).insertar(111, 8, "pantalla");
        verify(logDao, times(1)).insertar(eq(8), eq("SOLICITAR_STOCK"), any());
    }

    @Test void solicitudStockSinCabeceraSeCreaCadaVez() throws Exception {
        dosVecesSinCabecera(SOLICITUD, tecnico(), CUERPO_SOLICITUD);
        verify(solicitudDao, times(2)).insertar(111, 8, "pantalla");
    }

    @Test void solicitudStockConLaMismaClaveYOtroCuerpoEs422() throws Exception {
        mismaClaveOtroCuerpo(SOLICITUD, tecnico(), CUERPO_SOLICITUD, "{\"idCom\":112,\"descripcion\":\"pantalla\"}");
        verify(solicitudDao, times(1)).insertar(111, 8, "pantalla");
        verify(solicitudDao, times(0)).insertar(eq(112), anyInt(), any());
    }

    // ── POST /api/proveedores ───────────────────────────────────────────────────

    private static final String PROVEEDORES = "/api/proveedores";
    private static final String CUERPO_PROVEEDOR = "{\"nombre\":\"proveedor-a\",\"divisa\":\"EUR\",\"tipo\":\"COMPONENTES\"}";

    @Test void proveedorConLaMismaClaveSeCreaUnaVez() throws Exception {
        dosVecesMismaRespuesta(PROVEEDORES, supertecnico(), CUERPO_PROVEEDOR, claveNueva());
        verify(proveedorDao, times(1)).insertar("proveedor-a", "EUR", "COMPONENTES");
    }

    @Test void proveedorSinCabeceraSeCreaCadaVez() throws Exception {
        dosVecesSinCabecera(PROVEEDORES, supertecnico(), CUERPO_PROVEEDOR);
        verify(proveedorDao, times(2)).insertar("proveedor-a", "EUR", "COMPONENTES");
    }

    @Test void proveedorConLaMismaClaveYOtroCuerpoEs422() throws Exception {
        mismaClaveOtroCuerpo(PROVEEDORES, supertecnico(), CUERPO_PROVEEDOR,
                "{\"nombre\":\"proveedor-b\",\"divisa\":\"EUR\",\"tipo\":\"COMPONENTES\"}");
        verify(proveedorDao, times(1)).insertar("proveedor-a", "EUR", "COMPONENTES");
        verify(proveedorDao, times(0)).insertar(eq("proveedor-b"), any(), any());
    }

    // ── POST /api/clientes ──────────────────────────────────────────────────────

    private static final String CLIENTES = "/api/clientes";
    private static final String CUERPO_CLIENTE = "{\"nombre\":\"cliente-a\"}";

    @Test void clienteConLaMismaClaveSeCreaUnaVez() throws Exception {
        dosVecesMismaRespuesta(CLIENTES, supertecnico(), CUERPO_CLIENTE, claveNueva());
        verify(clienteDao, times(1)).insertar("cliente-a");
        verify(logDao, times(1)).insertar(eq(7), eq("CREAR_CLIENTE"), any());
    }

    @Test void clienteSinCabeceraSeCreaCadaVez() throws Exception {
        dosVecesSinCabecera(CLIENTES, supertecnico(), CUERPO_CLIENTE);
        verify(clienteDao, times(2)).insertar("cliente-a");
    }

    @Test void clienteConLaMismaClaveYOtroCuerpoEs422() throws Exception {
        mismaClaveOtroCuerpo(CLIENTES, supertecnico(), CUERPO_CLIENTE, "{\"nombre\":\"cliente-b\"}");
        verify(clienteDao, times(1)).insertar("cliente-a");
        verify(clienteDao, times(0)).insertar("cliente-b");
    }

    // ── POST /api/usuarios/tecnicos ─────────────────────────────────────────────

    private static final String TECNICOS = "/api/usuarios/tecnicos";
    private static final String CUERPO_TECNICO =
            "{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"TECNICO\"}";

    @Test void tecnicoConLaMismaClaveSeCreaUnaVez() throws Exception {
        dosVecesMismaRespuesta(TECNICOS, admin(), CUERPO_TECNICO, claveNueva());
        verify(usuarioDao, times(1)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        verify(logDao, times(1)).insertar(eq(1), eq("CREAR_USUARIO"), any());
    }

    @Test void tecnicoSinCabeceraSeCreaCadaVez() throws Exception {
        dosVecesSinCabecera(TECNICOS, admin(), CUERPO_TECNICO);
        verify(usuarioDao, times(2)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
    }

    @Test void tecnicoConLaMismaClaveYOtroCuerpoEs422() throws Exception {
        mismaClaveOtroCuerpo(TECNICOS, admin(), CUERPO_TECNICO,
                "{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"SUPERTECNICO\"}");
        verify(usuarioDao, times(1)).registrarTecnico(any(), any(), any(), any());
    }

    /** Un 409 de duplicado no queda registrado: el mismo envío, una vez resuelto el duplicado, sí da de alta. */
    @Test void tecnicoUnDuplicadoNoSeRecuerdaConLaClave() throws Exception {
        String clave = claveNueva();
        when(usuarioDao.existeNombreUsuario("usuario-a")).thenReturn(true);
        MockHttpServletResponse primera = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave);
        assertEquals(409, primera.getStatus());
        assertEquals("{\"message\":\"Ese nombre de usuario ya existe.\"}", primera.getContentAsString());

        when(usuarioDao.existeNombreUsuario("usuario-a")).thenReturn(false);
        assertEquals(201, enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getStatus());
        verify(usuarioDao, times(1)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        verify(logDao, times(1)).insertar(eq(1), eq("CREAR_USUARIO"), any());
    }

    /** Un 422 de validación del alta no queda registrado: el envío corregido con la misma clave da 201. */
    @Test void tecnicoUn422DeValidacionNoSeRecuerdaConLaClave() throws Exception {
        String clave = claveNueva();
        MockHttpServletResponse primera = enviar(TECNICOS, admin(),
                "{\"nombreTecnico\":\"\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"TECNICO\"}",
                clave);
        assertEquals(422, primera.getStatus());
        assertEquals("Todos los campos son obligatorios.", primera.getErrorMessage());

        assertEquals(201, enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getStatus());
        verify(usuarioDao, times(1)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        verify(logDao, times(1)).insertar(eq(1), eq("CREAR_USUARIO"), any());
    }

    /** Un 409 por nombre de técnico repetido tampoco queda registrado. */
    @Test void tecnicoUnNombreDeTecnicoRepetidoNoSeRecuerdaConLaClave() throws Exception {
        String clave = claveNueva();
        when(usuarioDao.existeNombreTecnico("tecnico-a")).thenReturn(true);
        MockHttpServletResponse primera = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave);
        assertEquals(409, primera.getStatus());
        assertEquals("{\"message\":\"Ya existe un técnico con ese nombre.\"}",
                primera.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        verify(usuarioDao, times(0)).registrarTecnico(any(), any(), any(), any());

        when(usuarioDao.existeNombreTecnico("tecnico-a")).thenReturn(false);
        assertEquals(201, enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getStatus());
        verify(usuarioDao, times(1)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
        verify(logDao, times(1)).insertar(eq(1), eq("CREAR_USUARIO"), any());
    }

    /** El reintento con la misma clave devuelve la MISMA temporal (si la respuesta se perdió, es la única forma de tenerla). */
    @Test void tecnicoElReintentoDevuelveLaMismaTemporal() throws Exception {
        String clave = claveNueva();
        String primera = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getContentAsString();
        String segunda = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getContentAsString();
        assertTrue(primera.matches("\\{\"value\":\"[A-Za-z0-9]{10}\"\\}"), primera);
        assertEquals(primera, segunda);
    }

    /** Un cliente viejo que aún mande "password" no la impone: se ignora y la temporal es otra. */
    @Test void tecnicoUnaPasswordEnElCuerpoSeIgnora() throws Exception {
        var resp = enviar(TECNICOS, admin(),
                "{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"password\":\"impuesta-1234\",\"rol\":\"TECNICO\"}", null);
        assertEquals(201, resp.getStatus());
        verify(usuarioDao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), argThat(p -> !"impuesta-1234".equals(p)), eq("TECNICO"));
    }

    // ── POST /api/reparaciones/{idRep}/incidencia ───────────────────────────────

    private static final String INCIDENCIA = "/api/reparaciones/R20260916_5/incidencia";
    private static final String CUERPO_INCIDENCIA = "{\"comentario\":\"no carga\",\"imei\":\"" + IMEI + "\",\"idTec\":4}";

    @Test void incidenciaConLaMismaClaveSeMarcaUnaVez() throws Exception {
        dosVecesMismaRespuesta(INCIDENCIA, supertecnico(), CUERPO_INCIDENCIA, claveNueva());
        verify(reparacionDao, times(1)).marcarIncidenciaYAsignar("R20260916_5", "no carga", IMEI, 4, 3, 7);
        verify(logDao, times(1)).insertar(eq(7), eq("MARCAR_INCIDENCIA"), any());
    }

    @Test void incidenciaSinCabeceraSeMarcaCadaVez() throws Exception {
        dosVecesSinCabecera(INCIDENCIA, supertecnico(), CUERPO_INCIDENCIA);
        verify(reparacionDao, times(2)).marcarIncidenciaYAsignar("R20260916_5", "no carga", IMEI, 4, 3, 7);
    }

    @Test void incidenciaConLaMismaClaveYOtroCuerpoEs422() throws Exception {
        mismaClaveOtroCuerpo(INCIDENCIA, supertecnico(), CUERPO_INCIDENCIA,
                "{\"comentario\":\"no carga\",\"imei\":\"" + IMEI + "\",\"idTec\":5}");
        verify(reparacionDao, times(1)).marcarIncidenciaYAsignar("R20260916_5", "no carga", IMEI, 4, 3, 7);
        verify(reparacionDao, times(0)).marcarIncidenciaYAsignar(any(), any(), any(), eq(5), any(), anyInt());
    }

    /** La clave queda ligada a la reparación del primer uso: sobre otra reparación es 422. */
    @Test void incidenciaConLaMismaClaveSobreOtraReparacionEs422() throws Exception {
        String clave = claveNueva();
        assertEquals(201, enviar(INCIDENCIA, supertecnico(), CUERPO_INCIDENCIA, clave).getStatus());
        assertEquals(422, enviar("/api/reparaciones/R20260916_6/incidencia", supertecnico(),
                CUERPO_INCIDENCIA, clave).getStatus());
        verify(reparacionDao, times(0)).marcarIncidenciaYAsignar(eq("R20260916_6"), any(), any(), anyInt(), any(), anyInt());
    }
}
