package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El mínimo es el suelo de la previsión y se guarda en el master del grupo compartido (spec 0.9.5 §4.2). */
class ComponenteDAOMinimoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    @Test void elMinimoDeUnSlaveSeGuardaEnSuMaster() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        dao.setStockMinimo(12, 4);
        verify(jdbc).update("UPDATE Componente SET STOCK_MINIMO = ? WHERE ID_COM = ?", 4, 3);
    }

    @Test void editarStockYaNoTocaElMinimo() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(5))).thenReturn(5);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        dao.actualizar(5, "lcd-x", 7, t);
        // El UPDATE ya no lleva STOCK_MINIMO: se comprueba con el SQL exacto.
        verify(jdbc).update(eq("UPDATE Componente SET TIPO = ?, STOCK = ? WHERE ID_COM = ? AND UPDATED_AT = ?"),
                eq("lcd-x"), eq(7), eq(5), any());
    }
}
