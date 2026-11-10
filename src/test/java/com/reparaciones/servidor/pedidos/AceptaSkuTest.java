package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AceptaSkuTest {

    /** Un producto del maestro: iPhone 13 128GB del color y grado dados, con sus SKU de canal. */
    static ProductoMaestro producto(String color, String grado, String web, String amazon, String particulares,
                                    String backiphone) {
        return new ProductoMaestro("clave-" + color + grado + web, "13", "13", "128GB", color, grado, web, amazon,
                "i13128" + color, false, "Apple iPhone 13 128GB " + color, amazon, particulares, backiphone);
    }

    /** Un iPhone 14 con y sin eSIM: como en el maestro, «SKU (Amazon)» es el mismo en las dos filas y «con eSIM» está en las dos. */
    private static ProductoMaestro iphone14(boolean esim) {
        return new ProductoMaestro("clave-14-" + esim, "14", "14", "256GB", "Azul", "A", "i14256AzulA" + (esim ? "E" : ""),
                "i14256AzulA", "i14256Azul", esim, "Apple iPhone 14 256GB Azul", esim ? "i14256AzulAesim" : "i14256AzulA", null, null);
    }

    private static final List<ProductoMaestro> MAESTRO = List.of(
            producto("Azul", "A", "i13128AzulA", "i13128AzulA", "i13128AzulAP", "bk-azul-a"),
            producto("Azul", "A+", "i13128AzulA", "i13128AzulA", "i13128AzulAP", "bk-azul-a"),
            producto("Azul", "A-", "i13128AzulA-", "i13128AzulA", "i13128AzulA-P", "bk-azul-am"),
            producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            producto("Azul", "B", "i13128AzulB", "i13128AzulB", "i13128AzulBP", "bk-azul-b"),
            producto("Rojo", "A", "i13128RojoA", "i13128RojoA", "i13128RojoAP", null));

    private final AceptaSku acepta = new AceptaSku(MAESTRO);

    @Test void amazonAceptaElAbanicoDeSuSkuYAparteLosB() {
        Aceptacion a = acepta.para(Canal.AMAZON, "I13128AZULA");
        assertTrue(a.acepta("i13128AzulA"));
        assertTrue(a.acepta("i13128azula-"));
        assertTrue(a.acepta("i13128AzulMs"));
        assertFalse(a.acepta("i13128AzulB"));
        assertTrue(a.aceptaBporA("i13128AzulB"));
        assertFalse(a.acepta("i13128RojoA"));
        assertEquals(new Atributos("13", "128GB", "Azul", "A", false), a.pide());
    }

    @Test void cadaWebUsaSuColumnaYElSimuladorLaNuestra() {
        assertTrue(acepta.para(Canal.PARTICULARES, "i13128AzulAP").acepta("i13128AzulA"));
        assertNull(acepta.para(Canal.PARTICULARES, "i13128AzulA"), "Particulares no usa nuestro SKU");
        assertTrue(acepta.para(Canal.BACKIPHONE, "bk-azul-a").acepta("i13128AzulA"));
        assertTrue(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").acepta("i13128AzulA"));
        assertFalse(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").acepta("i13128AzulA-"));
        assertTrue(acepta.para(Canal.SIMULADOR, "i13128RojoA").acepta("i13128RojoA"));
        assertTrue(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").productosBporA().isEmpty(), "B por A solo en Amazon");
    }

    @Test void unSkuQueElMaestroNoConoceNoSeAcepta() {
        assertNull(acepta.para(Canal.AMAZON, "i99128VerdeA"));
        assertNull(acepta.para(Canal.SIMULADOR, null));
    }

    @Test void colorYGradoParaUnMovilSinSku() {
        Aceptacion a = acepta.para(Canal.AMAZON, "i13128AzulA");
        assertTrue(a.aceptaColorYGrado("azul", "A+"));
        assertFalse(a.aceptaColorYGrado("Rojo", "A"));
    }

    @Test void lasVariantesBSeAnadenParaCargarElMaestro() {
        assertEquals(Set.of("i13128AzulA", "i13128AzulB", "GLS24"), AceptaSku.conVariantes(List.of("i13128AzulA", "GLS24")));
    }

    @Test void amazonDiferenciaLaEsimDelFisicoPorSuColumna() {
        AceptaSku a = new AceptaSku(List.of(iphone14(false), iphone14(true)));

        Aceptacion normal = a.para(Canal.AMAZON, "i14256AzulA");
        assertEquals(1, normal.productos().size());
        assertTrue(normal.acepta("i14256AzulA"));
        assertFalse(normal.acepta("i14256AzulAE"));
        assertFalse(normal.pide().esEsim());

        Aceptacion esim = a.para(Canal.AMAZON_STOCKROOM, "i14256AzulAesim");
        assertEquals(1, esim.productos().size());
        assertTrue(esim.acepta("i14256AzulAE"));
        assertFalse(esim.acepta("i14256AzulA"));
        assertTrue(esim.pide().esEsim());
    }

    @Test void unaFilaDelMaestroSoloConSkuAmazonYaNoSirveParaAmazon() {
        ProductoMaestro soloAmazon = new ProductoMaestro("c", "14", "14", "256GB", "Azul", "A", "i14256AzulA", "i14256AzulA",
                "i14256Azul", false, "Apple iPhone 14 256GB Azul", null, null, null);

        assertNull(new AceptaSku(List.of(soloAmazon)).para(Canal.AMAZON, "i14256AzulA"));
    }

    @Test void ultramarinoPideLoMismoQueAzulYSeBuscaEnElMaestroComoAzul() {
        AceptaSku acepta = new AceptaSku(List.of(producto("Azul", "A", "i16128AzulA", null, null, null)));
        assertNotNull(acepta.para(Canal.SIMULADOR, "i16128UltramarinoA"));
        assertTrue(acepta.para(Canal.SIMULADOR, "i16128UltramarinoA").acepta("i16128AzulA"));
        assertTrue(AceptaSku.conVariantes(List.of("i16128UltramarinoA")).containsAll(List.of("i16128AzulA", "i16128AzulB")));
    }
}
