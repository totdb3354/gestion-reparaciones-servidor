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

    @Test void cuentaLasFilasQueRepitenImeiYLote() {
        assertEquals(0, Comparador.repetidas(List.of(fila("350000000000001", "BT-9001", "A", "Available", "CAJA LISTOS"),
                fila("350000000000001", "BT-9002", "A", "Available", "CAJA LISTOS"))), "otro lote no es repetida");
        assertEquals(2, Comparador.repetidas(List.of(fila("350000000000001", "BT-9001", "A", "Available", "CAJA LISTOS"),
                fila("350000000000001", "BT-9001", "B", "Available", "CAJA GLASS"),
                fila("350000000000001", "BT-9001", "C", "Available", "CAJA GLASS"))));
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

    // 0.9.18.2: NSYS vacía la caja al cerrar un móvil (Released, Removed…). Con un Status que no es vivo, la caja vacía
    // no es «En revisión» ni un movimiento.

    @Test void ventaConLaCajaVaciadaSoloApuntaLaVentaYGuardaLaUltimaCaja() {
        FilaItems antes = fila("350000000000017", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems vendido = fila("350000000000017", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = normal(previo(antes), vendido);

        assertEquals(List.of(new EventoNsys("350000000000017", "BT-9001", HOY, false, TipoEvento.VENTA, "Available",
                "Released", null)), r.eventos());
        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.VENDIDO, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Listos", guardado.ubicacionUltima());
    }

    @Test void bajaConLaCajaVaciadaSoloApuntaElEstado() {
        FilaItems antes = fila("350000000000018", "BT-9001", "A", "InProcess", "CAJA GLASS");
        FilaItems quitado = fila("350000000000018", "BT-9001", "A", "Removed", "");
        ResultadoComparacion r = normal(previo(antes), quitado);

        assertEquals(List.of(TipoEvento.ESTADO), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("Removed", r.eventos().get(0).a());
        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.BAJA, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Taller · Glass", guardado.ubicacionUltima());
    }

    /** La misma fila guardada, con otra ubicación y otra última (como la dejan la 0.9.18 o la limpieza). */
    private static MovilNsys conUbicaciones(MovilNsys m, String ubicacion, String ultima) {
        return new MovilNsys(m.imeiSn(), m.batch(), m.goods(), m.grade(), m.status(), m.location(), m.supplier(),
                m.receivingDate(), m.soldDate(), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                m.gradoDetalle(), m.esEsim(), ubicacion, ultima, m.estado(), m.vistoPrimero(), m.cambiado());
    }

    @Test void unVendidoSinCajaComoLoDejaLaLimpiezaNoSeVuelveAGuardar() {
        FilaItems vendido = fila("350000000000019", "BT-9001", "A", "Released", "");
        MovilNsys limpio = conUbicaciones(previo(vendido).get(vendido.clave()), null, "Listos");
        ResultadoComparacion r = normal(new HashMap<>(Map.of(limpio.clave(), limpio)), vendido);

        assertTrue(r.aGuardar().isEmpty(), "sin ubicación y con su última caja: el mismo contenido");
        assertTrue(r.eventos().isEmpty());
    }

    @Test void ventaDeshechaAUnaCajaSaleSinDesde() {
        FilaItems vendido = fila("350000000000020", "BT-9001", "A", "Released", "");
        FilaItems vuelta = fila("350000000000020", "BT-9001", "A", "InProcess", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(vendido), vuelta);

        assertEquals(List.of(
                new EventoNsys("350000000000020", "BT-9001", HOY, false, TipoEvento.UBICACION, null, "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000020", "BT-9001", HOY, false, TipoEvento.VENTA_DESHECHA, "Released", "InProcess", null)),
                r.eventos());
    }

    @Test void ventaDeshechaAPedidosRealizadosConservaLaCajaDeAntesDeLaVenta() {
        FilaItems listo = fila("350000000000021", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems vendido = fila("350000000000021", "BT-9001", "A", "Released", "");
        FilaItems dePedido = fila("350000000000021", "BT-9001", "A", "InProcess", "Pedidos realizados");
        MovilNsys trasLaVenta = normal(previo(listo), vendido).aGuardar().get(0);
        ResultadoComparacion r = normal(new HashMap<>(Map.of(trasLaVenta.clave(), trasLaVenta)), dePedido);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.DE_PEDIDO, guardado.estado());
        assertEquals("Listos", guardado.ubicacionUltima());
        assertEquals(TipoEvento.UBICACION, r.eventos().get(0).tipo());
        assertNull(r.eventos().get(0).de(), "de vendido no sale de ninguna caja");
    }

    @Test void ventaDeshechaSinCajaQuedaEnRevisionSinEventoDeUbicacion() {
        FilaItems vendido = fila("350000000000022", "BT-9001", "A", "Released", "");
        FilaItems vuelta = fila("350000000000022", "BT-9001", "A", "InProcess", "");
        ResultadoComparacion r = normal(previo(vendido), vuelta);

        assertEquals(List.of(TipoEvento.VENTA_DESHECHA), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("En revisión", r.aGuardar().get(0).ubicacion());
    }

    @Test void filaNuevaYaVendidaSinCajaEntraSinUbicacion() {
        FilaItems otro = fila("350000000000023", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems nuevo = fila("350000000000024", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = normal(previo(otro), otro, nuevo);

        assertEquals(new EventoNsys("350000000000024", "BT-9001", HOY, false, TipoEvento.ENTRADA, null, null, ""),
                r.eventos().get(0));
        assertNull(r.aGuardar().get(0).ubicacion());
    }

    @Test void unMovilVivoQueSeQuedaSinCajaSigueYendoAEnRevision() {
        FilaItems antes = fila("350000000000025", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems ahora = fila("350000000000025", "BT-9001", "A", "Available", "");
        ResultadoComparacion r = normal(previo(antes), ahora);

        assertEquals(new EventoNsys("350000000000025", "BT-9001", HOY, false, TipoEvento.UBICACION, "Listos",
                "En revisión", ""), r.eventos().get(0));
        assertEquals("En revisión", r.aGuardar().get(0).ubicacion());
    }

    @Test void unaBajaQuePasaADesguaceSigueApuntandoLaCaja() {
        FilaItems quitado = fila("350000000000026", "BT-9001", "A", "Removed", "");
        FilaItems desguace = fila("350000000000026", "BT-9001", "A", "Removed", "Desguace");
        ResultadoComparacion r = normal(previo(quitado), desguace);

        assertEquals(List.of(new EventoNsys("350000000000026", "BT-9001", HOY, false, TipoEvento.UBICACION, null, null,
                "Desguace")), r.eventos());
    }

    @Test void elHistoricoTampocoApuntaLaCajaVaciadaDeUnaVenta() {
        FilaItems antes = fila("350000000000027", "BT-9001", "A", "Available", "Pedidos realizados");
        FilaItems vendido = fila("350000000000027", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = Comparador.comparar(previo(antes), List.of(vendido), HOY, Comparador.Modo.HISTORICO, T);

        assertEquals(List.of(TipoEvento.VENTA), r.eventos().stream().map(EventoNsys::tipo).toList());
    }

    @Test void unVendidoQueConservabaLaCajaYLaVaciaDespuesNoApuntaNada() {
        FilaItems conCaja = fila("350000000000028", "BT-9001", "A", "Released", "CAJA LISTOS");
        FilaItems vaciado = fila("350000000000028", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = normal(previo(conCaja), vaciado);

        assertTrue(r.eventos().isEmpty());
        MovilNsys guardado = r.aGuardar().get(0);
        assertNull(guardado.ubicacion());
        assertEquals("Listos", guardado.ubicacionUltima());
    }

    @Test void sinStatusYSinCajaNoEstaEnRevisionYDeVendidoASinStatusNoRompe() {
        FilaItems vendido = fila("350000000000029", "BT-9001", "A", "Released", "");
        FilaItems sinStatus = fila("350000000000029", "BT-9001", "A", null, "");
        ResultadoComparacion r = normal(previo(vendido), sinStatus);

        assertEquals(List.of(new EventoNsys("350000000000029", "BT-9001", HOY, false, TipoEvento.ESTADO, "Released",
                null, null)), r.eventos());
        assertEquals(EstadoMovil.BAJA, r.aGuardar().get(0).estado());
        assertNull(r.aGuardar().get(0).ubicacion());
    }

    @Test void unaFilaGuardadaConElFalloNoDaDesdeEnRevision() {
        // Así quedaron los vendidos y las bajas sin caja con la 0.9.17 y la 0.9.18, hasta la limpieza: la primera
        // importación con el código nuevo los lee así.
        FilaItems quitado = fila("350000000000030", "BT-9001", "A", "Removed", "");
        MovilNsys contaminado = conUbicaciones(previo(quitado).get(quitado.clave()), "En revisión", "En revisión");
        FilaItems desguace = fila("350000000000030", "BT-9001", "A", "Removed", "Desguace");
        ResultadoComparacion r = normal(new HashMap<>(Map.of(contaminado.clave(), contaminado)), desguace);

        assertEquals(TipoEvento.UBICACION, r.eventos().get(0).tipo());
        assertNull(r.eventos().get(0).de());
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
