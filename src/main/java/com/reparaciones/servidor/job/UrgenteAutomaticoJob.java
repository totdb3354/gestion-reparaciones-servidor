package com.reparaciones.servidor.job;

import com.reparaciones.servidor.dao.ReparacionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class UrgenteAutomaticoJob {

    private static final Logger log = LoggerFactory.getLogger(UrgenteAutomaticoJob.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ReparacionDAO reparacionDAO;
    private final Clock clock;

    public UrgenteAutomaticoJob(ReparacionDAO reparacionDAO) {
        this(reparacionDAO, Clock.system(MADRID));
    }

    UrgenteAutomaticoJob(ReparacionDAO reparacionDAO, Clock clock) {
        this.reparacionDAO = reparacionDAO;
        this.clock = clock;
    }

    /** Inicio del día de hoy en Madrid, como Timestamp del instante UTC equivalente. */
    static Timestamp cutoffInicioDeHoyMadrid(Clock clock) {
        return Timestamp.from(LocalDate.now(clock).atStartOfDay(MADRID).toInstant());
    }

    /** A las 00:00 Europe/Madrid marca como urgentes las asignaciones de reparación
     *  pendientes con cliente cuya fecha de asignación es de un día anterior a hoy. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Madrid")
    public void ejecutar() {
        Timestamp cutoff = cutoffInicioDeHoyMadrid(clock);
        int n = reparacionDAO.marcarUrgentesClienteVencidas(cutoff);
        if (n > 0) log.info("AUTO_URGENTE: {} asignaciones marcadas como urgentes", n);
    }
}
