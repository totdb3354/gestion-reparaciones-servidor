package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Las reglas de las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3) y el repaso de las marcas (§3.6). Datos inventados:
 *  iPhone 13 128GB de TallerDePrueba; los cambios de TallerDePrueba.vigente son de ayer (HOY - 1 día). */
class TarjetasNsysTest {

    private static final String IMEI = "350000000000001";
    private static final String OTRO = "350000000000002";
    private static final LocalDateTime AYER = HOY.minusDays(1);

    private static TallerNsys.MovilPorPasar porPasar(MovilNsys m, LoQueVale.Valor v, ProductoNsys.Poner poner,
                                                     CambioTaller... cambios) {
        return new TallerNsys.MovilPorPasar(m, v, List.of(cambios), poner);
    }

    private static LoQueVale.Valor vale(String color, String grade, String colorNsys, String gradoNsys) {
        return new LoQueVale.Valor(color, grade, null, colorNsys, gradoNsys, null, true);
    }

    private static HechoTaller marca(String imei, TarjetaHecho tarjeta, LocalDateTime fecha, LocalDateTime volvio) {
        return new HechoTaller(imei, tarjeta, 7, "Supertécnico 1", fecha, volvio);
    }

    @Test void unMovilQueCambiaDeColorYDeGradoSaleEnLasDosTarjetas() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rojo", "A", "Azul", "B"),
                new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false),
                vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A", "B"));
        FilaColorNsys c = TarjetasNsys.color(List.of(p), Map.of()).get(0);
        assertEquals("Blue", c.colorDe(), "como lo escribe NSYS");
        assertEquals("(PRODUCT)RED", c.colorA());
        assertEquals("Apple iPhone 13 128GB Blue", c.productoNsys());
        assertEquals("B", c.gradoNsys(), "el grado de NSYS hoy: el que lleva la plantilla");
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", c.ponerProducto());
        assertFalse(c.compruebalo());
        assertTrue(c.tambienGrado());
        assertEquals(List.of("COLOR"), c.porQue().stream().map(PorQueTaller::dato).toList());
        assertEquals(AYER, c.desde());
        assertNull(c.hechoEn());
        FilaGradoNsys g = TarjetasNsys.grado(List.of(p), Map.of()).get(0);
        assertEquals("B", g.gradoDe());
        assertEquals("A", g.gradoA());
        assertEquals("Apple iPhone 13 128GB Blue", g.productoNsys());
        assertTrue(g.conColor());
        assertEquals(List.of("GRADO"), g.porQue().stream().map(PorQueTaller::dato).toList());
    }

    @Test void soloGradoNoSaleEnColorYSinProductoElColorNuevoEsElDelMaestro() {
        TallerNsys.MovilPorPasar grado = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A100", null, "B"), null,
                vigente(2, IMEI, GRADO, "A100", "B"));
        assertTrue(TarjetasNsys.color(List.of(grado), Map.of()).isEmpty());
        assertFalse(TarjetasNsys.grado(List.of(grado), Map.of()).get(0).conColor());
        TallerNsys.MovilPorPasar sinProducto = porPasar(movil(OTRO, "Azul", "B"), vale("Verde", "B", "Azul", null), null,
                vigente(3, OTRO, COLOR, "Verde", "Azul"));
        FilaColorNsys c = TarjetasNsys.color(List.of(sinProducto), Map.of()).get(0);
        assertNull(c.ponerProducto(), "búscalo a mano");
        assertEquals("Verde", c.colorA());
        assertFalse(c.tambienGrado());
    }

    @Test void unProductoDelMaestroTalCualLlevaCompruebalo() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rosa", "B", "Azul", null),
                new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", true), vigente(1, IMEI, COLOR, "Rosa", "Azul"));
        FilaColorNsys c = TarjetasNsys.color(List.of(p), Map.of()).get(0);
        assertTrue(c.compruebalo());
        assertEquals("Pink", c.colorA());
    }

    @Test void sinColorCerradoEnNsysElColorNuevoEsElDeAhoraTambienSinCapacidadEnElGoods() {
        TallerNsys.MovilPorPasar normal = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "B", null, null), null,
                vigente(1, IMEI, COLOR, "Azul", "Azul"));
        FilaColorNsys c = TarjetasNsys.color(List.of(normal), Map.of()).get(0);
        assertEquals("Blue", c.colorDe());
        assertEquals("Blue", c.colorA());
        assertEquals("Apple iPhone 13 128GB Blue", c.ponerProducto());
        MovilNsys raro = new MovilNsys(OTRO, "BT-9001", "Apple iPhone 13 Blue", "B", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        FilaColorNsys r = TarjetasNsys.color(List.of(porPasar(raro, vale("Azul", "B", null, null), null,
                vigente(2, OTRO, COLOR, "Azul", "Azul"))), Map.of()).get(0);
        assertEquals("Azul", r.colorDe());
        assertEquals("Azul", r.colorA());
    }

    @Test void laMarcaValeSiNoEsAnteriorAlCambioYQuedaComoVueltaConSuHora() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A", null, "B"), null,
                vigente(2, IMEI, GRADO, "A", "B"));
        FilaGradoNsys conMarca = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, HOY, null)))).get(0);
        assertEquals("Supertécnico 1", conMarca.hechoPor());
        assertEquals(HOY, conMarca.hechoEn());
        assertTrue(TarjetasNsys.marcada(conMarca.hechoEn(), conMarca.hechoVolvio()));
        FilaGradoNsys deAntes = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, AYER.minusHours(1), null)))).get(0);
        assertNull(deAntes.hechoEn(), "una marca de un cambio anterior del mismo móvil no vale");
        FilaGradoNsys vuelta = TarjetasNsys.grado(List.of(p),
                TarjetasNsys.porClave(List.of(marca(IMEI, TarjetaHecho.GRADO, HOY, HOY.plusHours(1))))).get(0);
        assertEquals(HOY.plusHours(1), vuelta.hechoVolvio());
        assertFalse(TarjetasNsys.marcada(vuelta.hechoEn(), vuelta.hechoVolvio()), "vuelta: la fila cuenta otra vez");
        assertFalse(TarjetasNsys.marcada(null, null));
    }

    @Test void laMarcaVaPorTarjetaYPorImeiSinMayusculas() {
        TallerNsys.MovilPorPasar p = porPasar(movil("F2LXABC123", "Azul", "B"), vale("Rojo", "A", "Azul", "B"), null,
                vigente(1, "F2LXABC123", COLOR, "Rojo", "Azul"), vigente(2, "F2LXABC123", GRADO, "A", "B"));
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(List.of(marca("f2lxabc123", TarjetaHecho.COLOR, HOY, null)));
        assertEquals(HOY, TarjetasNsys.color(List.of(p), marcas).get(0).hechoEn());
        assertNull(TarjetasNsys.grado(List.of(p), marcas).get(0).hechoEn(), "marcar el color no marca el grado");
    }

    @Test void cadaListaVaDeLoMasAntiguoALoMasReciente() {
        TallerNsys.MovilPorPasar nuevo = porPasar(movil(IMEI, "Azul", "B"), vale("Azul", "A", null, "B"), null,
                vigente(2, IMEI, GRADO, "A", "B", HOY));
        TallerNsys.MovilPorPasar viejo = porPasar(movil(OTRO, "Azul", "B"), vale("Azul", "C", null, "B"), null,
                vigente(3, OTRO, GRADO, "C", "B", HOY.minusDays(3)));
        assertEquals(List.of(OTRO, IMEI),
                TarjetasNsys.grado(List.of(nuevo, viejo), Map.of()).stream().map(FilaGradoNsys::imeiSn).toList());
    }

    @Test void lasAltasLlevanSuCategoriaYSuMarca() {
        AltaTaller a = new AltaTaller("350000000000088", "14", "R20261006_57", false, "Técnico 2", "AMAZON", 2,
                LocalDateTime.of(2026, 10, 6, 9, 0), LocalDateTime.of(2026, 10, 6, 9, 0));
        FilaAltaNsys f = TarjetasNsys.altas(List.of(a),
                TarjetasNsys.porClave(List.of(marca("350000000000088", TarjetaHecho.ALTA, HOY, null))), Map.of()).get(0);
        assertEquals("REPARACION", f.categoria());
        assertEquals("R20261006_57", f.ultimoTrabajo());
        assertEquals("14", f.modelo());
        assertEquals(2, f.trabajos());
        assertEquals(HOY, f.hechoEn());
    }

    @Test void revisarLlevaElModeloDelEspejoYSoloLosAvisosDeRevisar() {
        AvisoTaller sinColor = new AvisoTaller(1, IMEI, "R20261022_2", AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow",
                null, "chasis chai13yellow nuevo", 8, "Técnico 2", AYER);
        AvisoTaller deshecho = new AvisoTaller(2, IMEI, "R20261022_3", AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA,
                COLOR, "Rojo", "Azul", "chasis chai13red nuevo", 8, "Técnico 2", AYER);
        AvisoTaller fuera = new AvisoTaller(3, OTRO, "R20261022_4", AvisoTaller.Tipo.SIN_DESHACER, AvisoTaller.Causa.EDITADA,
                GRADO, "A", null, "batería bati13 nueva", 8, "Técnico 2", HOY);
        List<FilaRevisarNsys> filas = TarjetasNsys.revisar(List.of(sinColor, deshecho, fuera), Map.of(IMEI, movil(IMEI, "Azul", "B")));
        assertEquals(List.of(1, 3), filas.stream().map(FilaRevisarNsys::idAviso).toList());
        assertEquals("13", filas.get(0).modelo());
        assertEquals("128GB", filas.get(0).capacidad());
        assertEquals("SIN_COLOR", filas.get(0).tipo());
        assertNull(filas.get(1).modelo(), "fuera del espejo, sin modelo");
        assertEquals("EDITADA", filas.get(1).causa());
        assertEquals("GRADO", filas.get(1).dato());
    }

    @Test void lasFilasDelRepasoSonLasDeColorGradoYAltas() {
        TallerNsys.MovilPorPasar p = porPasar(movil(IMEI, "Azul", "B"), vale("Rojo", "A", "Azul", "B"), null,
                vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A", "B"));
        AltaTaller a = new AltaTaller("350000000000088", "14", "R20261006_57", false, "Técnico 2", null, 1, HOY.minusDays(19), HOY.minusDays(19));
        Map<String, LocalDateTime> filas = TarjetasNsys.filas(List.of(p), List.of(a));
        assertEquals(Set.of(TarjetasNsys.clave(TarjetaHecho.COLOR, IMEI), TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI),
                TarjetasNsys.clave(TarjetaHecho.ALTA, "350000000000088")), filas.keySet());
        assertEquals(AYER, filas.get(TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI)));
    }

    @Test void elRepasoBorraLoQueYaNoEstaYLoDeOtroCambioYHaceVolverLoQueSigueIgual() {
        LocalDateTime fichero = HOY.plusHours(1);
        HechoTaller seFue = marca(IMEI, TarjetaHecho.COLOR, HOY, null);
        HechoTaller sigue = marca(IMEI, TarjetaHecho.GRADO, HOY, null);
        HechoTaller despuesDelFichero = marca(OTRO, TarjetaHecho.GRADO, HOY.plusHours(2), null);
        HechoTaller yaVuelta = marca(OTRO, TarjetaHecho.COLOR, HOY, HOY);
        HechoTaller deOtroCambio = marca("350000000000003", TarjetaHecho.GRADO, AYER.minusDays(1), null);
        Map<String, LocalDateTime> filas = Map.of(
                TarjetasNsys.clave(TarjetaHecho.GRADO, IMEI), AYER,
                TarjetasNsys.clave(TarjetaHecho.GRADO, OTRO), AYER,
                TarjetasNsys.clave(TarjetaHecho.COLOR, OTRO), AYER,
                TarjetasNsys.clave(TarjetaHecho.GRADO, "350000000000003"), AYER);
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(List.of(seFue, sigue, despuesDelFichero, yaVuelta, deOtroCambio), filas,
                fichero);
        assertEquals(List.of(seFue, deOtroCambio), r.borrar());
        assertEquals(List.of(sigue), r.vuelven(), "un fichero de después de marcar que no lo trae: vuelve a salir");
    }

    @Test void unaFilaConDesdeNullNoEsUnaFilaQueYaNoEstaNiAnteriorALaMarca() {
        LocalDateTime fichero = HOY.plusHours(1);
        HechoTaller alta = marca(IMEI, TarjetaHecho.ALTA, HOY, null);
        Map<String, LocalDateTime> filas = new java.util.HashMap<>();
        filas.put(TarjetasNsys.clave(TarjetaHecho.ALTA, IMEI), null);
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(List.of(alta), filas, HOY.minusHours(1));
        assertTrue(r.borrar().isEmpty());
        assertTrue(r.vuelven().isEmpty(), "el fichero es anterior a la marca");
        assertEquals(List.of(alta), TarjetasNsys.repasar(List.of(alta), filas, fichero).vuelven());
    }

    private static DescarteTaller descarte(String imei, LocalDateTime fecha) {
        return new DescarteTaller(imei, 7, "Supertécnico 1", fecha, "no es un móvil");
    }

    private static AltaTaller alta(String imei, LocalDateTime ultimoEn) {
        return new AltaTaller(imei, "14", "R20261006_57", false, "Técnico 2", null, 1, HOY.minusDays(19), ultimoEn);
    }

    @Test void unDescarteNoAnteriorALaUltimaAsignacionQuitaLaFilaYVaADescartadas() {
        AltaTaller a = alta("350000000000088", HOY.minusDays(1));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)));
        assertTrue(TarjetasNsys.altas(List.of(a), Map.of(), d).isEmpty());
        FilaDescartadaNsys f = TarjetasNsys.descartadas(List.of(a), d).get(0);
        assertEquals("350000000000088", f.imeiSn());
        assertEquals("14", f.modelo());
        assertEquals("R20261006_57", f.ultimoTrabajo());
        assertEquals("REPARACION", f.categoria());
        assertEquals("Supertécnico 1", f.descartadoPor());
        assertEquals(HOY, f.descartadoEn());
        assertEquals("no es un móvil", f.motivo());
    }

    @Test void conLaMismaHoraQueLaUltimaAsignacionTambienVale() {
        assertTrue(TarjetasNsys.descartada(alta("350000000000088", HOY),
                TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)))));
    }

    @Test void sinFechaDeAsignacionElDescarteVale() {
        assertTrue(TarjetasNsys.descartada(alta("350000000000088", null),
                TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)))));
    }

    @Test void unTrabajoNuevoDespuesDelDescarteLaSacaOtraVezConQuienLaDescarto() {
        AltaTaller a = alta("350000000000088", HOY.plusHours(1));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)));
        FilaAltaNsys f = TarjetasNsys.altas(List.of(a), Map.of(), d).get(0);
        assertEquals("Supertécnico 1", f.descartadoAntesPor());
        assertEquals(HOY, f.descartadoAntesEn());
        assertEquals("no es un móvil", f.descartadoAntesMotivo());
        assertTrue(TarjetasNsys.descartadas(List.of(a), d).isEmpty());
    }

    @Test void elDescarteCruzaElImeiSinMirarMayusculasNiEspacios() {
        assertTrue(TarjetasNsys.descartada(alta("f2lxabc123", HOY.minusDays(1)),
                TarjetasNsys.descartesPorClave(List.of(descarte(" F2LXABC123 ", HOY)))));
    }

    @Test void sinDescarteLaFilaNoLlevaNadaDelDescarte() {
        FilaAltaNsys f = TarjetasNsys.altas(List.of(alta("350000000000088", HOY)), Map.of(), Map.of()).get(0);
        assertNull(f.descartadoAntesPor());
        assertNull(f.descartadoAntesEn());
        assertNull(f.descartadoAntesMotivo());
        assertTrue(TarjetasNsys.descartadas(List.of(alta("350000000000088", HOY)), Map.of()).isEmpty());
    }

    private static List<AltaTaller.Trabajo> trabajosDeUnaAsignacionTerminada(LocalDateTime asignada, LocalDateTime cierre) {
        return List.of(
                new AltaTaller.Trabajo("350000000000088", "A20261006_1", asignada, cierre, "Técnico 2", "14", null),
                new AltaTaller.Trabajo("350000000000088", "R20261006_57", cierre, cierre, "Técnico 2", "14", null));
    }

    @Test void terminarLaAsignacionAbiertaAlDescartarNoSacaLaFilaPeroUnaAsignacionNuevaSi() {
        LocalDateTime asignada = HOY.minusHours(1);
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY)));
        AltaTaller terminada = AltaTaller.de(trabajosDeUnaAsignacionTerminada(asignada, HOY.plusHours(1))).get(0);
        assertTrue(TarjetasNsys.descartada(terminada, d));
        assertTrue(TarjetasNsys.altas(List.of(terminada), Map.of(), d).isEmpty());

        java.util.ArrayList<AltaTaller.Trabajo> con = new java.util.ArrayList<>(trabajosDeUnaAsignacionTerminada(asignada, HOY.plusHours(1)));
        con.add(new AltaTaller.Trabajo("350000000000088", "A20261006_2", HOY.plusHours(2), null, "Técnico 2", "14", null));
        AltaTaller nueva = AltaTaller.de(con).get(0);
        assertFalse(TarjetasNsys.descartada(nueva, d));
        assertEquals("Supertécnico 1", TarjetasNsys.altas(List.of(nueva), Map.of(), d).get(0).descartadoAntesPor());
    }

    @Test void losDescartadosVanDelMasRecienteAlMasAntiguo() {
        AltaTaller uno = alta("350000000000088", HOY.minusDays(3));
        AltaTaller dos = alta("350000000000099", HOY.minusDays(3));
        Map<String, DescarteTaller> d = TarjetasNsys.descartesPorClave(List.of(descarte("350000000000088", HOY.minusDays(1)),
                descarte("350000000000099", HOY)));
        assertEquals(List.of("350000000000099", "350000000000088"),
                TarjetasNsys.descartadas(List.of(uno, dos), d).stream().map(FilaDescartadaNsys::imeiSn).toList());
    }
}
