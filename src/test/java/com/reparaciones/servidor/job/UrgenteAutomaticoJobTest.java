package com.reparaciones.servidor.job;

import com.reparaciones.servidor.dao.ReparacionDAO;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UrgenteAutomaticoJobTest {

    @Test
    void cutoff_esInicioDeHoyEnMadrid_comoInstanteUtc() {
        // 2026-06-24 09:30 Madrid (verano = UTC+2) -> inicio de hoy Madrid = 2026-06-23 22:00 UTC
        Clock fixed = Clock.fixed(Instant.parse("2026-06-24T07:30:00Z"), ZoneId.of("Europe/Madrid"));
        Timestamp cutoff = UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(fixed);
        assertEquals(Instant.parse("2026-06-23T22:00:00Z"), cutoff.toInstant());
    }

    @Test
    void ejecutar_llamaAlDaoConElCutoff() {
        ReparacionDAO dao = mock(ReparacionDAO.class);
        when(dao.marcarUrgentesClienteVencidas(any())).thenReturn(3);
        UrgenteAutomaticoJob job = new UrgenteAutomaticoJob(dao);
        job.ejecutar();
        verify(dao, times(1)).marcarUrgentesClienteVencidas(any(Timestamp.class));
    }
}
