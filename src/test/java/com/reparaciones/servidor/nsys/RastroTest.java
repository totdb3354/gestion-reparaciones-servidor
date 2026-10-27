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
                new EventoRastro(t(30, 10), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "R20260930_2", "Técnico 5", "REPARACION", null, null, null, null, null),
                new EventoRastro(t(8, 13), false, "NSYS", "UBICACION", "BT-9001", "Taller · Glass", "Listos", "CAJA LISTOS", null, null, null, null, null, null, null, null),
                new EventoRastro(t(8, 12), false, "ERP", "TRABAJO_TERMINADO", null, null, null, null, "G20260905_1", "Técnico 3", "GLASS", null, null, null, null, null),
                new EventoRastro(t(5, 9), false, "ERP", "TRABAJO_ASIGNADO", null, null, null, null, "AG20260905_1", "Técnico 3", "GLASS", null, null, null, null, null),
                new EventoRastro(t(2, 0), true, "NSYS", "ENTRADA", "BT-9001", null, null, null, null, null, null, null, null, null, null, null)),
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
                null, null, null, null, null, "BT-1333", null, null, null), rastro.get(1));
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

    @Test void losCambiosDelTallerYSusCierresSeMezclanPorFecha() {
        // 0.9.21 (spec «SKU del taller» §2.4): cada cambio a su hora y cada cierre automático a la del cierre. Un SUSTITUIDO no
        // tiene evento de cierre: lo cuenta el que lo sustituye (aquí, la vuelta a NSYS).
        String imei = "350000000000001";
        CambioTaller pasado = new CambioTaller(1, imei, DatoTaller.GRADO, "A100", "B", "B", "MANO", null, 7, "Técnico 1",
                "batería nueva", t(10, 9), CambioTaller.Estado.PASADO, t(12, 9), "A100", null);
        CambioTaller sustituido = new CambioTaller(2, imei, DatoTaller.COLOR, "Rojo", "Azul", "Azul", "MANO", null, 7,
                "Técnico 1", "chasis rojo", t(11, 9), CambioTaller.Estado.SUSTITUIDO, t(13, 9), null, null);
        CambioTaller vuelta = new CambioTaller(3, imei, DatoTaller.COLOR, "Azul", "Rojo", "Azul", "MANO", null, 8, "Técnico 2",
                null, t(13, 9), CambioTaller.Estado.QUITADO, t(13, 9), null, 2);
        CambioTaller manda = new CambioTaller(4, imei, DatoTaller.COLOR, "Verde", "Azul", "Azul", "MANO", null, 7, "Técnico 1",
                "chasis verde", t(14, 9), CambioTaller.Estado.MANDA_NSYS, t(15, 9), "Rosa", null);
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(pasado, sustituido, vuelta, manda));
        assertEquals(List.of("TALLER_MANDA_NSYS", "TALLER_CAMBIO", "TALLER_VUELTA", "TALLER_PASADO", "TALLER_CAMBIO",
                "TALLER_CAMBIO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(t(15, 9), false, "TALLER", "TALLER_MANDA_NSYS", null, "Verde", "Rosa", null, null,
                "Técnico 1", null, null, null, "COLOR", "MANO", "chasis verde"), rastro.get(0));
        assertEquals(new EventoRastro(t(13, 9), false, "TALLER", "TALLER_VUELTA", null, "Rojo", "Azul", null, null,
                "Técnico 2", null, null, null, "COLOR", "MANO", null), rastro.get(2));
        assertEquals(new EventoRastro(t(12, 9), false, "TALLER", "TALLER_PASADO", null, "A100", "A100", null, null,
                "Técnico 1", null, null, null, "GRADO", "MANO", "batería nueva"), rastro.get(3));
    }

    @Test void losAvisosDelTallerSalenConSuTipoYDetrasDeLosCambiosALaMismaHora() {
        // 0.9.21 (pieza 2 §2.5): al editar una reparación, «Se deshace…» y el cambio nuevo llevan la misma hora; el cambio
        // es lo último y sale primero.
        String imei = "350000000000001";
        LocalDateTime hora = LocalDateTime.of(2026, 10, 21, 10, 0);
        CambioTaller nuevo = new CambioTaller(7, imei, DatoTaller.COLOR, "Verde", "Azul", "Azul", "REPARACION", "R20261021_3", 8,
                "Técnico 2", "chasis chai13green nuevo", hora, CambioTaller.Estado.VIGENTE, null, null, null);
        AvisoTaller deshecho = new AvisoTaller(1, imei, "R20261021_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA,
                DatoTaller.COLOR, "Rojo", "Azul", "chasis chai13red nuevo", 8, "Técnico 2", hora);
        AvisoTaller sinGrado = new AvisoTaller(2, imei, "R20261021_4", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO, "ID",
                null, "batería bati13 nueva", 8, "Técnico 2", hora.minusHours(1));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(nuevo), List.of(deshecho, sinGrado));
        assertEquals(List.of("TALLER_CAMBIO", "TALLER_DESHECHO_EDITADA", "TALLER_SIN_GRADO"),
                rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora, false, "TALLER", "TALLER_DESHECHO_EDITADA", null, "Rojo", "Azul", null,
                "R20261021_3", "Técnico 2", null, null, null, "COLOR", "REPARACION", "chasis chai13red nuevo"), rastro.get(1));
        assertEquals(new EventoRastro(hora.minusHours(1), false, "TALLER", "TALLER_SIN_GRADO", null, "ID", null, null,
                "R20261021_4", "Técnico 2", null, null, null, "GRADO", "REPARACION", "batería bati13 nueva"), rastro.get(2));
        assertEquals("REPARACION", rastro.get(0).origen());
        assertEquals("R20261021_3", rastro.get(0).idRep());
    }

    @Test void sinAvisosComoAntes() {
        assertEquals(Rastro.unir(List.of(), List.of(), List.of(), List.of()),
                Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test void unAvisoRevisadoSaleTambienALaHoraDeLaRevisionConQuienLoReviso() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 22, 11, 14);
        AvisoTaller a = new AvisoTaller(1, "350000000000001", "R20261022_2", AvisoTaller.Tipo.SIN_COLOR, null, DatoTaller.COLOR,
                "chai14yellowesim", null, "chasis chai14yellowesim nuevo", 8, "Técnico 2", hora, "Supertécnico 1",
                hora.plusDays(1));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_REVISADO_SIN_COLOR", "TALLER_SIN_COLOR"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusDays(1), false, "TALLER", "TALLER_REVISADO_SIN_COLOR", null, "chai14yellowesim",
                null, null, "R20261022_2", "Supertécnico 1", null, null, null, "COLOR", "MANO", "chasis chai14yellowesim nuevo"),
                rastro.get(0));
    }

    @Test void unAvisoSinRevisarNoTieneEventoDeRevision() {
        AvisoTaller a = new AvisoTaller(1, "350000000000001", "R20261022_2", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "ID", null, "chasis chai13blue nuevo", 8, "Técnico 2", LocalDateTime.of(2026, 10, 22, 11, 14));
        assertEquals(List.of("TALLER_SIN_GRADO"),
                Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a)).stream().map(EventoRastro::tipo).toList());
    }

    @Test void unaBateriaUsadaPorElErpSaleComoTallerBateriaUsadaConElGradoQueQuedo() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 26, 17, 28);
        AvisoTaller a = new AvisoTaller(40, "350000000000001", "R20261026_205", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "n/a", "A100", "batería bati13 nueva", 8, "Técnico 2", hora, null, hora.plusSeconds(4));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_BATERIA_USADA", "TALLER_SIN_GRADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusSeconds(4), false, "TALLER", "TALLER_BATERIA_USADA", null, "n/a", "A100", null,
                "R20261026_205", null, null, null, null, "GRADO", "REPARACION", "batería bati13 nueva"), rastro.get(0));
    }

    /** Un «Grado sin calcular» viejo dado por bueno a mano (de antes de la pieza 4, o de los que marca el guion de producción) no es
     *  una batería usada por el ERP: solo darla por usada escribe A; «✓ Revisado» y el guion no. */
    @Test void unGradoSinCalcularRevisadoAManoSaleComoRevisadoConQuienLoReviso() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 21, 9, 30);
        AvisoTaller a = new AvisoTaller(41, "350000000000001", "R20261021_7", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "n/a", null, "tapa trasera tapi13blue nueva", 8, "Técnico 2", hora, "Supertécnico 1", hora.plusHours(2));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_REVISADO_SIN_GRADO", "TALLER_SIN_GRADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusHours(2), false, "TALLER", "TALLER_REVISADO_SIN_GRADO", null, "n/a", null, null,
                "R20261021_7", "Supertécnico 1", null, null, null, "GRADO", "MANO", "tapa trasera tapi13blue nueva"), rastro.get(0));
    }

    /** Si se borra el usuario que lo revisó, REVISADO_POR queda a null (la FK es ON DELETE SET NULL) y el nombre no sale; sin A
     *  sigue siendo un «Revisado» a mano, no «Batería resuelta por el ERP». */
    @Test void unGradoSinCalcularRevisadoPorUnUsuarioBorradoSigueSiendoRevisadoAMano() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 21, 9, 30);
        AvisoTaller a = new AvisoTaller(42, "350000000000001", "R20261021_8", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "n/a", null, "tapa trasera tapi13blue nueva", 8, "Técnico 2", hora, null, hora.plusHours(2));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_REVISADO_SIN_GRADO", "TALLER_SIN_GRADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusHours(2), false, "TALLER", "TALLER_REVISADO_SIN_GRADO", null, "n/a", null, null,
                "R20261021_8", null, null, null, null, "GRADO", "MANO", "tapa trasera tapi13blue nueva"), rastro.get(0));
    }
}
