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

/** PUT /api/pedidos/filas/{id}/falta-enviar: reglas, 404/409, idempotencia y fila releida. */
class FaltaEnviarControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private final VistaFilas vista = mock(VistaFilas.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final FilasController controller = new FilasController(vista, dao, new RegistroIdempotencia(),
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    private static FilaPedido fila(String origen) {
        return new FilaPedido(5, LocalDate.of(2026, 11, 5), "Cliente Web A", "i13128AzulA", null, "37352",
                LocalDate.of(2026, 11, 4), origen, Canal.PARTICULARES, 1, null);
    }

    private static FilaVista vistaDe(LocalDateTime marca) {
        return new FilaVista(5, LocalDate.of(2026, 11, 5), "Cliente Web A", "i13128AzulA", null, "37352", LocalDate.of(2026, 11, 4),
                "ENVIADA", true, AHORA.minusDays(1), null, null, null, true, null, null, null, null, "PARTICULARES", marca, null);
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void marcarDevuelveLaFilaReleidaConLaMarcaYLlamaAlDaoConElUsuarioYLaHora() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(5, true, 7, AHORA)).thenReturn(FilasDAO.Resultado.PUESTO);
        FilaVista releida = vistaDe(AHORA);
        when(vista.fila(5)).thenReturn(Optional.of(releida));
        assertSame(releida, controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null));
        assertEquals(AHORA, releida.faltaEnviarEn());
        verify(dao).ponerFaltaEnviar(5, true, 7, AHORA);
    }

    @Test void sinCambioEsUn200ConLaFilaReleida() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.SIN_CAMBIO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(AHORA)));
        assertNotNull(controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null));
    }

    @Test void desmarcarLlamaAlDaoConFalse() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(null)));
        controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(false), super_, null);
        verify(dao).ponerFaltaEnviar(5, false, 7, AHORA);
    }

    @Test void noMarcableEsUn409ConElMensajeParaElUsuario() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.NO_MARCABLE);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("Solo se puede marcar en un pedido de web con la etiqueta ya generada", e.getReason());
        verify(vista, never()).fila(anyInt());
    }

    @Test void filaInexistenteEs404() {
        when(dao.porId(5)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null)));
        verify(dao, never()).ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any());
        // Y si desaparece entre la comprobacion y el bloqueo
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.NO_EXISTE);
        assertEquals(404, estado(() -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null)));
    }

    @Test void historicoEs409ComoElImei() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("HISTORICO")));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(false), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(FilasController.MSG_HISTORICO, e.getReason());
        verify(dao, never()).ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any());
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.CERRADA);
        assertEquals(409, estado(() -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null)));
    }

    @Test void sinFaltaEnElCuerpoEs400() {
        assertEquals(400, estado(() -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(null), super_, null)));
        verifyNoInteractions(dao);
    }

    @Test void elReintentoConLaMismaClaveNoVuelveAEscribir() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(AHORA)));
        controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, "clave-falta");
        controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, "clave-falta");
        verify(dao, times(1)).ponerFaltaEnviar(5, true, 7, AHORA);
    }
}
