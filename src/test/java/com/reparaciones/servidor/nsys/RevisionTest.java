package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Revisado» (spec 0.9.18 §5.1): de «sin caja» (En revisión) a una caja que no sea «Pedidos realizados». */
class RevisionTest {

    private static LocalDateTime t(int dia, int hora) {
        return LocalDateTime.of(2026, 10, dia, hora, 31);
    }

    private static EventoNsys ubicacion(String lote, LocalDateTime fecha, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, false, TipoEvento.UBICACION, de, a, aNsys);
    }

    private static MovilNsys movil(String ubicacion, LocalDate recepcion) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "", "Proveedor A",
                recepcion, null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, ubicacion, ubicacion,
                EstadoMovil.STOCK, t(1, 9), t(1, 9));
    }

    @Test void deEnRevisionAUnaCajaEsLaRevisionDeEntrada() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(17, 10), "En revisión", "Taller · Pulir", "CAJA PULIR"),
                ubicacion("BT-9001", t(18, 10), "Taller · Pulir", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList("ENTRADA", null), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(17, 10), false), Revision.ultima(e, "BT-9001"));
    }

    @Test void deEnRevisionAPedidosRealizadosEsUnaVentaSinRevisar() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(17, 10), "En revisión", null, "Pedidos realizados"));
        assertEquals(Collections.singletonList(null), Revision.tipos(e));
        assertNull(Revision.ultima(e, "BT-9001"));
    }

    @Test void soloElPrimerCambioDesdeEnRevisionDeCadaLoteEsDeEntrada() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9002", t(12, 10), "En revisión", "Taller · Glass", "CAJA GLASS"),
                ubicacion("BT-9001", t(14, 10), "Listos", "En revisión", ""),
                ubicacion("BT-9001", t(15, 10), "En revisión", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList("ENTRADA", "ENTRADA", null, null), Revision.tipos(e));
    }

    @Test void salirDeDevolucionAUnaCajaEsLaRevisionDeUnaDevolucion() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "Devolución", "Funcionalidad pendiente"),
                ubicacion("BT-9001", t(13, 10), "Devolución", "Taller · Reparación", "CAJA REP. OFICINA"),
                ubicacion("BT-9001", t(14, 10), "Devolución", null, "Pedidos realizados"));
        assertEquals(Arrays.asList("ENTRADA", null, "DEVOLUCION", null), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(13, 10), true), Revision.ultima(e, "BT-9001"));
    }

    @Test void laUltimaRevisionEsDelLoteQueSePide() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(10, 10), "En revisión", "Listos", "CAJA LISTOS"));
        assertNull(Revision.ultima(e, "BT-9002"));
    }

    @Test void pendienteDesdeElUltimoEventoQueLoDejoEnRevision() {
        List<EventoNsys> e = List.of(
                new EventoNsys("350000000000001", "BT-9001", t(5, 9), false, TipoEvento.ENTRADA, null, "En revisión", ""),
                ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 12), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void pendienteSinEventosDesdeLaRecepcionDelLote() {
        assertEquals(LocalDate.of(2026, 10, 1),
                Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), List.of()));
    }

    @Test void fueraDeRevisionNoHayPendienteYSinEventosNoHayNada() {
        assertNull(Revision.pendienteDesde(movil("Listos", LocalDate.of(2026, 10, 1)), List.of()));
        assertEquals(List.of(), Revision.tipos(List.of()));
        assertNull(Revision.ultima(List.of(), "BT-9001"));
    }
}
