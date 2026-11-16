package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PUT /api/pedidos/filas/{id}/validacion: validacion de la peticion, motivos, 404/409, imeiAnterior, idempotencia y fila releida. */
class ValidacionControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final String IMEI = "350000000000001";
    private final VistaFilas vista = mock(VistaFilas.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final FilasController controller = new FilasController(vista, dao, new RegistroIdempotencia(),
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    private static FilaPedido fila(String origen) {
        return new FilaPedido(5, LocalDate.of(2026, 11, 5), "Cliente Web A", "i13128AzulA", IMEI, "37352",
                LocalDate.of(2026, 11, 4), origen, Canal.PARTICULARES, 1, null);
    }

    private static FilaVista vistaDe(String imei, String nivel, String motivo, String motivoValidado) {
        return new FilaVista(5, LocalDate.of(2026, 11, 5), "Cliente Web A", "i13128AzulA", imei, "37352", LocalDate.of(2026, 11, 4),
                "PENDIENTE", true, null, null, null, null, false, nivel, motivo, "i13128RojoA", "Listos", "PARTICULARES", null, null,
                null, null, null, null, null, null, motivoValidado == null ? null : AHORA, motivoValidado == null ? null : "super", motivoValidado);
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    private void filaExiste() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
    }

    @Test void validarCadaMotivoDeSkuLlamaAlDaoConElImeiVistoElMotivoElUsuarioYLaHora() {
        for (String motivo : new String[]{"MODELO", "COLOR_O_GRADO", "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO"}) {
            filaExiste();
            when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI, "MAL", motivo, null)));
            when(dao.ponerValidacion(5, true, IMEI, motivo, null, 7, AHORA)).thenReturn(FilasDAO.Resultado.PUESTO);
            assertNotNull(controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, null), motivo);
            verify(dao).ponerValidacion(5, true, IMEI, motivo, null, 7, AHORA);
        }
    }

    @Test void validarLoYaValidadoUsaElMotivoGuardadoYEsUn200() {
        filaExiste();
        FilaVista validada = vistaDe(IMEI, "OK", null, "COLOR_O_GRADO");
        when(vista.fila(5)).thenReturn(Optional.of(validada));
        when(dao.ponerValidacion(5, true, IMEI, "COLOR_O_GRADO", null, 7, AHORA)).thenReturn(FilasDAO.Resultado.SIN_CAMBIO);
        assertSame(validada, controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, null));
    }

    @Test void losMotivosQueNoSonDeSkuSon409SinEscribir() {
        for (String motivo : new String[]{"NO_EN_NSYS", "EN_OTRA_FILA", "EN_TALLER", "MS", "B_POR_A", null}) {
            filaExiste();
            when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI, motivo == null ? "OK" : "MAL", motivo, null)));
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, null), String.valueOf(motivo));
            assertEquals(409, e.getStatusCode().value());
            assertEquals(FilasController.MSG_NO_VALIDABLE, e.getReason());
        }
        verify(dao, never()).ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test void sinImeiEs409() {
        filaExiste();
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(null, null, null, null)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(FilasController.MSG_SIN_IMEI, e.getReason());
        verify(dao, never()).ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test void quitarNoMiraElAvisoYLlamaAlDao() {
        filaExiste();
        when(dao.ponerValidacion(5, false, null, null, IMEI, 7, AHORA)).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI, "MAL", "COLOR_O_GRADO", null)));
        assertNotNull(controller.ponerValidacion(5, new PeticionValidacion(false, IMEI), super_, null));
        verify(dao).ponerValidacion(5, false, null, null, IMEI, 7, AHORA);
    }

    @Test void validarAusenteEs400() {
        assertEquals(400, estado(() -> controller.ponerValidacion(5, new PeticionValidacion(null, null), super_, null)));
        verifyNoInteractions(dao);
    }

    @Test void imeiAnteriorMalFormadoEs400() {
        assertEquals(400, estado(() -> controller.ponerValidacion(5, new PeticionValidacion(true, "123"), super_, null)));
        verifyNoInteractions(dao);
    }

    @Test void imeiAnteriorQueNoCoincideEs409ConElMensajeDelImei() {
        filaExiste();
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI, "MAL", "MODELO", null)));
        when(dao.ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.CAMBIADA);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerValidacion(5, new PeticionValidacion(true, "350000000000002"), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(FilasController.MSG_CAMBIADO, e.getReason());
    }

    @Test void filaInexistenteEs404() {
        when(dao.porId(5)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.ponerValidacion(5, new PeticionValidacion(false, null), super_, null)));
        filaExiste();
        when(dao.ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.NO_EXISTE);
        assertEquals(404, estado(() -> controller.ponerValidacion(5, new PeticionValidacion(false, null), super_, null)));
    }

    @Test void historicoEs409() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("HISTORICO")));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(FilasController.MSG_HISTORICO, e.getReason());
        verify(dao, never()).ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any());
        filaExiste();
        when(dao.ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.CERRADA);
        assertEquals(409, estado(() -> controller.ponerValidacion(5, new PeticionValidacion(false, null), super_, null)));
    }

    @Test void elReintentoConLaMismaClaveNoVuelveAEscribir() {
        filaExiste();
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI, "MAL", "MODELO", null)));
        when(dao.ponerValidacion(anyInt(), anyBoolean(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, "clave-v");
        controller.ponerValidacion(5, new PeticionValidacion(true, null), super_, "clave-v");
        verify(dao, times(1)).ponerValidacion(5, true, IMEI, "MODELO", null, 7, AHORA);
    }
}
