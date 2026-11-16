package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El interruptor PEDIDOS_LIBROS_ACTIVA (pedidos.libros.activa): apagado o sin poner, no hay tarea; encendido, cada vuelta es una pasada. */
class TareaLecturaLibrosTest {

    private final LecturaLibros lectura = mock(LecturaLibros.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(LecturaLibros.class, () -> lectura)
            .withUserConfiguration(TareaLecturaLibros.class);

    @Test void apagadaOSinPonerNoHayTarea() {
        contexto.run(c -> assertThat(c).doesNotHaveBean(TareaLecturaLibros.class));
        contexto.withPropertyValues("pedidos.libros.activa=false").run(c -> assertThat(c).doesNotHaveBean(TareaLecturaLibros.class));
    }

    @Test void encendidaCadaVueltaEsUnaPasada() {
        contexto.withPropertyValues("pedidos.libros.activa=true").run(c -> {
            assertThat(c).hasSingleBean(TareaLecturaLibros.class);
            c.getBean(TareaLecturaLibros.class).ejecutar();
            verify(lectura).pasada();
        });
    }
}
