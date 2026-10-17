package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION"),
                new EventoRastro(t(8, 13), false, "NSYS", "UBICACION", "BT-9001", "Taller · Glass", "Listos", "CAJA LISTOS", null, null, null),
                new EventoRastro(t(8, 12), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "G20260905_1", "Técnico 3", "GLASS"),
                new EventoRastro(t(5, 9), false, "ERP", "TRABAJO_ASIGNADO", null, null, null, null, "AG20260905_1", "Técnico 3", "GLASS"),
                new EventoRastro(t(2, 0), true, "NSYS", "ENTRADA", "BT-9001", null, null, null, null, null, null)),
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
}
