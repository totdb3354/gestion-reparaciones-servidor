package com.reparaciones.servidor.job;

import com.reparaciones.servidor.dao.ReparacionDAO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
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
    void ejecutar_llamaAlDaoConElCutoffExacto() {
        // 2026-06-24 09:30 Madrid (verano = UTC+2) -> inicio de hoy Madrid = 2026-06-23 22:00 UTC
        Clock fixed = Clock.fixed(Instant.parse("2026-06-24T07:30:00Z"), ZoneId.of("Europe/Madrid"));
        Timestamp expected = Timestamp.from(Instant.parse("2026-06-23T22:00:00Z"));

        ReparacionDAO dao = mock(ReparacionDAO.class);
        when(dao.marcarUrgentesClienteVencidas(any())).thenReturn(3);
        UrgenteAutomaticoJob job = new UrgenteAutomaticoJob(dao, fixed);
        job.ejecutar();
        verify(dao, times(1)).marcarUrgentesClienteVencidas(eq(expected));
    }

    @Test
    void spring_puedeInstanciarElJobComoBean() {
        // Reproduce el arranque real: un @Component con varios constructores necesita
        // uno marcado @Autowired; si no, Spring intenta el constructor por defecto
        // (inexistente) y el contexto no arranca.
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ReparacionDAO.class, () -> mock(ReparacionDAO.class));
            ctx.register(UrgenteAutomaticoJob.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(UrgenteAutomaticoJob.class));
        }
    }
}
