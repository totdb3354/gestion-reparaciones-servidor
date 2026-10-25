package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3) con todo mockeado y la hora fija (25/10/2026 12:31 en Madrid). */
class PorPasarNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 12, 31);
    private static final String IMEI = "350000000000001";
    private final TallerNsys taller = mock(TallerNsys.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final HechoDAO hechos = mock(HechoDAO.class);
    private final LogDAO registro = mock(LogDAO.class);
    private final PorPasarNsys servicio = new PorPasarNsys(taller, tallerDao, nsys, hechos, registro,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static HttpStatusCode estado(Executable accion) {
        return assertThrows(ResponseStatusException.class, accion).getStatusCode();
    }

    private static TallerNsys.MovilPorPasar soloGrado(String imei) {
        return new TallerNsys.MovilPorPasar(movil(imei, "Azul", "B"), new LoQueVale.Valor("Azul", "A100", null, null, "B", null, true),
                List.of(vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))), null);
    }

    private static AvisoTaller sinGrado() {
        return new AvisoTaller(5, IMEI, "R20261024_19", AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "ID", null,
                "chasis chai13blue nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void lasTarjetasJuntanLoDelTallerLasAltasYLosAvisos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI, "F2LXABC123"));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI, "f2lxabc123", "350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14", "R20261006_57",
                false, "Técnico 2", null, 1, AHORA.minusDays(19))));
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinGrado()));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.color().isEmpty());
        assertEquals(List.of(IMEI), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
        assertEquals(List.of("350000000000088"), t.altas().stream().map(FilaAltaNsys::imeiSn).toList(),
                "un número de serie en minúsculas cruza con el del espejo");
        assertEquals("13", t.revisar().get(0).modelo());
    }

    @Test void sinImeisFueraDeNsysNiAvisosNoSeLeeNadaMas() {
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.altas().isEmpty());
        assertTrue(t.revisar().isEmpty());
        verify(nsys, never()).altas(any());
        verify(nsys, never()).porImeis(any());
    }

    @Test void marcarLimpiaLosImeisYPoneLaHoraDeMadrid() {
        servicio.marcar("GRADO", List.of(" 350000000000001 ", IMEI, "350000000000002"), 7);
        verify(hechos).marcar(TarjetaHecho.GRADO, List.of(IMEI, "350000000000002"), 7, AHORA);
    }

    @Test void marcarRechazaLoQueNoVale() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("REVISAR", List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar(null, List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", List.of(), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", Arrays.asList(IMEI, " "), 7)));
        List<String> muchos = new ArrayList<>();
        for (int n = 0; n < 501; n++) muchos.add(String.format("3500000000%05d", n));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", muchos, 7)));
        verifyNoInteractions(hechos);
    }

    @Test void deshacerQuitaLaMarcaDeEsaTarjeta() {
        servicio.desmarcar("ALTA", " 350000000000088 ");
        verify(hechos).desmarcar(TarjetaHecho.ALTA, "350000000000088");
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("ALTA", " ")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("OTRA", IMEI)));
    }

    @Test void revisadoApuntaQuienYCuandoYLoDejaEnElRegistro() {
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(1);
        servicio.revisado(5, 7);
        verify(registro).insertar(7, "REVISAR_AVISO_TALLER", "IMEI: 350000000000001, SIN_GRADO de R20261024_19", null);
    }

    @Test void revisadoDeUnAvisoQueNoExisteOYaEstaRevisado() {
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.revisado(9, 7)));
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(5, 7)));
        verifyNoInteractions(registro);
    }

    @Test void elRepasoSinMarcasNoLeeNadaMas() {
        servicio.repasarHechos(AHORA);
        verifyNoInteractions(taller, nsys);
        verify(hechos, never()).repasar(any(), any(), any());
    }

    @Test void elRepasoBorraYHaceVolverConLaHoraDeAhora() {
        HechoTaller seFue = new HechoTaller("350000000000009", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        HechoTaller sigue = new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        when(hechos.todas()).thenReturn(List.of(seFue, sigue));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos).repasar(List.of(seFue), List.of(sigue), AHORA);
    }

    @Test void unRepasoSinNadaQueHacerNoEscribe() {
        when(hechos.todas()).thenReturn(List.of(new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA, null)));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos, never()).repasar(any(), any(), any());
    }
}
