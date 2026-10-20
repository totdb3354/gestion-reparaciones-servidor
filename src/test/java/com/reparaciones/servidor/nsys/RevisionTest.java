package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Revisado» (spec 0.9.18 §5.1): de «sin caja» (En revisión) o de «Devolución» a una caja que no sea «Pedidos
 *  realizados». */
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

    // 0.9.18, decisión del 19/10: «Devolución» (Funcionalidad pendiente) está por revisar, como «En revisión» (sin caja);
    // la revisión es el paso a una caja de verdad.

    @Test void deDevolucionAEnRevisionNoEsRevision() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(12, 10), "Devolución", "En revisión", ""));
        assertEquals(Collections.singletonList(null), Revision.tipos(e));
        assertNull(Revision.ultima(e, "BT-9001"));
    }

    @Test void deDevolucionPasandoPorEnRevisionHastaUnaCajaLaRevisionEsDeDevolucion() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(12, 10), "Listos", "Devolución", "Funcionalidad pendiente"),
                ubicacion("BT-9001", t(13, 10), "Devolución", "En revisión", ""),
                ubicacion("BT-9001", t(14, 10), "En revisión", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList(null, null, "DEVOLUCION"), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(14, 10), true), Revision.ultima(e, "BT-9001"));
    }

    @Test void deEnRevisionADevolucionNoEsRevision() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(12, 10), "En revisión", "Devolución", "Funcionalidad pendiente"));
        assertEquals(Collections.singletonList(null), Revision.tipos(e));
        assertNull(Revision.ultima(e, "BT-9001"));
    }

    @Test void unLoteNuevoQueEntraEnDevolucionSeRevisaComoDevolucion() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(5, 10), "En revisión", "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000001", "BT-9002", t(12, 9), false, TipoEvento.ENTRADA, null, "Devolución",
                        "Funcionalidad pendiente"),
                ubicacion("BT-9002", t(13, 10), "Devolución", "Listos", "CAJA LISTOS"));
        assertEquals(Arrays.asList("ENTRADA", null, "DEVOLUCION"), Revision.tipos(e));
        assertEquals(new Revision.Ultima(t(13, 10), true), Revision.ultima(e, "BT-9002"));
    }

    @Test void pendienteEnDevolucionDesdeElPasoADevolucion() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "Devolución", "Funcionalidad pendiente"));
        assertEquals(LocalDate.of(2026, 10, 12), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void deDevolucionAEnRevisionSigueElMismoPendiente() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(12, 10), "Listos", "Devolución", "Funcionalidad pendiente"),
                ubicacion("BT-9001", t(14, 10), "Devolución", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 12), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void deVueltaAEnRevisionPendienteDesdeElUltimoPaso() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                ubicacion("BT-9001", t(12, 10), "Listos", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 12), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
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

    // 0.9.18.2: la vuelta de un vendido o de una baja (venta deshecha, o de un Status que no es vivo a uno vivo) empieza
    // otra espera. Si vuelve sin caja, la caja de NSYS no cambia y no hay evento de ubicación.

    private static EventoNsys estado(LocalDateTime fecha, TipoEvento tipo, String de, String a) {
        return new EventoNsys("350000000000001", "BT-9001", fecha, false, tipo, de, a, null);
    }

    @Test void devolucionQueVuelveSinCajaYPasaAFuncionalidadPendienteEsperaDesdeLaVuelta() {
        List<EventoNsys> e = new ArrayList<>(List.of(
                ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                estado(t(14, 10), TipoEvento.VENTA_DESHECHA, "Released", "InProcess"),
                ubicacion("BT-9001", t(15, 10), "En revisión", "Devolución", "Funcionalidad pendiente")));
        assertEquals(LocalDate.of(2026, 10, 14), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));

        e.add(ubicacion("BT-9001", t(16, 10), "Devolución", "Listos", "CAJA LISTOS"));
        assertEquals(new Revision.Ultima(t(16, 10), true), Revision.ultima(e, "BT-9001"));
    }

    @Test void vueltaDirectaADevolucionConElDesdeFalsoDeLosEventosAntiguosEsperaDesdeLaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                ubicacion("BT-9001", t(14, 10), "En revisión", "Devolución", "Funcionalidad pendiente"),
                estado(t(14, 10), TipoEvento.VENTA_DESHECHA, "Released", "InProcess"));
        assertEquals(LocalDate.of(2026, 10, 14), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void vueltaDirectaADevolucionConLosEventosLimpiosEsperaDesdeLaVueltaYNoEsRevision() {
        // Como quedan tras la limpieza: sin el «→ En revisión» de la venta y la vuelta sin «Desde».
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                ubicacion("BT-9001", t(14, 10), null, "Devolución", "Funcionalidad pendiente"),
                estado(t(14, 10), TipoEvento.VENTA_DESHECHA, "Released", "InProcess"));
        assertEquals(LocalDate.of(2026, 10, 14), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));
        assertEquals(Arrays.asList("ENTRADA", null, null, null), Revision.tipos(e));
    }

    @Test void bajaDeshechaSinCajaEsperaDesdeLaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.ESTADO, "InProcess", "Removed"),
                estado(t(13, 10), TipoEvento.ESTADO, "Removed", "InProcess"));
        assertEquals(LocalDate.of(2026, 10, 13), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void trasLaVueltaUnaCajaYOtraVezSinCajaEsperaDesdeLaSegundaVez() {
        List<EventoNsys> e = List.of(estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                ubicacion("BT-9001", t(12, 10), null, "Listos", "CAJA LISTOS"),
                estado(t(12, 10), TipoEvento.VENTA_DESHECHA, "Released", "Available"),
                ubicacion("BT-9001", t(15, 10), "Listos", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 15), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void unCambioEntreStatusVivosNoEsUnaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "Listos", "En revisión", ""),
                estado(t(12, 10), TipoEvento.ESTADO, "InProcess", "Available"));
        assertEquals(LocalDate.of(2026, 10, 8), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }
}
