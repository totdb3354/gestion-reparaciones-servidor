package com.reparaciones.servidor.nsys;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Un paso del histórico (spec pieza 1 §6) cada 5 s después del anterior (antes, 60 s: tres noches en vez de unas cinco
 *  horas). Solo existe con nsys.historico.activo=true; el paso no hace nada en horario del taller ni cuando ya ha terminado.
 *  Comparte el hilo de las tareas programadas: la importación normal espera como mucho un paso. */
@Component
@ConditionalOnProperty(name = "nsys.historico.activo", havingValue = "true")
public class TareaHistoricoNsys {

    private final HistoricoNsys historico;

    public TareaHistoricoNsys(HistoricoNsys historico) {
        this.historico = historico;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 5_000)
    public void ejecutar() {
        historico.paso();
    }
}
