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

/** PUT /api/pedidos/filas/{id}/marca: validacion, 404/409, marcaAnterior, idempotencia y fila releida. */
class MarcaControllerTest {

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

    private static FilaVista vistaDe(String marca, String nota) {
        return new FilaVista(5, LocalDate.of(2026, 11, 5), "Cliente Web A", "i13128AzulA", null, "37352", LocalDate.of(2026, 11, 4),
                "PENDIENTE", true, null, null, null, null, false, null, null, null, null, "PARTICULARES", null, null,
                marca, nota, marca == null ? null : AHORA, marca == null ? null : "super");
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    private void filaExiste() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
    }

    @Test void cadaMarcaDevuelveLaFilaReleidaYLlamaAlDaoConUsuarioYHora() {
        for (String marca : new String[]{"ESPERA_STOCK", "EN_REVISION", "PENDIENTE_COLOR", "CANCELADO"}) {
            filaExiste();
            when(dao.ponerMarca(5, marca, "hay que pedirlo", null, 7, AHORA)).thenReturn(FilasDAO.Resultado.PUESTO);
            FilaVista releida = vistaDe(marca, "hay que pedirlo");
            when(vista.fila(5)).thenReturn(Optional.of(releida));
            assertSame(releida, controller.ponerMarca(5, new PeticionMarca(marca, "hay que pedirlo", null), super_, null), marca);
            verify(dao).ponerMarca(5, marca, "hay que pedirlo", null, 7, AHORA);
        }
    }

    @Test void laNotaSeRecortaYVaciaEsNula() {
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe("CANCELADO", null)));
        controller.ponerMarca(5, new PeticionMarca("CANCELADO", "   ", null), super_, null);
        verify(dao).ponerMarca(5, "CANCELADO", null, null, 7, AHORA);
        controller.ponerMarca(5, new PeticionMarca("CANCELADO", "  hola  ", null), super_, null);
        verify(dao).ponerMarca(5, "CANCELADO", "hola", null, 7, AHORA);
    }

    @Test void marcaNulaQuitaLaMarca() {
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(null, null)));
        controller.ponerMarca(5, new PeticionMarca(null, null, "CANCELADO"), super_, null);
        verify(dao).ponerMarca(5, null, null, "CANCELADO", 7, AHORA);
    }

    @Test void sinCambioEsUn200ConLaFilaReleida() {
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.SIN_CAMBIO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe("CANCELADO", null)));
        assertNotNull(controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, null), super_, null));
    }

    @Test void marcaDesconocidaEs400() {
        for (String mala : new String[]{"cancelado", "OTRA", "", " "})
            assertEquals(400, estado(() -> controller.ponerMarca(5, new PeticionMarca(mala, null, null), super_, null)), mala);
        verifyNoInteractions(dao);
    }

    @Test void marcaAnteriorDesconocidaEs400() {
        assertEquals(400, estado(() -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, "XX"), super_, null)));
        verifyNoInteractions(dao);
    }

    @Test void notaDeMasDe200EsUn400YDe200Vale() {
        assertEquals(400, estado(() -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", "x".repeat(201), null), super_, null)));
        verifyNoInteractions(dao);
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe("CANCELADO", "x")));
        controller.ponerMarca(5, new PeticionMarca("CANCELADO", "x".repeat(200), null), super_, null);
        verify(dao).ponerMarca(5, "CANCELADO", "x".repeat(200), null, 7, AHORA);
    }

    @Test void marcaAnteriorQueNoCoincideEs409ConElMensaje() {
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.CAMBIADA);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, ""), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("Otra persona ha cambiado la marca de esta fila mientras tanto. Recarga y vuelve a intentarlo.", e.getReason());
        verify(vista, never()).fila(anyInt());
    }

    @Test void filaInexistenteEs404() {
        when(dao.porId(5)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, null), super_, null)));
        verify(dao, never()).ponerMarca(anyInt(), any(), any(), any(), any(), any());
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.NO_EXISTE);
        assertEquals(404, estado(() -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, null), super_, null)));
    }

    @Test void historicoEs409() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("HISTORICO")));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, null), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(FilasController.MSG_HISTORICO, e.getReason());
        verify(dao, never()).ponerMarca(anyInt(), any(), any(), any(), any(), any());
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.CERRADA);
        assertEquals(409, estado(() -> controller.ponerMarca(5, new PeticionMarca("CANCELADO", null, null), super_, null)));
    }

    @Test void elReintentoConLaMismaClaveNoVuelveAEscribir() {
        filaExiste();
        when(dao.ponerMarca(anyInt(), any(), any(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe("CANCELADO", "a")));
        controller.ponerMarca(5, new PeticionMarca("CANCELADO", "a", null), super_, "clave-m");
        controller.ponerMarca(5, new PeticionMarca("CANCELADO", "a", null), super_, "clave-m");
        verify(dao, times(1)).ponerMarca(5, "CANCELADO", "a", null, 7, AHORA);
    }
}
