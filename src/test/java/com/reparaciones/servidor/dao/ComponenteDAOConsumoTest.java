package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Consumo por master y día para la previsión de pedidos (spec 0.9.5 §3.2). Que las solicitudes nunca caen bajo
 *  R…/G… se comprueba con datos reales en preprod; aquí, que el SQL lleva cada filtro y que las filas se leen. */
class ComponenteDAOConsumoTest {

    @SuppressWarnings("unchecked")
    @Test void consultaSoloReparacionesResultantesSinReutilizadasNiOtrosYSumaEnElMaster() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_MASTER")).thenReturn(3);
        when(rs.getDate("DIA")).thenReturn(Date.valueOf(LocalDate.of(2026, 10, 1)));
        when(rs.getInt("UNIDADES")).thenReturn(4);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), any(RowMapper.class), any(), any()))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        LocalDate desde = LocalDate.of(2026, 7, 9);
        LocalDate hasta = LocalDate.of(2026, 10, 7);
        List<ConsumoDia> filas = new ComponenteDAO(jdbc).getConsumoDiario(desde, hasta);

        assertEquals(List.of(new ConsumoDia(3, LocalDate.of(2026, 10, 1), 4)), filas);
        String q = sql.getValue();
        assertTrue(q.contains("r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%'"), q);
        assertTrue(q.contains("rc.ES_REUTILIZADO = 0"), q);
        assertTrue(q.contains("c.TIPO NOT LIKE 'otro%'"), q);
        assertTrue(q.contains("COALESCE(c.ID_COM_MASTER, c.ID_COM) AS ID_MASTER"), q);
        assertTrue(q.contains("r.FECHA_FIN >= ? AND r.FECHA_FIN < ?"), q);
        verify(jdbc).query(anyString(), any(RowMapper.class),
                eq(Timestamp.valueOf(desde.atStartOfDay())), eq(Timestamp.valueOf(hasta.atStartOfDay())));
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
