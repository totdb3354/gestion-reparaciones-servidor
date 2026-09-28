package com.reparaciones.servidor.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un token bien firmado pero incompleto es 401 (spec sp7b §4.5). */
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
class JwtAuthFilterTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @Value("${jwt.secret}") String secreto;

    @MockBean com.reparaciones.servidor.dao.ReparacionDAO reparacionDao;
    @MockBean com.reparaciones.servidor.dao.LogDAO logDao;

    private String firmar(java.util.Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("alguien")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        claims.forEach(b::claim);
        return b.signWith(key).compact();
    }

    @Test void tokenSinIdUsuEs401() throws Exception {
        String token = firmar(java.util.Map.of("rol", "TECNICO"));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenSinRolEs401() throws Exception {
        String token = firmar(java.util.Map.of("idUsu", 8));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenConRolVacioEs401() throws Exception {
        String token = firmar(java.util.Map.of("idUsu", 8, "rol", "  "));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    /** Un claim con otro tipo del esperado (idUsu como texto) no rompe el filtro: 401, no 500. */
    @Test void tokenConClaimDeTipoEquivocadoEs401() throws Exception {
        String token = firmar(java.util.Map.of("idUsu", "ocho", "rol", "TECNICO"));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenCompletoPasa() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());
    }

    /**
     * Sin cabecera Authorization, la petición no lleva credenciales de ningún tipo: la rechaza
     * la configuración de seguridad antes de llegar al filtro de JWT, con 403 (no hay
     * AuthenticationEntryPoint registrado, igual que documenta OpenApiContractTest). El valor que
     * importa aquí es el mismo que en los demás casos: sin credenciales válidas, no se llega al
     * controlador.
     */
    @Test void sinCabeceraLaRechazaLaSeguridad() throws Exception {
        mvc.perform(get("/api/reparaciones/historial")).andExpect(status().isForbidden());
    }
}
