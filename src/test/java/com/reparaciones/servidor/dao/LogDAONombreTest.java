package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El nombre del usuario viaja con la línea del registro (spec sp7b §5.6). */
class LogDAONombreTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LogDAO dao = new LogDAO(jdbc);

    @Test void insertarGuardaElNombreResolviendoloDelUsuario() {
        dao.insertar(8, "LOGIN", "");
        verify(jdbc).update(contains("SELECT ?, NOMBRE_USUARIO"), eq(8), eq("LOGIN"), eq(""), eq(null), eq(8));
    }

    @Test void insertarIntentoGuardaElNombreSinUsuario() {
        dao.insertarIntento("alguien", "LOGIN_FALLIDO", "ORIGEN: 10.0.0.1");
        verify(jdbc).update(contains("VALUES (NULL, ?, ?, ?, NULL)"),
                eq("alguien"), eq("LOGIN_FALLIDO"), eq("ORIGEN: 10.0.0.1"));
    }

    @Test void insertarIntentoRecortaUnNombreDemasiadoLargo() {
        String nombreLargo = "a".repeat(60);
        dao.insertarIntento(nombreLargo, "LOGIN_FALLIDO", "ORIGEN: 10.0.0.1");
        verify(jdbc).update(contains("VALUES (NULL, ?, ?, ?, NULL)"),
                eq(nombreLargo.substring(0, LogDAO.MAX_NOMBRE_USUARIO)), eq("LOGIN_FALLIDO"), eq("ORIGEN: 10.0.0.1"));
    }

    @Test void elListadoUsaUnJoinQueConservaLasLineasSinUsuario() {
        dao.getFiltered(null, null, null, null);
        verify(jdbc).query(contains("LEFT JOIN Usuario u"), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
    }
}
