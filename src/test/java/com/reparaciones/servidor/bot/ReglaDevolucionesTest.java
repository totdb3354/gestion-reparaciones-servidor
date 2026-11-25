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
    private static final Salida SALIDA = new Salida(29116, SALIO, "el libro de ventas", ReglaDevoluciones.SALIDA_IMEI);
    private static final Map<String, UbicacionNsys> UBICACIONES = Map.of(
            "CAJA LISTOS", new UbicacionNsys("CAJA LISTOS", "Caja · Listos", true, false),
            "Pedidos realizados", new UbicacionNsys("Pedidos realizados", "Pedidos realizados", true, true),
            "Desguace", new UbicacionNsys("Desguace", null, false, false),
            "CAJA NO CUENTA", new UbicacionNsys("CAJA NO CUENTA", "No cuenta", false, false));

    private static Paso descontado(String origen) { return new Paso("DESCONTADO", "OK", origen, BOT); }
    private static Paso devuelto(String resultado, LocalDateTime cuando) { return new Paso("DEVUELTO", resultado, "Pedidos realizados", cuando); }

    private static Datos datos(List<Paso> pasos) {
        return new Datos("350000000000001", pasos, SALIDA, List.of(PUESTO), "DE_PEDIDO", "Pedidos realizados", "A", ENTRADA, List.of(), List.of(), List.of());
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
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), null, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
        Salida antes = new Salida(29116, BOT.minusMinutes(1), "Lucía", ReglaDevoluciones.SALIDA_IMEI);
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), antes, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
    }

    @Test void siNuncaEstuvoEnUnaFilaAntesDelDescuentoNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(BOT.plusMinutes(5)), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
    }

    @Test void siElEspejoYaNoLoTieneEnPedidosRealizadosNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), "STOCK", "CAJA LISTOS", b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), null, null, b.grado(), b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
    }

    @Test void siLoSacaronYLoVolvieronAMeterNoSale() {
        Datos b = base();
        LocalDateTime tarde = BOT.plusMinutes(31);
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), tarde, b.trabajos(), b.filas(), b.vueltas())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), null, b.trabajos(), b.filas(), b.vueltas())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), BOT.plusMinutes(30), b.trabajos(), b.filas(), b.vueltas())).tipo());
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
        Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), trabajos, b.filas(), b.vueltas()));
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
            Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), grado, b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas()));
            assertEquals(Tipo.SIN_DESTINO, v.tipo(), grado);
            assertEquals("grado " + grado.toUpperCase() + " no es vendible", v.motivo());
        }
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), "A-100", b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), null, b.entradaPedidos(), b.trabajos(), b.filas(), b.vueltas())).tipo());
    }

    /** Ventas anteriores enviadas (envio = cierre, etiqueta impresa, IMEI puesto la vispera). */
    private static Veredicto conFilasCerradas(List<LocalDateTime> cierres, List<LocalDateTime> vueltas) {
        List<FilaConImei> filas = cierres.stream()
                .map(c -> new FilaConImei(20000, c, c, c, null, false, null, null, c.minusDays(1), false)).toList();
        return conFilas(SALIDA, filas, vueltas);
    }

    private static Veredicto conFilas(Salida salida, List<FilaConImei> filas, List<LocalDateTime> vueltas) {
        return evaluar(conFilas(salida, filas, vueltas, base().pasos()));
    }

    private static Datos conFilas(Salida salida, List<FilaConImei> filas, List<LocalDateTime> vueltas, List<Paso> pasos) {
        Datos b = base();
        return new Datos(b.imei(), pasos, salida, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(),
                b.trabajos(), filas, vueltas);
    }

    private static LocalDateTime dia(int mes, int dia) { return LocalDateTime.of(2026, mes, dia, 12, 0); }

    /** Una fila abierta marcada «Cancelar móvil» por Fati a la hora de SALIO, con el IMEI puesto en PUESTO. */
    private static FilaConImei cancelada(LocalDateTime impresa) {
        return new FilaConImei(29200, null, null, impresa, null, true, SALIO, "Fati", PUESTO, false);
    }

    @Test void cancelarMovilSinEtiquetaSeDevuelve() {
        Veredicto v = conFilas(null, List.of(cancelada(null)), List.of());
        assertEquals(Tipo.DEVOLVER, v.tipo());
        assertEquals("CAJA LISTOS", v.destino());
        assertEquals(new Salida(29200, SALIO, "Fati", "CANCELADO"), v.salida());
        assertEquals("CANCELADO", v.salida().motivo());
    }

    @Test void cancelarMovilSinQuienLaPusoEsUnProceso() {
        FilaConImei sinQuien = new FilaConImei(29200, null, null, null, null, true, SALIO, null, PUESTO, false);
        assertEquals("un proceso", conFilas(null, List.of(sinQuien), List.of()).salida().quien());
    }

    @Test void cancelarMovilConEtiquetaImpresaNoSeTocaNada() {
        assertEquals(Tipo.NADA, conFilas(null, List.of(cancelada(SALIO.minusHours(2))), List.of()).tipo());
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(cancelada(SALIO.minusHours(2))), List.of()).tipo());
    }

    @Test void unaFilaAbiertaSinCancelarImpide() {
        FilaConImei abierta = new FilaConImei(29300, null, null, null, null, false, null, null, PUESTO, false);
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(abierta), List.of()).tipo());
        assertEquals(Tipo.NADA, conFilas(null, List.of(cancelada(null), abierta), List.of()).tipo());
    }

    @Test void yaNoEstaEnShippyProConElImeiPuestoAntesDelCierreQuedaSinDestino() {
        FilaConImei cerrada = new FilaConImei(29400, SALIO, null, null, null, false, null, null, PUESTO, false);
        Veredicto v = conFilas(null, List.of(cerrada), List.of());
        assertEquals(Tipo.SIN_DESTINO, v.tipo());
        assertEquals(ReglaDevoluciones.MOTIVO_YA_NO_ESTA, v.motivo());
        assertEquals("el pedido ya no está en ShippyPro: comprobar si salió", v.motivo());
        assertEquals(new Salida(29400, SALIO, "ShippyPro", "YA_NO_ESTA"), v.salida());
        // Antes de mirar la caja: sin caja de origen sigue siendo «ya no está».
        assertEquals(ReglaDevoluciones.MOTIVO_YA_NO_ESTA,
                evaluar(conFilas(null, List.of(cerrada), List.of(), List.of(descontado(null)))).motivo());
        // Y las demas condiciones siguen valiendo: con el espejo fuera de «Pedidos realizados» no sale.
        Datos d = conFilas(null, List.of(cerrada), List.of(), base().pasos());
        assertEquals(Tipo.NADA, evaluar(new Datos(d.imei(), d.pasos(), d.salida(), d.puestos(), "STOCK", "CAJA LISTOS", d.grado(),
                d.entradaPedidos(), d.trabajos(), d.filas(), d.vueltas())).tipo());
    }

    @Test void yaNoEstaEnShippyProConElImeiTecleadoTrasElCierreEsVendido() {
        FilaConImei tecleado = new FilaConImei(29400, SALIO, null, null, null, false, null, null, SALIO.plusMinutes(5), false);
        assertEquals(Tipo.NADA, conFilas(null, List.of(tecleado), List.of()).tipo());
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(tecleado), List.of()).tipo());
        FilaConImei sinApunte = new FilaConImei(29400, SALIO, null, null, null, false, null, null, null, false);
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(sinApunte), List.of()).tipo());
    }

    @Test void enviadaConFaltaPorEnviarImpide() {
        LocalDateTime c = dia(1, 10);
        FilaConImei falta = new FilaConImei(29500, c, c, c, c.plusHours(1), false, null, null, c.minusDays(1), false);
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(falta), List.of(dia(2, 1))).tipo());
    }

    @Test void unaVentaDelHistoricoCuentaComoEnviadaSalvoQueNsysRegistreUnaVueltaPosterior() {
        LocalDateTime c = dia(1, 10);
        // Hoja antigua: cerrada, sin envio, sin apuntes (puestoEn null) y con ORIGEN = HISTORICO.
        FilaConImei historica = new FilaConImei(100, c, null, null, null, false, null, null, null, true);
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(historica), List.of()).tipo());
        assertEquals(Tipo.NADA, conFilas(SALIDA, List.of(historica), List.of(c.minusDays(1))).tipo());
        Veredicto v = conFilas(SALIDA, List.of(historica), List.of(c.plusDays(5)));
        assertEquals(Tipo.DEVOLVER, v.tipo());
        assertEquals(SALIDA, v.salida());
    }

    @Test void valeLaSalidaMasReciente() {
        Salida vieja = new Salida(29116, BOT.minusMinutes(1), "Lucía", ReglaDevoluciones.SALIDA_IMEI);
        Veredicto v = conFilas(vieja, List.of(cancelada(null)), List.of());
        assertEquals(Tipo.DEVOLVER, v.tipo());
        assertEquals(new Salida(29200, SALIO, "Fati", "CANCELADO"), v.salida());
        // Al reves: cancelado antes del descuento y el IMEI quitado despues: vale el apunte.
        FilaConImei canceladaAntes = new FilaConImei(29200, null, null, null, null, true, BOT.minusHours(1), "Fati", PUESTO, false);
        Veredicto w = conFilas(SALIDA, List.of(canceladaAntes), List.of());
        assertEquals(Tipo.DEVOLVER, w.tipo());
        assertEquals(SALIDA, w.salida());
        // Las dos antes del descuento: no sale.
        assertEquals(Tipo.NADA, conFilas(vieja, List.of(canceladaAntes), List.of()).tipo());
    }

    @Test void unaVentaAnteriorDevueltaPorElClienteNoCuenta() {
        assertEquals(Tipo.DEVOLVER, conFilasCerradas(List.of(dia(1, 10)), List.of(dia(2, 1))).tipo());
    }

    @Test void unaFilaCerradaSinVueltaPosteriorCuentaYNoSeDevuelve() {
        assertEquals(Tipo.NADA, conFilasCerradas(List.of(dia(1, 10)), List.of()).tipo());
    }

    @Test void unaVueltaAnteriorAlCierreNoVale() {
        assertEquals(Tipo.NADA, conFilasCerradas(List.of(dia(1, 10)), List.of(dia(1, 5))).tipo());
    }

    @Test void dosVentasAnterioresTienenQueTenerVueltaLasDos() {
        List<LocalDateTime> cierres = List.of(dia(1, 10), dia(3, 10));
        assertEquals(Tipo.NADA, conFilasCerradas(cierres, List.of(dia(2, 1))).tipo());
        assertEquals(Tipo.DEVOLVER, conFilasCerradas(cierres, List.of(dia(2, 1), dia(4, 1))).tipo());
    }

    @Test void erroresSeguidosAlFinalCuentaSoloLosUltimos() {
        assertEquals(0, ReglaDevoluciones.erroresSeguidosAlFinal(List.of()));
        assertEquals(1, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("NO_APARECE", BOT), devuelto("ERROR", BOT))));
        assertEquals(2, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("ERROR", BOT))));
    }
}
