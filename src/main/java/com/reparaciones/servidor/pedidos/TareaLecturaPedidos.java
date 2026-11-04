package com.reparaciones.servidor.pedidos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 5 minutos, una vuelta de lectura de pedidos (spec pieza 2 §4.4). Solo existe con pedidos.lectura.activa=true: en los
 *  tests y en los entornos sin las fuentes no hay tarea. */
@Component
@ConditionalOnProperty(name = "pedidos.lectura.activa", havingValue = "true")
public class TareaLecturaPedidos {

    private final LecturaPedidos lectura;

    public TareaLecturaPedidos(LecturaPedidos lectura) {
        this.lectura = lectura;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 300_000)
    public void ejecutar() {
        lectura.pasada();
    }
}
