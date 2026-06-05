package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImeiLookupServiceTest {

    private final ImeiLookupService service = new ImeiLookupService("");

    @Test
    void convierte_nombres_comerciales_a_interno() {
        assertThat(service.comercialACodigoInterno("Apple iPhone 12 Pro")).isEqualTo("12pro");
        assertThat(service.comercialACodigoInterno("iPhone 13")).isEqualTo("13");
        assertThat(service.comercialACodigoInterno("iPhone XS Max")).isEqualTo("xsmax");
        assertThat(service.comercialACodigoInterno("iPhone SE 2020")).isEqualTo("se2020");
        assertThat(service.comercialACodigoInterno("iPhone 16e")).isEqualTo("16e");
        assertThat(service.comercialACodigoInterno("iPhone 14 Pro Max")).isEqualTo("14promax");
        assertThat(service.comercialACodigoInterno("iPhone 13 Mini")).isEqualTo("13mini");
    }

    @Test
    void android_devuelve_null() {
        assertThat(service.comercialACodigoInterno("Samsung Galaxy S24")).isNull();
        assertThat(service.comercialACodigoInterno("Moto G22")).isNull();
    }

    @Test
    void modelo_apple_desconocido_devuelve_null() {
        assertThat(service.comercialACodigoInterno("iPhone 99")).isNull();
        assertThat(service.comercialACodigoInterno("iPhone 99 Pro")).isNull();
    }

    @Test
    void key_vacia_no_llama_a_la_api() {
        assertThat(service.lookupModeloInterno("352322311421731")).isNull();
    }
}
