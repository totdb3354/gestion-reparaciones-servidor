package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.pedidos.CargaHistorico.Estado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class CargaHistoricoTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 11, 10, 0);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ID_HOJA = "hoja-inventada-123";
    private static final String PED1 = "402-0000000-0000001";

    private final ClienteSheets sheets = mock(ClienteSheets.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final LecturaFilas lectura = mock(LecturaFilas.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    private final List<Runnable> pendientes = new ArrayList<>();
    private final Executor aparte = pendientes::add;
    private final Executor enSeco = Runnable::run;
    private final List<FilaPedido> erp = new ArrayList<>();

    @BeforeEach void preparar() throws Exception {
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(lectura.sinVueltas(any())).thenAnswer(i -> ((Supplier<Object>) i.getArgument(0)).get());
        when(dao.todasDeShippyProBloqueadas()).thenAnswer(i -> List.copyOf(erp));
        when(dao.traspasarImei(anyInt(), anyString(), any())).thenAnswer(i -> {
            int id = i.getArgument(0);
            for (int k = 0; k < erp.size(); k++) {
                FilaPedido f = erp.get(k);
                if (f.id() == id) erp.set(k, new FilaPedido(id, f.fecha(), f.plataforma(), f.sku(), i.getArgument(1), f.pedido(), null,
                        f.origen(), f.canal(), f.posicion(), f.cerradaEn()));
            }
            return true;
        });
    }

    private CargaHistorico carga(Executor hilo) {
        return new CargaHistorico(sheets, dao, nsys, lectura, tm, ID_HOJA, "PEDIDOS 2026", 0, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID), hilo);
    }

    private static JsonNode c(Object o) {
        return JSON.valueToTree(o);
    }

    private static TablaHoja.Fila fila(int n, Object... celdas) {
        return new TablaHoja.Fila(n, java.util.Arrays.stream(celdas).map(CargaHistoricoTest::c).toList());
    }

    /** Hoja de prueba: A pedido casado, B sin numero, una web, una vacia, una incidencia y un IMEI que no es IMEI. Serie 46332 = 2026-11-06. */
    private TablaHoja hoja() {
        return new TablaHoja(Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3, "Pedido Amazon", 4, "Fecha compra", 5),
                List.of(fila(2, 46332, "AMZ", "i13128AzulA", 350000000000001L, PED1),
                        fila(3, 46332, "AMZ", "i13128AzulA", 350000000000002L),
                        fila(4, 46332, "Cliente Web A", "i13128AzulA", 350000000000003L),
                        fila(5),
                        fila(6, 46332, "INCIDENCIA AMZ 1", "i13128AzulA", "CANCELADO"),
                        fila(7, 46300, "AMZ", "i13128AzulA", 350000000000004L)));
    }

    private void erpDePrueba() {
        LocalDate ayer = LocalDate.of(2026, 11, 10);
        erp.add(new FilaPedido(10, ayer, "AMZ", "i13128AzulA", null, PED1, null, "SHIPPYPRO", Canal.AMAZON, 1, null));
        erp.add(new FilaPedido(11, ayer, "AMZ", "i13128AzulA", null, "402-0000000-0000002", null, "SHIPPYPRO", Canal.AMAZON, 1, null));
        erp.add(new FilaPedido(12, ayer, "Cliente Web A", "i13128AzulA", null, "W-1", null, "SHIPPYPRO", Canal.PARTICULARES, 1, null));
    }

    // ------------------------------------------------------------------ lanzar

    @Test void sinElAjusteDeLaHojaNoSeLeeNadaYSeAvisa() throws Exception {
        CargaHistorico sin = new CargaHistorico(sheets, dao, nsys, lectura, tm, "  ", "PEDIDOS 2026", 1, Clock.systemUTC(), enSeco);
        assertThrows(CargaHistorico.NoConfigurada.class, sin::iniciar);
        assertEquals(Estado.NUNCA, sin.estado().estado());
        verifyNoInteractions(sheets, dao, nsys);
    }

    @Test void alPrincipioElEstadoEsNunca() {
        CargaHistorico c = carga(enSeco);
        assertEquals(new CargaHistorico.EstadoCarga(Estado.NUNCA, null, null, null, null), c.estado());
    }

    @Test void iniciarDevuelveEnCursoAlMomentoYElTrabajoVaEnOtroHilo() throws Exception {
        CargaHistorico c = carga(aparte);
        CargaHistorico.EstadoCarga e = c.iniciar();
        assertEquals(Estado.EN_CURSO, e.estado());
        assertEquals(AHORA, e.empezo());
        assertEquals(1, pendientes.size());
        verifyNoInteractions(sheets, dao, nsys);
        assertEquals(Estado.EN_CURSO, c.estado().estado());
    }

    @Test void conOtraEnMarchaUnaNuevaEs409YCuandoAcabaSePuedeOtra() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenReturn(new TablaHoja(Map.of(), List.of()));
        CargaHistorico c = carga(aparte);
        c.iniciar();
        assertThrows(CargaHistorico.YaEnCurso.class, c::iniciar);
        pendientes.remove(0).run();
        assertEquals(Estado.OK, c.estado().estado());
        c.iniciar();
        assertEquals(1, pendientes.size());
    }

    // ------------------------------------------------------------------ pendientes recientes sin IMEI ni pedido

    private static final Map<String, Integer> COLS = Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3, "Pedido Amazon", 4, "Fecha compra", 5);

    /** AHORA = 2026-11-11: el dia 14 es 2026-10-28 (serie 46323), el 15 es 2026-10-27 (46322). */
    private InformeCarga cargarHoja(List<TablaHoja.Fila> filas) throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenReturn(new TablaHoja(COLS, filas));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(Estado.OK, c.estado().estado());
        return c.estado().informe();
    }

    private static void cuadra(InformeCarga i) {
        assertEquals(i.filasLeidas(), i.descartadasVacias() + i.sinFechaDescartada() + i.incidenciasDescartadas() + i.pendientesSinImeiNoCargadas()
                + i.cargadasHistorico() + i.traspasadasA() + i.traspasadasB() + i.traspasadasC() + i.yaPuesto() + i.conflicto() + i.casadasSinImei(), "cuadre");
    }

    private List<FilaPedido> insertadas() {
        ArgumentCaptor<List<FilaPedido>> historico = ArgumentCaptor.forClass(List.class);
        verify(dao).insertarHistorico(historico.capture());
        return historico.getValue();
    }

    @Test void unaFilaRecienteSinImeiNiPedidoNoSeCargaYSeCuenta() throws Exception {
        InformeCarga i = cargarHoja(List.of(fila(2, 46332, "AMZ", "i13128AzulA"), fila(3, 46332, "Cliente Web A", "i13128AzulA", "CANCELADO")));
        assertEquals(2, i.pendientesSinImeiNoCargadas(), "vacia y con texto que no es IMEI");
        assertEquals(0, i.cargadasHistorico());
        assertEquals(2, i.filasLeidas());
        cuadra(i);
        assertEquals(List.of(), insertadas());
    }

    @Test void unaFilaDeQuinceDiasSinImeiNiPedidoSiSeCarga() throws Exception {
        InformeCarga i = cargarHoja(List.of(fila(2, 46322, "AMZ", "i13128AzulA")));
        assertEquals(0, i.pendientesSinImeiNoCargadas());
        assertEquals(1, i.cargadasHistorico());
        cuadra(i);
        assertEquals(1, insertadas().size());
    }

    @Test void elLimiteEsElDia14Incluido() throws Exception {
        InformeCarga i = cargarHoja(List.of(fila(2, 46323, "AMZ", "i13128AzulA"), fila(3, 46322, "AMZ", "i13128AzulA")));
        assertEquals(1, i.pendientesSinImeiNoCargadas(), "el dia 14 (2026-10-28) no se carga");
        assertEquals(1, i.cargadasHistorico(), "el dia 15 si");
        cuadra(i);
    }

    @Test void unaFilaRecienteConNumeroDePedidoNoCambia() throws Exception {
        InformeCarga i = cargarHoja(List.of(fila(2, 46332, "AMZ", "i13128AzulA", null, "999-0000000-0000001")));
        assertEquals(0, i.pendientesSinImeiNoCargadas());
        assertEquals(1, i.cargadasHistorico());
        cuadra(i);
    }

    @Test void unaFilaRecienteConImeiNoCambia() throws Exception {
        InformeCarga i = cargarHoja(List.of(fila(2, 46332, "AMZ", "i13128AzulA", 350000000000001L)));
        assertEquals(0, i.pendientesSinImeiNoCargadas());
        assertEquals(1, i.cargadasHistorico(), "sin fila en el ERP: sinPareja, va al historico");
        cuadra(i);
    }

    @Test void laIdentidadCuadraConTodoMezclado() throws Exception {
        erpDePrueba();
        InformeCarga i = cargarHoja(List.of(
                fila(2, 46332, "AMZ", "i13128AzulA", 350000000000001L, PED1), fila(3, 46332, "AMZ", "i13128AzulA", 350000000000002L),
                fila(4, 46332, "Cliente Web A", "i13128AzulA", 350000000000003L), fila(5),
                fila(6, 46332, "INCIDENCIA AMZ 1", "i13128AzulA", "CANCELADO"), fila(7, 46300, "AMZ", "i13128AzulA", 350000000000004L),
                fila(8, 46332, "AMZ", "i13128AzulA"), fila(9, 46332, "Cliente Web B", "i13128AzulA", "pendiente"),
                fila(10, 46300, "AMZ", "i13128AzulA")));
        assertEquals(2, i.pendientesSinImeiNoCargadas());
        assertEquals(9, i.filasLeidas());
        cuadra(i);
    }

    @Test void lasPendientesNoBajanLaGuardaDelMinimo() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenReturn(new TablaHoja(COLS, List.of(fila(2, 46332, "AMZ", "i13128AzulA"))));
        CargaHistorico c = new CargaHistorico(sheets, dao, nsys, lectura, tm, ID_HOJA, "PEDIDOS 2026", 1,
                Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID), enSeco);
        c.iniciar();
        assertEquals(Estado.OK, c.estado().estado(), "la guarda cuenta las filas utiles leidas, antes de quitar las pendientes");
        assertEquals(1, c.estado().informe().pendientesSinImeiNoCargadas());
    }

    // ------------------------------------------------------------------ hacer

    @Test void lasPruebasDeLaHojaCuadran() {
        // 46332 es 2026-11-06 (una semana antes del «hoy» de la prueba, dentro de los 14 dias).
        assertEquals(LocalDate.of(2026, 11, 6), TablaHoja.fecha(c(46332)).toLocalDate());
    }

    @Test void cargaLaHojaEnteraTraspasaYCargaElRestoEnUnaSolaTransaccion() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(eq(ID_HOJA), eq("PEDIDOS 2026"), any())).thenReturn(hoja());
        CargaHistorico c = carga(enSeco);

        c.iniciar();

        CargaHistorico.EstadoCarga e = c.estado();
        assertEquals(Estado.OK, e.estado());
        assertEquals(AHORA, e.empezo());
        assertEquals(AHORA, e.acabo());
        assertNull(e.motivo());
        InformeCarga i = e.informe();
        assertEquals(6, i.filasLeidas());
        assertEquals(1, i.descartadasVacias());
        assertEquals(1, i.traspasadasA());
        assertEquals(1, i.traspasadasB());
        assertEquals(1, i.traspasadasC());
        assertEquals(1, i.incidenciasDescartadas());
        assertEquals(1, i.cargadasHistorico(), "solo la fila antigua: la incidencia no se carga");
        assertEquals(i.filasLeidas(), i.descartadasVacias() + i.sinFechaDescartada() + i.incidenciasDescartadas() + i.pendientesSinImeiNoCargadas() + i.cargadasHistorico()
                + i.traspasadasA() + i.traspasadasB() + i.traspasadasC() + i.yaPuesto() + i.conflicto() + i.casadasSinImei(), "cuadre");
        assertEquals(0, i.imeiNoEsImei(), "el CANCELADO era de la incidencia");
        assertEquals(Map.of("AMZ", 3, "Cliente Web A", 1), i.plataformas());

        InOrder orden = inOrder(lectura, dao, tm);
        orden.verify(lectura).sinVueltas(any());
        orden.verify(dao).todasDeShippyProBloqueadas();
        orden.verify(dao).borrarHistorico();
        orden.verify(dao, times(3)).traspasarImei(anyInt(), anyString(), eq(AHORA));
        ArgumentCaptor<List<FilaPedido>> historico = ArgumentCaptor.forClass(List.class);
        orden.verify(dao).insertarHistorico(historico.capture());
        orden.verify(tm).commit(any());
        verify(tm, never()).rollback(any());
        assertEquals(List.of("AMZ"), historico.getValue().stream().map(FilaPedido::plataforma).toList(),
                "en el orden de la hoja");
        FilaPedido h = historico.getValue().get(0);
        assertEquals("HISTORICO", h.origen());
        assertNull(h.canal());
        assertNull(h.posicion());
        assertEquals(LocalDate.of(2026, 10, 5).atStartOfDay(), h.cerradaEn());
        assertEquals("350000000000004", h.imei());
        ArgumentCaptor<TransactionDefinition> def = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(tm).getTransaction(def.capture());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, def.getValue().getIsolationLevel());
    }

    @Test void lasIncidenciasEnCualquierGrafiaNoSeCarganNiTraspasanNiBloquean() throws Exception {
        erpDePrueba();
        TablaHoja t = new TablaHoja(Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3, "Pedido Amazon", 4, "Fecha compra", 5),
                List.of(fila(2, 46332, "  incidencia amz stock", "i13128AzulA", 350000000000001L, PED1),
                        fila(3, 46332, "INCIDENCIA", "i13128AzulA", 350000000000009L),
                        fila(4, 46332, "Incidencia AMZ DE", "i13128AzulA", 350000000000008L, PED1),
                        fila(5, 46332, "AMZ", "i13128AzulA", 350000000000001L, PED1),
                        fila(6, 46332, "Taller Incidencias SL", "i13128AzulA", 350000000000007L)));
        when(sheets.leerTodo(any(), any(), any())).thenReturn(t);
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        InformeCarga i = c.estado().informe();
        assertEquals(3, i.incidenciasDescartadas());
        assertEquals(1, i.traspasadasA(), "la fila real recibe el IMEI aunque una incidencia traiga su pedido y su IMEI");
        assertEquals(1, i.cargadasHistorico(), "el cliente cuyo nombre solo contiene la palabra se carga");
        assertEquals(Map.of("AMZ", 1, "Taller Incidencias SL", 1), i.plataformas());
        assertEquals(5, i.filasLeidas());
        assertEquals(i.filasLeidas(), i.descartadasVacias() + i.sinFechaDescartada() + i.incidenciasDescartadas() + i.pendientesSinImeiNoCargadas() + i.cargadasHistorico()
                + i.traspasadasA() + i.traspasadasB() + i.traspasadasC() + i.yaPuesto() + i.conflicto() + i.casadasSinImei());
        verify(dao).traspasarImei(10, "350000000000001", AHORA);
        ArgumentCaptor<List<FilaPedido>> historico = ArgumentCaptor.forClass(List.class);
        verify(dao).insertarHistorico(historico.capture());
        assertEquals(List.of("Taller Incidencias SL"), historico.getValue().stream().map(FilaPedido::plataforma).toList());
    }

    @Test void laHojaSeLeeFueraDeLaTransaccionYDeLasVueltasDeLectura() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenAnswer(i -> {
            verifyNoInteractions(tm, lectura);
            return hoja();
        });
        carga(enSeco).iniciar();
        verify(lectura).sinVueltas(any());
    }

    @Test void repetirLaCargaDaElMismoHistoricoYLaSegundaVezNoTraspasaNada() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        CargaHistorico c = carga(enSeco);

        c.iniciar();
        InformeCarga primera = c.estado().informe();
        c.iniciar();
        InformeCarga segunda = c.estado().informe();

        assertEquals(3, primera.traspasadasA() + primera.traspasadasB() + primera.traspasadasC());
        assertEquals(0, segunda.traspasadasA() + segunda.traspasadasB() + segunda.traspasadasC());
        assertEquals(3, segunda.yaPuesto());
        assertEquals(primera.cargadasHistorico(), segunda.cargadasHistorico());
        assertEquals(primera.filasLeidas(), segunda.filasLeidas());
        verify(dao, times(2)).borrarHistorico();
        verify(dao, times(3)).traspasarImei(anyInt(), anyString(), any());
    }

    @Test void siFallaAMediasSeDeshaceTodoYElMotivoEsUnTextoFijo() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        doThrow(new IllegalStateException("Duplicate entry '350000000000001' for key 'idx' en " + ID_HOJA)).when(dao).insertarHistorico(any());
        CargaHistorico c = carga(enSeco);

        c.iniciar();

        CargaHistorico.EstadoCarga e = c.estado();
        assertEquals(Estado.ERROR, e.estado());
        assertEquals(CargaHistorico.MOTIVO_GUARDAR, e.motivo());
        assertNull(e.informe());
        assertNotNull(e.acabo());
        verify(tm).rollback(any());
        verify(tm, never()).commit(any());
        assertFalse(e.toString().contains("350000000000001"));
        assertFalse(e.toString().contains(ID_HOJA));
    }

    @Test void despuesDeUnErrorSePuedeVolverAIntentar() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenThrow(new IOException("Google Sheets respondió 403 para " + ID_HOJA))
                .thenReturn(new TablaHoja(Map.of(), List.of()));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(Estado.ERROR, c.estado().estado());
        c.iniciar();
        assertEquals(Estado.OK, c.estado().estado());
    }

    @Test void unaHojaSinLasColumnasEsUnErrorSinTocarLaBaseDeDatos() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenThrow(new FormatoHojaException("Falta la columna «IMEI» en «PEDIDOS 2026»"));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(Estado.ERROR, c.estado().estado());
        assertEquals(CargaHistorico.MOTIVO_FORMATO, c.estado().motivo());
        verifyNoInteractions(dao, tm, lectura);
    }

    @Test void unFalloDeLecturaDeLaHojaNoLlevaElTextoDeLaExcepcionNiElIdDeLaHoja() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenThrow(new IOException("Google Sheets respondió 404 en " + ID_HOJA));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(CargaHistorico.MOTIVO_LEER, c.estado().motivo());
        assertFalse(c.estado().motivo().contains(ID_HOJA));
        verifyNoInteractions(dao, tm);
    }

    @Test void unaInterrupcionEsUnErrorYDejaLaMarcaDeInterrupcion() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenThrow(new InterruptedException());
        CargaHistorico c = carga(enSeco);
        try {
            c.iniciar();
            assertEquals(CargaHistorico.MOTIVO_INTERRUMPIDA, c.estado().motivo());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // que no se contagie a otras pruebas
        }
    }

    @Test void unaFilaDelErpQueCambiaDeImeiMientrasSeCargaHaceFallarLaCargaEntera() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        when(dao.traspasarImei(anyInt(), anyString(), any())).thenReturn(false);
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(Estado.ERROR, c.estado().estado());
        verify(tm).rollback(any());
        verify(dao, never()).insertarHistorico(any());
    }

    // ------------------------------------------------------------------ informe

    @Test void elInformeSoloTieneLasQuinceMayoresPlataformasYNingunDatoDeFilas() throws Exception {
        List<TablaHoja.Fila> filas = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            for (int k = 0; k <= i; k++) filas.add(fila(2 + filas.size(), 46332, String.format("Plataforma %02d", i), "x", 350000000000001L + filas.size()));
        when(sheets.leerTodo(any(), any(), any())).thenReturn(new TablaHoja(Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3), filas));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        InformeCarga i = c.estado().informe();
        assertEquals(15, i.plataformas().size());
        assertEquals(List.of("Plataforma 19", "Plataforma 18"), i.plataformas().keySet().stream().limit(2).toList());
        assertEquals(20, i.plataformas().get("Plataforma 19"));
        assertFalse(i.plataformas().containsKey("Plataforma 04"));
        assertEquals(210, i.filasLeidas());
        assertEquals(210, i.cargadasHistorico());
        assertFalse(c.estado().toString().contains("35000000000000"));
    }

    // ------------------------------------------------------------------ revision

    private static MovilNsys movil(String imei, EstadoMovil estado) {
        return new MovilNsys(imei, "L1", null, null, null, null, null, null, null, null, null, null, null, null, null, false, null, null,
                estado, null, null);
    }

    @Test void unImeiVendidoEnNsysNoSeTraspasaYVaAlHistoricoYSeCuenta() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        // La fila B de la hoja (IMEI ...002): su lote mas reciente esta vendido; el anterior (en stock) no cuenta.
        when(nsys.porImeis(any())).thenReturn(List.of(movil("350000000000002", EstadoMovil.VENDIDO), movil("350000000000002", EstadoMovil.STOCK),
                movil("350000000000003", EstadoMovil.STOCK)));
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        InformeCarga i = c.estado().informe();
        assertEquals(1, i.yaVendidoNsys());
        assertEquals(0, i.traspasadasB());
        assertEquals(1, i.traspasadasC());
        assertEquals(2, i.cargadasHistorico(), "la vendida y la antigua; la incidencia ya no se carga");
        ArgumentCaptor<java.util.Collection<String>> pedidos = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(nsys, times(1)).porImeis(pedidos.capture());
        assertEquals(java.util.Set.of("350000000000002", "350000000000003"), java.util.Set.copyOf(pedidos.getValue()));
    }

    @Test void siLaHojaTrajoMuyPocasFilasSeAbortaAntesDeBorrarNada() throws Exception {
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        CargaHistorico c = new CargaHistorico(sheets, dao, nsys, lectura, tm, ID_HOJA, "PEDIDOS 2026", 1000,
                Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID), enSeco);
        c.iniciar();
        assertEquals(Estado.ERROR, c.estado().estado());
        assertEquals(CargaHistorico.MOTIVO_POCAS, c.estado().motivo());
        assertEquals("La hoja ha devuelto muy pocas filas: no se ha tocado nada", c.estado().motivo());
        verifyNoInteractions(dao, tm, lectura);
    }

    @Test void conApuntesDeCambiosColgandoDelHistoricoSeAbortaSinBorrarNada() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        when(dao.contarCambiosDeHistorico()).thenReturn(2);
        CargaHistorico c = carga(enSeco);
        c.iniciar();
        assertEquals(Estado.ERROR, c.estado().estado());
        assertEquals(CargaHistorico.MOTIVO_CAMBIOS, c.estado().motivo());
        verify(dao, never()).borrarHistorico();
        verify(dao, never()).traspasarImei(anyInt(), anyString(), any());
        verify(dao, never()).insertarHistorico(any());
        verify(tm).rollback(any());
    }

    @Test void nsysSeConsultaAntesDeTomarLosBloqueosDeLaTransaccion() throws Exception {
        erpDePrueba();
        when(sheets.leerTodo(any(), any(), any())).thenReturn(hoja());
        when(nsys.porImeis(any())).thenAnswer(i -> {
            verifyNoInteractions(tm, lectura);
            verify(dao, never()).todasDeShippyProBloqueadas();
            return List.of();
        });
        carga(enSeco).iniciar();
        verify(nsys).porImeis(any());
        assertEquals(Estado.OK, carga(enSeco).estado().estado() == Estado.NUNCA ? Estado.OK : Estado.ERROR);
    }
}
