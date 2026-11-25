package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.FilaConImei;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Paso;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Salida;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El servicio de devoluciones: junta los datos, aplica la regla, agrupa y apunta (DAO simulados; IMEI inventados). */
class DevolucionesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 10, 0);
    private static final LocalDateTime PUESTO = LocalDateTime.of(2026, 11, 23, 9, 10);
    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 23, 9, 25);
    private static final LocalDateTime SALIO = LocalDateTime.of(2026, 11, 24, 18, 7);
    private static final String A = "350000000000001", B = "350000000000002", C = "350000000000003";

    private final BotDAO dao = mock(BotDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private Devoluciones servicio;

    @BeforeEach void preparar() {
        servicio = new Devoluciones(dao, nsys, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
        when(nsys.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA LISTOS", "Caja · Listos", true, false),
                new UbicacionNsys("CAJA GLASS", "Caja · Glass", true, false)));
    }

    private static MovilNsys movil(String imei) {
        MovilNsys m = mock(MovilNsys.class);
        when(m.imeiSn()).thenReturn(imei);
        when(m.estado()).thenReturn(EstadoMovil.DE_PEDIDO);
        when(m.location()).thenReturn("Pedidos realizados");
        when(m.grade()).thenReturn("A");
        return m;
    }

    /** Tres candidatos que cumplen; A y B van a CAJA LISTOS, C a CAJA GLASS. */
    private void tresCandidatos() {
        List<String> imeis = List.of(A, B, C);
        when(dao.candidatos(AHORA.minusDays(Devoluciones.DIAS_DESCONTADO), Devoluciones.TOPE_CANDIDATOS)).thenReturn(imeis);
        when(dao.pasos(imeis)).thenReturn(Map.of(
                A, List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT)),
                B, List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT)),
                C, List.of(new Paso("DESCONTADO", "OK", "CAJA GLASS", BOT))));
        when(dao.apuntes(imeis)).thenReturn(List.of(
                new BotDAO.Apunte(10, PUESTO, null, A, "IMEI", null, "Fati"),
                new BotDAO.Apunte(11, PUESTO, null, B, "IMEI", null, "Fati"),
                new BotDAO.Apunte(12, PUESTO, null, C, "IMEI", null, "Fati"),
                new BotDAO.Apunte(10, SALIO, A, "359999999999990", "IMEI", "LIBRO", null),
                new BotDAO.Apunte(11, SALIO, B, null, "IMEI", null, "Lucía"),
                new BotDAO.Apunte(12, SALIO, C, null, "IMEI", null, "Lucía")));
        when(dao.entradasEnPedidos(imeis)).thenReturn(Map.of(A, BOT.plusMinutes(7), B, BOT.plusMinutes(7), C, BOT.plusMinutes(7)));
        List<MovilNsys> moviles = List.of(movil(C), movil(A), movil(B));
        when(nsys.porImeis(imeis)).thenReturn(moviles);
        when(nsys.trabajosDe(imeis)).thenReturn(List.of());
    }

    @Test void sinCandidatosLaListaEstaVaciaYNoConsultaNadaMas() {
        when(dao.candidatos(any(), anyInt())).thenReturn(List.of());
        assertEquals(new ListaDevoluciones(List.of()), servicio.lista());
        verify(nsys, never()).porImeis(any());
    }

    @Test void agrupaPorDestinoEnOrdenDeCajaYDeImei() {
        tresCandidatos();
        ListaDevoluciones lista = servicio.lista();
        assertEquals(List.of(
                new ListaDevoluciones.Grupo("CAJA GLASS", List.of(new ListaDevoluciones.ImeiADevolver(C, "IMEI_CORREGIDO"))),
                new ListaDevoluciones.Grupo("CAJA LISTOS", List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO"),
                        new ListaDevoluciones.ImeiADevolver(B, "IMEI_CORREGIDO")))), lista.grupos());
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void unReparadoDespuesNoSaleYSeApuntaUnaVezEnLaFilaDeLaSalida() {
        tresCandidatos();
        when(nsys.trabajosDe(List.of(A, B, C))).thenReturn(List.of(new NsysDAO.TrabajoFecha(C, "R20261123_47", BOT.plusHours(1))));
        ListaDevoluciones lista = servicio.lista();
        assertEquals(1, lista.grupos().size());
        verify(dao).apuntar(12, AHORA, C, "SIN_DESTINO", "reparado después (R20261123_47)");
    }

    @Test void siElSinDestinoYaEstaApuntadoEnEsaFilaNoSeRepite() {
        tresCandidatos();
        when(nsys.trabajosDe(List.of(A, B, C))).thenReturn(List.of(new NsysDAO.TrabajoFecha(C, "R20261123_47", BOT.plusHours(1))));
        List<BotDAO.Apunte> conApunte = new java.util.ArrayList<>(dao.apuntes(List.of(A, B, C)));
        conApunte.add(new BotDAO.Apunte(12, AHORA.minusHours(1), C, null, "SIN_DESTINO", "reparado después (R20261123_47)", null));
        when(dao.apuntes(List.of(A, B, C))).thenReturn(conApunte);
        servicio.lista();
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void unaFilaCerradaSoloImpideDevolverSiNoHayVueltaPosterior() {
        tresCandidatos();
        LocalDateTime cierre = LocalDateTime.of(2026, 1, 10, 12, 0);
        FilaConImei enviada = new FilaConImei(9, cierre, cierre, cierre, null, false, null, null, cierre.minusDays(1));
        when(dao.filasConImei(List.of(A, B, C))).thenReturn(Map.of(A, List.of(enviada), B, List.of(enviada)));
        when(dao.vueltas(List.of(A, B, C))).thenReturn(Map.of(A, List.of(cierre.plusDays(20))));
        ListaDevoluciones lista = servicio.lista();
        assertEquals(List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO")), lista.grupos().get(1).imeis());
        assertEquals(2, lista.grupos().size());
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void unCanceladoSinApunteDeSalidaSeDevuelveYUnoQueYaNoEstaSeApuntaSinDestino() {
        tresCandidatos();
        // A y B sin apunte de salida: A cancelado (sale) y B cerrado sin envio («Ya no está en ShippyPro»: se apunta en su fila).
        when(dao.apuntes(List.of(A, B, C))).thenReturn(List.of(
                new BotDAO.Apunte(10, PUESTO, null, A, "IMEI", null, "Fati"),
                new BotDAO.Apunte(11, PUESTO, null, B, "IMEI", null, "Fati"),
                new BotDAO.Apunte(12, PUESTO, null, C, "IMEI", null, "Fati"),
                new BotDAO.Apunte(12, SALIO, C, null, "IMEI", null, "Lucía")));
        when(dao.filasConImei(List.of(A, B, C))).thenReturn(Map.of(
                A, List.of(new FilaConImei(10, null, null, null, null, true, SALIO, "Fati", PUESTO)),
                B, List.of(new FilaConImei(11, SALIO, null, null, null, false, null, null, PUESTO))));
        ListaDevoluciones lista = servicio.lista();
        assertEquals(List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO")), lista.grupos().get(1).imeis());
        verify(dao).apuntar(11, AHORA, B, "SIN_DESTINO", "el pedido ya no está en ShippyPro: comprobar si salió");
        verify(dao, times(1)).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void elEspejoPrefiereElLoteQueEstaDePedido() {
        tresCandidatos();
        MovilNsys otroLote = mock(MovilNsys.class);
        when(otroLote.imeiSn()).thenReturn(A);
        when(otroLote.estado()).thenReturn(EstadoMovil.BAJA);
        when(otroLote.location()).thenReturn("");
        List<MovilNsys> moviles = List.of(otroLote, movil(A), movil(B), movil(C));
        when(nsys.porImeis(List.of(A, B, C))).thenReturn(moviles);
        assertEquals(2, servicio.lista().grupos().get(1).imeis().size());
    }

    @Test void elTopeCortaLaListaEnOrden() {
        tresCandidatos();
        // Tope 2: el primer grupo entero (CAJA GLASS con C) y el primero del segundo (CAJA LISTOS con A).
        ListaDevoluciones lista = servicio.lista(2);
        assertEquals(2, lista.grupos().size());
        assertEquals("CAJA GLASS", lista.grupos().get(0).destino());
        assertEquals(List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO")), lista.grupos().get(1).imeis());
    }

    @Test void registrarGuardaCuentaRepetidosYApuntaLasDevoluciones() {
        MovimientoBot desc = new MovimientoBot(A, "DESCONTADO", "OK", "CAJA LISTOS", "Pedidos realizados", null, BOT);
        MovimientoBot dev = new MovimientoBot(B, "DEVUELTO", "OK", "Pedidos realizados", "CAJA LISTOS", "IMEI_CORREGIDO", AHORA);
        MovimientoBot noAp = new MovimientoBot(C, "DEVUELTO", "NO_APARECE", null, "CAJA GLASS", "IMEI_CORREGIDO", AHORA);
        when(dao.guardar(eq("run-7"), any(), eq(AHORA))).thenReturn(0, 1, 1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.of(new Salida(11, SALIO, "Lucía", "IMEI")));
        when(dao.ultimaSalida(C)).thenReturn(Optional.of(new Salida(12, SALIO, "el libro de ventas", "IMEI")));
        assertEquals(new InformeMovimientos(2, 1), servicio.registrar(new MovimientosBot("run-7", List.of(desc, dev, noAp))));
        verify(dao).apuntar(11, AHORA, B, "DEVUELTO", "de Pedidos realizados a CAJA LISTOS; IMEI cambiado por Lucía el 24/11 18:07");
        verify(dao).apuntar(12, AHORA, C, "NO_DEVUELTO", "no aparece en NSYS en Pedidos realizados");
        verify(dao, never()).apuntar(anyInt(), any(), eq(A), any(), any());
    }

    @Test void unaDevolucionDeUnPedidoCanceladoSeApuntaEnLaFilaCancelada() {
        when(dao.guardar(any(), any(), any())).thenReturn(1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.of(new Salida(29200, SALIO, "Fati", "CANCELADO")));
        servicio.registrar(new MovimientosBot("r", List.of(new MovimientoBot(B, "DEVUELTO", "OK", "Pedidos realizados", "CAJA LISTOS",
                "IMEI_CORREGIDO", AHORA))));
        verify(dao).apuntar(29200, AHORA, B, "DEVUELTO", "de Pedidos realizados a CAJA LISTOS; pedido cancelado por Fati el 24/11 18:07");
        verify(dao, times(1)).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void elTercerErrorSeguidoSeApuntaUnaSolaVez() {
        MovimientoBot err = new MovimientoBot(B, "DEVUELTO", "ERROR", null, "CAJA LISTOS", "IMEI_CORREGIDO", AHORA);
        when(dao.guardar(any(), any(), any())).thenReturn(1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.of(new Salida(11, SALIO, "Lucía", "IMEI")));
        when(dao.ultimosDevueltos(B)).thenReturn(List.of("ERROR", "ERROR"), List.of("ERROR", "ERROR", "ERROR"),
                List.of("ERROR", "ERROR", "ERROR", "ERROR"));
        servicio.registrar(new MovimientosBot("r2", List.of(err)));
        servicio.registrar(new MovimientosBot("r3", List.of(err)));
        servicio.registrar(new MovimientosBot("r4", List.of(err)));
        verify(dao, times(1)).apuntar(11, AHORA, B, "NO_DEVUELTO", "3 errores seguidos");
    }

    @Test void unaDevolucionSinFilaDeSalidaSeGuardaPeroNoApunta() {
        when(dao.guardar(any(), any(), any())).thenReturn(1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.empty());
        servicio.registrar(new MovimientosBot("r", List.of(new MovimientoBot(B, "DEVUELTO", "OK", null, "CAJA LISTOS", null, AHORA))));
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void quienDeUnApunte() {
        assertEquals("Lucía", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", null, "Lucía").quien());
        assertEquals("el libro de ventas", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", "LIBRO", null).quien());
        assertEquals("un proceso", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", null, null).quien());
    }
}
