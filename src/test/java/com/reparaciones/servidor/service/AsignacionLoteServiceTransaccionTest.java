package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba de frontera transaccional REAL de {@link AsignacionLoteService#guardar} (deviación aprobada del
 * task brief de la 3b, tarea 3): no hay BD de test en el proyecto (sin H2/Testcontainers; el
 * {@code application-test.properties} apunta a una MariaDB local que esta suite no usa), así que en vez de
 * levantar el contexto completo con {@code @SpringBootTest} se monta un contexto mínimo
 * ({@code @SpringJUnitConfig}) con solo el servicio real detrás del proxy {@code @Transactional}
 * ({@code @EnableTransactionManagement} + {@code DataSourceTransactionManager}), los DAO mockeados y un
 * {@code DataSource} mock cuyo {@code getConnection()} devuelve una {@code Connection} mock. Así se
 * verifica el efecto real de la transacción (commit/rollback en la conexión), no solo que la anotación
 * esté presente ni que la excepción se propague. Si se quita {@code @Transactional} de {@code guardar}
 * (comprobado manualmente durante el desarrollo), {@code unErrorHaceRollbackYNoCommit} falla porque
 * {@code rollback()} nunca se invoca.
 *
 * <p>Los mocks de los DAO NO se registran como beans de Spring: {@code ReparacionDAO} tiene sus propios
 * métodos anotados con {@code @Transactional}, así que si el mock fuera un bean, el auto-proxy de
 * {@code @EnableTransactionManagement} envolvería también al mock en un proxy transaccional propio —
 * abriendo una transacción anidada dentro del propio stubbing de Mockito y corrompiendo la pila estática
 * de argument matchers (se detectó así: {@code dataSource.getConnection()} fallaba con
 * "InvalidUseOfMatchersException" porque quedaban matchers sin consumir de la llamada a
 * {@code dao.insertarAsignacion(...)}). Solo {@code AsignacionLoteService} —el bean real que sí debe
 * quedar detrás del proxy— se registra como bean; los DAO viajan como objetos Java normales.
 */
@SpringJUnitConfig(AsignacionLoteServiceTransaccionTest.Config.class)
class AsignacionLoteServiceTransaccionTest {

    private static final String IMEI = "111111111111111";
    private static final ReparacionDAO dao = mock(ReparacionDAO.class);
    private static final TelefonoDAO telefonoDao = mock(TelefonoDAO.class);

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
        AsignacionLoteService asignacionLoteService() {
            return new AsignacionLoteService(dao, telefonoDao);
        }
    }

    @Autowired AsignacionLoteService servicio;
    @Autowired DataSource dataSource;

    private Connection connection;

    /** Los beans mock son singletons reutilizados entre tests: se resetean stubs/interacciones en cada uno
     *  y se prepara una Connection mock nueva para poder aserta commit/rollback de forma aislada. */
    @BeforeEach
    void resetMocks() throws SQLException {
        reset(dataSource, dao, telefonoDao);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    private static Peticion lote(AsignacionDelLote... a) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, "13pro", 12, false)), List.of(a));
    }

    @Test void unErrorAMitadHaceRollbackYNuncaCommit() throws SQLException {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt()))
                .thenReturn("A1")
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class, () -> servicio.guardar(lote(
                new AsignacionDelLote(IMEI, "R", 3, null, false),
                new AsignacionDelLote(IMEI, "R", 4, null, false)), 7, 70));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void elCaminoFelizHaceCommitYNuncaRollback() throws SQLException {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt()))
                .thenReturn("A1");

        servicio.guardar(lote(new AsignacionDelLote(IMEI, "R", 3, null, false)), 7, 70);

        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
