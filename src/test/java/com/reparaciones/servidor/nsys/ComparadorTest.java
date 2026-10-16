package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de comparación de la spec pieza 1 §5.4 y §6 (datos inventados). */
class ComparadorTest {

    private static final String AZUL = "Apple iPhone 13 128GB Blue";
    private static final LocalDateTime AYER = LocalDateTime.of(2026, 10, 15, 13, 31);
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 16, 9, 1);
    private static final Traductor T = new Traductor(
            List.of(new ProductoMaestro(Normalizacion.clave(AZUL, "A"), "13", "13", "128GB", "Azul", "A",
                            "i13128AzulA", "i13128AzulA", "i13128Azul", false),
                    new ProductoMaestro(Normalizacion.clave(AZUL, "C100"), "13", "13", "128GB", "Azul", "C100",
                            "i13128AzulA100", "i13128AzulA", "i13128Azul", false)),
            List.of(new UbicacionNsys("", "En revisión", true, false),
                    new UbicacionNsys("CAJA GLASS", "Taller · Glass", true, false),
                    new UbicacionNsys("CAJA LISTOS", "Listos", true, false),
                    new UbicacionNsys("AFaltaRepuestos", "Esperando pieza", false, false),
                    new UbicacionNsys("Pedidos realizados", null, false, true),
                    new UbicacionNsys("Desguace", null, false, false)));

    private static FilaItems fila(String imei, String batch, String grade, String status, String caja) {
        return new FilaItems(imei, batch, AZUL, grade, status, caja, "Proveedor A", LocalDate.of(2026, 9, 2), null);
    }

    private static Map<ClaveMovil, MovilNsys> previo(FilaItems... filas) {
        return new HashMap<>(Comparador.foto(List.of(filas), T, AYER));
    }

    private static ResultadoComparacion normal(Map<ClaveMovil, MovilNsys> antes, FilaItems... ahora) {
        return Comparador.comparar(antes, List.of(ahora), HOY, Comparador.Modo.NORMAL, T);
    }

    @Test void primeraCargaGuardaTodoYApuntaEntradaYVentaSoloConElDia() {
        FilaItems vendido = new FilaItems("350000000000001", "BT-9001", AZUL, "A", "Released", "Pedidos realizados",
                "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 12));
        FilaItems enStock = fila("350000000000002", "BT-9001", "A", "Available", "CAJA LISTOS");
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(vendido, enStock), HOY, Comparador.Modo.PRIMERA_CARGA, T);

        assertEquals(2, r.aGuardar().size());
        MovilNsys listo = r.aGuardar().get(1);
        assertEquals("i13128AzulA", listo.sku());
        assertEquals("13", listo.modelo());
        assertEquals("Listos", listo.ubicacion());
        assertEquals(EstadoMovil.STOCK, listo.estado());
        assertEquals(HOY, listo.vistoPrimero());
        assertEquals(List.of(
                new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 9, 2, 0, 0), true, TipoEvento.ENTRADA, null, null, null),
                new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 9, 12, 0, 0), true, TipoEvento.VENTA, null, null, null),
                new EventoNsys("350000000000002", "BT-9001", LocalDateTime.of(2026, 9, 2, 0, 0), true, TipoEvento.ENTRADA, null, null, null)),
                r.eventos());
    }

    @Test void primeraCargaConVariosLotesDelMismoImeiApuntaReentradaEnElPosterior() {
        FilaItems primero = new FilaItems("350000000000003", "BT-9001", AZUL, "A", "Removed", "", "Proveedor A",
                LocalDate.of(2026, 9, 2), null);
        FilaItems vuelta = new FilaItems("350000000000003", "BT-9002", AZUL, "A", "InProcess", "Funcionalidad pendiente",
                "Proveedor A", LocalDate.of(2026, 9, 29), null);
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(vuelta, primero), HOY, Comparador.Modo.PRIMERA_CARGA, T);

        assertEquals(TipoEvento.REENTRADA, r.eventos().get(0).tipo());
        assertEquals("BT-9002", r.eventos().get(0).a());
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), r.eventos().get(0).fecha());
        assertEquals(TipoEvento.ENTRADA, r.eventos().get(1).tipo());
        assertEquals("BT-9001", r.eventos().get(1).batch());
    }

    @Test void sinCambiosNoGuardaNiApunta() {
        FilaItems f = fila("350000000000004", "BT-9001", "A", "Available", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(f), f);
        assertTrue(r.aGuardar().isEmpty());
        assertTrue(r.eventos().isEmpty());
        assertEquals(0, r.desaparecidos());
    }

    @Test void cambioDeCajaApuntaLaUbicacionTraducidaYGuardaConLaHoraDelCambio() {
        FilaItems antes = fila("350000000000005", "BT-9001", "C100", "InProcess", "CAJA GLASS");
        FilaItems ahora = fila("350000000000005", "BT-9001", "C100", "Available", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(antes), ahora);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals("Listos", guardado.ubicacion());
        assertEquals(AYER, guardado.vistoPrimero());
        assertEquals(HOY, guardado.cambiado());
        assertEquals(List.of(
                new EventoNsys("350000000000005", "BT-9001", HOY, false, TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000005", "BT-9001", HOY, false, TipoEvento.ESTADO, "InProcess", "Available", null)),
                r.eventos());
    }

    @Test void alPasarAPedidosRealizadosConservaLaUltimaUbicacion() {
        FilaItems antes = fila("350000000000006", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems ahora = fila("350000000000006", "BT-9001", "A", "Available", "Pedidos realizados");
        ResultadoComparacion r = normal(previo(antes), ahora);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.DE_PEDIDO, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Listos", guardado.ubicacionUltima());
        assertEquals(new EventoNsys("350000000000006", "BT-9001", HOY, false, TipoEvento.UBICACION, "Listos", null, "Pedidos realizados"),
                r.eventos().get(0));
    }

    @Test void cambioDeGradoYDeProducto() {
        FilaItems antes = fila("350000000000007", "BT-9001", "C100", "Available", "CAJA LISTOS");
        FilaItems ahora = new FilaItems("350000000000007", "BT-9001", "Apple iPhone 13 128GB Pink", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null);
        ResultadoComparacion r = normal(previo(antes), ahora);

        assertEquals(List.of(TipoEvento.GRADO, TipoEvento.PRODUCTO), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("C100", r.eventos().get(0).de());
        assertEquals("A", r.eventos().get(0).a());
        assertEquals(AZUL, r.eventos().get(1).de());
        assertNull(r.aGuardar().get(0).sku(), "el maestro de la prueba no tiene el rosa");
    }

    @Test void ventaVentaDeshechaYBajaPorReentrada() {
        FilaItems vivo = fila("350000000000008", "BT-9001", "A", "Available", "Pedidos realizados");
        FilaItems vendido = fila("350000000000008", "BT-9001", "A", "Released", "Pedidos realizados");
        assertEquals(TipoEvento.VENTA, normal(previo(vivo), vendido).eventos().get(0).tipo());
        assertEquals(TipoEvento.VENTA_DESHECHA, normal(previo(vendido), vivo).eventos().get(0).tipo());
        FilaItems quitado = fila("350000000000008", "BT-9001", "A", "Removed", "Pedidos realizados");
        EventoNsys baja = normal(previo(vendido), quitado).eventos().get(0);
        assertEquals(TipoEvento.ESTADO, baja.tipo());
        assertEquals("Removed", baja.a());
    }

    @Test void filaNuevaDeUnImeiConocidoApuntaEntradaYReentrada() {
        FilaItems viejo = fila("350000000000009", "BT-9001", "A", "Removed", "");
        FilaItems nuevo = fila("350000000000009", "BT-9002", "A", "InProcess", "Funcionalidad pendiente");
        ResultadoComparacion r = normal(previo(viejo), viejo, nuevo);

        assertEquals(1, r.aGuardar().size());
        assertEquals(List.of(TipoEvento.ENTRADA, TipoEvento.REENTRADA), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals(HOY, r.eventos().get(0).fecha());
        assertFalse(r.eventos().get(0).soloDia());
        assertEquals("BT-9002", r.eventos().get(1).a());
        assertEquals(Set.of("Funcionalidad pendiente"), r.cajasDesconocidas(), "la prueba no tiene esa caja");
    }

    @Test void historicoSoloApuntaCambiosYNoGuarda() {
        FilaItems antes = fila("350000000000010", "BT-9001", "C100", "InProcess", "CAJA GLASS");
        FilaItems ahora = fila("350000000000010", "BT-9001", "C100", "InProcess", "CAJA LISTOS");
        FilaItems nuevo = fila("350000000000011", "BT-9001", "A", "InProcess", "");
        ResultadoComparacion r = Comparador.comparar(previo(antes), List.of(ahora, nuevo), HOY, Comparador.Modo.HISTORICO, T);

        assertTrue(r.aGuardar().isEmpty());
        assertEquals(1, r.eventos().size());
        assertEquals(TipoEvento.UBICACION, r.eventos().get(0).tipo());
    }

    @Test void unaCajaDesconocidaSeAvisaYElMovilQuedaDeBaja() {
        FilaItems f = fila("350000000000012", "BT-9001", "A", "Available", "CAJA NUEVA");
        ResultadoComparacion r = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.NORMAL, T);
        assertEquals(Set.of("CAJA NUEVA"), r.cajasDesconocidas());
        assertEquals(EstadoMovil.BAJA, r.aGuardar().get(0).estado());
        assertNull(r.aGuardar().get(0).ubicacion());
    }

    @Test void losQueDesaparecenSeCuentanYLasFilasRepetidasCuentanUnaVez() {
        FilaItems a = fila("350000000000013", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems b = fila("350000000000014", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems aMovido = fila("350000000000013", "BT-9001", "A", "Available", "CAJA GLASS");
        ResultadoComparacion r = normal(previo(a, b), aMovido, a);
        assertEquals(1, r.desaparecidos());
        assertEquals(1, r.eventos().size(), "la segunda fila del mismo móvil se ignora");
        assertEquals("Taller · Glass", r.aGuardar().get(0).ubicacion());
    }

    @Test void sinProductoEnElMaestroElEsimSaleDelNombre() {
        FilaItems f = new FilaItems("350000000000015", "BT-9001", "Apple iPhone 15 128GB eSIM Black", "A", "Available",
                "CAJA LISTOS", "Proveedor A", null, null);
        MovilNsys m = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.NORMAL, T).aGuardar().get(0);
        assertNull(m.sku());
        assertNull(m.modelo());
        assertTrue(m.esEsim());
    }

    @Test void primeraCargaSinFechaDeEntradaUsaLaDeLaExportacion() {
        FilaItems f = new FilaItems("350000000000016", "BT-9001", AZUL, "A", "Available", "", "Proveedor A", null, null);
        EventoNsys entrada = Comparador.comparar(Map.of(), List.of(f), HOY, Comparador.Modo.PRIMERA_CARGA, T).eventos().get(0);
        assertEquals(HOY, entrada.fecha());
        assertFalse(entrada.soloDia());
    }
}
