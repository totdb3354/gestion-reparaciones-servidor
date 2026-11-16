package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.job.UrgenteAutomaticoJob;
import com.reparaciones.servidor.model.ReparacionResumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga las asignaciones abiertas (las tres categorías) y las completadas hoy, tal y como las
 * necesitan {@code GET /reparaciones/carga-tecnicos} (spec 3a §5) y {@code POST /glass/prediccion}
 * (spec 3b §4.1): compartido para que las dos rutas calculen sobre exactamente los mismos datos.
 */
@Component
public class CargaAsignacionesService {

    private static final Logger log = LoggerFactory.getLogger(CargaAsignacionesService.class);

    private final ReparacionDAO dao;

    public CargaAsignacionesService(ReparacionDAO dao) {
        this.dao = dao;
    }

    /** Abiertas (las tres categorías) + completadas hoy; cerradasHoy vacío si esa consulta falla. inicioHoy: el corte
     *  de las completadas hoy en la zona de la JVM, para saber qué «por cerrar» se marcó hoy (spec 2026-11-16 §2). */
    public record Estado(List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, LocalDateTime inicioHoy) {}

    public Estado cargar() {
        // Las TRES categorías, como el JavaFX (PendientesSuperTecnicoController: la lista que pasa a
        // calcularDia es reparaciones + glass + pulido). getAsignaciones() sola trae solo las A… (su SQL
        // excluye AG% y AP%), así que el tramo pendiente de un técnico de glass saldría a cero mientras
        // el tramo hecho sí las cuenta (getAsignacionesCompletadasHoy une A% y AG%). El pulido se pasa
        // aunque calcularDia lo descarte (decisión A5): así el calco es literal y no hay que acordarse
        // de por qué faltaba uno.
        List<ReparacionResumen> abiertas = new ArrayList<>(dao.getAsignaciones(null));
        abiertas.addAll(dao.getAsignacionesGlass(null));
        abiertas.addAll(dao.getAsignacionesPulido(null));
        Timestamp corte = UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock.system(ZoneId.of("Europe/Madrid")));
        List<ReparacionResumen> cerradasHoy;
        try {
            cerradasHoy = dao.getAsignacionesCompletadasHoy(corte);
        } catch (RuntimeException e) {
            // degradación deliberada: solo lo abierto. Se registra porque es indistinguible de un día sin
            // trabajo cerrado; ReparacionDAO puede lanzar aquí un IllegalStateException si ASIGNACION_SELECT
            // se desincroniza con getAsignacionesCompletadasHoy.
            log.warn("getAsignacionesCompletadasHoy() falló; la carga degrada a solo lo abierto", e);
            cerradasHoy = List.of();
        }
        return new Estado(abiertas, cerradasHoy, corte.toLocalDateTime());
    }
}
