package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RastroTest {

    private static LocalDateTime t(int dia, int hora) {
        return LocalDateTime.of(2026, 9, dia, hora, 0);
    }

    @Test void uneNsysYTallerDelMasRecienteAlMasAntiguo() {
        List<EventoRastro> rastro = Rastro.unir(
                List.of(new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.ENTRADA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(8, 13), false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")),
                List.of(new TrabajoTaller("AG20260905_1", t(5, 9), null, "Técnico 3"),
                        new TrabajoTaller("G20260905_1", t(5, 9), t(8, 12), "Técnico 3"),
                        new TrabajoTaller("R20260930_2", t(30, 10), null, "Técnico 5")));

        assertEquals(List.of(
                new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION", null, null),
                new EventoRastro(t(8, 13), false, "NSYS", "UBICACION", "BT-9001", "Taller · Glass", "Listos", "CAJA LISTOS", null, null, null, null, null),
                new EventoRastro(t(8, 12), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "G20260905_1", "Técnico 3", "GLASS", null, null),
                new EventoRastro(t(5, 9), false, "ERP", "TRABAJO_ASIGNADO", null, null, null, null, "AG20260905_1", "Técnico 3", "GLASS", null, null),
                new EventoRastro(t(2, 0), true, "NSYS", "ENTRADA", "BT-9001", null, null, null, null, null, null, null, null)),
                rastro);
    }

    @Test void laVentaDelEsqueletoSeQuitaSiHayUnaConHora() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(12, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", t(12, 17), false, TipoEvento.VENTA, "Available", "Released", null),
                new EventoNsys("350000000000001", "BT-9002", t(20, 0), true, TipoEvento.VENTA, null, null, null)), List.of());
        assertEquals(List.of("BT-9002", "BT-9001"), rastro.stream().map(EventoRastro::lote).toList());
        assertEquals(List.of(true, false), rastro.stream().map(EventoRastro::soloDia).toList());
    }

    @Test void aLaMismaHoraVaPrimeroLoUltimoApuntadoYElTrabajoDelTaller() {
        // Esqueleto con entrada y venta el mismo día; un cambio de NSYS a la misma hora que un trabajo terminado.
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.ENTRADA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(2, 0), true, TipoEvento.VENTA, null, null, null),
                        new EventoNsys("350000000000001", "BT-9001", t(8, 13), false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")),
                List.of(new TrabajoTaller("G20260905_1", t(5, 9), t(8, 13), "Técnico 3")));
        assertEquals(List.of("TRABAJO_TERMINADO", "UBICACION", "VENTA", "ENTRADA"),
                rastro.stream().map(EventoRastro::tipo).toList());
    }

    @Test void categoriaPorElIdDelTrabajo() {
        assertEquals("GLASS", Rastro.categoria("AG1"));
        assertEquals("GLASS", Rastro.categoria("G1"));
        assertEquals("PULIDO", Rastro.categoria("AP1"));
        assertEquals("PULIDO", Rastro.categoria("P1"));
        assertEquals("REPARACION", Rastro.categoria("A1"));
        assertEquals("REPARACION", Rastro.categoria("R1"));
    }

    @Test void elCambioDeCajaQueEsLaRevisionLlevaSuTipo() {
        // 0.9.18 (spec §5.3): de «En revisión» a una caja es ENTRADA; salir de «Devolución», DEVOLUCION.
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(2, 9), false, TipoEvento.UBICACION, "En revisión", "Taller · Pulir", "CAJA PULIR"),
                new EventoNsys("350000000000001", "BT-9001", t(4, 9), false, TipoEvento.UBICACION, "Taller · Pulir", "Devolución", "Funcionalidad pendiente"),
                new EventoNsys("350000000000001", "BT-9001", t(6, 9), false, TipoEvento.UBICACION, "Devolución", "Listos", "CAJA LISTOS")), List.of());
        assertEquals(Arrays.asList("DEVOLUCION", null, "ENTRADA"), rastro.stream().map(EventoRastro::revision).toList());
    }

    @Test void laRevisionNoSeDescuadraAlQuitarLaVentaDelEsqueleto() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                new EventoNsys("350000000000001", "BT-9001", t(1, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", t(2, 9), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000001", "BT-9001", t(3, 9), false, TipoEvento.VENTA, "Available", "Released", null)), List.of());
        assertEquals(List.of("VENTA", "UBICACION"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(Arrays.asList(null, "ENTRADA"), rastro.stream().map(EventoRastro::revision).toList());
    }

    // 0.9.21 (corrección en NSYS): la baja de un lote y el alta del IMEI en otro, en una sola línea.

    private static MovilNsys lote(String batch, String goods, String grade, String capacidad, String color, LocalDate recepcion) {
        return new MovilNsys("350000000000001", batch, goods, grade, "InProcess", "", "Proveedor A", recepcion, null, null,
                "15", "15", capacidad, color, grade, true, null, null, EstadoMovil.STOCK, recepcion.atStartOfDay(),
                recepcion.atStartOfDay());
    }

    private static EventoNsys nsys(String lote, LocalDateTime fecha, boolean soloDia, TipoEvento tipo, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, soloDia, tipo, de, a, aNsys);
    }

    @Test void unaCorreccionSaleComoUnSoloEventoConLosDosLotes() {
        LocalDateTime baja = LocalDateTime.of(2026, 10, 20, 10, 1);
        List<MovilNsys> lotes = List.of(
                lote("BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 9, 23)),
                lote("BT-1345", "Apple iPhone 15 eSIM 512GB Black", "A100", "512GB", "Negro", LocalDate.of(2026, 10, 20)));
        List<EventoRastro> rastro = Rastro.unir(lotes, List.of(
                nsys("BT-1333", LocalDateTime.of(2026, 9, 23, 0, 0), true, TipoEvento.ENTRADA, null, null, null),
                nsys("BT-1333", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                nsys("BT-1345", baja, false, TipoEvento.ENTRADA, null, null, "Pedidos realizados"),
                nsys("BT-1345", baja, false, TipoEvento.REENTRADA, null, "BT-1345", null),
                nsys("BT-1345", LocalDateTime.of(2026, 10, 20, 14, 1), false, TipoEvento.VENTA, "InProcess", "Released", null)),
                List.of());

        assertEquals(List.of("VENTA", "CORRECCION", "ENTRADA"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(baja, false, "NSYS", "CORRECCION", "BT-1345", "512GB Amarillo · A", "512GB Negro · A100",
                null, null, null, null, null, "BT-1333"), rastro.get(1));
    }

    @Test void sinMaestroLaCorreccionLlevaElGoods() {
        LocalDateTime baja = LocalDateTime.of(2026, 7, 7, 10, 1);
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1307", "Apple iPhone 15 Pro eSIM 256GB", "n/a", null, null, LocalDate.of(2026, 7, 6)),
                        lote("BT-1309", "Apple iPhone 15 Pro eSIM 256GB Blue Titanium", "B100", null, null, LocalDate.of(2026, 7, 7))),
                List.of(nsys("BT-1307", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null)), List.of());
        EventoRastro c = rastro.get(0);
        assertEquals("CORRECCION", c.tipo());
        assertEquals("Apple iPhone 15 Pro eSIM 256GB · n/a", c.de());
        assertEquals("Apple iPhone 15 Pro eSIM 256GB Blue Titanium · B100", c.a());
    }

    @Test void unaReentradaDeVerdadSigueSaliendo() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 8, 1)),
                        lote("BT-2", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 10, 15))),
                List.of(nsys("BT-1", LocalDateTime.of(2026, 9, 2, 10, 1), false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 15, 10, 1), false, TipoEvento.ENTRADA, null, "Listos", "CAJA LISTOS"),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 15, 10, 1), false, TipoEvento.REENTRADA, null, "BT-2", null)),
                List.of());
        assertEquals(List.of("REENTRADA", "ENTRADA", "ESTADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertNull(rastro.get(0).loteAnterior());
    }

    @Test void trasUnaCorreccionLaRevisionNoSeRepite() {
        LocalDateTime baja = LocalDateTime.of(2026, 10, 20, 10, 1);
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 9, 23)),
                        lote("BT-2", "Apple iPhone 15 eSIM 512GB Black", "A", "512GB", "Negro", LocalDate.of(2026, 10, 20))),
                List.of(nsys("BT-1", LocalDateTime.of(2026, 10, 13, 8, 46), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                        nsys("BT-1", LocalDateTime.of(2026, 10, 18, 9, 1), false, TipoEvento.UBICACION, "Listos", "En revisión", ""),
                        nsys("BT-1", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                        nsys("BT-2", baja, false, TipoEvento.ENTRADA, null, "En revisión", ""),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 21, 9, 1), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS")),
                List.of());
        assertEquals(List.of("UBICACION", "CORRECCION", "UBICACION", "UBICACION"),
                rastro.stream().map(EventoRastro::tipo).toList());
        assertNull(rastro.get(0).revision(), "ya se revisó en BT-1: no vuelve a ser la revisión de entrada");
        assertEquals("ENTRADA", rastro.get(3).revision());
    }
}
