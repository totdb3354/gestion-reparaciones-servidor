package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReparacionDAOEstadisticasPuntosTest {

    @SuppressWarnings("unchecked")
    @Test void laConsultaDePuntosLeeLaObservacionDeCadaPieza() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class))
                .getEstadisticasPuntos("Mes", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Map.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(), any());
        assertTrue(sql.getValue().contains("c.TIPO, rc.CANTIDAD, rc.OBSERVACIONES"), sql.getValue());
    }
}
