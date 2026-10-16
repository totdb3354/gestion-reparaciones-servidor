package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El histórico (spec pieza 1 §6): de noche, un fichero por paso, solo eventos y reanudable. */
class HistoricoNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String PRIMERO = "Items_2026.10.01_09.01.xlsx";
    private static final String SEGUNDO = "Items_2026.10.01_09.31.xlsx";
    private static final String ACTUAL = "Items_2026.10.16_09.01.xlsx";
    private final ClienteDrive drive = mock(ClienteDrive.class);
    private final NsysDAO dao = mock(NsysDAO.class);

    private HistoricoNsys a(int hora, int minuto) {
        LocalDateTime cuando = LocalDateTime.of(2026, 10, 16, hora, minuto);
        return new HistoricoNsys(drive, dao, "carpeta-1", Clock.fixed(cuando.atZone(MADRID).toInstant(), MADRID));
    }

    private static ClienteDrive.FicheroDrive enDrive(String nombre) {
        return new ClienteDrive.FicheroDrive("id-" + nombre, nombre, Instant.parse("2026-10-16T07:00:00Z"));
    }

    private static byte[] unMovilEn(String caja) throws IOException {
        return XlsxDePrueba.items(List.of(XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "C100",
                "InProcess", "BT-9001", caja, "9/2/2026", null)));
    }

    @BeforeEach void base() throws Exception {
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(1, ACTUAL, LocalDateTime.of(2026, 10, 16, 9, 1), "OK", 1, null)));
        when(dao.empezarImportacion(anyString(), any(), anyString(), any())).thenReturn(11, 12, 13);
        when(dao.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA GLASS", "Taller · Glass", true, false),
                new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive(ACTUAL), enDrive(SEGUNDO), enDrive(PRIMERO),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-" + PRIMERO)).thenReturn(unMovilEn("CAJA GLASS"));
        when(drive.descargar("id-" + SEGUNDO)).thenReturn(unMovilEn("CAJA LISTOS"));
    }

    @Test void soloDeNoche() {
        assertTrue(HistoricoNsys.deNoche(LocalTime.of(23, 0)));
        assertTrue(HistoricoNsys.deNoche(LocalTime.of(6, 59)));
        assertFalse(HistoricoNsys.deNoche(LocalTime.of(7, 0)));
        assertFalse(HistoricoNsys.deNoche(LocalTime.of(22, 59)));
    }

    @Test void deDiaNoHaceNada() {
        a(12, 0).paso();
        verifyNoInteractions(drive, dao);
    }

    @Test void sinLaPrimeraImportacionNormalEspera() {
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.empty());
        a(23, 30).paso();
        verifyNoInteractions(drive);
    }

    @SuppressWarnings("unchecked")
    @Test void elPrimerFicheroEsElPuntoDePartidaYElSiguienteApuntaCambios() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(), Set.of(PRIMERO));
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.empty(),
                Optional.of(new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 1, null)));
        HistoricoNsys historico = a(23, 30);

        historico.paso();
        verify(dao).terminarImportacion(eq(11), eq("OK"), eq(1), eq(0), eq("Punto de partida del histórico"), any());
        verify(dao, never()).insertarEventos(any(), any());

        historico.paso();
        ArgumentCaptor<List<EventoNsys>> eventos = ArgumentCaptor.forClass(List.class);
        verify(dao).insertarEventos(eventos.capture(), eq(12));
        assertEquals(List.of(new EventoNsys("350000000000001", "BT-9001", LocalDateTime.of(2026, 10, 1, 9, 31), false,
                TipoEvento.UBICACION, "Taller · Glass", "Listos", "CAJA LISTOS")), eventos.getValue());
        verify(drive, times(1)).descargar("id-" + PRIMERO);
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void trasReiniciarRecargaLaFotoDelUltimoFicheroHecho() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(PRIMERO));
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 1, null)));

        a(2, 0).paso();

        InOrder orden = inOrder(drive);
        orden.verify(drive).descargar("id-" + PRIMERO);
        orden.verify(drive).descargar("id-" + SEGUNDO);
        verify(dao).insertarEventos(argThat(l -> l.size() == 1), eq(11));
    }

    @Test void alAcabarNoVuelveAMirarDrive() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(PRIMERO, SEGUNDO, ACTUAL));
        HistoricoNsys historico = a(23, 30);
        historico.paso();
        historico.paso();
        verify(drive, times(1)).listarItems("carpeta-1");
        verify(drive, never()).descargar(anyString());
    }
}
