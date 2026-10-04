package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un usuario que ya no puede operar recibe 401, que es lo que expulsa en los dos clientes (spec sp7b §5.1 y D4). */
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
class JwtAuthFilterEstadoTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean EstadoUsuarioService estado;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;
    @MockBean UsuarioDAO usuarioDao;
    @MockBean AuthenticationManager authManager;

    private String token() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
    }

    @Test void unUsuarioOperativoPasa() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isOk());
    }

    @Test void unUsuarioDesactivadoOBorradoEs401NoEs403() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isUnauthorized());
    }

    /** El inicio de sesión no pasa por el filtro: aunque el usuario diera negativo en la comprobación,
     *  la petición llega al controlador sin bloquearse, y es el propio login el que decide su 401. */
    @Test void elLoginNoPasaPorLaComprobacion() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("credenciales incorrectas"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x\",\"password\":\"y\"}"))
           .andExpect(status().isUnauthorized());
    }

    /** El caso real que motiva el arreglo: la petición de login trae un token de un usuario que ya no puede
     *  operar (el cliente JavaFX nunca limpia el suyo). Aun así, la comprobación de estado no debe interceptar
     *  la petición: tiene que llegar al controlador y ser este quien decida, con sus propias credenciales. */
    @Test void elLoginConTokenDeUnUsuarioNoOperativoLlegaAlControlador() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("credenciales incorrectas"));
        mvc.perform(post("/api/auth/login")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x\",\"password\":\"y\"}"))
           .andExpect(status().isUnauthorized());
        // El 401 es del controlador, no del filtro: llegó a comprobar las credenciales y a registrar el intento.
        verify(authManager).authenticate(any());
        verify(logDao).insertarIntento(eq("x"), eq("LOGIN_FALLIDO"), any());
    }

    /** Con la contraseña marcada como temporal, cualquier otra ruta responde 403: la sesión es válida, solo
     *  falta ese paso (spec sp7b §5.4, arreglo E3-2). */
    @Test void conPasswordTemporalUnaRutaCualquieraEs403() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        when(estado.tienePasswordTemporal(anyInt())).thenReturn(true);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isForbidden());
    }

    /** La excepción: con la contraseña temporal, cambiarla sigue llegando al controlador. */
    @Test void conPasswordTemporalCambiarPasswordSiLlega() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        when(estado.tienePasswordTemporal(anyInt())).thenReturn(true);
        mvc.perform(patch("/api/auth/cambiar-password")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"secreta1\",\"passwordNueva\":\"tortuga violeta lampara nube 47\"}"))
           .andExpect(status().isNoContent());
    }

    /**
     * El texto de ese 403 es contrato con la web, que lo reconoce para llevar a la pantalla de cambio obligatorio en vez
     * de enseñar "no tienes permisos" en bucle. Si se cambia aquí hay que cambiarlo tambien en
     * gestion-reparaciones-web/src/shared/api/errors.ts (MSG_403_PASSWORD_TEMPORAL), donde otro test fija el mismo texto.
     */
    @Test
    void elTextoDel403EsElQueReconoceLaWeb() {
        assertEquals("Tienes que cambiar la contraseña antes de seguir.", JwtAuthFilter.MSG_PASSWORD_TEMPORAL);
    }
}
