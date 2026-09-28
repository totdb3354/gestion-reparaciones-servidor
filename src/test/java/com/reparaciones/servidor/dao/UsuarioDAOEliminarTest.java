package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Borrar un usuario ya no borra su registro de actividad (spec sp7b §5.6, revisa la decisión G8 del SP6). */
class UsuarioDAOEliminarTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioDAO dao = new UsuarioDAO(jdbc, mock(PasswordEncoder.class));

    @Test void borrarUnTecnicoNoBorraSuRegistroDeActividad() {
        dao.eliminarTecnico(4, 8);
        verify(jdbc, never()).update(contains("DELETE FROM Log_Actividad"), anyInt());
        verify(jdbc).update("DELETE FROM Usuario WHERE ID_USU = ?", 8);
        verify(jdbc).update("DELETE FROM Tecnico WHERE ID_TEC = ?", 4);
    }
}
