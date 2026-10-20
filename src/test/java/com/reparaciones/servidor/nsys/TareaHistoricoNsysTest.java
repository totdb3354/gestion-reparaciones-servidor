package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TareaHistoricoNsysTest {

    @Test void cadaEjecucionEsUnPaso() {
        HistoricoNsys historico = mock(HistoricoNsys.class);
        new TareaHistoricoNsys(historico).ejecutar();
        verify(historico).paso();
    }

    /** 5 s entre pasos (20/10/2026): con 60 s el histórico tardaba tres noches; con 5 s, unas cinco horas. */
    @Test void entrePasoYPasoEsperaCincoSegundos() throws NoSuchMethodException {
        Scheduled programa = TareaHistoricoNsys.class.getMethod("ejecutar").getAnnotation(Scheduled.class);
        assertEquals(5_000, programa.fixedDelay());
    }
}
