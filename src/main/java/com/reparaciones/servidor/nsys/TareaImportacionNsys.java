package com.reparaciones.servidor.nsys;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 5 minutos, una pasada de importación (spec pieza 1 §5). Solo existe con nsys.importacion.activa=true: en los
 *  tests y en los entornos sin la cuenta de Drive no hay tarea. */
@Component
@ConditionalOnProperty(name = "nsys.importacion.activa", havingValue = "true")
public class TareaImportacionNsys {

    private final ImportadorNsys importador;

    public TareaImportacionNsys(ImportadorNsys importador) {
        this.importador = importador;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void ejecutar() {
        importador.pasada();
    }
}
