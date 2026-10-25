package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El SKU del taller con los DAO mockeados y la hora fija (21/10/2026 10:30 en Madrid). */
class TallerNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 30);
    private static final String IMEI = "350000000000001";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO taller = mock(TallerDAO.class);
    private final LogDAO registro = mock(LogDAO.class);
    private final TallerNsys servicio = new TallerNsys(nsys, taller, registro,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("Blue", "Azul", "A100"),
                producto("(PRODUCT)RED", "Rojo", "B"), producto("(PRODUCT)RED", "Rojo", "A100")));
    }

    private static HttpStatusCode estado(Executable accion) {
        return assertThrows(ResponseStatusException.class, accion).getStatusCode();
    }

    // ---------------------------------------------------------------- lo que vale (spec §2.1)

    @Test void elValedorAplicaLoDelTallerSoloASuImeiYLeeElMaestroUnaVez() {
        String otro = "350000000000002";
        when(taller.vigentes(List.of(IMEI, otro))).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B")));
        Function<MovilNsys, LoQueVale.Valor> vale = servicio.valedor(List.of(IMEI, otro));
        assertEquals("A100", vale.apply(movil(IMEI, "Azul", "B")).grade());
        assertEquals("i13128AzulA100", vale.apply(movil(IMEI, "Azul", "B")).sku());
        assertEquals(new LoQueVale.Valor("Azul", "B", "i13128AzulB", null, null, null, false),
                vale.apply(movil(otro, "Azul", "B")));
        verify(nsys, times(1)).cargarProductos();
    }

    @Test void sinNadaDelTallerNoSeLeeElMaestro() {
        servicio.valedorDeTodos().apply(movil(IMEI, "Azul", "B"));
        verify(nsys, never()).cargarProductos();
    }

    @Test void unNumeroDeSerieEnMinusculasCruza() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, "f2lxabc123", GRADO, "A100", "B")));
        assertEquals("A100", servicio.valedorDeTodos().apply(movil("F2LXABC123", "Azul", "B")).grade());
    }
    @Test void elColorQueValeDeTodosEsElDelValedorYNoLeeElMaestro() {
        String otro = "350000000000002";
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A100", "B"),
                vigente(3, "f2lxabc123", COLOR, "Verde", "Azul")));
        Function<MovilNsys, String> color = servicio.colorDeTodos();
        assertEquals("Rojo", color.apply(movil(IMEI, "Azul", "B")));
        assertEquals("Azul", color.apply(movil(otro, "Azul", "B")), "sin nada del taller, el de NSYS");
        assertEquals("Verde", color.apply(movil("F2LXABC123", "Azul", "B")), "el número de serie cruza sin mirar mayúsculas");
        verify(nsys, never()).cargarProductos();
        verify(taller, times(1)).vigentes();
        assertEquals(servicio.valedorDeTodos().apply(movil(IMEI, "Azul", "B")).color(), color.apply(movil(IMEI, "Azul", "B")));
    }

    @Test void elProductoAPonerEsElDeAhoraSiSoloCambiaElGradoYElMasUsadoSiCambiaElColor() {
        MovilNsys m = movil(IMEI, "Azul", "B");
        LoQueVale.Valor grado = new LoQueVale.Valor("Azul", "A100", "i13128AzulA100", null, "B", "i13128AzulB", true);
        assertEquals("Apple iPhone 13 128GB Blue", servicio.productoAPoner(m, grado));
        verify(nsys, never()).goodsMasUsados(any());
        when(nsys.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        LoQueVale.Valor rojo = new LoQueVale.Valor("Rojo", "B", "i13128RojoB", "Azul", null, "i13128AzulB", true);
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", servicio.productoAPoner(m, rojo));
        LoQueVale.Valor verde = new LoQueVale.Valor("Verde", "B", null, "Azul", null, "i13128AzulB", true);
        assertNull(servicio.productoAPoner(m, verde), "ningún móvil de ese color: la web dice «búscalo a mano»");
    }

    // ---------------------------------------------------------------- corrección a mano (spec §2.3)

    @Test void corregirElGradoLoDejaVigenteYLoApunta() {
        servicio.corregir(IMEI, null, "A100", "  batería cambiada ", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), 7, "batería cambiada", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: B → A100", "batería cambiada");
    }

    @Test void corregirColorYGradoApuntaUnaLineaPorDato() {
        servicio.corregir(IMEI, "Rojo", "A100", "chasis rojo nuevo", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), 7, "chasis rojo nuevo", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, COLOR: Azul → Rojo", "chasis rojo nuevo");
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: B → A100", "chasis rojo nuevo");
    }

    @Test void unaCorreccionSobreOtraDelTallerParteDeLoQueVale() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, GRADO, "A100", "B")));
        servicio.corregir(IMEI, null, "A", "era A sin batería", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), 7, "era A sin batería", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A100 → A", "era A sin batería");
    }

    @Test void elegirLoQueDiceNsysQuitaLoDelTaller() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, GRADO, "A100", "B")));
        servicio.corregir(IMEI, null, "B", "me equivoqué", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "B", "B", QUITADO)), 7, "me equivoqué", AHORA);
        verify(registro).insertar(7, "CORREGIR_MOVIL_TALLER",
                "IMEI: 350000000000001, GRADO: A100 → B (vuelve a lo de NSYS)", "me equivoqué");
    }

    @Test void sinCambiosEs400YNoGuardaNada() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, "Azul", "B", "nada", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, null, "nada", 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(registro);
    }

    @Test void elMotivoEsObligatorioYNoMuyLargo() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", "   ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, "A100", "x".repeat(501), 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
    }

    @Test void elGradoSeEscribeComoEnNsys() {
        for (String malo : List.of("A+", "Z", "100", "a100", "MsMsA"))
            assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, null, malo, "x", 7)), malo);
        servicio.corregir(IMEI, null, "MsA-100", "mensaje de pieza", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "MsA-100", "B", VIGENTE)), 7, "mensaje de pieza", AHORA);
    }

    @Test void unColorQueNoEstaEnElMaestroParaEseModeloEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.corregir(IMEI, "Verde", null, "x", 7)));
    }

    @Test void unImeiQueNoEstaEnElEspejoEs404() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.corregir("350000000000099", null, "A100", "x", 7)));
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.volver("350000000000099", null, 7)));
    }

    @Test void volverQuitaLosDosDatosConElMotivoSoloSiLoHay() {
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(4, IMEI, COLOR, "Rojo", "Azul"),
                vigente(5, IMEI, GRADO, "A100", "B")));
        servicio.volver(IMEI, "  ", 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Azul", "Azul", QUITADO),
                new TallerDAO.Nuevo(GRADO, "B", "B", QUITADO)), 8, null, AHORA);
        verify(registro).insertar(8, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, COLOR: Rojo → Azul (vuelve a lo de NSYS)", null);
        verify(registro).insertar(8, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A100 → B (vuelve a lo de NSYS)", null);
    }

    @Test void volverSinNadaDelTallerEs400() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.volver(IMEI, "x", 7)));
        verify(taller, never()).guardar(any(), any(), anyInt(), any(), any());
    }

    // ---------------------------------------------------------------- cierre tras la importación (spec §2.2)

    @Test void elCierrePasaLoQueNsysYaTieneYCedeLoQueNsysCambioDespues() {
        String otro = "350000000000002";
        String fuera = "350000000000003";
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B"), vigente(2, IMEI, COLOR, "Rojo", "Azul"),
                vigente(3, otro, GRADO, "C", "B"), vigente(4, fuera, GRADO, "A", "B")));
        // Del IMEI, la fila más reciente primero (la de A100): vale esa.
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "A100"), movil(IMEI, "Azul", "B"),
                movil(otro, "Azul", "B-")));
        when(taller.cerrar(anyInt(), any(), any(), any())).thenReturn(1);
        assertEquals(2, servicio.cerrar());
        verify(taller).cerrar(1, PASADO, "A100", AHORA);
        verify(taller).cerrar(3, MANDA_NSYS, "B-", AHORA);
        verify(taller, times(2)).cerrar(anyInt(), any(), any(), any());
    }

    @Test void sinNadaVigenteNoSeLeeElEspejo() {
        assertEquals(0, servicio.cerrar());
        verify(nsys, never()).porImeis(any());
    }

    // ---------------------------------------------------------------- «Por pasar a NSYS» (spec §2.4)

    @Test void laListaConLoQueHayQuePonerEnNsysLosMasAntiguosPrimero() {
        String otro = "350000000000002";
        MovilNsys uno = movil(IMEI, "Azul", "B");
        MovilNsys dos = movil(otro, "Azul", "B");
        when(taller.vigentes()).thenReturn(List.of(vigente(1, otro, COLOR, "Rojo", "Azul", AHORA.minusDays(3)),
                vigente(2, IMEI, GRADO, "A100", "B", AHORA.minusDays(2)), vigente(3, otro, GRADO, "A100", "B", AHORA.minusDays(1))));
        when(nsys.porImeis(any())).thenReturn(List.of(uno, dos));
        when(nsys.goodsMasUsados(dos)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        List<FilaPorPasar> lista = servicio.porPasar();
        assertEquals(List.of(otro, IMEI), lista.stream().map(FilaPorPasar::imeiSn).toList(), "los más antiguos primero");
        assertEquals(new FilaPorPasar(otro, "13", "13", "128GB", "Apple iPhone 13 128GB Blue", "B",
                "Apple iPhone 13 128GB (PRODUCT)RED", "A100", "i13128RojoA100",
                List.of(new PorQueTaller("COLOR", "MANO", "Técnico 1", "prueba", null),
                        new PorQueTaller("GRADO", "MANO", "Técnico 1", "prueba", null)),
                AHORA.minusDays(3)), lista.get(0));
        assertEquals("Apple iPhone 13 128GB Blue", lista.get(1).ponerProducto(), "solo cambia el grado: el producto de ahora");
        assertEquals("i13128AzulA100", lista.get(1).sku());
    }

    @Test void sinMovilesDeEseColorElProductoSaleDelNombreDelMaestroEscritoComoNsys() {
        when(nsys.cargarProductos()).thenReturn(List.of(productoConNombre("Blue", "Azul", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rosa", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(nsys.goodsContados()).thenReturn(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));
        assertEquals("Apple iPhone 13 128GB Pink", servicio.porPasar().get(0).ponerProducto());
    }

    @Test void sinNombreEnElMaestroNoSeLeenLasGrafias() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertNull(servicio.porPasar().get(0).ponerProducto());
        verify(nsys, never()).goodsContados();
    }

    @Test void lasOpcionesUsanTambienElNombreDelMaestro() {
        when(nsys.cargarProductos()).thenReturn(List.of(productoConNombre("Blue", "Azul", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        when(nsys.goodsContados()).thenReturn(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));
        assertEquals(List.of(new OpcionColor("Azul", "Apple iPhone 13 128GB Blue"),
                new OpcionColor("Rosa", "Apple iPhone 13 128GB Pink")), servicio.opciones(IMEI).colores());
    }

    @Test void sinNingunMovilDeEseColorElProductoVaVacio() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertNull(servicio.porPasar().get(0).ponerProducto());
    }

    @Test void unMovilQueYaNoEstaEnElEspejoNoSale() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, GRADO, "A100", "B")));
        assertTrue(servicio.porPasar().isEmpty());
    }

    @Test void sinNadaVigenteLaListaEstaVaciaSinLeerElEspejo() {
        assertTrue(servicio.porPasar().isEmpty());
        verify(nsys, never()).porImeis(any());
        verify(nsys, never()).cargarProductos();
    }

    // ---------------------------------------------------------------- opciones del diálogo (spec §2.3)

    @Test void lasOpcionesConElProductoDeCadaColorYLosSku() {
        MovilNsys m = movil(IMEI, "Azul", "B");
        when(nsys.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED",
                "Azul", "Apple iPhone 13 128GB Azul de otro lote"));
        OpcionesTaller o = servicio.opciones(IMEI);
        assertEquals("Apple iPhone 13 128GB Blue", o.productoActual());
        assertEquals(List.of(new OpcionColor("Azul", "Apple iPhone 13 128GB Blue"),
                new OpcionColor("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED")), o.colores(), "el de NSYS, con el producto de ahora");
        assertTrue(o.skus().contains(new SkuMaestro("Rojo", "A100", "i13128RojoA100")));
        assertEquals(4, o.skus().size());
    }

    @Test void sinModeloEnElMaestroNoHayColoresNiSeBuscaProducto() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(sinMaestro(IMEI, "B")));
        OpcionesTaller o = servicio.opciones(IMEI);
        assertTrue(o.colores().isEmpty());
        assertTrue(o.skus().isEmpty());
        verify(nsys, never()).goodsMasUsados(any());
    }

    @Test void lasOpcionesDeUnImeiFueraDelEspejoSon404() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.opciones("350000000000099")));
    }
}
