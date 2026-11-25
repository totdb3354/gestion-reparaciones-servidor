package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.*;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La regla de la lista de devoluciones (spec 2026-11-24-bot-devoluciones §5), condición a condición. Datos inventados. */
class ReglaDevolucionesTest {

    private static final LocalDateTime PUESTO = LocalDateTime.of(2026, 11, 23, 9, 10);
    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 23, 9, 25);
    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 11, 23, 9, 32);
    private static final LocalDateTime SALIO = LocalDateTime.of(2026, 11, 24, 18, 7);
    private static final Salida SALIDA = new Salida(29116, SALIO, "el libro de ventas");
    private static final Map<String, UbicacionNsys> UBICACIONES = Map.of(
            "CAJA LISTOS", new UbicacionNsys("CAJA LISTOS", "Caja · Listos", true, false),
            "Pedidos realizados", new UbicacionNsys("Pedidos realizados", "Pedidos realizados", true, true),
            "Desguace", new UbicacionNsys("Desguace", null, false, false),
            "CAJA NO CUENTA", new UbicacionNsys("CAJA NO CUENTA", "No cuenta", false, false));

    private static Paso descontado(String origen) { return new Paso("DESCONTADO", "OK", origen, BOT); }
    private static Paso devuelto(String resultado, LocalDateTime cuando) { return new Paso("DEVUELTO", resultado, "Pedidos realizados", cuando); }

    private static Datos datos(List<Paso> pasos) {
        return new Datos("350000000000001", pasos, SALIDA, List.of(PUESTO), "DE_PEDIDO", "Pedidos realizados", "A", ENTRADA, List.of());
    }

    private static Datos base() { return datos(List.of(descontado("CAJA LISTOS"))); }

    private static Veredicto evaluar(Datos d) { return ReglaDevoluciones.evaluar(d, UBICACIONES); }

    @Test void elCasoNormalSeDevuelveASuCajaDeAntes() {
        Veredicto v = evaluar(base());
        assertEquals(Tipo.DEVOLVER, v.tipo());
        assertEquals("CAJA LISTOS", v.destino());
        assertEquals(SALIDA, v.salida());
    }

    @Test void sinDescontadoOkDelBotNoSale() {
        assertEquals(Tipo.NADA, evaluar(datos(List.of())).tipo());
        assertEquals(Tipo.NADA, evaluar(datos(List.of(new Paso("DESCONTADO", "ERROR", "CAJA LISTOS", BOT)))).tipo());
    }

    @Test void sinSalidaOSalidaAntesDelDescuentoNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), null, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        Salida antes = new Salida(29116, BOT.minusMinutes(1), "Lucía");
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), antes, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siNuncaEstuvoEnUnaFilaAntesDelDescuentoNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(BOT.plusMinutes(5)), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siElEspejoYaNoLoTieneEnPedidosRealizadosNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), "STOCK", "CAJA LISTOS", b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), null, null, b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siLoSacaronYLoVolvieronAMeterNoSale() {
        Datos b = base();
        LocalDateTime tarde = BOT.plusMinutes(31);
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), tarde, b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), null, b.trabajos())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), BOT.plusMinutes(30), b.trabajos())).tipo());
    }

    @Test void yaDevueltoONoApareceNoSale() {
        assertEquals(Tipo.NADA, evaluar(datos(List.of(descontado("CAJA LISTOS"), devuelto("OK", BOT.plusDays(1))))).tipo());
        assertEquals(Tipo.NADA, evaluar(datos(List.of(descontado("CAJA LISTOS"), devuelto("NO_APARECE", BOT.plusDays(1))))).tipo());
    }

    @Test void tresErroresSeguidosLoDejanYDosNo() {
        List<Paso> dos = new ArrayList<>(List.of(descontado("CAJA LISTOS"), devuelto("ERROR", BOT.plusHours(1)), devuelto("ERROR", BOT.plusHours(2))));
        assertEquals(Tipo.DEVOLVER, evaluar(datos(dos)).tipo());
        List<Paso> tres = new ArrayList<>(dos);
        tres.add(devuelto("ERROR", BOT.plusHours(3)));
        assertEquals(Tipo.NADA, evaluar(datos(tres)).tipo());
    }

    @Test void unaDevolucionAnteriorAlDescuentoNoCuenta() {
        // Devuelto OK de una venta anterior, y despues otro descuento: vale el ultimo descuento.
        List<Paso> pasos = List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT.minusDays(5)), devuelto("OK", BOT.minusDays(4)), descontado("CAJA LISTOS"));
        assertEquals(Tipo.DEVOLVER, evaluar(datos(pasos)).tipo());
    }

    @Test void reparadoDespuesDelDescuentoNoSeDevuelveSolo() {
        Datos b = base();
        List<Trabajo> trabajos = List.of(new Trabajo("R20261101_1", BOT.minusDays(20)), new Trabajo("A20261123_41", BOT.plusHours(1)),
                new Trabajo("R20261123_47", BOT.plusHours(2)));
        Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), trabajos));
        assertEquals(Tipo.SIN_DESTINO, v.tipo());
        assertEquals("reparado después (A20261123_41, R20261123_47)", v.motivo());
        assertEquals(SALIDA, v.salida());
    }

    @Test void sinCajaDeOrigen() {
        assertEquals("sin caja", evaluar(datos(List.of(descontado(null)))).motivo());
        assertEquals("sin caja", evaluar(datos(List.of(descontado("  ")))).motivo());
        assertEquals(Tipo.SIN_DESTINO, evaluar(datos(List.of(descontado(null)))).tipo());
    }

    @Test void cajaDeOrigenQueNoEsVendible() {
        for (String caja : List.of("Pendiente revisar si hay error de inventario", "MV Pendiente revisar si hay error de inventario",
                "Pedidos realizados", "Desguace", "CAJA NO CUENTA", "CAJA QUE NO CONOCE EL ERP")) {
            Veredicto v = evaluar(datos(List.of(descontado(caja))));
            assertEquals(Tipo.SIN_DESTINO, v.tipo(), caja);
            assertEquals(caja + " no es vendible", v.motivo());
        }
    }

    @Test void gradoQueNoSeVende() {
        Datos b = base();
        for (String grado : List.of("F", "fd", "RMA")) {
            Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), grado, b.entradaPedidos(), b.trabajos()));
            assertEquals(Tipo.SIN_DESTINO, v.tipo(), grado);
            assertEquals("grado " + grado.toUpperCase() + " no es vendible", v.motivo());
        }
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), "A-100", b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), null, b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void erroresSeguidosAlFinalCuentaSoloLosUltimos() {
        assertEquals(0, ReglaDevoluciones.erroresSeguidosAlFinal(List.of()));
        assertEquals(1, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("NO_APARECE", BOT), devuelto("ERROR", BOT))));
        assertEquals(2, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("ERROR", BOT))));
    }
}
