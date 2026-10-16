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
        verify(dao, never()).cerrarHistorico(any(), anyInt(), anyInt(), any());

        historico.paso();
        ArgumentCaptor<List<EventoNsys>> eventos = ArgumentCaptor.forClass(List.class);
        verify(dao).cerrarHistorico(eventos.capture(), eq(12), eq(1), any());
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
        verify(dao).cerrarHistorico(argThat(l -> l.size() == 1), eq(11), eq(1), any());
    }

    @Test void alAcabarNoVuelveAMirarDrive() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(PRIMERO, SEGUNDO, ACTUAL));
        HistoricoNsys historico = a(23, 30);
        historico.paso();
        historico.paso();
        verify(drive, times(1)).listarItems("carpeta-1");
        verify(drive, never()).descargar(anyString());
    }

    private static byte[] conFilas(int n, String caja) throws IOException {
        List<List<Object>> filas = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++)
            filas.add(XlsxDePrueba.fila(String.format("35000000001%04d", i), "Apple iPhone 13 128GB Blue", "C100",
                    "InProcess", "BT-9001", caja, "9/2/2026", null));
        return XlsxDePrueba.items(filas);
    }

    @SuppressWarnings("unchecked")
    @Test void elFrenoCompruebaContraElFicheroAnteriorYNoSoloContraElUltimoOk() throws Exception {
        String tercero = "Items_2026.10.01_10.01.xlsx";
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive(PRIMERO), enDrive(SEGUNDO), enDrive(tercero),
                enDrive(ACTUAL)));
        when(drive.descargar("id-" + PRIMERO)).thenReturn(conFilas(70, "CAJA GLASS"));
        when(drive.descargar("id-" + SEGUNDO)).thenReturn(conFilas(60, "CAJA LISTOS"));
        when(drive.descargar("id-" + tercero)).thenReturn(conFilas(61, "CAJA LISTOS"));
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(), Set.of(PRIMERO), Set.of(PRIMERO, SEGUNDO));
        Importacion primero = new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 70, null);
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.empty(), Optional.of(primero),
                Optional.of(primero));
        HistoricoNsys historico = a(23, 30);

        historico.paso();
        historico.paso();
        verify(dao).terminarImportacion(eq(12), eq("RECHAZADO"), eq(60), eq(0), anyString(), any());
        historico.paso();

        ArgumentCaptor<List<EventoNsys>> eventos = ArgumentCaptor.forClass(List.class);
        verify(dao).cerrarHistorico(eventos.capture(), eq(13), eq(61), any());
        assertTrue(eventos.getValue().stream().anyMatch(e -> "Taller · Glass".equals(e.de())));
    }

    @Test void siElCierreFallaNoAvanzaLaFotoYElSiguientePasoRecalculaElMismoFichero() throws Exception {
        when(dao.ficherosProcesados("HISTORICO")).thenReturn(Set.of(), Set.of(PRIMERO));
        when(dao.ultimaImportacion("HISTORICO", Set.of("OK"))).thenReturn(Optional.empty(),
                Optional.of(new Importacion(11, PRIMERO, LocalDateTime.of(2026, 10, 1, 9, 1), "OK", 1, null)));
        doThrow(new IllegalStateException("BD caida")).doNothing().when(dao)
                .cerrarHistorico(any(), anyInt(), anyInt(), any());
        HistoricoNsys historico = a(23, 30);

        historico.paso();
        historico.paso();
        historico.paso();

        @SuppressWarnings("unchecked") ArgumentCaptor<List<EventoNsys>> eventos = ArgumentCaptor.forClass(List.class);
        verify(dao, times(2)).cerrarHistorico(eventos.capture(), anyInt(), eq(1), any());
        assertEquals(1, eventos.getAllValues().get(0).size());
        assertEquals(eventos.getAllValues().get(0), eventos.getAllValues().get(1));
        verify(drive, times(1)).descargar("id-" + PRIMERO);
    }

    @Test void unaCaidaDeLaBdAlPrincipioNoSale() {
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenThrow(new IllegalStateException("BD caida"));
        assertDoesNotThrow(() -> a(23, 30).paso());
    }
}
