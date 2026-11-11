package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LecturaFilasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 10, 0);
    private final ClienteShippyPro shippy = mock(ClienteShippyPro.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final Clock reloj = Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID);

    private LecturaFilas lectura(String canales) {
        return new LecturaFilas(shippy, dao, nsys, canales, reloj);
    }

    private static PedidoShippyPro pedido(String numero) {
        return new PedidoShippyPro(numero, "100", AHORA.minusDays(1), null, "ES",
                List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)));
    }

    private static FilaPedido viva(int id, String pedido) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 9), "AMZ", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, 9),
                "SHIPPYPRO", Canal.AMAZON, 1, null);
    }

    @Test void unaPasadaBuenaInsertaCierraYApuntaOk() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(AceptaSkuTest.producto("Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128AzulAP", "bk-azul-a")));

        lectura("100:AMAZON").pasada();

        verify(dao).insertar(argThat(l -> l.size() == 1 && l.get(0).pedido().equals("402-0000000-0000011")
                && l.get(0).fecha().equals(LocalDate.of(2026, 11, 10))));
        verify(dao).cerrar(List.of(), AHORA); // la primera lectura buena no cierra: falta confirmarlo con la siguiente
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()), isNull(), eq(AHORA));
        verify(dao).podarLecturas(AHORA.minusDays(30));
    }

    @Test void unaFilaSeCierraAlFaltarEnDosLecturasBuenasSeguidas() throws Exception {
        unPedidoBien();
        LecturaFilas l = lectura("100:AMAZON");
        l.pasada();
        l.pasada();
        InOrder orden = inOrder(dao);
        orden.verify(dao).cerrar(eq(List.of()), any());
        orden.verify(dao).cerrar(eq(List.of(9)), any());
    }

    @Test void ausentePresenteAusenteNoCierra() throws Exception {
        unPedidoBien();
        LecturaFilas l = lectura("100:AMAZON");
        l.pasada();
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000011")));
        l.pasada();
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        l.pasada();
        verify(dao, never()).cerrar(eq(List.of(9)), any());
    }

    @Test void unErrorEntreLasDosLecturasReiniciaLaConfirmacion() throws Exception {
        unPedidoBien();
        LecturaFilas l = lectura("100:AMAZON");
        l.pasada();
        when(shippy.pendientes()).thenThrow(new IOException("x"));
        l.pasada();
        doReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of())).when(shippy).pendientes();
        l.pasada();
        verify(dao, never()).cerrar(eq(List.of(9)), any());
    }

    @Test void unaLecturaConPedidosDescartadosNoCierraNiConfirmaNada() throws Exception {
        unPedidoBien();
        LecturaFilas l = lectura("100:AMAZON");
        l.pasada();
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")),
                List.of("402-0000000-0000099")));
        l.pasada();
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        l.pasada();
        verify(dao, never()).cerrar(eq(List.of(9)), any());
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()),
                contains("402-0000000-0000099"), eq(AHORA));
    }

    @Test void unaLecturaBuenaSinPedidosConFilasVivasLoApuntaEnElMensaje() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        lectura("100:AMAZON").pasada();
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(0), eq(Set.of()),
                contains("no trae pendientes"), eq(AHORA));
        verify(dao, never()).cerrar(eq(List.of(9)), any());
    }

    @Test void unPedidoQueVuelveAPendienteReabreSusFilasYLaLecturaTerminaOk() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of());
        when(dao.cerradasDeShippyPro(any())).thenReturn(List.of(new FilaPedido(4, LocalDate.of(2026, 11, 9), "AMZ",
                "i13128AzulA", "350000000000001", "402-0000000-0000011", LocalDate.of(2026, 11, 9), "SHIPPYPRO", Canal.AMAZON, 1,
                AHORA.minusHours(1))));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(AceptaSkuTest.producto("Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128AzulAP", "bk-azul-a")));

        lectura("100:AMAZON").pasada();

        verify(dao).cerradasDeShippyPro(Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000011")));
        verify(dao).insertar(List.of());
        verify(dao).reabrir(List.of(4), AHORA);
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()), contains("1 filas reabiertas"), eq(AHORA));
    }

    private static ClienteShippyPro.EnvioShippyPro envio(String numero, String marketplace, int horas) {
        return new ClienteShippyPro.EnvioShippyPro(numero, marketplace, AHORA.minusHours(horas), "UPSv2", "1Z0000000000000001");
    }

    private static FilaPedido cerradaSinEnvio(int id, String pedido) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 9), "AMZ", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, 9),
                "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(1));
    }

    @Test void pideLosEnviadosDeLosUltimosDiezDiasYMarcaYCierraAlMomentoLasFilasEnviadas() throws Exception {
        unPedidoBien();
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        when(shippy.enviados(any(), any())).thenReturn(List.of(envio("402-0000000-0000099", "100", 5)));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(viva(9, "402-0000000-0000099"),
                cerradaSinEnvio(8, "402-0000000-0000099")));

        lectura("100:AMAZON").pasada();

        verify(shippy).enviados(AHORA.atZone(MADRID).toInstant().minus(10, java.time.temporal.ChronoUnit.DAYS),
                AHORA.atZone(MADRID).toInstant());
        verify(dao).porMarcarDeShippyPro(Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000099")));
        verify(dao).marcarEnviadas(List.of(
                new SincroShippyPro.MarcaEnvio(9, AHORA.minusHours(5), "UPSv2", "1Z0000000000000001", null, null),
                new SincroShippyPro.MarcaEnvio(8, AHORA.minusHours(5), "UPSv2", "1Z0000000000000001", null, null)), AHORA);
        // Sin esperar a las dos lecturas: la primera ya cierra por envio; el cierre por ausencia sigue pendiente de confirmar.
        verify(dao).cerrar(eq(List.of()), any());
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()), isNull(), eq(AHORA));
    }

    @Test void alMarcarSeGuardanElIdDelEnvioYLaImpresion() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(new ClienteShippyPro.EnvioShippyPro("402-0000000-0000099", "100",
                AHORA.minusHours(5), "UPSv2", "1Z0000000000000001", 4321L, AHORA.minusHours(1))));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(viva(9, "402-0000000-0000099")));

        lectura("100:AMAZON").pasada();

        verify(dao).marcarEnviadas(List.of(new SincroShippyPro.MarcaEnvio(9, AHORA.minusHours(5), "UPSv2", "1Z0000000000000001",
                4321L, AHORA.minusHours(1))), AHORA);
    }

    @Test void unaFilaYaEnviadaSinImprimirSeCompletaEnUnaVueltaPosteriorEnUnaSolaConsulta() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(new ClienteShippyPro.EnvioShippyPro("402-0000000-0000099", "100",
                AHORA.minusDays(4), "UPSv2", "1Z0000000000000001", 4321L, AHORA.minusHours(1))));
        FilaPedido yaEnviada = new FilaPedido(9, LocalDate.of(2026, 11, 6), "AMZ", "i13128AzulA", null, "402-0000000-0000099",
                LocalDate.of(2026, 11, 6), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(4), AHORA.minusDays(4), "UPSv2",
                "1Z0000000000000001", null, 4321L, null);
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(yaEnviada));

        lectura("100:AMAZON").pasada();

        verify(dao, times(1)).porMarcarDeShippyPro(any());
        verify(dao).marcarEnviadas(List.of(), AHORA);
        verify(dao).marcarEtiquetas(List.of(new SincroShippyPro.MarcaEtiqueta(9, null, AHORA.minusHours(1))));
    }

    @Test void unEnvioDeHaceNueveDiasMarcaUnaFilaNoPendienteSinEnvio() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(new ClienteShippyPro.EnvioShippyPro("402-0000000-0000099", "100",
                AHORA.minusDays(9), "UPSv2", "1Z0000000000000001")));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(cerradaSinEnvio(8, "402-0000000-0000099")));

        lectura("100:AMAZON").pasada();

        verify(dao).marcarEnviadas(List.of(
                new SincroShippyPro.MarcaEnvio(8, AHORA.minusDays(9), "UPSv2", "1Z0000000000000001", null, null)), AHORA);
    }

    @Test void unaFilaCuyoPedidoSeReabreEnLaMismaVueltaNoRecibeEtiqueta() throws Exception {
        unPedidoBien(); // pendiente: 402-0000000-0000011
        when(shippy.enviados(any(), any())).thenReturn(List.of(new ClienteShippyPro.EnvioShippyPro("402-0000000-0000011", "100",
                AHORA.minusDays(2), "UPSv2", "x", 4321L, AHORA.minusDays(1))));
        FilaPedido vieja = new FilaPedido(7, LocalDate.of(2026, 11, 6), "AMZ", "i13128AzulA", null, "402-0000000-0000011",
                LocalDate.of(2026, 11, 6), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(2), AHORA.minusDays(3), "UPSv2", "x",
                null, 1L, null);
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(vieja));

        lectura("100:AMAZON").pasada();

        verify(dao).marcarEtiquetas(List.of());
    }

    @Test void marcarEnviosVaAntesQueCerrar() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(envio("402-0000000-0000099", "100", 5)));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        lectura("100:AMAZON").pasada();
        InOrder orden = inOrder(dao);
        orden.verify(dao).insertar(any());
        orden.verify(dao).marcarEnviadas(any(), any());
        orden.verify(dao).cerrar(any(), any());
    }

    @Test void unPedidoPendienteEnEstaVueltaNoSeMarcaAunqueFigureComoEnviado() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(envio("402-0000000-0000011", "100", 5)));
        when(dao.porMarcarDeShippyPro(any())).thenReturn(List.of(viva(7, "402-0000000-0000011")));

        lectura("100:AMAZON").pasada();

        verify(dao).marcarEnviadas(List.of(), AHORA);
    }

    @Test void unEnvioDeUnMarketplaceSinCanalNoPideNadaALaBase() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenReturn(List.of(envio("402-0000000-0000099", "999", 5)));

        lectura("100:AMAZON").pasada();

        verify(dao, never()).porMarcarDeShippyPro(any());
        verify(dao).marcarEnviadas(List.of(), AHORA);
    }

    @Test void siLosEnviadosFallanLaVueltaSigueOkSinMarcarNadaYLoApunta() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenThrow(new IOException("fallo en https://ejemplo.invalid/?clave=secreta"));

        lectura("100:AMAZON").pasada();

        verify(dao).insertar(argThat(l -> l.size() == 1));
        verify(dao, never()).porMarcarDeShippyPro(any());
        verify(dao).marcarEnviadas(List.of(), AHORA);
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()),
                argThat(m -> m.contains("no se pudieron leer los envíos") && !m.contains("secreta")), eq(AHORA));
        verify(dao, never()).terminarLectura(anyInt(), eq(LecturaFilas.ERROR), anyInt(), any(), any(), any());
    }

    @Test void siLosEnviadosFallanLaRegladeLasDosLecturasSigueIgual() throws Exception {
        unPedidoBien();
        when(shippy.enviados(any(), any())).thenThrow(new IOException("x"));
        LecturaFilas l = lectura("100:AMAZON");
        l.pasada();
        l.pasada();
        InOrder orden = inOrder(dao);
        orden.verify(dao).cerrar(eq(List.of()), any());
        orden.verify(dao).cerrar(eq(List.of(9)), any());
    }

    @Test void conPedidosDescartadosNoSeMarcaNingunEnvio() throws Exception {
        unPedidoBien();
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")),
                List.of("402-0000000-0000077")));
        when(shippy.enviados(any(), any())).thenReturn(List.of(envio("402-0000000-0000099", "100", 5)));

        lectura("100:AMAZON").pasada();

        verify(shippy, never()).enviados(any(), any());
        verify(dao).marcarEnviadas(List.of(), AHORA);
    }

    @Test void siShippyProFallaAlLeerPendientesNoSePidenLosEnvios() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenThrow(new IOException("x"));
        lectura("100:AMAZON").pasada();
        verify(shippy, never()).enviados(any(), any());
    }

    @Test void sinElAjusteDeCanalesEsRechazadaYNoCierraNada() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);

        lectura("").pasada();

        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.RECHAZADA), eq(0), eq(Set.of()),
                eq("Falta el ajuste pedidos.shippypro.canales"), eq(AHORA));
        verify(dao, never()).cerrar(any(), any());
        verify(dao, never()).insertar(any());
        verifyNoInteractions(shippy);
    }

    @Test void siShippyProFallaEsErrorSinUrlYNoCierraNada() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenThrow(new IOException("fallo en https://ejemplo.invalid/?clave=secreta"));

        lectura("100:AMAZON").pasada();

        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.ERROR), eq(0), eq(Set.of()),
                argThat(m -> m.contains("IOException") && !m.contains("secreta") && !m.contains("http")), eq(AHORA));
        verify(dao, never()).cerrar(any(), any());
        verify(dao, never()).vivasDeShippyPro();
    }

    private void unPedidoBien() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(AceptaSkuTest.producto("Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128AzulAP", "bk-azul-a")));
    }

    @Test void insertarVaAntesQueCerrar() throws Exception {
        unPedidoBien();
        lectura("100:AMAZON").pasada();
        InOrder orden = inOrder(dao);
        orden.verify(dao).insertar(any());
        orden.verify(dao).cerrar(any(), any());
    }

    @Test void siCerrarFallaLaLecturaTerminaComoErrorNoComoOk() throws Exception {
        unPedidoBien();
        doThrow(new IllegalStateException("fallo")).when(dao).cerrar(any(), any());
        lectura("100:AMAZON").pasada();
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.ERROR), eq(0), eq(Set.of()), contains("IllegalStateException"), eq(AHORA));
        verify(dao, never()).terminarLectura(anyInt(), eq(LecturaFilas.OK), anyInt(), any(), any(), any());
    }

    @Test void siTerminarLecturaFallaEnUnCaminoDeErrorNoSePropaga() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenThrow(new IOException("x"));
        doThrow(new IllegalStateException("bd")).when(dao).terminarLectura(anyInt(), any(), anyInt(), any(), any(), any());
        lectura("100:AMAZON").pasada();
    }

    @Test void siEmpezarLecturaFallaNoSePropagaNiLlamaAShippyPro() throws Exception {
        when(dao.empezarLectura(any())).thenThrow(new IllegalStateException("bd"));
        lectura("100:AMAZON").pasada();
        verifyNoInteractions(shippy);
    }

    @Test void unaSegundaPasadaMientrasLaPrimeraEstaEnCursoNoEmpiezaOtraLectura() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        when(shippy.pendientes()).thenAnswer(inv -> {
            dentro.countDown();
            soltar.await(10, TimeUnit.SECONDS);
            return new ClienteShippyPro.Leidos(List.of(), List.of());
        });
        LecturaFilas l = lectura("100:AMAZON");
        Thread primera = new Thread(l::pasada);
        primera.start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(dentro.await(10, TimeUnit.SECONDS));
            l.pasada();
            verify(dao, times(1)).empezarLectura(any());
        } finally {
            soltar.countDown();
            primera.join(10_000);
        }
    }

    @Test void sinVueltasEjecutaElTrabajoYMientrasDuraLasVueltasSeSaltan() throws Exception {
        LecturaFilas l = lectura("100:AMAZON");
        assertEquals("hecho", l.sinVueltas(() -> "hecho"));

        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        Thread carga = new Thread(() -> {
            try {
                l.sinVueltas(() -> {
                    dentro.countDown();
                    try {
                        soltar.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        carga.start();
        try {
            assertTrue(dentro.await(10, TimeUnit.SECONDS));
            clearInvocations(dao);
            l.pasada();
            verifyNoInteractions(dao, shippy);
        } finally {
            soltar.countDown();
            carga.join(10_000);
        }
        // Y al terminar el trabajo el candado queda libre.
        when(dao.empezarLectura(any())).thenReturn(1);
        l.pasada();
        verify(dao).empezarLectura(any());
    }

    /** Un reloj que avanza 3 minutos en cada lectura: lo que tarda ShippyPro en responder. */
    private static Clock relojQueAvanza() {
        return new Clock() {
            private Instant ahora = AHORA.atZone(MADRID).toInstant();
            @Override public ZoneId getZone() { return MADRID; }
            @Override public Clock withZone(ZoneId zona) { return this; }
            @Override public Instant instant() { Instant i = ahora; ahora = ahora.plusSeconds(180); return i; }
        };
    }

    @Test void laReaperturaLlevaLaHoraDeInicioDeLaVueltaNoLaDeDespuesDeLeerShippyPro() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of());
        when(dao.cerradasDeShippyPro(any())).thenReturn(List.of(new FilaPedido(4, LocalDate.of(2026, 11, 9), "AMZ",
                "i13128AzulA", "350000000000001", "402-0000000-0000011", LocalDate.of(2026, 11, 9), "SHIPPYPRO", Canal.AMAZON, 1,
                AHORA.minusHours(1))));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(AceptaSkuTest.producto("Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128AzulAP", "bk-azul-a")));

        new LecturaFilas(shippy, dao, nsys, "100:AMAZON", relojQueAvanza()).pasada();

        verify(dao).reabrir(List.of(4), AHORA);
    }
}
