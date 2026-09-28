package com.reparaciones.servidor;

import com.reparaciones.servidor.security.EstadoUsuarioService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** En los tests de web todos los usuarios están operativos, salvo que el test diga lo contrario. */
@TestConfiguration
public class UsuariosOperativosTestConfig {

    @Bean
    @Primary
    EstadoUsuarioService estadoUsuarioService() {
        EstadoUsuarioService m = mock(EstadoUsuarioService.class);
        when(m.estaOperativo(anyInt())).thenReturn(true);
        return m;
    }
}
