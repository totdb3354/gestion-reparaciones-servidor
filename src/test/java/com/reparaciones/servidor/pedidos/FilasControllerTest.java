package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FilasControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final String IMEI = "350000000000001";
    private final VistaFilas vista = mock(VistaFilas.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final FilasController controller = new FilasController(vista, dao, new RegistroIdempotencia(),
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    private static FilaPedido fila(String origen, LocalDateTime cerradaEn) {
        return new FilaPedido(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null, "402-0000000-0000005",
                LocalDate.of(2026, 11, 4), origen, Canal.AMAZON, 1, cerradaEn);
    }

    private static FilaVista vistaDe(String imei) {
        return new FilaVista(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", imei, "402-0000000-0000005",
                LocalDate.of(2026, 11, 4), "PENDIENTE", true, null, null, null, null, false, "OK", null, "i13128AzulA", "Listos", "AMAZON");
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    // ---------------------------------------------------------------- GET /filas

    @Test void buscarConUnSoloCaracterEs400() {
        assertEquals(400, estado(() -> controller.filas(null, null, "a")));
        assertEquals(400, estado(() -> controller.filas(null, null, "  a ")));
        verifyNoInteractions(vista);
    }

    @Test void buscarSeRecortaYSinBuscarPasaElPeriodo() {
        ListaFilas vacia = new ListaFilas(List.of(), false);
        when(vista.filas(any(), any(), any())).thenReturn(vacia);

        assertSame(vacia, controller.filas(null, null, "  amz "));
        verify(vista).filas(null, null, "amz");

        controller.filas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "   ");
        verify(vista).filas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "");
    }

    // ---------------------------------------------------------------- recomendados

    @Test void recomendadosDeUnaFilaInexistenteEs404() {
        when(vista.recomendados(99)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.recomendados(99)));
    }

    @Test void recomendadosDevuelveLosCandidatos() {
        List<Recomendador.Candidato> c = List.of(new Recomendador.Candidato(IMEI, "i13128AzulA", "Listos", 1));
        when(vista.recomendados(5)).thenReturn(Optional.of(c));
        assertEquals(c, controller.recomendados(5));
    }

    // ---------------------------------------------------------------- cambios

    @Test void cambiosDeUnaFilaQueNoExisteEs404() {
        when(dao.porId(99)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.cambios(99)));
        verify(dao, never()).cambiosDeFila(anyInt());
    }

    @Test void cambiosDeUnaFilaDelHistoricoEsVaciaSinConsultarCambios() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("HISTORICO", AHORA.minusDays(30))));
        assertEquals(List.of(), controller.cambios(5));
        verify(dao, never()).cambiosDeFila(anyInt());
    }

    @Test void cambiosDeUnaFilaDeShippyProLosDaElDao() {
        List<CambioImei> c = List.of(new CambioImei("super", AHORA, IMEI, null), new CambioImei(null, AHORA.minusDays(1), null, IMEI));
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", null)));
        when(dao.cambiosDeFila(5)).thenReturn(c);
        assertEquals(c, controller.cambios(5));
    }

    // ---------------------------------------------------------------- PUT /filas/{id}/imei

    @Test void unImeiQueNoSon15CifrasEs400SinTocarNada() {
        for (String malo : List.of("123", "35000000000000A", "3500000000000011", "3500 0000 0000 01"))
            assertEquals(400, estado(() -> controller.ponerImei(5, new PeticionImei(malo), super_, null)), malo);
        verify(dao, never()).ponerImei(anyInt(), any(), any(), any());
    }

    @Test void unaFilaQueNoExisteEs404() {
        when(dao.porId(5)).thenReturn(Optional.empty());
        assertEquals(404, estado(() -> controller.ponerImei(5, new PeticionImei(IMEI), super_, null)));
        verify(dao, never()).ponerImei(anyInt(), any(), any(), any());
    }

    @Test void unaFilaDelHistoricoEs409() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("HISTORICO", AHORA.minusDays(30))));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerImei(5, new PeticionImei(IMEI), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("La fila es del histórico y no se edita", e.getReason());
        verify(dao, never()).ponerImei(anyInt(), any(), any(), any());
    }

    @Test void unaFilaDeShippyProCerradaOEnviadaSiSeEdita() {
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", AHORA.minusDays(1))));
        controller.ponerImei(5, new PeticionImei(IMEI), super_, null);
        when(dao.porId(5)).thenReturn(Optional.of(new FilaPedido(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null,
                "402-0000000-0000005", LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(1),
                AHORA.minusDays(2), "UPSv2", "1Z0000000000000001")));
        controller.ponerImei(5, new PeticionImei(IMEI), super_, null);
        verify(dao, times(2)).ponerImei(5, IMEI, 7, AHORA);
    }

    @Test void siElDaoDiceCerradaTrasLaComprobacionEs409YSiNoExisteEs404() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", null)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.CERRADA);
        assertEquals(409, estado(() -> controller.ponerImei(5, new PeticionImei(IMEI), super_, null)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.NO_EXISTE);
        assertEquals(404, estado(() -> controller.ponerImei(5, new PeticionImei(IMEI), super_, null)));
        verify(vista, never()).fila(anyInt());
    }

    @Test void ponerUnImeiLoRegistraConElUsuarioYDevuelveLaFilaReleida() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", null)));
        FilaVista releida = vistaDe(IMEI);
        when(vista.fila(5)).thenReturn(Optional.of(releida));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);

        FilaVista r = controller.ponerImei(5, new PeticionImei(" " + IMEI + " "), super_, null);

        assertSame(releida, r);
        verify(dao).ponerImei(5, IMEI, 7, AHORA);
    }

    @Test void nuloOEnBlancoQuitaElImei() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", null)));
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(null)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);

        controller.ponerImei(5, new PeticionImei(null), super_, null);
        controller.ponerImei(5, new PeticionImei("   "), super_, null);

        verify(dao, times(2)).ponerImei(5, null, 7, AHORA);
    }

    @Test void elReintentoConLaMismaClaveNoRepiteLaEscritura() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO", null)));
        when(vista.fila(5)).thenReturn(Optional.of(vistaDe(IMEI)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);

        FilaVista primera = controller.ponerImei(5, new PeticionImei(IMEI), super_, "clave-1");
        FilaVista segunda = controller.ponerImei(5, new PeticionImei(IMEI), super_, "clave-1");

        assertSame(primera, segunda);
        verify(dao, times(1)).ponerImei(5, IMEI, 7, AHORA);
    }

    // ---------------------------------------------------------------- fuentes

    @Test void lasFuentesSonLasDeLaVista() {
        List<FuenteVista> f = List.of(new FuenteVista("SHIPPYPRO", null, null, 0, null, null, "SIN_DATOS", null));
        when(vista.fuentes()).thenReturn(f);
        assertSame(f, controller.fuentes());
    }
}
