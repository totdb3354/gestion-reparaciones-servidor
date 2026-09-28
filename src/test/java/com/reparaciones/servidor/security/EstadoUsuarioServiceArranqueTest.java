package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.mockito.internal.util.MockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring puede construir el servicio de estado de usuario de verdad, con su constructor de producción.
 *
 * Los tests de web sustituyen este servicio por un simulacro para no consultar la base de datos, así que
 * ninguno de ellos comprueba que el bean real se pueda montar. Este sí: pide el bean y no hace ninguna
 * petición, de modo que no llega a consultar nada. Si el servicio gana una dependencia o deja de tener un
 * constructor que Spring pueda elegir, la aplicación no arrancaría y este test es el que lo dice.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class EstadoUsuarioServiceArranqueTest {

    @Autowired EstadoUsuarioService servicio;

    @Test void spring_construyeElServicioRealNoUnSimulacro() {
        assertNotNull(servicio, "el contexto no trae el servicio de estado de usuario");
        assertFalse(MockUtil.isMock(servicio),
                "este test debe recibir el servicio real: si le llega un simulacro, deja de comprobar el arranque");
    }
}
