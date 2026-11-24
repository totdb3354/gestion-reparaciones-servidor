package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La asociacion IMEI -> pedido de los libros de ventas de Amazon (spec «asociación de IMEI y pedido en Amazon»): las cuatro reglas,
 *  sobre una tabla falsa detras del JdbcTemplate simulado (datos inventados). */
@SuppressWarnings("unchecked")
class AsociacionAmazonTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final LocalDateTime ANTES = LocalDateTime.of(2026, 11, 12, 9, 30);
    private static final Timestamp T = Timestamp.valueOf(AHORA);
    private static final String PA = "402-0000000-0000001";
    private static final String PB = "402-0000000-0000002";
    private static final String PC = "402-0000000-0000003";
    private static final String X = "350000000000001";
    private static final String Y = "350000000000002";
    private static final String Z = "350000000000003";
    private static final String W = "350000000000004";

    /** Una fila de ShippyPro de la tabla falsa. */
    private static final class Fila {
        final int id;
        final String pedido;
        final Integer posicion;
        String canal = "AMAZON";
        String imei;
        LocalDateTime envio;
        String marca;
        LocalDateTime asociadaEn;
        String asociadaOrigen;
        String sku = "i14128AzulA";

        Fila(int id, String pedido, Integer posicion, String imei) {
            this.id = id;
            this.pedido = pedido;
            this.posicion = posicion;
            this.imei = imei;
        }

        Fila etiqueta() {
            envio = ANTES;
            return this;
        }

        Fila marca(String m) {
            marca = m;
            return this;
        }

        Fila asociada(String origen) {
            asociadaEn = ANTES;
            asociadaOrigen = origen;
            return this;
        }

        Fila canal(String c) {
            canal = c;
            return this;
        }

        String estado() {
            return id + " " + canal + " " + pedido + " " + posicion + " " + imei + " " + envio + " " + marca + " " + asociadaEn + " " + asociadaOrigen;
        }
    }

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    /** IMEI → rechazo que da el comprobador falso (por defecto, todo vale). */
    private final Map<String, ComprobadorLibro.Rechazo> rechazos = new HashMap<>();
    /** Lo que se le pidio comprobar en cada pasada (IMEI → SKU del pedido). */
    private final List<Map<String, String>> comprobados = new ArrayList<>();
    private final ComprobadorLibro comprobador = mock(ComprobadorLibro.class);
    private final AsociacionAmazon servicio = new AsociacionAmazon(jdbc, comprobador);
    private final List<Fila> tabla = new ArrayList<>();
    private final List<Object[]> apuntes = new ArrayList<>();
    /** {canal, pedido, imei, motivo, vistoEn}. */
    private final List<Object[]> revisar = new ArrayList<>();
    private final List<Object[]> pasadas = new ArrayList<>();
    private final List<String> consultas = new ArrayList<>();
    private final List<Object[]> libros = new ArrayList<>();

    @BeforeEach void preparar() {
        when(comprobador.rechazos(any(Canal.class), anyMap())).thenAnswer(inv -> {
            Map<String, String> pedidos = inv.getArgument(1);
            comprobados.add(Map.copyOf(pedidos));
            Map<String, ComprobadorLibro.Rechazo> r = new HashMap<>();
            for (String imei : pedidos.keySet()) if (rechazos.containsKey(imei)) r.put(imei, rechazos.get(imei));
            return r;
        });
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            consultas.add(sql);
            RowMapper<Object> mapa = inv.getArgument(1);
            Object[] args = Arrays.copyOfRange(inv.getArguments(), 2, inv.getArguments().length);
            Set<Object> valores = new HashSet<>(Arrays.asList(args).subList(1, args.length));
            boolean porPedido = sql.contains("PEDIDO IN (");
            List<Object> r = new ArrayList<>();
            for (Fila f : tabla)
                if (f.canal.equals(args[0]) && valores.contains(porPedido ? f.pedido : f.imei)) r.add(mapa.mapRow(rs(f), r.size()));
            return r;
        });
        when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            List<Object[]> lote = inv.getArgument(1);
            if (sql.equals(AsociacionAmazon.SQL_ACTUALIZAR)) {
                for (Object[] a : lote) {
                    Fila f = porId((Integer) a[3]);
                    f.imei = (String) a[0];
                    f.asociadaEn = a[1] == null ? null : ((Timestamp) a[1]).toLocalDateTime();
                    f.asociadaOrigen = (String) a[2];
                }
            } else if (sql.equals(AsociacionAmazon.SQL_QUITAR_ESPERA)) {
                for (Object[] a : lote) if ("ESPERA_STOCK".equals(porId((Integer) a[0]).marca)) porId((Integer) a[0]).marca = null;
            } else if (sql.equals(AsociacionAmazon.SQL_APUNTE)) {
                apuntes.addAll(lote);
            } else if (sql.equals(AsociacionAmazon.SQL_INSERTAR_REVISAR)) {
                revisar.addAll(lote);
            } else {
                fail(sql);
            }
            return new int[lote.size()];
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Object[] args = Arrays.copyOfRange(inv.getArguments(), 1, inv.getArguments().length);
            if (sql.equals(AsociacionAmazon.SQL_BORRAR_REVISAR)) revisar.removeIf(r -> r[0].equals(args[0]));
            else if (sql.equals(AsociacionAmazon.SQL_GUARDAR_PASADA)) pasadas.add(args);
            else if (sql.equals(AsociacionAmazon.SQL_GUARDAR_LIBRO)) libros.add(args);
            else if (!sql.equals(AsociacionAmazon.SQL_TOMAR_PASADA)) fail(sql);
            return 1;
        });
    }

    private static Timestamp ts(LocalDateTime t) {
        return t == null ? null : Timestamp.valueOf(t);
    }

    private static ResultSet rs(Fila f) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_FILA")).thenReturn(f.id);
        when(rs.getString("PEDIDO")).thenReturn(f.pedido);
        when(rs.getObject("POSICION", Integer.class)).thenReturn(f.posicion);
        when(rs.getString("IMEI")).thenReturn(f.imei);
        when(rs.getTimestamp("ENVIO_FECHA")).thenReturn(ts(f.envio));
        when(rs.getString("MARCA")).thenReturn(f.marca);
        when(rs.getTimestamp("ASOCIADA_EN")).thenReturn(ts(f.asociadaEn));
        when(rs.getString("ASOCIADA_ORIGEN")).thenReturn(f.asociadaOrigen);
        when(rs.getString("SKU")).thenReturn(f.sku);
        return rs;
    }

    private Fila porId(int id) {
        return tabla.stream().filter(f -> f.id == id).findFirst().orElseThrow();
    }

    private Fila fila(int id, String pedido, Integer posicion, String imei) {
        Fila f = new Fila(id, pedido, posicion, imei);
        tabla.add(f);
        return f;
    }

    /** Pares pedido, imei, pedido, imei... del canal AMAZON. */
    private RespuestaAsociaciones aplicar(String... pedidoImei) {
        return servicio.aplicar(Canal.AMAZON, pares(pedidoImei), AHORA);
    }

    private static List<PeticionAsociaciones.Par> pares(String... pedidoImei) {
        List<PeticionAsociaciones.Par> pares = new ArrayList<>();
        for (int i = 0; i < pedidoImei.length; i += 2) pares.add(new PeticionAsociaciones.Par(pedidoImei[i], pedidoImei[i + 1]));
        return pares;
    }

    private static RespuestaAsociaciones r(int confirmadas, int intercambiadas, int puestas, int yaEstaban, int paraRevisar, int descartadas) {
        return new RespuestaAsociaciones(confirmadas, intercambiadas, puestas, yaEstaban, paraRevisar, descartadas, 0);
    }

    private static RespuestaAsociaciones r(int confirmadas, int intercambiadas, int puestas, int yaEstaban, int paraRevisar, int descartadas,
                                           int ajenos) {
        return new RespuestaAsociaciones(confirmadas, intercambiadas, puestas, yaEstaban, paraRevisar, descartadas, ajenos);
    }

    /** Un apunte del historial, sin usuario (lo hace una maquina) y con la hora de la pasada. */
    private static String apunte(int id, String anterior, String nuevo, String tipo, String detalle) {
        return Arrays.toString(new Object[]{id, null, T, anterior, nuevo, tipo, detalle});
    }

    private static String imei(int id, String anterior, String nuevo) {
        return apunte(id, anterior, nuevo, "IMEI", null);
    }

    private static String asociada(int id) {
        return apunte(id, null, null, "ASOCIADA", "LIBRO");
    }

    private static String intercambiado(int id, int con) {
        return apunte(id, null, null, "IMEI_INTERCAMBIADO", "con la fila " + con);
    }

    private List<String> apuntes() {
        return apuntes.stream().map(Arrays::toString).toList();
    }

    private List<String> revisar() {
        return revisar.stream().map(Arrays::toString).toList();
    }

    private static String paraRevisar(String pedido, String imei, String motivo) {
        return paraRevisar(pedido, imei, motivo, null);
    }

    private static String paraRevisar(String pedido, String imei, String motivo, String detalle) {
        return Arrays.toString(new Object[]{"AMAZON", pedido, imei, motivo, detalle, T});
    }

    @Test void elEstadoLeeElDetalleDeParaRevisar() {
        when(jdbc.query(eq(AsociacionAmazon.SQL_REVISAR), any(RowMapper.class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("CANAL")).thenReturn("AMAZON");
            when(rs.getString("PEDIDO")).thenReturn(PB);
            when(rs.getString("IMEI")).thenReturn(X);
            when(rs.getString("MOTIVO")).thenReturn("OTRO_MODELO");
            when(rs.getString("DETALLE")).thenReturn("el pedido es a y el móvil es b");
            when(rs.getTimestamp("VISTO_EN")).thenReturn(T);
            return List.of(((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0));
        });
        when(jdbc.query(eq(AsociacionAmazon.SQL_PASADAS), any(RowMapper.class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("CANAL")).thenReturn("AMAZON");
            when(rs.getTimestamp("CUANDO")).thenReturn(T);
            return List.of(((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0));
        });
        EstadoAsociaciones e = servicio.estado();
        assertEquals(new EstadoAsociaciones.Revisar(PB, X, "OTRO_MODELO", AHORA, "el pedido es a y el móvil es b"),
                e.canales().get(0).revisar().get(0));
    }

    private List<String> estado() {
        return tabla.stream().map(Fila::estado).toList();
    }

    // ------------------------------------------------------------------ regla 1: ya esta en una fila del pedido

    @Test void regla1ElImeiYaEnUnaFilaDelPedidoSeConfirmaSinTocarElImei() {
        Fila b = fila(20, PB, 1, X);
        assertEquals(r(1, 0, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals("LIBRO", b.asociadaOrigen);
        assertEquals(List.of(asociada(20)), apuntes());
    }

    @Test void regla1SiYaEstabaConfirmadaCuentaComoYaEstabaYNoEscribeNada() {
        Fila b = fila(20, PB, 1, X).asociada("HISTORICO");
        assertEquals(r(0, 0, 0, 1, 0, 0), aplicar(PB, X));
        assertEquals(ANTES, b.asociadaEn);
        assertEquals("HISTORICO", b.asociadaOrigen, "no pasa a LIBRO");
        assertTrue(apuntes.isEmpty());
        verify(jdbc, never()).batchUpdate(eq(AsociacionAmazon.SQL_ACTUALIZAR), anyList());
    }

    @Test void regla1ConfirmaAunqueLaFilaTengaLaEtiquetaGenerada() {
        Fila b = fila(20, PB, 1, X).etiqueta();
        assertEquals(r(1, 0, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(AHORA, b.asociadaEn);
        assertEquals(X, b.imei);
    }

    @Test void regla1EnUnaFilaCanceladaQuedaParaRevisarSinTocarla() {
        Fila b = fila(20, PB, 1, X).marca("CANCELADO");
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertNull(b.asociadaEn);
        assertTrue(apuntes.isEmpty());
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    // ------------------------------------------------------------------ regla 2: esta en una fila de otro pedido sin etiqueta

    @Test void regla2IntercambiaLosImeiConLaPrimeraFilaSinConfirmarDelPedidoPorPosicion() {
        Fila a = fila(10, PA, 1, X);
        Fila b1 = fila(20, PB, 1, W).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, Y);
        Fila b3 = fila(22, PB, 3, null);
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b2.imei);
        assertEquals(AHORA, b2.asociadaEn);
        assertEquals("LIBRO", b2.asociadaOrigen);
        assertEquals(Y, a.imei, "la otra fila se queda con el IMEI que tenia la de B");
        assertNull(a.asociadaEn);
        assertEquals(W, b1.imei, "la confirmada no se toca");
        assertEquals(ANTES, b1.asociadaEn);
        assertNull(b3.imei, "la primera sin confirmar por posicion es la 2, aunque la 3 este libre");
        assertEquals(List.of(imei(21, Y, X), imei(10, X, Y), intercambiado(21, 10), intercambiado(10, 21), asociada(21)), apuntes());
    }

    @Test void regla2ConLaFilaDelPedidoVaciaLaOtraSeQuedaSinImei() {
        Fila a = fila(10, PA, 1, X);
        Fila b = fila(20, PB, 1, null);
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertNull(a.imei);
        assertEquals(List.of(imei(20, null, X), imei(10, X, null), intercambiado(20, 10), intercambiado(10, 20), asociada(20)), apuntes());
    }

    @Test void regla2SiLaFilaDeOrigenEstabaConfirmadaParaOtroPedidoDejaDeEstarlo() {
        Fila a = fila(10, PA, 1, X).asociada("LIBRO");
        Fila b = fila(20, PB, 1, Y);
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(Y, a.imei);
        assertNull(a.asociadaEn, "manda lo ultimo que diga el libro");
        assertNull(a.asociadaOrigen);
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
    }

    @Test void regla2PonerUnImeiEnUnaFilaEnEsperaDeStockLeQuitaLaMarcaYLasDemasMarcasSeQuedan() {
        Fila a = fila(10, PA, 1, X).marca("ESPERA_STOCK");
        Fila b = fila(20, PB, 1, Y).marca("EN_REVISION");
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertNull(a.marca, "recibe Y");
        assertEquals("EN_REVISION", b.marca);
        assertTrue(apuntes().contains(apunte(10, null, null, "MARCA_QUITADA", "ESPERA_STOCK — al poner el IMEI")), apuntes().toString());
    }

    @Test void regla2ElImeiEnUnaFilaConEtiquetaDeOtroPedidoQuedaParaRevisarComoYaSalio() {
        fila(10, PA, 1, X).etiqueta();
        fila(20, PB, 1, null);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertTrue(apuntes.isEmpty());
        assertEquals(List.of(paraRevisar(PB, X, "YA_SALIO")), revisar());
    }

    @Test void regla2ElImeiEnUnaFilaCanceladaDeOtroPedidoQuedaParaRevisarComoCancelado() {
        fila(10, PA, 1, X).marca("CANCELADO");
        fila(20, PB, 1, null);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    @Test void regla2SinFilaPendienteYConUnaConEtiquetaElLibroLaUsa() {
        Fila a = fila(10, PA, 1, X);
        fila(20, PB, 1, Y).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, null).etiqueta();
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b2.imei);
        assertNull(a.imei, "pendiente: intercambio con el vacio de la fila de B");
        assertTrue(revisar().isEmpty());
    }

    @Test void regla2SiLasUnicasFilasPosiblesDelPedidoEstanCanceladasEsCancelado() {
        fila(10, PA, 1, X);
        fila(20, PB, 1, Y).asociada("LIBRO");
        fila(21, PB, 2, null).marca("CANCELADO");
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    // ------------------------------------------------------------------ regla 3: no esta en ninguna fila

    @Test void regla3PoneElImeiEnLaPrimeraFilaLibreDelPedidoYLaConfirma() {
        Fila b1 = fila(20, PB, 1, Y).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, Z);
        Fila b3 = fila(22, PB, 3, null);
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b3.imei);
        assertEquals(AHORA, b3.asociadaEn);
        assertEquals("LIBRO", b3.asociadaOrigen);
        assertEquals(Z, b2.imei, "una libre va antes que una sin confirmar con otro IMEI");
        assertNull(b2.asociadaEn);
        assertEquals(Y, b1.imei);
        assertEquals(List.of(imei(22, null, X), asociada(22)), apuntes());
    }

    @Test void regla3NuncaSacaDeLaTablaUnImeiLeidoSinFilaLibreQuedaParaRevisarSinHueco() {
        Fila b = fila(20, PB, 1, Y);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(Y, b.imei, "el IMEI leido sigue en su fila");
        assertNull(b.asociadaEn);
        assertEquals(antes, estado());
        assertTrue(apuntes.isEmpty());
        assertEquals(List.of(paraRevisar(PB, X, "SIN_HUECO")), revisar());
        verify(jdbc, never()).batchUpdate(eq(AsociacionAmazon.SQL_ACTUALIZAR), anyList());
    }

    @Test void regla3SiLaUnicaFilaLibreEstaCanceladaEsCanceladoAunqueHayaOtraSinConfirmarConImei() {
        fila(20, PB, 1, Y);
        fila(21, PB, 2, null).marca("CANCELADO");
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    @Test void regla3QuitaLaMarcaEsperaDeStockComoElPutDelImei() {
        Fila b = fila(20, PB, 1, null).marca("ESPERA_STOCK");
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertNull(b.marca);
        assertEquals(List.of(imei(20, null, X), apunte(20, null, null, "MARCA_QUITADA", "ESPERA_STOCK — al poner el IMEI"), asociada(20)),
                apuntes());
        verify(jdbc).batchUpdate(eq(AsociacionAmazon.SQL_QUITAR_ESPERA), anyList());
    }

    @Test void regla3NoTocaLasDemasMarcasNiLasFilasConEtiqueta() {
        Fila b1 = fila(20, PB, 1, null).etiqueta();
        Fila b2 = fila(21, PB, 2, null).marca("PENDIENTE_COLOR");
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertNull(b1.imei, "con etiqueta no es un hueco");
        assertEquals(X, b2.imei);
        assertEquals("PENDIENTE_COLOR", b2.marca);
        verify(jdbc, never()).batchUpdate(eq(AsociacionAmazon.SQL_QUITAR_ESPERA), anyList());
    }

    @Test void regla3ConEtiquetaElLibroPoneElImeiYSiSoloQuedanCanceladasEsCancelado() {
        fila(20, PB, 1, Y).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b2.imei);

        fila(30, PC, 1, null).marca("CANCELADO");
        List<String> antes = estado();
        int apuntesAntes = apuntes.size();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PC, W));
        assertEquals(antes, estado());
        assertEquals(apuntesAntes, apuntes.size(), "con CANCELADO no se escribe ningun apunte");
        assertEquals(List.of(paraRevisar(PC, W, "CANCELADO")), revisar(), "la lista se rehace: solo la ultima pasada");
    }

    // ------------------------------------------------------------------ regla 4 y bordes

    @Test void regla4UnPedidoSinFilasEnElErpSeCuentaComoAjenoYNoVaAParaRevisar() {
        Fila a = fila(10, PA, 1, X);
        assertEquals(r(0, 0, 0, 0, 0, 0, 1), aplicar(PC, X));
        assertEquals(X, a.imei, "el IMEI no se mueve");
        assertNull(a.asociadaEn);
        assertTrue(revisar().isEmpty());
        assertTrue(apuntes.isEmpty());
        assertArrayEquals(new Object[]{T, 0, 0, 0, 0, 0, 0, "AMAZON"}, pasadas.get(0));
    }

    @Test void unParSinHuecoSeReintentaAlFinalCuandoUnParPosteriorLiberaElHueco() {
        // B (PB) tiene su unica fila ocupada por Y sin confirmar; el par «X -> PB» va antes que «Y -> PC» (PC tiene una fila libre).
        Fila b = fila(20, PB, 1, Y);
        Fila c = fila(30, PC, 1, null);
        assertEquals(r(0, 1, 1, 0, 0, 0, 0), aplicar(PB, X, PC, Y));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals(Y, c.imei);
        assertEquals(AHORA, c.asociadaEn);
        assertTrue(revisar().isEmpty());
    }

    @Test void unParQueSigueSinHuecoTrasLasVueltasSeGuardaComoSinHueco() {
        fila(20, PB, 1, Y);
        fila(30, PC, 1, Z).etiqueta();
        assertEquals(r(0, 1, 0, 0, 1, 0), aplicar(PB, W, PC, Y));
        assertEquals(List.of(paraRevisar(PB, W, "SIN_HUECO")), revisar());
        assertEquals(Y, porId(30).imei, "el libro manda en PC, que tiene etiqueta");
        assertEquals(Z, porId(20).imei, "PB pendiente recibe por intercambio el que tenia PC; W sigue sin hueco");
    }

    @Test void unParConPedidoAjenoNoSeReintentaYRepetirLaPasadaConReintentoNoCambiaNada() {
        fila(20, PB, 1, Y);
        fila(30, PC, 1, null);
        String[] pasada = {PB, X, PC, Y, "999-0000000-0000009", W};
        assertEquals(r(0, 1, 1, 0, 0, 0, 1), aplicar(pasada));
        List<String> trasLaPrimera = estado();
        int apuntesPrimera = apuntes.size();
        assertEquals(r(0, 0, 0, 2, 0, 0, 1), aplicar(pasada));
        assertEquals(trasLaPrimera, estado());
        assertEquals(apuntesPrimera, apuntes.size());
        assertTrue(revisar().isEmpty());
    }

    @Test void soloCuentanLasFilasDelCanalDeLaPasada() {
        fila(20, PB, 1, null).canal("AMAZON_STOCKROOM");
        fila(10, PA, 1, X).canal("AMAZON_STOCKROOM");
        fila(30, PC, 1, null);
        assertEquals(r(0, 0, 1, 0, 0, 0, 1), aplicar(PB, Y, PC, X));
        assertTrue(revisar().isEmpty());
        assertEquals(X, porId(30).imei, "el X del otro canal no cuenta: se pone");
        assertEquals(X, porId(10).imei);
        verify(jdbc, atLeastOnce()).query(eq(String.format(AsociacionAmazon.SQL_DE_PEDIDOS, "?, ?")), any(RowMapper.class), eq("AMAZON"), eq(PB), eq(PC));
    }

    @Test void repetirLaMismaPasadaNoCambiaNada() {
        fila(10, PA, 1, X);
        fila(20, PB, 1, Y);
        fila(30, PC, 1, null);
        String[] pasada = {PB, X, PC, Z, PA, Y, "999-0000000-0000009", W};
        assertEquals(r(1, 1, 1, 0, 0, 0, 1), aplicar(pasada));
        List<String> trasLaPrimera = estado();
        int apuntesPrimera = apuntes.size();
        assertEquals(r(0, 0, 0, 3, 0, 0, 1), aplicar(pasada));
        assertEquals(trasLaPrimera, estado());
        assertEquals(apuntesPrimera, apuntes.size(), "ningun apunte nuevo");
        assertTrue(revisar().isEmpty());
    }

    @Test void unImeiConEspaciosValeYLoQueNoEsUnImeiOUnPedidoSeDescartaYSeCuenta() {
        Fila b = fila(20, PB, 1, null);
        List<PeticionAsociaciones.Par> pares = new ArrayList<>(pares(PB, "  " + X + " ", PB, "CANCELADO", PB, "", PB, "12345",
                PB, "3.50000000000001E14", PB, "3500000000000011", "  ", Y));
        pares.add(new PeticionAsociaciones.Par(PB, null));
        pares.add(new PeticionAsociaciones.Par(null, Y));
        pares.add(null);
        assertEquals(r(0, 0, 1, 0, 0, 9), servicio.aplicar(Canal.AMAZON, pares, AHORA));
        assertEquals(X, b.imei);
    }

    @Test void elNumeroDePedidoSeNormalizaComoLoGuardaLaLecturaDeShippyPro() {
        Fila b = fila(20, PB, 1, null);
        Fila largo = fila(30, "9".repeat(AnchosPedidos.PEDIDO), 1, null);
        assertEquals(r(0, 0, 2, 0, 0, 0), aplicar("  " + PB + "  ", X, "9".repeat(80), Y));
        assertEquals(X, b.imei);
        assertEquals(Y, largo.imei);
    }

    @Test void dosDeTresDelMismoPedidoOcupanLasDosPrimerasYLaTerceraSigueSinAsociar() {
        Fila b1 = fila(20, PB, 1, null);
        Fila b2 = fila(21, PB, 2, null);
        Fila b3 = fila(22, PB, 3, null);
        assertEquals(r(0, 0, 2, 0, 0, 0), aplicar(PB, X, PB, Y));
        assertEquals(X, b1.imei);
        assertEquals(Y, b2.imei);
        assertNull(b3.imei);
        assertNull(b3.asociadaEn);
        assertEquals(r(0, 0, 0, 2, 0, 0), aplicar(PB, X, PB, Y));
    }

    @Test void lasFilasSeOrdenanPorPosicionYNoPorId() {
        Fila segunda = fila(20, PB, 2, null);
        Fila primera = fila(21, PB, 1, null);
        aplicar(PB, X);
        assertEquals(X, primera.imei);
        assertNull(segunda.imei);
    }

    @Test void unImeiEnDosPedidosDelLibroVaARevisarEnLosDosYNoSeTocaNinguno() {
        fila(10, PA, 1, null);
        fila(20, PB, 1, null);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 2, 0), aplicar(PA, X, PB, X));
        assertEquals(antes, estado());
        assertTrue(apuntes.isEmpty());
        assertEquals(List.of(paraRevisar(PA, X, "REPETIDO_EN_LIBRO"), paraRevisar(PB, X, "REPETIDO_EN_LIBRO")), revisar());
    }

    /** Devuelto y revendido (decision de Imad, 24/11): X ya confirmado en PA y el libro lo pone tambien en PB, sin confirmar. PB lo
     *  recibe como cualquier otro par; PA se queda como estaba. */
    @Test void unImeiYaConfirmadoEnUnPedidoYNuevoEnOtroEsUnaReventaYElNuevoSeAplica() {
        Fila a = fila(10, PA, 1, X).etiqueta().asociada("LIBRO");
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 0, 1, 1, 0, 0), aplicar(PA, X, PB, X));
        assertEquals(X, a.imei, "el pedido viejo conserva su IMEI");
        assertNotNull(a.asociadaEn);
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals(List.of(libro(20, Y, X), asociada(20)), apuntes());
        assertTrue(revisar().isEmpty());
    }

    @Test void unaReventaEnUnPedidoPendienteVaASuFilaLibre() {
        Fila a = fila(10, PA, 1, X).etiqueta().asociada("HISTORICO");
        Fila b = fila(20, PB, 1, null);
        assertEquals(r(0, 0, 1, 1, 0, 0), aplicar(PB, X, PA, X));
        assertEquals(X, a.imei);
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertTrue(revisar().isEmpty());
    }

    @Test void unImeiYaConfirmadoEnLosDosPedidosNoEsRepetido() {
        fila(10, PA, 1, X).etiqueta().asociada("LIBRO");
        fila(20, PB, 1, X).etiqueta().asociada("LIBRO");
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 2, 0, 0), aplicar(PA, X, PB, X));
        assertEquals(antes, estado());
        assertTrue(revisar().isEmpty());
    }

    @Test void conDosPedidosNuevosParaElMismoImeiLosNuevosVanARevisarAunqueOtroLoTengaConfirmado() {
        Fila a = fila(10, PA, 1, X).etiqueta().asociada("LIBRO");
        fila(20, PB, 1, null);
        fila(30, PC, 1, null);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 1, 2, 0), aplicar(PA, X, PB, X, PC, X));
        assertEquals(antes, estado());
        assertEquals(X, a.imei);
        assertEquals(List.of(paraRevisar(PB, X, "REPETIDO_EN_LIBRO"), paraRevisar(PC, X, "REPETIDO_EN_LIBRO")), revisar());
    }

    @Test void elMismoParRepetidoNoEsRepetidoYLasRepeticionesSeDescartan() {
        Fila b = fila(20, PB, 1, null);
        assertEquals(r(0, 0, 1, 0, 0, 1), aplicar(PB, X, PB, X));
        assertEquals(X, b.imei);
        assertTrue(revisar().isEmpty());
    }

    @Test void unImeiRepetidoEnDosPedidosNoImpideQueLosDemasParesSeApliquen() {
        fila(10, PA, 1, null);
        Fila c = fila(30, PC, 1, null);
        assertEquals(r(0, 0, 1, 0, 2, 0), aplicar(PA, X, PB, X, PC, Y));
        assertEquals(Y, c.imei);
    }

    @Test void unIntercambioDejaElImeiDesplazadoDondePuedeConfirmarloOtraAsociacionDeLaMismaPasada() {
        Fila a = fila(10, PA, 1, X);
        Fila b = fila(20, PB, 1, Y);
        assertEquals(r(1, 1, 0, 0, 0, 0), aplicar(PB, X, PA, Y));
        assertEquals(Y, a.imei);
        assertEquals(AHORA, a.asociadaEn);
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
    }

    @Test void cadaPasadaRehaceLaListaDeSuCanalYGuardaSusRecuentos() {
        revisar.add(new Object[]{"AMAZON_STOCKROOM", PA, Y, "SIN_HUECO", Timestamp.valueOf(ANTES)});
        revisar.add(new Object[]{"AMAZON", PA, Z, "YA_SALIO", Timestamp.valueOf(ANTES)});
        fila(20, PB, 1, null);
        assertEquals(r(0, 0, 1, 0, 0, 1, 1), aplicar(PB, X, PC, W, PB, "CANCELADO"));
        assertEquals(List.of(Arrays.toString(new Object[]{"AMAZON_STOCKROOM", PA, Y, "SIN_HUECO", Timestamp.valueOf(ANTES)})), revisar());
        assertEquals(1, pasadas.size());
        assertArrayEquals(new Object[]{T, 0, 0, 1, 0, 0, 1, "AMAZON"}, pasadas.get(0));
        InOrder orden = inOrder(jdbc);
        orden.verify(jdbc).update(AsociacionAmazon.SQL_TOMAR_PASADA, "AMAZON", T);
        orden.verify(jdbc, atLeastOnce()).query(anyString(), any(RowMapper.class), any(Object[].class));
        orden.verify(jdbc).update(AsociacionAmazon.SQL_BORRAR_REVISAR, "AMAZON");
        orden.verify(jdbc).update(eq(AsociacionAmazon.SQL_GUARDAR_PASADA), any(Object[].class));
    }

    @Test void unaPasadaVaciaDejaLaListaVaciaYGuardaLaPasadaConCeros() {
        revisar.add(new Object[]{"AMAZON", PA, Z, "YA_SALIO", Timestamp.valueOf(ANTES)});
        assertEquals(r(0, 0, 0, 0, 0, 0), aplicar());
        assertTrue(revisar.isEmpty());
        assertArrayEquals(new Object[]{T, 0, 0, 0, 0, 0, 0, "AMAZON"}, pasadas.get(0));
        assertTrue(consultas.isEmpty(), "sin pares no hay filas que leer");
    }

    @Test void lasFilasSeLeenEnTandasDe500PorPedidoYPorImei() {
        List<String> muchos = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            muchos.add(String.format("402-0000000-%07d", i));
            muchos.add(String.format("3500000000%05d", i));
        }
        assertEquals(r(0, 0, 0, 0, 0, 0, 501), aplicar(muchos.toArray(String[]::new)));
        assertEquals(2, consultas.stream().filter(s -> s.contains("PEDIDO IN (")).count());
        assertEquals(2, consultas.stream().filter(s -> s.contains("IMEI IN (")).count());
    }

    @Test void laPasadaVaEnReadCommittedYBloqueaLasFilasDeShippyProDelCanal() throws Exception {
        var m = AsociacionAmazon.class.getMethod("aplicar", Canal.class, List.class, LocalDateTime.class);
        Transactional t = m.getAnnotation(Transactional.class);
        assertNotNull(t);
        assertEquals(Isolation.READ_COMMITTED, t.isolation());
        for (String sql : List.of(AsociacionAmazon.SQL_DE_PEDIDOS, AsociacionAmazon.SQL_CON_IMEI)) {
            assertTrue(sql.endsWith(" FOR UPDATE"), sql);
            assertTrue(sql.contains("WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ?"), "nunca el historico: " + sql);
        }
    }

    @Test void unCanalQueNoEsDeAmazonNoSeAplica() {
        for (Canal c : new Canal[]{Canal.DISTRIBUIDORES, Canal.PARTICULARES, Canal.BACKIPHONE, null})
            assertThrows(IllegalArgumentException.class, () -> servicio.aplicar(c, List.of(), AHORA));
        verifyNoInteractions(jdbc);
    }

    @Test void stockroomGuardaSuPropiaPasadaYSuLista() {
        fila(20, PB, 1, null).canal("AMAZON_STOCKROOM");
        assertEquals(r(0, 0, 1, 0, 0, 0, 1), servicio.aplicar(Canal.AMAZON_STOCKROOM, pares(PB, X, PC, Y), AHORA));
        assertTrue(revisar().isEmpty());
        assertEquals("AMAZON_STOCKROOM", pasadas.get(0)[7]);
    }

    // ------------------------------------------------------------------ el libro manda en pedidos con etiqueta (spec §2.3)

    private static String libro(int id, String anterior, String nuevo) {
        return apunte(id, anterior, nuevo, "IMEI", "LIBRO");
    }

    @Test void conEtiquetaSinImprimirPoneElImeiDelLibroYLoConfirma() {
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals("LIBRO", b.asociadaOrigen);
        assertEquals(List.of(libro(20, Y, X), asociada(20)), apuntes());
        assertEquals(List.of(Map.of(X, "i14128AzulA")), comprobados, "se comprueba con el SKU del pedido");
    }

    @Test void siElMovilNoCuadraVaARevisarConSuDetalleYNoSeToca() {
        Fila b = fila(20, PB, 1, Y).etiqueta();
        rechazos.put(X, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA"));
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(Y, b.imei);
        assertEquals(List.of(paraRevisar(PB, X, "OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA")), revisar());
    }

    @Test void siElImeiDelLibroNoEstaEnNsysVaARevisar() {
        fila(20, PB, 1, Y).etiqueta();
        rechazos.put(X, new ComprobadorLibro.Rechazo("NO_EN_NSYS", null));
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(List.of(paraRevisar(PB, X, "NO_EN_NSYS")), revisar());
    }

    @Test void elImeiDeOtroPedidoConEtiquetaSeLeQuitaSiEsePedidoEstaEnElLibroDeLaPasada() {
        Fila o = fila(10, PA, 1, X).etiqueta();
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 1, 1, 0, 0, 0), aplicar(PB, X, PA, W));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals(W, o.imei, "PA pierde X y su linea del libro le pone W");
        assertEquals(List.of(libro(20, Y, X), libro(10, X, null), asociada(20), libro(10, null, W), asociada(10)), apuntes());
    }

    @Test void elImeiDeOtroPedidoConEtiquetaQueNoEstaEnElLibroDeLaPasadaEsYaSalioYNoSeToca() {
        Fila o = fila(10, PA, 1, X).etiqueta();
        Fila b = fila(20, PB, 1, Y).etiqueta();
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "YA_SALIO")), revisar());
        assertTrue(apuntes.isEmpty());
    }

    @Test void unCruceDeDosPedidosConEtiquetaSeResuelveEnLaMismaPasada() {
        Fila a = fila(10, PA, 1, Y).etiqueta();
        Fila b = fila(20, PB, 1, X).etiqueta();
        aplicar(PA, X, PB, Y);
        assertEquals(X, a.imei);
        assertEquals(Y, b.imei);
        assertEquals(AHORA, a.asociadaEn);
        assertEquals(AHORA, b.asociadaEn);
        assertTrue(revisar().isEmpty());
    }

    @Test void elImeiDeUnPedidoPendienteSeIntercambiaComoHoy() {
        Fila o = fila(10, PA, 1, X);
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(Y, o.imei, "el pendiente recibe el que tenia la fila de B, como en la regla 2");
        assertNull(o.asociadaEn);
    }

    @Test void elImeiConfirmadoEnOtroPedidoEsYaSalioYNoSeToca() {
        fila(10, PA, 1, X).etiqueta().asociada("LIBRO");
        fila(20, PB, 1, Y).etiqueta();
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "YA_SALIO")), revisar());
    }

    @Test void elImeiEnUnaFilaCanceladaDeOtroPedidoEsCancelado() {
        fila(10, PA, 1, X).marca("CANCELADO");
        fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    @Test void conVariasUnidadesVaALaFilaCuyoImeiNoEstaEnElLibro() {
        Fila b1 = fila(20, PB, 1, Y).etiqueta();
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        aplicar(PB, Y, PB, X);
        assertEquals(Y, b1.imei, "Y la pone el libro en PB: se confirma");
        assertEquals(AHORA, b1.asociadaEn);
        assertEquals(X, b2.imei, "X va a la fila de Z, que el libro no pone en PB");
        assertEquals(AHORA, b2.asociadaEn);
    }

    @Test void unaFilaConEtiquetaCanceladaNoRecibeNada() {
        Fila b = fila(20, PB, 1, Y).etiqueta().marca("CANCELADO");
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(Y, b.imei);
        assertEquals(List.of(paraRevisar(PB, X, "SIN_HUECO")), revisar(), "sin fila corregible siguen las reglas de hoy");
        assertTrue(comprobados.isEmpty() || comprobados.get(0).isEmpty());
    }

    @Test void unParQueLlegaAlCaminoConEtiquetaEnElReintentoTambienSeComprueba() {
        // Al empezar, PB tiene la fila 20 pendiente (Y): X no va por el camino con etiqueta ni se comprueba. «Y -> PB» confirma la 20 y en
        // el reintento X ya va por ese camino: se comprueba entonces, con el SKU de la fila destino.
        fila(20, PB, 1, Y);
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        rechazos.put(X, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA"));
        assertEquals(r(1, 0, 0, 0, 1, 0), aplicar(PB, X, PB, Y));
        assertEquals(Z, b2.imei, "no se pone un IMEI sin comprobar");
        assertNull(b2.asociadaEn);
        assertEquals(List.of(paraRevisar(PB, X, "OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA")), revisar());
        assertEquals(List.of(Map.of(), Map.of(X, "i14128AzulA")), comprobados);
    }

    @Test void siLaFilaDestinoCambiaAOtraDeOtroSkuSeVuelveAComprobarConElSkuDeEsaFila() {
        // Dos filas con etiqueta de PB con SKU distinto. Antes de la pasada los dos pares apuntan a la fila 20 (SKU A) y se comprueban con
        // el suyo; al aplicar, X se queda la 20 y W va a la 21 (SKU B): W se vuelve a comprobar con el SKU de la 21 y, rechazado, no se escribe.
        Fila b1 = fila(20, PB, 1, Y).etiqueta();
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        b2.sku = "i14256NegroA";
        rechazos.put(W, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i14256NegroA y el móvil es i14128AzulA"));
        assertEquals(r(0, 0, 1, 0, 1, 0), aplicar(PB, X, PB, W));
        assertEquals(X, b1.imei);
        assertEquals(Z, b2.imei, "W no se pone: no cuadra con el SKU de la fila 21");
        assertNull(b2.asociadaEn);
        assertEquals(List.of(Map.of(X, "i14128AzulA", W, "i14128AzulA"), Map.of(W, "i14256NegroA")), comprobados);
        assertEquals(List.of(paraRevisar(PB, W, "OTRO_MODELO", "el pedido es i14256NegroA y el móvil es i14128AzulA")), revisar());
        assertEquals(List.of(libro(20, Y, X), asociada(20)), apuntes(), "solo lo de X: nada de W");
    }

    @Test void conUnaFilaPendienteLibreEnElPedidoSiguenLasReglasDeHoyYNoSeComprueba() {
        Fila b1 = fila(20, PB, 1, null).etiqueta();
        Fila b2 = fila(21, PB, 2, null);
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertNull(b1.imei);
        assertEquals(X, b2.imei);
        assertTrue(comprobados.isEmpty() || comprobados.get(0).isEmpty());
    }

    // ------------------------------------------------------------------ estado (GET)

    private static ResultSet pasada(String canal, int base) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CANAL")).thenReturn(canal);
        when(rs.getTimestamp("CUANDO")).thenReturn(Timestamp.valueOf(AHORA));
        when(rs.getInt("CONFIRMADAS")).thenReturn(base);
        when(rs.getInt("INTERCAMBIADAS")).thenReturn(base + 1);
        when(rs.getInt("PUESTAS")).thenReturn(base + 2);
        when(rs.getInt("YA_ESTABAN")).thenReturn(base + 3);
        when(rs.getInt("PARA_REVISAR")).thenReturn(base + 4);
        when(rs.getInt("DESCARTADAS")).thenReturn(base + 5);
        if (base == 1) {
            when(rs.getTimestamp("LIBRO_MODIFICADO")).thenReturn(Timestamp.valueOf(ANTES));
            when(rs.getString("ERROR")).thenReturn("falta la columna IMEI en Paso 1. Meter IMEI");
            when(rs.getTimestamp("ERROR_EN")).thenReturn(Timestamp.valueOf(AHORA));
        }
        return rs;
    }

    private static ResultSet aRevisar(String canal, String pedido, String imei, String motivo) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CANAL")).thenReturn(canal);
        when(rs.getString("PEDIDO")).thenReturn(pedido);
        when(rs.getString("IMEI")).thenReturn(imei);
        when(rs.getString("MOTIVO")).thenReturn(motivo);
        when(rs.getTimestamp("VISTO_EN")).thenReturn(Timestamp.valueOf(AHORA));
        return rs;
    }

    @Test void elEstadoJuntaCadaPasadaConSuListaEnElOrdenDelLibro() {
        when(jdbc.query(eq(AsociacionAmazon.SQL_REVISAR), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<Object> m = inv.getArgument(1);
            return List.of(m.mapRow(aRevisar("AMAZON", PB, X, "SIN_HUECO"), 0), m.mapRow(aRevisar("AMAZON", PA, Y, "YA_SALIO"), 1));
        });
        when(jdbc.query(eq(AsociacionAmazon.SQL_PASADAS), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<Object> m = inv.getArgument(1);
            return List.of(m.mapRow(pasada("AMAZON", 1), 0), m.mapRow(pasada("AMAZON_STOCKROOM", 10), 1));
        });
        EstadoAsociaciones e = servicio.estado();
        assertEquals(new EstadoAsociaciones(List.of(
                new EstadoAsociaciones.Pasada("AMAZON", AHORA, 1, 2, 3, 4, 5, 6, ANTES, "falta la columna IMEI en Paso 1. Meter IMEI",
                        AHORA, List.of(
                        new EstadoAsociaciones.Revisar(PB, X, "SIN_HUECO", AHORA), new EstadoAsociaciones.Revisar(PA, Y, "YA_SALIO", AHORA))),
                new EstadoAsociaciones.Pasada("AMAZON_STOCKROOM", AHORA, 10, 11, 12, 13, 14, 15, null, null, null, List.of()))), e);
        assertTrue(AsociacionAmazon.SQL_REVISAR.endsWith("ORDER BY CANAL, ID"));
    }

    @Test void sinNingunaPasadaElEstadoNoTraeCanales() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        assertEquals(new EstadoAsociaciones(List.of()), servicio.estado());
    }

    // ------------------------------------------------------------------ lector de los libros

    @Test void laPasadaDelLectorGuardaLaFechaDelLibroYBorraElErrorEnLaMismaTransaccion() throws Exception {
        fila(20, PB, 1, null);
        LocalDateTime libro = LocalDateTime.of(2026, 11, 13, 9, 15, 7);
        assertEquals(r(0, 0, 1, 0, 0, 0), servicio.aplicarLibro(Canal.AMAZON, pares(PB, X), AHORA, libro));
        assertEquals(1, pasadas.size());
        assertEquals(1, libros.size());
        assertArrayEquals(new Object[]{Timestamp.valueOf(libro), "AMAZON"}, libros.get(0));
        assertTrue(AsociacionAmazon.SQL_GUARDAR_LIBRO.contains("ERROR = NULL") && AsociacionAmazon.SQL_GUARDAR_LIBRO.contains("ERROR_EN = NULL"));
        Transactional tx = AsociacionAmazon.class.getMethod("aplicarLibro", Canal.class, List.class, LocalDateTime.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);
        assertEquals(Isolation.READ_COMMITTED, tx.isolation());
    }

    @Test void laPasadaDeLaRutaNoTocaLaFechaDelLibro() {
        fila(20, PB, 1, null);
        aplicar(PB, X);
        assertTrue(libros.isEmpty());
    }

    @Test void laFechaDelLibroLeidoSaleDeLaPasadaDelCanal() throws Exception {
        LocalDateTime libro = LocalDateTime.of(2026, 11, 13, 9, 15, 7);
        when(jdbc.query(eq(AsociacionAmazon.SQL_LIBRO_LEIDO), any(RowMapper.class), eq("AMAZON"))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getTimestamp("LIBRO_MODIFICADO")).thenReturn(Timestamp.valueOf(libro));
            return List.of(((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0));
        });
        when(jdbc.query(eq(AsociacionAmazon.SQL_LIBRO_LEIDO), any(RowMapper.class), eq("AMAZON_STOCKROOM"))).thenReturn(List.of());
        assertEquals(libro, servicio.libroLeido(Canal.AMAZON));
        assertNull(servicio.libroLeido(Canal.AMAZON_STOCKROOM));
    }

    @Test void unErrorDelLectorSoloApuntaElErrorEnLaPasada() {
        reset(jdbc);
        servicio.apuntarError(Canal.AMAZON_STOCKROOM, "falta la pestaña Paso 1. Meter IMEI", AHORA);
        verify(jdbc).update(AsociacionAmazon.SQL_APUNTAR_ERROR, "AMAZON_STOCKROOM", T, "falta la pestaña Paso 1. Meter IMEI", T);
        verifyNoMoreInteractions(jdbc);
        assertTrue(AsociacionAmazon.SQL_APUNTAR_ERROR.endsWith("ON DUPLICATE KEY UPDATE ERROR = VALUES(ERROR), ERROR_EN = VALUES(ERROR_EN)"),
                "con pasada previa solo cambia el error: recuentos, CUANDO y LIBRO_MODIFICADO se quedan");
    }

    @Test void borrarElErrorSoloLimpiaElErrorDeEsaPasadaSiLoHay() {
        reset(jdbc);
        servicio.borrarError(Canal.AMAZON);
        verify(jdbc).update(AsociacionAmazon.SQL_BORRAR_ERROR, "AMAZON");
        verifyNoMoreInteractions(jdbc);
        assertEquals("UPDATE Pedido_asociacion_pasada SET ERROR = NULL, ERROR_EN = NULL WHERE CANAL = ? AND ERROR IS NOT NULL",
                AsociacionAmazon.SQL_BORRAR_ERROR, "recuentos, CUANDO y LIBRO_MODIFICADO se quedan");
    }
}
