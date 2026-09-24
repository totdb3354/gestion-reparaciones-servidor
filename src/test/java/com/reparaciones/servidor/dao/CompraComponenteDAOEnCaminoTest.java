package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La cantidad en camino de un SKU compartido es la de su master: las compras se insertan siempre en el master
 *  (insertar → resolveToMasterId), así que consultar por el id del slave devolvía 0 (spec 4a §4.1). */
class CompraComponenteDAOEnCaminoTest {

    @Test void unSlaveConsultaLaSumaDeSuMaster() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        when(jdbc.queryForObject(contains("SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0))"), eq(Integer.class), eq(3))).thenReturn(5);

        int enCamino = new CompraComponenteDAO(jdbc).getCantidadEnCaminoPorComponente(12);

        assertEquals(5, enCamino);
        verify(jdbc, never()).queryForObject(contains("SUM(CANTIDAD"), eq(Integer.class), eq(12));
    }

    @Test void unMasterConsultaSuPropioId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(3))).thenReturn(3);
        when(jdbc.queryForObject(contains("SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0))"), eq(Integer.class), eq(3))).thenReturn(2);

        assertEquals(2, new CompraComponenteDAO(jdbc).getCantidadEnCaminoPorComponente(3));
    }
}
