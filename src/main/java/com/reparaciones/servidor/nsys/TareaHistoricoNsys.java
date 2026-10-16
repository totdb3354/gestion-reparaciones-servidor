package com.reparaciones.servidor.nsys;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto, un paso del histórico (spec pieza 1 §6). Solo existe con nsys.historico.activo=true; el paso no hace nada
 *  de día ni cuando ya ha terminado. */
@Component
@ConditionalOnProperty(name = "nsys.historico.activo", havingValue = "true")
public class TareaHistoricoNsys {

    private final HistoricoNsys historico;

    public TareaHistoricoNsys(HistoricoNsys historico) {
        this.historico = historico;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 60_000)
    public void ejecutar() {
        historico.paso();
    }
}
