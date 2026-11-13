package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La marca «falta enviar» y el aviso del plugin de la web en el DAO (JdbcTemplate simulado; datos inventados). */
@SuppressWarnings("unchecked")
class FaltaEnviarDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final LocalDateTime ENVIO = LocalDateTime.of(2026, 11, 12, 9, 0);
    private static final Timestamp T_AHORA = Timestamp.valueOf(AHORA);
    private static final String UPD_FALTA = "UPDATE Pedido_fila SET FALTA_ENVIAR_EN = ?, FALTA_ENVIAR_USU = ? WHERE ID_FILA = ?";
    private static final String INS_TIPO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO) VALUES (?, ?, ?, ?)";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private void fila(String origen, String canal, LocalDateTime envio, LocalDateTime marca) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_FALTA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            when(rs.getString("CANAL")).thenReturn(canal);
            when(rs.getTimestamp("ENVIO_FECHA")).thenReturn(envio == null ? null : Timestamp.valueOf(envio));
            when(rs.getTimestamp("FALTA_ENVIAR_EN")).thenReturn(marca == null ? null : Timestamp.valueOf(marca));
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void marcarEnWebConEtiquetaBloqueaPoneLaMarcaConUsuarioYApuntaFaltaEnviar() {
        fila("SHIPPYPRO", "PARTICULARES", ENVIO, null);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(FilasDAO.SQL_FILA_FALTA_BLOQUEADA), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update(UPD_FALTA, T_AHORA, 9, 5);
        orden.verify(jdbc).update(INS_TIPO, 5, 9, T_AHORA, "FALTA_ENVIAR");
    }

    @Test void losTresCanalesWebSeMarcan() {
        for (String canal : List.of("DISTRIBUIDORES", "PARTICULARES", "BACKIPHONE")) {
            fila("SHIPPYPRO", canal, ENVIO, null);
            assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerFaltaEnviar(5, true, 9, AHORA), canal);
        }
    }

    @Test void marcarEnAmazonNoEscribeNada() {
        for (String canal : List.of("AMAZON", "AMAZON_STOCKROOM")) {
            fila("SHIPPYPRO", canal, ENVIO, null);
            assertEquals(FilasDAO.Resultado.NO_MARCABLE, dao.ponerFaltaEnviar(5, true, 9, AHORA), canal);
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void marcarSinEtiquetaOSinCanalNoEscribeNada() {
        fila("SHIPPYPRO", "PARTICULARES", null, null);
        assertEquals(FilasDAO.Resultado.NO_MARCABLE, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        fila("SHIPPYPRO", null, ENVIO, null);
        assertEquals(FilasDAO.Resultado.NO_MARCABLE, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void unaFilaDelHistoricoEsCerradaYUnaQueNoExisteNoExiste() {
        fila("HISTORICO", null, null, null);
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerFaltaEnviar(5, false, 9, AHORA));
        assertEquals(FilasDAO.Resultado.NO_EXISTE, dao.ponerFaltaEnviar(6, true, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void marcarUnaFilaYaMarcadaNoHaceNadaNiApunta() {
        fila("SHIPPYPRO", "PARTICULARES", ENVIO, AHORA.minusHours(1));
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void desmarcarUnaSinMarcaNoHaceNadaNiApunta() {
        fila("SHIPPYPRO", "PARTICULARES", ENVIO, null);
        assertEquals(FilasDAO.Resultado.SIN_CAMBIO, dao.ponerFaltaEnviar(5, false, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void desmarcarQuitaLaMarcaYApuntaYaEnviadoConElUsuario() {
        fila("SHIPPYPRO", "PARTICULARES", ENVIO, AHORA.minusHours(1));
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerFaltaEnviar(5, false, 9, AHORA));
        verify(jdbc).update(UPD_FALTA, null, null, 5);
        verify(jdbc).update(INS_TIPO, 5, 9, T_AHORA, "YA_ENVIADO");
    }

    @Test void desmarcarSiempreSePuedeAunqueYaNoCumplaLasReglasDeMarcar() {
        // Fila marcada de un canal que no es web y sin etiqueta (p. ej. datos corregidos a mano): quitar la marca no exige nada mas.
        fila("SHIPPYPRO", "AMAZON", null, AHORA.minusHours(1));
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerFaltaEnviar(5, false, 9, AHORA));
    }

    @Test void laLecturaDeShippyProYLosCierresNoTocanLaMarca() {
        for (String sql : List.of(FilasDAO.SQL_MARCAR_ENVIO, FilasDAO.SQL_CAMBIAR_ENVIO, FilasDAO.SQL_MARCAR_ETIQUETA,
                FilasDAO.SQL_CERRAR, FilasDAO.SQL_REABRIR))
            assertFalse(sql.contains("FALTA_ENVIAR") || sql.contains("SINCRONIZADA"), sql);
    }

    @Test void elHistorialLeeElTipo() {
        assertTrue(FilasDAO.SQL_CAMBIOS_DE_FILA.contains("c.TIPO"));
        assertEquals("IMEI", new CambioImei("u", AHORA, null, null).tipo());
    }

    // ---------------------------------------------------------------- sincronizar

    private void filasDePedido(String pedido, Object[]... filas) {
        when(jdbc.query(eq(FilasDAO.SQL_FILAS_DE_PEDIDO_BLOQUEADAS), any(RowMapper.class), eq("PARTICULARES"), eq(pedido))).thenAnswer(inv -> {
            List<Object> r = new ArrayList<>();
            for (Object[] f : filas) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getInt("ID_FILA")).thenReturn((Integer) f[0]);
                when(rs.getString("IMEI")).thenReturn((String) f[1]);
                when(rs.getTimestamp("FALTA_ENVIAR_EN")).thenReturn(f[2] == null ? null : Timestamp.valueOf((LocalDateTime) f[2]));
                r.add(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0));
            }
            return r;
        });
    }

    private static FilasDAO.PedidoSincronizado ped(String numero, String... imeis) {
        return new FilasDAO.PedidoSincronizado(numero, Set.of(imeis));
    }

    @Test void unParcialSincronizaLasDosQueVienenYDejaLaTerceraConSuMarca() {
        filasDePedido("37352", new Object[]{1, "350000000000001", AHORA.minusDays(1)}, new Object[]{2, "350000000000002", null},
                new Object[]{3, "350000000000003", AHORA.minusDays(1)});
        var r = dao.sincronizar(Canal.PARTICULARES, List.of(ped("37352", "350000000000001", "350000000000002")), AHORA);
        assertEquals(new FilasDAO.InformeSincronizacion(2, 1, 0), r);
        verify(jdbc).update(FilasDAO.SQL_SINCRONIZAR, T_AHORA, 1);
        verify(jdbc).update(FilasDAO.SQL_SINCRONIZAR, T_AHORA, 2);
        verify(jdbc, never()).update(eq(FilasDAO.SQL_SINCRONIZAR), eq(T_AHORA), eq(3));
        // La marca de la fila 1 se quita con apunte YA_ENVIADO sin usuario; la 3 conserva la suya; la 2 no tenia.
        verify(jdbc).update(FilasDAO.SQL_QUITAR_FALTA, 1);
        verify(jdbc).update(eq(INS_TIPO), eq(1), isNull(), eq(T_AHORA), eq("YA_ENVIADO"));
        verify(jdbc, times(1)).update(eq(INS_TIPO), anyInt(), any(), any(), any());
        verify(jdbc, never()).update(eq(FilasDAO.SQL_QUITAR_FALTA), eq(3));
    }

    @Test void repetirLaLlamadaNoApuntaNadaNuevo() {
        filasDePedido("37352", new Object[]{1, "350000000000001", null}, new Object[]{3, "350000000000003", AHORA.minusDays(1)});
        var r = dao.sincronizar(Canal.PARTICULARES, List.of(ped("37352", "350000000000001")), AHORA);
        assertEquals(new FilasDAO.InformeSincronizacion(1, 0, 0), r);
        verify(jdbc, never()).update(startsWith("INSERT"), any(Object[].class));
        verify(jdbc, never()).update(eq(FilasDAO.SQL_QUITAR_FALTA), any(Object[].class));
    }

    @Test void losImeiQueNoCasanSeCuentanEnSinFilaYUnPedidoSinFilasSeIgnora() {
        filasDePedido("37352", new Object[]{1, "350000000000001", null}, new Object[]{2, null, null});
        var r = dao.sincronizar(Canal.PARTICULARES,
                List.of(ped("37352", "350000000000001", "350000000000009"), ped("99999", "350000000000007", "350000000000008")), AHORA);
        assertEquals(new FilasDAO.InformeSincronizacion(1, 0, 3), r);
    }

    @Test void buscaSoloFilasDeShippyProDeEseCanalYPedido() {
        assertTrue(FilasDAO.SQL_FILAS_DE_PEDIDO_BLOQUEADAS.contains("ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND PEDIDO = ?"));
        assertTrue(FilasDAO.SQL_FILAS_DE_PEDIDO_BLOQUEADAS.endsWith("FOR UPDATE"));
    }
}
