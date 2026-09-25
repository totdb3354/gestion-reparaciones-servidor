package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frontera transaccional REAL de CompraLoteService (spec 4b §4.4: un lote es todo o nada): servicio real detrás del
 *  proxy @Transactional, DataSource mock y Connection mock para asertar commit/rollback. Si se quita @Transactional,
 *  unFalloEnLaSegundaLineaHaceRollbackYNuncaCommit falla porque rollback() nunca se invoca. */
@SpringJUnitConfig(CompraLoteServiceTransaccionTest.Config.class)
class CompraLoteServiceTransaccionTest {

    private static final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    private static final CompraOtroDAO compraOtroDao = mock(CompraOtroDAO.class);
    private static final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private static final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);

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
        CompraLoteService compraLoteService() {
            return new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao);
        }
    }

    @Autowired CompraLoteService servicio;
    @Autowired DataSource dataSource;

    private Connection connection;

    @BeforeEach
    void resetMocks() throws SQLException {
        reset(dataSource, compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    private static CompraLoteService.LineaCompra linea(int idCom) {
        return new CompraLoteService.LineaCompra(idCom, 2, 1, false, 0.0, "EUR", 0.0);
    }

    @Test void unFalloEnLaSegundaLineaHaceRollbackYNuncaCommit() throws SQLException {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(41)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarCompras(List.of(linea(1), linea(7)), List.of(11), List.of()));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verifyNoInteractions(reparacionComponenteDao);
    }

    @Test void elCaminoFelizHaceCommitYNuncaRollback() throws SQLException {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(41);

        servicio.guardarCompras(List.of(linea(1)), List.of(11), List.of(21));

        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test void unFalloEnElLoteDeOtrosHaceRollbackYNuncaCommit() throws SQLException {
        when(compraOtroDao.insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(51)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class, () -> servicio.guardarOtros(List.of(
                new CompraLoteService.LineaOtro(2, "Cinta de embalar", 1, false, 0.0, "EUR", 0.0),
                new CompraLoteService.LineaOtro(2, "Bolsas", 1, false, 0.0, "EUR", 0.0))));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
