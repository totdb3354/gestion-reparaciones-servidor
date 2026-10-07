package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/** Pesos de la previsión en la tabla Parametro (spec 0.9.5 §4.1). */
class ParametroDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ParametroDAO dao = new ParametroDAO(jdbc);

    private static Map<String, Object> fila(String clave, int valor) { return Map.of("CLAVE", clave, "VALOR", valor); }

    @Test void leeLosTresPesos() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 40), fila("PREVISION_PESO_2", 35), fila("PREVISION_PESO_3", 25)));
        assertEquals(new Pesos(40, 35, 25), dao.getPesosPrevision());
    }

    @Test void siFaltaUnaClaveUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 40), fila("PREVISION_PESO_2", 35)));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void siNoSumanCienUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 50), fila("PREVISION_PESO_2", 50), fila("PREVISION_PESO_3", 50)));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void sinTablaUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenThrow(new DataAccessResourceFailureException("no existe"));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void guardaLosTresConUpsert() {
        dao.guardarPesosPrevision(new Pesos(40, 35, 25));
        String sql = "INSERT INTO Parametro (CLAVE, VALOR) VALUES (?, ?) ON DUPLICATE KEY UPDATE VALOR = VALUES(VALOR)";
        verify(jdbc).update(sql, "PREVISION_PESO_1", 40);
        verify(jdbc).update(sql, "PREVISION_PESO_2", 35);
        verify(jdbc).update(sql, "PREVISION_PESO_3", 25);
    }
}
