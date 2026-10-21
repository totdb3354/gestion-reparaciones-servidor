package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Corrección en NSYS (spec 0.9.21 «corrección en NSYS» §2): baja de un lote y alta del IMEI en otro. */
class CorreccionesTest {

    private static final String IMEI = "350000000000001";

    private static LocalDate d(int dia, int mes) {
        return LocalDate.of(2026, mes, dia);
    }

    private static LocalDateTime t(int dia, int mes) {
        return LocalDateTime.of(2026, mes, dia, 10, 1);
    }

    private static MovilNsys lote(String batch, LocalDate recepcion) {
        return new MovilNsys(IMEI, batch, "Apple iPhone 15 eSIM 512GB Yellow", "A", "InProcess", "CAJA LISTOS",
                "Proveedor A", recepcion, null, null, "15", "15", "512GB", "Amarillo", "A", true, "Listos", "Listos",
                EstadoMovil.STOCK, t(1, 1), t(1, 1));
    }

    private static EventoNsys baja(String batch, LocalDateTime fecha) {
        return new EventoNsys(IMEI, batch, fecha, false, TipoEvento.ESTADO, "InProcess", "Removed", null);
    }

    @Test void unaBajaYUnAltaElMismoDiaEsUnaCorreccion() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(23, 9)), lote("BT-2", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals(List.of(new Correcciones.Correccion("BT-1", "BT-2", t(20, 10))), c.todas());
        assertEquals("BT-1", c.primerLote("BT-2"));
        assertEquals("BT-2", c.ultimoLote("BT-1"));
    }

    @Test void elAltaHastaSieteDiasAntesOUnDiaDespuesCuenta() {
        assertEquals(1, Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(13, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().size(), "7 días antes");
        assertEquals(1, Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(21, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().size(), "1 día después");
    }

    @Test void fueraDelMargenNoEsCorreccion() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(12, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "8 días antes");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(22, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "2 días después");
    }

    @Test void unaReentradaSemanasDespuesNoEsCorreccion() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 8)), lote("BT-2", d(15, 10))),
                List.of(baja("BT-1", t(2, 9)))).todas().isEmpty());
    }

    @Test void tresLotesSonUnaCadena() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1307", d(6, 7)), lote("BT-1309", d(7, 7)), lote("BT-1345", d(20, 10))),
                List.of(baja("BT-1307", t(7, 7)), baja("BT-1309", t(20, 10))));
        assertEquals(2, c.todas().size());
        assertEquals("BT-1307", c.primerLote("BT-1345"));
        assertEquals("BT-1345", c.ultimoLote("BT-1307"));
    }

    @Test void conDosCandidatosGanaElMasCercano() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(17, 10)), lote("BT-3", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals("BT-3", c.todas().get(0).lote());
    }

    @Test void sinFechaDeRecepcionDelEsqueletoOAntesQueElViejoNoSigue() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", null)),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "sin fecha de recepción");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(20, 10))),
                List.of(new EventoNsys(IMEI, "BT-1", t(20, 10), true, TipoEvento.ESTADO, null, "Removed", null))).todas().isEmpty(),
                "baja del esqueleto");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(19, 10)), lote("BT-2", d(15, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "recibido antes que el lote dado de baja");
    }

    @Test void unLoteYaCorregidoNoVuelveASerElNuevoDeOtro() {
        // Los dos el mismo día: BT-1 sigue en BT-2; la baja de BT-2 no puede volver a BT-1 (sería un círculo).
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(20, 10)), lote("BT-2", d(20, 10))),
                List.of(baja("BT-1", t(20, 10)), baja("BT-2", LocalDateTime.of(2026, 10, 20, 15, 1))));
        assertEquals(1, c.todas().size());
    }

    @Test void paraRevisionJuntaLaCadenaEnSuUltimoLoteYNeutralizaLaCorreccion() {
        List<EventoNsys> eventos = List.of(
                new EventoNsys(IMEI, "BT-1", t(13, 10), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                baja("BT-1", t(20, 10)),
                new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.ENTRADA, null, "En revisión", ""),
                new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.REENTRADA, null, "BT-2", null));
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(23, 9)), lote("BT-2", d(20, 10))), eventos);
        List<EventoNsys> r = c.paraRevision(eventos);

        assertEquals(4, r.size());
        assertTrue(r.stream().allMatch(e -> e.batch().equals("BT-2")));
        assertEquals(TipoEvento.UBICACION, r.get(0).tipo());
        // La entrada del nuevo en una ubicación pendiente es un paso desde la última caja del viejo (revisión final).
        assertEquals(new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.UBICACION, "Listos", "En revisión", ""), r.get(2));
        assertEquals(List.of(TipoEvento.ESTADO, TipoEvento.ESTADO), List.of(r.get(1).tipo(), r.get(3).tipo()));
        assertTrue(r.get(1).de() == null && r.get(1).a() == null && r.get(3).de() == null && r.get(3).a() == null);
        assertTrue(c.sustituido(eventos.get(1)) && c.sustituido(eventos.get(2)) && c.sustituido(eventos.get(3)));
        assertFalse(c.sustituido(eventos.get(0)));
    }

    @Test void laEntradaDelNuevoEnUnaCajaDeVerdadSigueNeutralizada() {
        List<EventoNsys> eventos = List.of(baja("BT-1", t(20, 10)),
                new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.ENTRADA, null, "Listos", "CAJA LISTOS"));
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(23, 9)), lote("BT-2", d(20, 10))), eventos);
        EventoNsys entrada = c.paraRevision(eventos).get(1);
        assertEquals(TipoEvento.ESTADO, entrada.tipo());
        assertNull(entrada.a());
    }

    @Test void aIgualDistanciaGanaElLoteMayorYDosViejosNoSeRepartenUnNuevo() {
        Correcciones empate = Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(19, 10)), lote("BT-3", d(21, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals("BT-3", empate.todas().get(0).lote());
        Correcciones dos = Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(1, 9)), lote("BT-3", d(20, 10))),
                List.of(baja("BT-1", t(20, 10)), baja("BT-2", LocalDateTime.of(2026, 10, 20, 15, 1))));
        assertEquals(List.of(new Correcciones.Correccion("BT-1", "BT-3", t(20, 10))), dos.todas(), "la primera baja se lo queda");
    }

    @Test void laUltimaCajaAnteriorYLaDescripcion() {
        MovilNsys viejo = new MovilNsys(IMEI, "BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "Removed", "", "Proveedor A",
                d(23, 9), null, null, "15", "15", "512GB", "Amarillo", "A", true, null, "Listos", EstadoMovil.BAJA,
                t(1, 1), t(1, 1));
        MovilNsys nuevo = new MovilNsys(IMEI, "BT-2", "Apple iPhone 15 Pro eSIM 256GB", "n/a", "InProcess",
                "Pedidos realizados", "Proveedor A", d(20, 10), null, null, null, null, null, null, null, true, null, null,
                EstadoMovil.DE_PEDIDO, t(1, 1), t(1, 1));
        Correcciones c = Correcciones.de(List.of(viejo, nuevo), List.of(baja("BT-1", t(20, 10))));

        assertEquals("Listos", c.ultimaCajaAnterior("BT-2"));
        assertNull(c.ultimaCajaAnterior("BT-1"));
        assertEquals(new Correcciones.Cambio("Apple iPhone 15 eSIM 512GB Yellow · A", "Apple iPhone 15 Pro eSIM 256GB · n/a"),
                c.cambio(c.todas().get(0)), "si a uno le falta el maestro, el Goods en los dos");
    }

    private static MovilNsys conMaestro(String batch, String goods, String grade, String modeloMaestro, String color, LocalDate recepcion) {
        return new MovilNsys(IMEI, batch, goods, grade, "InProcess", "CAJA LISTOS", "Proveedor A", recepcion, null, null, "15",
                modeloMaestro, "512GB", color, grade, true, "Listos", "Listos", EstadoMovil.STOCK, t(1, 1), t(1, 1));
    }

    @Test void elCambioUsaElMaestroSoloSiLosDosLoTienenYDiceAlgo() {
        Correcciones color = Correcciones.de(List.of(
                        conMaestro("BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "15", "Amarillo", d(23, 9)),
                        conMaestro("BT-2", "Apple iPhone 15 eSIM 512GB Black", "A100", "15", "Negro", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals(new Correcciones.Cambio("512GB Amarillo · A", "512GB Negro · A100"), color.cambio(color.todas().get(0)));

        Correcciones modelo = Correcciones.de(List.of(
                        conMaestro("BT-1", "Apple iPhone 15 eSIM 512GB Black", "A", "15", "Negro", d(23, 9)),
                        conMaestro("BT-2", "Apple iPhone 15 Pro eSIM 512GB Black", "A", "15 PRO", "Negro", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals(new Correcciones.Cambio("Apple iPhone 15 eSIM 512GB Black · A", "Apple iPhone 15 Pro eSIM 512GB Black · A"),
                modelo.cambio(modelo.todas().get(0)), "con el mismo texto del maestro no se vería el cambio");
    }

    @Test void sinCorreccionesCadaLoteEsSuPropiaCadena() {
        assertEquals("BT-9", Correcciones.NINGUNA.primerLote("BT-9"));
        assertNull(Correcciones.NINGUNA.lote("BT-9"));
        assertNull(Correcciones.NINGUNA.ultimaCajaAnterior("BT-9"));
    }
}
