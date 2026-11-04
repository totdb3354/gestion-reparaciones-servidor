package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LecturaPedidosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final ClienteShippyPro shippy = mock(ClienteShippyPro.class);
    private final ClienteSheets sheets = mock(ClienteSheets.class);
    private final PedidosDAO dao = mock(PedidosDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final LecturaPedidos lectura = new LecturaPedidos(shippy, sheets, dao, nsys, "100:AMAZON", "hoja-p", "PEDIDOS 2026",
            "hoja-s", "Registro actividad", 4000, 14, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    /** ShippyPro sin pendientes y sin pedidos saltados. */
    private static final ClienteShippyPro.Leidos NINGUNO = new ClienteShippyPro.Leidos(List.of(), List.of());

    private static TablaHoja vacia(int total) {
        return new TablaHoja(total, Map.of(), List.of());
    }

    private void hojasBien(int filasPedidos, int filasSimulador) throws Exception {
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(vacia(filasPedidos));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(filasSimulador));
    }

    @Test void conLasTresFuentesBienAplicaYApuntaCadaLectura() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        hojasBien(100, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.OK), eq(0), isNull(), any());
        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.OK), eq(100), isNull(), any());
        verify(dao).terminarLectura(eq(3), eq(LecturaPedidos.OK), eq(200), isNull(), any());
        verify(dao).aplicar(anyList(), eq(AHORA));
        verify(dao).podarLecturas(AHORA.minusDays(30));
    }

    @Test void recienArrancadoYConShippyProCaidoNoTocaNadaYDespuesUsaLoUltimoBueno() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3, 4, 5, 6, 7, 8, 9);
        when(shippy.pendientes()).thenThrow(new IOException("ShippyPro respondió 500")).thenReturn(NINGUNO)
                .thenThrow(new IOException("ShippyPro respondió 500"));
        hojasBien(100, 200);

        lectura.pasada();
        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.ERROR), eq(0), contains("500"), any());
        verify(dao, never()).aplicar(anyList(), any());

        lectura.pasada();
        lectura.pasada();
        verify(dao, times(2)).aplicar(anyList(), eq(AHORA));
    }

    @Test void unaHojaConMenosDelNoventaPorCientoEsIncompletaYNoSeUsa() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(dao.ultimaLecturaConFilas(eq(FuentePedidos.PEDIDOS_2026), any()))
                .thenReturn(Optional.of(new LecturaGuardada(7, FuentePedidos.PEDIDOS_2026, AHORA, AHORA, "OK", 1000, null, null)));
        hojasBien(899, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.INCOMPLETA), eq(899), contains("1000"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void unaColumnaQueFaltaEsRechazada() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt()))
                .thenThrow(new FormatoHojaException("Falta la columna «IMEI» en «PEDIDOS 2026»"));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.RECHAZADA), eq(0), eq("Falta la columna «IMEI» en «PEDIDOS 2026»"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void sinElAjusteDeUnaHojaSeApuntaRechazadaSinLlamarAGoogle() throws Exception {
        LecturaPedidos sinHoja = new LecturaPedidos(shippy, sheets, dao, nsys, "", "", "PEDIDOS 2026", "hoja-s", "Registro actividad",
                4000, 14, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        sinHoja.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.RECHAZADA), eq(0), eq("Falta el ajuste pedidos.pedidos2026.hoja"), any());
        verify(sheets, never()).leer(eq(""), any(), any(), anyInt());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void losPedidosSinFechaValidaSeApuntanEnUnaLecturaBuena() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(), List.of("402-0000000-0000008", "(sin número)")));
        hojasBien(100, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.OK), eq(0),
                eq("Pedidos sin fecha válida, saltados: 402-0000000-0000008, (sin número)"), any());
        verify(dao).aplicar(anyList(), eq(AHORA));
    }

    @Test void unFalloAlCruzarOGuardarMarcaERRORLasTresLecturasDeLaVuelta() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        hojasBien(100, 200);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("x")).when(dao).aplicar(anyList(), any());

        lectura.pasada();

        verify(dao).marcarError(argThat(ids -> java.util.Set.copyOf(ids).equals(Set.of(1, 2, 3))),
                startsWith("No se pudo cruzar o guardar: "));
    }

    @Test void laPodaSeHaceTambienEnUnaVueltaQueSaleTemprano() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenThrow(new IOException("500"));
        hojasBien(100, 200);

        lectura.pasada();

        verify(dao, never()).aplicar(anyList(), any());
        verify(dao).podarLecturas(AHORA.minusDays(30));
    }

    @Test void sinCanalesMapeadosShippyProSeRechazaSinLlamar() throws Exception {
        LecturaPedidos sinCanales = new LecturaPedidos(shippy, sheets, dao, nsys, "", "hoja-p", "PEDIDOS 2026", "hoja-s",
                "Registro actividad", 4000, 14, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        hojasBien(100, 200);

        sinCanales.pasada();

        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.RECHAZADA), eq(0), eq("Falta el ajuste pedidos.shippypro.canales"), any());
        verify(shippy, never()).pendientes();
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void unaSegundaLecturaSinFilasNoSeAceptaSiLaUltimaBuenaTeniaFilas() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(dao.ultimaLecturaConFilas(eq(FuentePedidos.PEDIDOS_2026), any()))
                .thenReturn(Optional.of(new LecturaGuardada(7, FuentePedidos.PEDIDOS_2026, AHORA, AHORA, "OK", 1000, null, null)));
        hojasBien(0, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.INCOMPLETA), eq(0), contains("1000"), any());
    }

    @Test void unaInterrupcionCortaLaVueltaSinLeerLoDemasNiAplicar() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenThrow(new InterruptedException());

        try {
            lectura.pasada();
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        verify(sheets, never()).leer(any(), any(), any(), anyInt());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void elMensajeDeFormatoSeCorta() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenThrow(new FormatoHojaException("x".repeat(2000)));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.RECHAZADA), eq(0), argThat(m -> m.length() == 1000), any());
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Una cola de PEDIDOS 2026 con {@code total} filas desde la {@code desde}, de las que solo las primeras {@code validas} valen. */
    private static TablaHoja colaPedidos(int total, int validas) {
        List<TablaHoja.Fila> filas = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            String plataforma = i < validas ? "Amazon" : "";
            filas.add(new TablaHoja.Fila(2 + i, List.of(JSON.getNodeFactory().numberNode(46329), JSON.getNodeFactory().textNode(plataforma),
                    JSON.getNodeFactory().textNode("i13128AzulA"), JSON.getNodeFactory().textNode("35000000000" + String.format("%04d", i)))));
        }
        return new TablaHoja(total, Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3), filas);
    }

    @Test void unaColaConLaMitadDeFilasInvalidasEsIncompletaYNoAplicaNada() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(colaPedidos(400, 100));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.INCOMPLETA), eq(400),
                eq("Solo 100 de las 400 filas leídas valen: puede haber algo escrito por debajo de los datos en «Plataforma»"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void unaColaDeCuatrocientasConTrescientasNoventaValidasEsOK() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(colaPedidos(400, 390));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.OK), eq(400), isNull(), any());
        verify(dao).aplicar(anyList(), eq(AHORA));
    }

    @Test void unaColaPorDebajoDelMinimoNoMideLaDensidad() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(colaPedidos(50, 10));
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.OK), eq(50), isNull(), any());
    }

    /** Una cola del Simulador de una línea por fila, desde la fila {@code desde}, la primera con esa fecha (número de serie). */
    private static TablaHoja colaSimulador(int desde, int serieDeLaPrimera, int lineas) {
        var f = JSON.getNodeFactory();
        List<TablaHoja.Fila> filas = new java.util.ArrayList<>();
        for (int i = 0; i < lineas; i++)
            filas.add(new TablaHoja.Fila(desde + i, List.of(f.textNode("640" + i), f.textNode("X"), f.numberNode(serieDeLaPrimera + i),
                    f.textNode("Cliente A"), f.textNode("i13128AzulA"), f.numberNode(1), f.textNode(""))));
        return new TablaHoja(desde - 2 + lineas, Map.of("Order id", 0, "Linea facturación", 1, "Fecha", 2, "Cliente", 3, "SKU", 4,
                "Cantidad", 5, "IMEI", 6), filas);
    }

    @Test void siElSimuladorNoLlegaALaVentanaYNoEmpiezaEnLaPrimeraFilaEsIncompleta() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(vacia(10));
        // 4/11/2026 = serie 46330; la línea más antigua es del 1/11, a 3 días
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(colaSimulador(500, 46327, 3));

        lectura.pasada();

        verify(dao).terminarLectura(eq(3), eq(LecturaPedidos.INCOMPLETA), eq(501),
                eq("Las 3 líneas leídas no llegan a 14 días: hay que subir pedidos.filas"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void elSimuladorQueLlegaALaVentanaOQueEmpiezaEnLaPrimeraFilaEsOK() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3, 4, 5, 6);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), anyInt())).thenReturn(vacia(10));
        // más antigua a 20 días
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(colaSimulador(500, 46310, 3));
        lectura.pasada();
        verify(dao).terminarLectura(eq(3), eq(LecturaPedidos.OK), anyInt(), isNull(), any());

        // toda la hoja leída (empieza en la fila 2): no hay nada más antiguo
        when(sheets.leer(eq("hoja-s"), any(), any(), anyInt())).thenReturn(colaSimulador(2, 46327, 3));
        lectura.pasada();
        verify(dao).terminarLectura(eq(6), eq(LecturaPedidos.OK), anyInt(), isNull(), any());
    }
}
