package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Las marcas de la fila (espera de stock, en revision, pendiente de color, cancelado) en el DAO (JdbcTemplate simulado; datos inventados). */
@SuppressWarnings("unchecked")
class MarcaDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final Timestamp T_AHORA = Timestamp.valueOf(AHORA);
    private static final String UPD = "UPDATE Pedido_fila SET MARCA = ?, MARCA_NOTA = ?, MARCA_EN = ?, MARCA_USU = ? WHERE ID_FILA = ?";
    private static final String INS = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO, DETALLE) VALUES (?, ?, ?, ?, ?)";
    private static final String QUITAR = "UPDATE Pedido_fila SET MARCA = NULL, MARCA_NOTA = NULL, MARCA_EN = NULL, MARCA_USU = NULL WHERE ID_FILA = ?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private void fila(String origen, String marca, String nota) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_MARCA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            when(rs.getString("MARCA")).thenReturn(marca);
            when(rs.getString("MARCA_NOTA")).thenReturn(nota);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    private void filaConImei(String imei, String marca) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
            when(rs.getString("MARCA")).thenReturn(marca);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void ponerCadaMarcaBloqueaLaFilaLaEscribeConUsuarioYHoraYApuntaMarcaConLaNota() {
        for (String marca : List.of("ESPERA_STOCK", "EN_REVISION", "PENDIENTE_COLOR", "CANCELADO")) {
            clearInvocations(jdbc);
            fila("SHIPPYPRO", null, null);
            assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, marca, "pedir mas", null, 9, AHORA), marca);
            var orden = inOrder(jdbc);
            orden.verify(jdbc).query(eq(FilasDAO.SQL_FILA_MARCA_BLOQUEADA), any(RowMapper.class), eq(5));
            orden.verify(jdbc).update(UPD, marca, "pedir mas", T_AHORA, 9, 5);
            orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA", marca + " — pedir mas");
        }
    }

    @Test void sinNotaElDetalleEsSoloLaMarca() {
        fila("SHIPPYPRO", null, null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", null, "", 9, AHORA));
        verify(jdbc).update(UPD, "CANCELADO", null, T_AHORA, 9, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA", "CANCELADO");
    }

    @Test void cambiarDeMarcaApuntaMarcaNuevaSinQuitada() {
        fila("SHIPPYPRO", "EN_REVISION", null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", null, "EN_REVISION", 9, AHORA));
        verify(jdbc).update(UPD, "CANCELADO", null, T_AHORA, 9, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA", "CANCELADO");
        verify(jdbc, never()).update(eq(INS), eq(5), any(), any(), eq("MARCA_QUITADA"), any());
    }

    @Test void cambiarSoloLaNotaEsUnCambioConApunteNuevo() {
        fila("SHIPPYPRO", "EN_REVISION", "uno");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "EN_REVISION", "dos", null, 9, AHORA));
        verify(jdbc).update(UPD, "EN_REVISION", "dos", T_AHORA, 9, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA", "EN_REVISION — dos");
    }

    @Test void mismaMarcaYMismaNotaNoEscribeNiApunta() {
        fila("SHIPPYPRO", "EN_REVISION", "uno");
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerMarca(5, "EN_REVISION", "uno", null, 9, AHORA));
        fila("SHIPPYPRO", "EN_REVISION", null);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerMarca(5, "EN_REVISION", null, null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void quitarSinMarcaNoHaceNada() {
        fila("SHIPPYPRO", null, null);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerMarca(5, null, null, null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void quitarLaMarcaLaDejaNulaYApuntaMarcaQuitadaConLaQueTenia() {
        fila("SHIPPYPRO", "CANCELADO", "cliente anula");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, null, "ignorada", "CANCELADO", 9, AHORA));
        verify(jdbc).update(UPD, null, null, null, null, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA_QUITADA", "CANCELADO");
    }

    @Test void marcaAnteriorQueNoCoincideEsCambiadaYNoEscribeNada() {
        fila("SHIPPYPRO", "EN_REVISION", null);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerMarca(5, "CANCELADO", null, "", 9, AHORA));
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerMarca(5, "CANCELADO", null, "ESPERA_STOCK", 9, AHORA));
        fila("SHIPPYPRO", null, null);
        assertEquals(FilasDAO.Resultado.CAMBIADA, dao.ponerMarca(5, "CANCELADO", null, "EN_REVISION", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void marcaAnteriorVaciaCoincideConLaFilaSinMarca() {
        fila("SHIPPYPRO", null, null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", null, "", 9, AHORA));
    }

    @Test void historicoEsCerradaYNoExisteEsNoExiste() {
        fila("HISTORICO", null, null);
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerMarca(5, "CANCELADO", null, null, 9, AHORA));
        assertEquals(FilasDAO.Resultado.NO_EXISTE, dao.ponerMarca(6, "CANCELADO", null, null, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void ponerImeiEnFilaConEsperaDeStockQuitaLaMarcaYApuntaMarcaQuitada() {
        filaConImei(null, "ESPERA_STOCK");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, "350000000000002", null, 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "350000000000002", 5);
        orden.verify(jdbc).update(QUITAR, 5);
        orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA_QUITADA", "ESPERA_STOCK — al poner el IMEI");
    }

    @Test void ponerImeiNoTocaLasOtrasMarcas() {
        for (String marca : List.of("EN_REVISION", "PENDIENTE_COLOR", "CANCELADO")) {
            filaConImei(null, marca);
            assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, "350000000000002", null, 9, AHORA));
        }
        verify(jdbc, never()).update(eq(QUITAR), anyInt());
        verify(jdbc, never()).update(eq(INS), anyInt(), any(), any(), any(), any());
    }

    @Test void quitarElImeiNoTocaLaMarcaEsperaDeStock() {
        filaConImei("350000000000001", "ESPERA_STOCK");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, null, null, 9, AHORA));
        verify(jdbc, never()).update(eq(QUITAR), anyInt());
        verify(jdbc, never()).update(eq(INS), anyInt(), any(), any(), any(), any());
    }

    @Test void elHistorialLeeElDetalleYLasConsultasDeFilasTraenLaMarca() {
        assertTrue(FilasDAO.SQL_CAMBIOS_DE_FILA.contains("c.DETALLE"));
        assertTrue(FilasDAO.SQL_FILAS.contains("MARCA_NOTA") && FilasDAO.SQL_FILAS.contains("MARCA_EN"));
        assertTrue(FilasDAO.SQL_FILAS.contains("MARCA_POR"));
        assertEquals("MARCA", CambioImei.MARCA);
        assertEquals("MARCA_QUITADA", CambioImei.MARCA_QUITADA);
        assertNull(new CambioImei("u", AHORA, null, null).detalle());
    }

    @Test void laLecturaDeShippyProYLosCierresNoTocanLaMarca() {
        for (String sql : List.of(FilasDAO.SQL_MARCAR_ENVIO, FilasDAO.SQL_CAMBIAR_ENVIO, FilasDAO.SQL_MARCAR_ETIQUETA,
                FilasDAO.SQL_CERRAR, FilasDAO.SQL_REABRIR))
            assertFalse(sql.contains("MARCA_") || sql.contains("MARCA ="), sql);
    }

    @Test void losVendidosExcluyenLasMarcadasYCuentanLaRetiradaDeLaMarcaComoFecha() {
        assertTrue(FilasDAO.SQL_VENDIDOS.contains("f.MARCA IS NULL"));
        assertTrue(FilasDAO.SQL_VENDIDOS.contains("TIPO IN ('IMEI', 'MARCA_QUITADA')"));
        assertFalse(FilasDAO.SQL_VENDIDOS.contains("TIPO = 'IMEI'"));
    }
}
