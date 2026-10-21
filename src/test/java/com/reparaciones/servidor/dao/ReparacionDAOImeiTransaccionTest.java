package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** El IMEI de una R… se lee de verdad fuera de la transacción del que llama (0.9.21, pieza 2): con el proxy de Spring sobre el
 *  DAO, imeiDe va por otra conexión (sin candados ni la foto de REPEATABLE READ de la transacción) y bloquearReparacion por
 *  la de la transacción. Montaje como el de GuardadoReparacionTransaccionTest, pero con el DAO como bean (para que lo envuelva
 *  el proxy) y un JdbcTemplate de verdad sobre un DataSource mock. */
@SpringJUnitConfig(ReparacionDAOImeiTransaccionTest.Config.class)
class ReparacionDAOImeiTransaccionTest {

    private static final String IMEI = "355400000000111";
    private static final String SIN_BLOQUEAR = "SELECT IMEI FROM Reparacion WHERE ID_REP = ?";
    private static final String BLOQUEANDO = SIN_BLOQUEAR + " FOR UPDATE";

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
        ReparacionDAO reparacionDAO(DataSource dataSource) {
            return new ReparacionDAO(new JdbcTemplate(dataSource), mock(BorradorDAO.class), mock(MovimientoDAO.class));
        }
    }

    @Autowired ReparacionDAO dao;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** Una conexión de mentira: cada consulta devuelve una fila con el IMEI. */
    private static Connection conexion() throws SQLException {
        Connection conexion = mock(Connection.class);
        when(conexion.prepareStatement(anyString())).thenAnswer(invocacion -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString(1)).thenReturn(IMEI);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(ps.executeQuery()).thenReturn(rs);
            return ps;
        });
        return conexion;
    }

    @Test void elImeiVaPorOtraConexionYElCandadoPorLaDeLaTransaccion() throws SQLException {
        Connection laDeLaTransaccion = conexion();
        Connection otra = conexion();
        when(dataSource.getConnection()).thenReturn(laDeLaTransaccion, otra);

        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            assertEquals(Optional.of(IMEI), dao.imeiDe("R20260916_5"));
            assertEquals(Optional.of(IMEI), dao.bloquearReparacion("R20260916_5"));
        });

        verify(otra).prepareStatement(SIN_BLOQUEAR);
        verify(otra, never()).prepareStatement(BLOQUEANDO);
        verify(laDeLaTransaccion).prepareStatement(BLOQUEANDO);
        verify(laDeLaTransaccion, never()).prepareStatement(SIN_BLOQUEAR);
        verify(laDeLaTransaccion).commit();
    }
}
