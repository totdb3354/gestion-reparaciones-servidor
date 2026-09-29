package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El alta de un técnico deja su contraseña marcada como temporal, igual que un restablecimiento
 *  (spec sp7b §5.4): la elige el administrador, así que quien la recibe tiene que cambiarla al entrar. */
class UsuarioDAORegistrarTecnicoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UsuarioDAO dao = new UsuarioDAO(jdbc, passwordEncoder);

    @Test void elAltaDejaLaContrasenaMarcadaComoTemporal() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash-secreta1");
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 4));
            return 1;
        });

        dao.registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO");

        verify(jdbc).update(
                "INSERT INTO Usuario (NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC, PASSWORD_TEMPORAL) VALUES (?, ?, ?, ?, 1)",
                "usuario-a", "hash-secreta1", "TECNICO", 4);
    }
}
