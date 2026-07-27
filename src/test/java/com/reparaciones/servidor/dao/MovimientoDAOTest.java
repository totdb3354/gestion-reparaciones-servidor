package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.MovimientoTelefono;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MovimientoDAOTest {

    private static final String IMEI = "351111112222333";

    @Test void registrarInsertaConTodosLosCampos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new MovimientoDAO(jdbc).registrar(IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
        verify(jdbc).update(
                "INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA) VALUES (?, ?, ?, ?, ?, ?)",
                IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void getPorImeiVacioDevuelveListaVacia() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("FROM Movimiento_telefono m"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        assertTrue(new MovimientoDAO(jdbc).getPorImei(IMEI).isEmpty());
    }

    @Test void ubicacionDeMapeaTodosLosEstados() {
        assertEquals("ALMACEN",      MovimientoDAO.ubicacionDe("RECIBIDO", null));
        assertEquals("PARA_REVISAR", MovimientoDAO.ubicacionDe("EN_REVISION", null));
        assertEquals("BLOQUEO",      MovimientoDAO.ubicacionDe("BLOQUEADO", null));
        assertEquals("LISTOS",       MovimientoDAO.ubicacionDe("OK", null));
        assertEquals("PEDIDOS",      MovimientoDAO.ubicacionDe("OK", 5));
        assertEquals("ENVIADO",      MovimientoDAO.ubicacionDe("ENVIADO", null));
        assertEquals("DESGUACE",     MovimientoDAO.ubicacionDe("DESGUACE", null));
        assertNull(MovimientoDAO.ubicacionDe(null, null));
    }
}
