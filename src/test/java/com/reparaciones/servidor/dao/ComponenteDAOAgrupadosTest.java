package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Componente;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orden de los grupos del formulario (spec 0.9.7 §4.2): la tapa trasera justo después del chasis. */
class ComponenteDAOAgrupadosTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    private static Componente c(int id, String tipo) {
        LocalDateTime t = LocalDateTime.of(2026, 10, 9, 10, 0);
        return new Componente(id, tipo, t, 0, 2, true, t);
    }

    @Test @SuppressWarnings("unchecked")
    void laTapaVaJustoDespuesDelChasis() {
        // La base los devuelve en ORDER BY TIPO.
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                c(1, "bati15"), c(2, "cami15"), c(3, "chai15black"), c(4, "gi15negra"), c(5, "lcdi15negraic"),
                c(6, "mci15negra"), c(7, "otroi15"), c(8, "tapai15black")));
        assertEquals(List.of("bat", "cha", "tapa", "g", "mc", "lcd", "cam", "otro"),
                List.copyOf(dao.getAgrupadosPorTipo().keySet()));
    }
}
