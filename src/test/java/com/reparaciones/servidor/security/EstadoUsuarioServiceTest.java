package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ¿Sigue existiendo y operativo el usuario del token? Con caché corta (spec sp7b §5.1). */
class EstadoUsuarioServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final EstadoUsuarioService servicio = new EstadoUsuarioService(jdbc, ahora::get);

    private void responde(int idUsu, boolean operativo) {
        responde(idUsu, operativo, false);
    }

    private void responde(int idUsu, boolean operativo, boolean passwordTemporal) {
        when(jdbc.queryForList(anyString(), eq(Boolean.class), eq(idUsu)))
                .thenReturn(operativo ? List.of(passwordTemporal) : List.of());
    }

    @Test void unUsuarioActivoEsOperativo() {
        responde(8, true);
        assertTrue(servicio.estaOperativo(8));
    }

    @Test void unUsuarioDesactivadoOBorradoNoLoEs() {
        responde(8, false);
        assertFalse(servicio.estaOperativo(8));
    }

    @Test void dentroDeLaVentanaNoVuelveAConsultar() {
        responde(8, true);
        servicio.estaOperativo(8);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS - 1);
        servicio.estaOperativo(8);
        verify(jdbc, times(1)).queryForList(anyString(), eq(Boolean.class), eq(8));
    }

    @Test void pasadaLaVentanaVuelveAConsultar() {
        responde(8, true);
        servicio.estaOperativo(8);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS);
        servicio.estaOperativo(8);
        verify(jdbc, times(2)).queryForList(anyString(), eq(Boolean.class), eq(8));
    }

    @Test void unaDesactivacionSeNotaAlCaducarLaVentana() {
        responde(8, true);
        assertTrue(servicio.estaOperativo(8));
        responde(8, false);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS);
        assertFalse(servicio.estaOperativo(8));
    }

    @Test void laCacheEsPorUsuario() {
        responde(8, true);
        responde(9, false);
        assertTrue(servicio.estaOperativo(8));
        assertFalse(servicio.estaOperativo(9));
    }

    /** Si la base de datos falla, no se expulsa a nadie: se deja pasar y se registra. */
    @Test void siLaConsultaFallaDejaPasar() {
        when(jdbc.queryForList(anyString(), eq(Boolean.class), eq(8)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("caída"));
        assertTrue(servicio.estaOperativo(8));
    }

    @Test void unUsuarioConLaMarcaTienePasswordTemporal() {
        responde(8, true, true);
        assertTrue(servicio.tienePasswordTemporal(8));
    }

    @Test void unUsuarioSinLaMarcaNoLaTiene() {
        responde(8, true, false);
        assertFalse(servicio.tienePasswordTemporal(8));
    }

    /** Un usuario que ya no está operativo no tiene sentido preguntarle por la marca: no la tiene. */
    @Test void unUsuarioNoOperativoNoTienePasswordTemporal() {
        responde(8, false);
        assertFalse(servicio.tienePasswordTemporal(8));
    }

    /** estaOperativo y tienePasswordTemporal comparten la misma consulta y la misma entrada de caché. */
    @Test void lasDosConsultasComparteLaMismaCacheYNoDuplicanLaLlamada() {
        responde(8, true, true);
        servicio.estaOperativo(8);
        servicio.tienePasswordTemporal(8);
        verify(jdbc, times(1)).queryForList(anyString(), eq(Boolean.class), eq(8));
    }

    /** Tras invalidar, la próxima consulta vuelve a la base aunque siga dentro de la ventana de caché
     *  (spec sp7b, arreglo E3-2): así un cambio de contraseña propio surte efecto sin esperar a los 30 s. */
    @Test void invalidarFuerzaUnaNuevaConsultaAunDentroDeLaVentana() {
        responde(8, true, true);
        assertTrue(servicio.tienePasswordTemporal(8));

        responde(8, true, false);
        servicio.invalidar(8);
        assertFalse(servicio.tienePasswordTemporal(8));
        verify(jdbc, times(2)).queryForList(anyString(), eq(Boolean.class), eq(8));
    }
}
