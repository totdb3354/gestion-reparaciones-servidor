package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.ProductoMaestro;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La pasada de reservas (spec reserva de IMEI §5) con el DAO y NSYS simulados. Datos inventados: todo es un iPhone 13 128GB Azul A
 *  de Distribuidores (SKU «i13128AzulA»). */
class ReservasTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final String SKU = "i13128AzulA";
    private static final String LISTOS = "CAJA LISTOS";
    private static final String REVISAR = "Pendiente revisar si hay error de inventario";
    private static final String MV_REVISAR = "MV Pendiente revisar si hay error de inventario";
    private static final String IMEI_1 = "350000000000001";
    private static final String IMEI_2 = "350000000000002";
    private static final String IMEI_3 = "350000000000003";

    private final FilasDAO dao = mock(FilasDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final Reservas reservas = new Reservas(dao, nsys, taller);

    @BeforeEach void maestroYTaller() {
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(new ProductoMaestro("clave-azul-a", "13", "13", "128GB", "Azul",
                "A", SKU, null, "i13128Azul", false)));
        Function<MovilNsys, LoQueVale.Valor> vale = m -> new LoQueVale.Valor(m.color(), m.grade(), m.sku(), null, null, null, false);
        when(taller.valedorDeTodos()).thenReturn(vale);
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(dao.reservar(anyInt(), anyString(), anyString(), any())).thenReturn(true);
    }

    private static FilaPedido fila(int id, String imei, String marca, LocalDateTime cerradaEn, LocalDateTime envioFecha, String reservado,
                                   LocalDateTime sinStockEn) {
        return new FilaPedido(id, HOY, "Distribuidor", SKU, imei, "W-" + id, HOY, "SHIPPYPRO", Canal.DISTRIBUIDORES, null, cerradaEn,
                envioFecha, null, null, null, null, null, null, null, marca, null, null, null, null, null, null, null, null, null, "ES",
                reservado, reservado == null ? null : AHORA.minusHours(1), sinStockEn);
    }

    private static FilaPedido esperando(int id) {
        return fila(id, null, null, null, null, null, null);
    }

    private static FilaPedido reservada(int id, String reservado) {
        return fila(id, null, null, null, null, reservado, null);
    }

    private static MovilNsys movil(String imei, String location, EstadoMovil estado) {
        return new MovilNsys(imei, "BT-1", "Apple iPhone 13 128GB", "A", "Available", location, "Proveedor", LocalDate.of(2026, 9, 1),
                null, SKU, "13", "13", "128GB", "Azul", "A", false, null, null, estado, null, null);
    }

    private static MovilNsys enStock(String imei) {
        return movil(imei, LISTOS, EstadoMovil.STOCK);
    }

    private void filas(FilaPedido... f) {
        when(dao.conReservaOEsperando()).thenReturn(List.of(f));
    }

    private void stock(MovilNsys... m) {
        when(nsys.enEmpresa()).thenReturn(List.of(m));
    }

    /** Lo que da {@code porImeis} para los reservados. */
    private void reservados(MovilNsys... m) {
        when(nsys.porImeis(any())).thenReturn(List.of(m));
    }

    // ---------------------------------------------------------------- 3. reservar

    @Test void reservaLaFilaQueEsperaConElTelefonoDelRepartoYSuPorque() {
        filas(esperando(1));
        stock(enStock(IMEI_1));
        Reservas.Resumen r = reservas.pasada(AHORA);
        verify(dao).reservar(1, IMEI_1, "Puesto 1 · Caja · Listos · A", AHORA);
        verify(dao, never()).apuntarSinStock(anyInt(), any());
        assertEquals(new Reservas.Resumen(1, 0, 0), r);
    }

    @Test void sinTelefonoApuntaSinStockSoloSiNoEstabaApuntado() {
        filas(esperando(1), fila(2, null, null, null, null, null, AHORA.minusDays(1)));
        stock();
        Reservas.Resumen r = reservas.pasada(AHORA);
        verify(dao).apuntarSinStock(1, AHORA);
        verify(dao, never()).apuntarSinStock(eq(2), any());
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
        assertEquals(new Reservas.Resumen(0, 0, 2), r);
    }

    @Test void unaReservaQueElDaoNoHaceNoCuenta() {
        filas(esperando(1));
        stock(enStock(IMEI_1));
        when(dao.reservar(anyInt(), anyString(), anyString(), any())).thenReturn(false);
        assertEquals(new Reservas.Resumen(0, 0, 0), reservas.pasada(AHORA));
    }

    @Test void elMaestroSeCargaConLosSkuDeLasFilasQueEsperan() {
        filas(esperando(1));
        stock();
        reservas.pasada(AHORA);
        verify(nsys).productosPorSkuDeCanal(AceptaSku.conVariantes(java.util.Set.of(SKU)));
    }

    @Test void noReservanNiApuntanLasFilasSinCanalOConSkuQueElMaestroNoConoce() {
        FilaPedido sinCanal = new FilaPedido(1, HOY, "?", SKU, null, "W-1", HOY, "SHIPPYPRO", null, null, null);
        FilaPedido desconocido = new FilaPedido(2, HOY, "Distribuidor", "i99Rosa", null, "W-2", HOY, "SHIPPYPRO", Canal.DISTRIBUIDORES,
                null, null);
        filas(sinCanal, desconocido);
        stock(enStock(IMEI_1));
        assertEquals(new Reservas.Resumen(0, 0, 0), reservas.pasada(AHORA));
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
        verify(dao, never()).apuntarSinStock(anyInt(), any());
    }

    @Test void unaFilaConMarcaNoReserva() {
        filas(fila(1, null, "ESPERA_STOCK", null, null, null, null));
        stock(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
        verify(dao, never()).apuntarSinStock(anyInt(), any());
    }

    @Test void sinFilasQueEsperanNoSeLeeElStock() {
        filas();
        assertEquals(new Reservas.Resumen(0, 0, 0), reservas.pasada(AHORA));
        verify(nsys, never()).enEmpresa();
        verify(nsys, never()).porImeis(any());
    }

    @Test void unTelefonoReservadoEnOtraFilaNoSeReservaOtraVez() {
        filas(reservada(1, IMEI_1), esperando(2));
        stock(enStock(IMEI_1));
        reservados(enStock(IMEI_1));
        Reservas.Resumen r = reservas.pasada(AHORA);
        verify(dao, never()).soltarReserva(anyInt(), any(), any(), any());
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
        verify(dao).apuntarSinStock(2, AHORA);
        assertEquals(new Reservas.Resumen(0, 0, 1), r);
    }

    @Test void unTelefonoPuestoEnOtraFilaNoSeReserva() {
        // Una fila viva con el IMEI puesto: «ya puesto», con el criterio de VistaFilas (60 dias).
        when(dao.shippyProConImeiDesde(HOY.minusDays(60))).thenReturn(List.of(fila(7, IMEI_1, null, null, null, null, null)));
        filas(esperando(2));
        stock(enStock(IMEI_1), enStock(IMEI_2));
        reservas.pasada(AHORA);
        verify(dao).reservar(eq(2), eq(IMEI_2), anyString(), eq(AHORA));
    }

    @Test void unaEnviadaCuyoTelefonoYaEstaVendidoNoLoOcupa() {
        // Como en VistaFilas: la enviada solo ocupa su movil mientras NSYS no lo da por vendido.
        FilaPedido enviada = fila(7, IMEI_1, null, AHORA.minusDays(1), AHORA.minusDays(1), null, null);
        when(dao.shippyProConImeiDesde(any())).thenReturn(List.of(enviada));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI_1, LISTOS, EstadoMovil.VENDIDO)));
        filas(esperando(2));
        stock(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao).reservar(eq(2), eq(IMEI_1), anyString(), eq(AHORA));
    }

    @Test void unTelefonoConUnImeiQueNoCabeEnLaReservaNoSeReparte() {
        filas(esperando(1));
        stock(enStock("A".repeat(21)), enStock(IMEI_2));
        reservas.pasada(AHORA);
        verify(dao).reservar(eq(1), eq(IMEI_2), anyString(), eq(AHORA));
    }

    // ---------------------------------------------------------------- 1. resolver las puestas

    @Test void unaFilaConImeiYReservaSeResuelveSinUsuarioYNoSeSuelta() {
        filas(fila(1, IMEI_2, null, null, null, IMEI_1, null));
        Reservas.Resumen r = reservas.pasada(AHORA);
        verify(dao).resolverReservaPuesta(1, null, AHORA);
        verify(dao, never()).soltarReserva(anyInt(), any(), any(), any());
        assertEquals(new Reservas.Resumen(0, 0, 0), r);
    }

    @Test void unaFilaYaCerradaConImeiYReservaTambienSeResuelve() {
        filas(fila(1, IMEI_1, null, AHORA.minusHours(2), AHORA.minusHours(2), IMEI_1, null));
        reservas.pasada(AHORA);
        verify(dao).resolverReservaPuesta(1, null, AHORA);
        verify(dao, never()).soltarReserva(anyInt(), any(), any(), any());
    }

    // ---------------------------------------------------------------- 2. soltar

    @Test void cerradaSinEnvioSeSueltaComoYaNoEsta() {
        filas(fila(1, null, null, AHORA.minusHours(1), null, IMEI_1, null));
        reservados(enStock(IMEI_1));
        assertEquals(new Reservas.Resumen(0, 1, 0), reservas.pasada(AHORA));
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.YA_NO_ESTA), eq(AHORA));
    }

    @Test void conOtraMarcaSeSueltaComoMarcada() {
        filas(fila(1, null, "PENDIENTE_COLOR", null, null, IMEI_1, null));
        reservados(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.MARCADA), eq(AHORA));
    }

    @Test void canceladaYCerradaSigueSiendoCancelada() {
        // Como la 0.9.47: «Cancelar móvil» manda sobre «ya no está».
        filas(fila(1, null, "CANCELADO", AHORA.minusHours(1), null, IMEI_1, null));
        reservados(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.CANCELADA), eq(AHORA));
    }

    @Test void conMarcaYEtiquetaImpresaSeSueltaComoConEtiqueta() {
        for (String marca : List.of("CANCELADO", "EN_REVISION")) {
            org.mockito.Mockito.clearInvocations(dao);
            FilaPedido f = fila(1, null, marca, null, null, IMEI_1, null);
            filas(new FilaPedido(f.id(), f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), f.origen(), f.canal(),
                    f.posicion(), f.cerradaEn(), f.envioFecha(), f.transportista(), f.seguimiento(), f.reabiertaEn(), f.envioId(),
                    AHORA.minusHours(1), f.faltaEnviarEn(), f.sincronizadaEn(), f.marca(), f.marcaNota(), f.marcaEn(), f.marcaPor(),
                    f.asociadaEn(), f.asociadaOrigen(), f.validadaImei(), f.validadaEn(), f.validadaPor(), f.fechaSalida(), f.pais(),
                    f.imeiReservado(), f.reservadaEn(), f.sinStockEn()));
            reservados(enStock(IMEI_1));
            reservas.pasada(AHORA);
            verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.CON_ETIQUETA), eq(AHORA));
        }
    }

    @Test void cerradaConEnvioYSinImeiSeSueltaComoEnviadaSinImei() {
        // Logistica seguramente lo envio sin teclearlo: las devoluciones no deben devolverlo a stock.
        filas(fila(1, null, null, AHORA.minusHours(1), AHORA.minusHours(1), IMEI_1, null));
        reservados(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(1, IMEI_1, Reservas.ENVIADA_SIN_IMEI, AHORA);
        assertEquals("ENVIADA_SIN_IMEI", Reservas.ENVIADA_SIN_IMEI);
    }

    @Test void enLasCajasDeRevisarSeSueltaComoNoEncontrado() {
        filas(reservada(1, IMEI_1), reservada(2, IMEI_2));
        reservados(movil(IMEI_1, REVISAR, EstadoMovil.NO_CUENTA), movil(IMEI_2, " " + MV_REVISAR + " ", EstadoMovil.NO_CUENTA));
        stock();
        assertEquals(2, reservas.pasada(AHORA).soltadas());
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.NO_ENCONTRADO), eq(AHORA));
        verify(dao).soltarReserva(eq(2), anyString(), eq(Reservas.NO_ENCONTRADO), eq(AHORA));
    }

    @Test void fueraDeLaEmpresaVendidoDeBajaOEnOtraCajaSeSueltaComoNoDisponible() {
        filas(reservada(1, IMEI_1), reservada(2, IMEI_2), reservada(3, IMEI_3), reservada(4, "350000000000004"));
        reservados(movil(IMEI_2, LISTOS, EstadoMovil.VENDIDO), movil(IMEI_3, LISTOS, EstadoMovil.BAJA),
                movil("350000000000004", "CF", EstadoMovil.NO_CUENTA)); // IMEI_1 ya no esta en el espejo
        stock();
        reservas.pasada(AHORA);
        for (int id = 1; id <= 4; id++) verify(dao).soltarReserva(eq(id), anyString(), eq(Reservas.NO_DISPONIBLE), eq(AHORA));
    }

    @Test void enStockConUnGradoQueNoSeVendeSeSueltaComoNoDisponible() {
        filas(reservada(1, IMEI_1));
        MovilNsys conRma = new MovilNsys(IMEI_1, "BT-1", "Apple iPhone 13 128GB", "RMA", "Available", LISTOS, "Proveedor", null, null,
                SKU, "13", "13", "128GB", "Azul", "RMA", false, null, null, EstadoMovil.STOCK, null, null);
        reservados(conRma);
        stock();
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.NO_DISPONIBLE), eq(AHORA));
    }

    @Test void puestoEnOtraFilaSeSueltaComoPuestoEnOtra() {
        when(dao.shippyProConImeiDesde(HOY.minusDays(60))).thenReturn(List.of(fila(7, IMEI_1, null, null, null, null, null)));
        filas(reservada(1, IMEI_1));
        reservados(enStock(IMEI_1));
        stock();
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.PUESTO_EN_OTRA), eq(AHORA));
    }

    @Test void enStockOEnPedidosRealizadosNoSeSuelta() {
        filas(reservada(1, IMEI_1), reservada(2, IMEI_2));
        reservados(enStock(IMEI_1), movil(IMEI_2, "Pedidos realizados", EstadoMovil.DE_PEDIDO));
        stock(enStock(IMEI_1));
        assertEquals(new Reservas.Resumen(0, 0, 0), reservas.pasada(AHORA));
        verify(dao, never()).soltarReserva(anyInt(), any(), any(), any());
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
    }

    @Test void deCadaImeiCuentaElLoteMasReciente() {
        // porImeis da el lote mas reciente primero: el viejo (vendido) no suelta la reserva.
        filas(reservada(1, IMEI_1));
        reservados(enStock(IMEI_1), movil(IMEI_1, LISTOS, EstadoMovil.VENDIDO));
        stock();
        reservas.pasada(AHORA);
        verify(dao, never()).soltarReserva(anyInt(), any(), any(), any());
    }

    @Test void laFilaSoltadaQueSigueEsperandoReservaOtroTelefonoEnLaMismaPasada() {
        filas(reservada(1, IMEI_1));
        reservados(movil(IMEI_1, REVISAR, EstadoMovil.NO_CUENTA));
        stock(enStock(IMEI_2));
        assertEquals(new Reservas.Resumen(1, 1, 0), reservas.pasada(AHORA));
        InOrder orden = inOrder(dao);
        orden.verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.NO_ENCONTRADO), eq(AHORA));
        orden.verify(dao).reservar(eq(1), eq(IMEI_2), anyString(), eq(AHORA));
    }

    @Test void laCanceladaSoltadaNoVuelveAReservarYSuTelefonoQuedaLibre() {
        filas(fila(1, null, "CANCELADO", null, null, IMEI_1, null), esperando(2));
        reservados(enStock(IMEI_1));
        stock(enStock(IMEI_1));
        assertEquals(new Reservas.Resumen(1, 1, 0), reservas.pasada(AHORA));
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.CANCELADA), eq(AHORA));
        verify(dao).reservar(eq(2), eq(IMEI_1), anyString(), eq(AHORA));
        verify(dao, never()).reservar(eq(1), anyString(), anyString(), any());
    }

    @Test void elEnviadoSinImeiSoltadoNoSeReservaAOtraFilaEnLaMismaPasada() {
        filas(fila(1, null, null, AHORA.minusHours(2), AHORA.minusHours(3), IMEI_1, null), esperando(2));
        reservados(enStock(IMEI_1));
        stock(enStock(IMEI_1));
        assertEquals(new Reservas.Resumen(0, 1, 1), reservas.pasada(AHORA));
        verify(dao).soltarReserva(eq(1), anyString(), eq(Reservas.ENVIADA_SIN_IMEI), eq(AHORA));
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
    }

    @Test void elOrdenEsResolverSoltarYReservar() {
        filas(fila(1, IMEI_3, null, null, null, IMEI_3, null), reservada(2, IMEI_1), esperando(3));
        reservados(movil(IMEI_1, LISTOS, EstadoMovil.VENDIDO));
        stock(enStock(IMEI_2));
        reservas.pasada(AHORA);
        InOrder orden = inOrder(dao);
        orden.verify(dao).resolverReservaPuesta(1, null, AHORA);
        orden.verify(dao).soltarReserva(eq(2), anyString(), eq(Reservas.NO_DISPONIBLE), eq(AHORA));
        orden.verify(dao).reservar(anyInt(), eq(IMEI_2), anyString(), eq(AHORA));
    }

    @Test void losReservadosSePidenANsysUnaSolaVez() {
        filas(reservada(1, IMEI_1), reservada(2, IMEI_2));
        reservados(enStock(IMEI_1), enStock(IMEI_2));
        stock();
        reservas.pasada(AHORA);
        verify(nsys).porImeis(argThat(c -> new ArrayList<>(c).containsAll(List.of(IMEI_1, IMEI_2)) && c.size() == 2));
    }

    @Test void alSoltarSePasaElImeiReservadoQueSeVio() {
        filas(fila(1, null, "CANCELADO", null, null, IMEI_2, null));
        reservados(enStock(IMEI_2));
        reservas.pasada(AHORA);
        verify(dao).soltarReserva(1, IMEI_2, Reservas.CANCELADA, AHORA);
    }

    @Test void unImeiConfirmadoEnElPaso1NoSeOfreceAOtraFilaEnLaMismaPasada() {
        // La lectura de «ya puestos» no lo trae (p. ej. leida antes): aun asi el IMEI de la fila resuelta queda ocupado.
        filas(fila(1, IMEI_1, null, null, null, IMEI_1, null), esperando(2));
        stock(enStock(IMEI_1));
        reservas.pasada(AHORA);
        verify(dao).resolverReservaPuesta(1, null, AHORA);
        verify(dao, never()).reservar(anyInt(), anyString(), anyString(), any());
        verify(dao).apuntarSinStock(2, AHORA);
    }
}
