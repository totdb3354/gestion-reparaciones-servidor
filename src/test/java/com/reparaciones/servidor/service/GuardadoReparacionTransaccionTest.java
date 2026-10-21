package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frontera transaccional REAL de GuardadoReparacion (spec 0.9.21 pieza 2 §2.1: «si el guardado falla, no queda ningún
 *  cambio»), con el mismo montaje que AsignacionLoteServiceTransaccionTest: contexto mínimo, DataSourceTransactionManager
 *  sobre un DataSource mock y los DAO como objetos normales (no beans, para que el auto-proxy no los envuelva). */
@SpringJUnitConfig(GuardadoReparacionTransaccionTest.Config.class)
class GuardadoReparacionTransaccionTest {

    private static final String IMEI = "355400000000111";
    private static final ReparacionDAO dao = mock(ReparacionDAO.class);
    private static final TallerReparaciones taller = mock(TallerReparaciones.class);

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        GuardadoReparacion guardadoReparacion() {
            return new GuardadoReparacion(dao, taller);
        }
    }

    @Autowired GuardadoReparacion guardado;
    @Autowired DataSource dataSource;

    private Connection connection;

    @BeforeEach
    void reiniciar() throws SQLException {
        reset(dataSource, dao, taller);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    /** Una fila con uso real: el DAO crea una reparación por cada una. */
    private static FilaReparacion uso(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        return f;
    }

    @Test void siElTallerFallaLaReparacionTampocoQueda() throws SQLException {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any())).thenReturn(List.of("R1"));
        doThrow(new DataAccessResourceFailureException("BD caída")).when(taller).aplicar(any(), any(), anyInt());
        assertThrows(DataAccessResourceFailureException.class,
                () -> guardado.insertarCompleta(List.of(uso(131)), IMEI, 4, null, "A1", null, 8));
        verify(taller).aplicar(IMEI, List.of(new TallerReparaciones.PiezaGuardada("R1", 131, false)), 8);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void siLaReparacionFallaNoSeTocaElTallerYNadaQueda() throws SQLException {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));
        assertThrows(ResponseStatusException.class, () -> guardado.insertarCompleta(List.of(), IMEI, 4, null, "A1", null, 8));
        verify(taller).bloquear(IMEI);
        verifyNoMoreInteractions(taller);
        verify(connection).rollback();
    }

    @Test void siBorrarFallaTrasDeshacerNoQuedaDeshecho() throws SQLException {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado")).when(dao).eliminar("R1");
        assertThrows(ResponseStatusException.class, () -> guardado.eliminar("R1", 7));
        verify(taller).deshacer("R1", AvisoTaller.Causa.BORRADA, 7);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void elCaminoFelizHaceCommit() throws SQLException {
        when(dao.guardarFilaIndividual(any(), any(), anyInt(), any(), any())).thenReturn("R1");
        guardado.guardarFilaIndividual(List.of(), IMEI, 4, null, "A1", 8);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
