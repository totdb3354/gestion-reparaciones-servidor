package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TareaImportacionNsysTest {

    @Test void cadaEjecucionEsUnaPasada() {
        ImportadorNsys importador = mock(ImportadorNsys.class);
        new TareaImportacionNsys(importador).ejecutar();
        verify(importador).pasada();
    }

    @Test void soloExisteConLaImportacionEncendida() {
        ConditionalOnProperty condicion = TareaImportacionNsys.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"nsys.importacion.activa"}, condicion.name());
        assertEquals("true", condicion.havingValue());
    }
}
