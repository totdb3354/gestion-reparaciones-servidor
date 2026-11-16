package com.reparaciones.servidor.pedidos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 10 minutos, una vuelta del lector de los libros de ventas de Amazon. Solo existe con pedidos.libros.activa=true
 *  (PEDIDOS_LIBROS_ACTIVA): en los tests y en los entornos sin los libros no hay tarea. */
@Component
@ConditionalOnProperty(name = "pedidos.libros.activa", havingValue = "true")
public class TareaLecturaLibros {

    private final LecturaLibros lectura;

    public TareaLecturaLibros(LecturaLibros lectura) {
        this.lectura = lectura;
    }

    @Scheduled(initialDelay = 150_000, fixedDelay = 600_000)
    public void ejecutar() {
        lectura.pasada();
    }
}
