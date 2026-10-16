package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Una pasada de importación con Drive y la base de datos mockeados; los .xlsx se leen de verdad (spec pieza 1 §5). */
class ImportadorNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 9, 5);
    private final ClienteDrive drive = mock(ClienteDrive.class);
    private final NsysDAO dao = mock(NsysDAO.class);
    private final ImportadorNsys importador = new ImportadorNsys(drive, dao, "carpeta-1", "maestro-1",
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() throws Exception {
        when(dao.empezarImportacion(anyString(), any(), anyString(), any())).thenReturn(1, 2, 3);
        when(dao.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));
        when(dao.cargarMoviles()).thenReturn(Map.of());
        // Maestro sin cambios: el último que se cargó es más reciente que el de Drive.
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-15T08:00:00Z")));
        when(dao.ultimaImportacion("MAESTRO", Set.of("OK", "RECHAZADO"))).thenReturn(Optional.of(
                new Importacion(9, "Maestro.xlsx", LocalDateTime.of(2026, 10, 15, 10, 0), "OK", 23000, null)));
    }

    private static ClienteDrive.FicheroDrive enDrive(String nombre) {
        return new ClienteDrive.FicheroDrive("id-" + nombre, nombre, Instant.parse("2026-10-16T07:00:00Z"));
    }

    private static byte[] items(int filas) throws IOException {
        List<List<Object>> lista = new ArrayList<>();
        for (int i = 0; i < filas; i++)
            lista.add(XlsxDePrueba.fila(String.format("3500000000%05d", i), "Apple iPhone 13 128GB Blue", "A", "Available",
                    "BT-9001", "CAJA LISTOS", "9/2/2026", null));
        return XlsxDePrueba.items(lista);
    }

    private void ultimaActual(LocalDateTime fecha, int filas) {
        Optional<Importacion> ultima = Optional.of(new Importacion(5, "previo", fecha, "OK", filas, null));
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK", "RECHAZADO"))).thenReturn(ultima);
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(ultima);
    }

    @Test void laPrimeraVezSoloImportaElMasRecienteComoPrimeraCarga() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_08.31.xlsx"),
                enDrive("Items_2026.10.15_17.01.xlsx"), enDrive("otro.xlsx"), enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn(items(2));

        importador.pasada();

        verify(drive, times(1)).descargar(anyString());
        verify(dao).empezarImportacion("Items_2026.10.16_09.01.xlsx", LocalDateTime.of(2026, 10, 16, 9, 1), "ACTUAL", AHORA);
        ArgumentCaptor<ResultadoComparacion> r = ArgumentCaptor.forClass(ResultadoComparacion.class);
        verify(dao).aplicar(r.capture(), eq(1));
        assertEquals(2, r.getValue().aGuardar().size());
        assertTrue(r.getValue().eventos().stream().allMatch(e -> e.tipo() == TipoEvento.ENTRADA && e.soloDia()));
        verify(dao).rellenarModelosTelefono();
        verify(dao).terminarImportacion(1, "OK", 2, 2, null, AHORA);
    }

    @Test void procesaLosPendientesEnOrden() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.31.xlsx"),
                enDrive("Items_2026.10.16_08.01.xlsx"), enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(drive);
        orden.verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        orden.verify(drive).descargar("id-Items_2026.10.16_09.31.xlsx");
        verify(drive, never()).descargar("id-Items_2026.10.16_08.01.xlsx");
        verify(dao, times(2)).aplicar(any(), anyInt());
    }

    @Test void unFicheroConMenosDel90PorCientoSeRechazaYSeSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 100);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn(items(50));
        when(drive.descargar("id-Items_2026.10.16_09.31.xlsx")).thenReturn(items(95));

        importador.pasada();

        verify(dao).terminarImportacion(1, "RECHAZADO", 50, 0,
                "Trae 50 filas frente a las 100 de la última importación: se trata como incompleto", AHORA);
        verify(dao, never()).aplicar(any(), eq(1));
        verify(dao).aplicar(any(), eq(2));
        verify(dao).terminarImportacion(eq(2), eq("OK"), eq(95), anyInt(), any(), eq(AHORA));
    }

    @Test void unFormatoMaloSeRechazaYSeSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn("roto".getBytes(StandardCharsets.UTF_8));
        when(drive.descargar("id-Items_2026.10.16_09.31.xlsx")).thenReturn(items(2));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("RECHAZADO"), eq(0), eq(0), contains("no es un .xlsx válido"), eq(AHORA));
        verify(dao).terminarImportacion(eq(2), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
    }

    @Test void unFalloDeDriveDejaErrorYParaLaPasada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenThrow(new IOException("Drive respondió 500 a x"));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("ERROR"), eq(0), eq(0), contains("Drive respondió 500"), eq(AHORA));
        verify(drive, never()).descargar("id-Items_2026.10.16_09.31.xlsx");
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void unMaestroNuevoSeRecargaYSinItemsNuevosSeRetraduce() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00.250Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        ProductoMaestro azul = new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13", "13",
                "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false);
        when(dao.cargarProductos()).thenReturn(List.of(azul));
        MovilNsys sinSku = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null, null, null, null, null, null, null, false,
                "Listos", "Listos", EstadoMovil.STOCK, AHORA.minusDays(10), AHORA.minusDays(10));
        when(dao.cargarMoviles()).thenReturn(Map.of(sinSku.clave(), sinSku));
        ultimaActual(LocalDateTime.of(2026, 10, 16, 9, 1), 1);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));

        importador.pasada();

        verify(dao).empezarImportacion("Maestro.xlsx", LocalDateTime.of(2026, 10, 16, 9, 0), "MAESTRO", AHORA);
        verify(dao).reemplazarProductos(List.of(azul));
        verify(dao).terminarImportacion(1, "OK", 1, 0, null, AHORA);
        ArgumentCaptor<ResultadoComparacion> r = ArgumentCaptor.forClass(ResultadoComparacion.class);
        verify(dao).aplicar(r.capture(), isNull());
        assertEquals("i13128AzulA", r.getValue().aGuardar().get(0).sku());
        assertTrue(r.getValue().eventos().isEmpty());
    }

    @Test void unMaestroSinCambiosNoSeDescarga() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());
        importador.pasada();
        verify(drive, never()).descargar("maestro-1");
        verify(dao, never()).reemplazarProductos(any());
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void sinCarpetaNoHaceNada() {
        new ImportadorNsys(drive, dao, "", "", Clock.systemUTC()).pasada();
        verifyNoInteractions(drive);
        verifyNoInteractions(dao);
    }

    private static ClienteDrive.FicheroDrive subidoA(String nombre, String instante) {
        return new ClienteDrive.FicheroDrive("id-" + nombre, nombre, Instant.parse(instante));
    }

    @Test void unFicheroRecienSubidoEsperaALaSiguientePasada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        // AHORA = 07:05Z; modificado a las 07:04Z: hace 1 minuto.
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(subidoA("Items_2026.10.16_09.01.xlsx", "2026-10-16T07:04:00Z")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();
        verify(drive, never()).descargar(anyString());
        verify(dao, never()).empezarImportacion(anyString(), any(), anyString(), any());

        ImportadorNsys tresMinutosDespues = new ImportadorNsys(drive, dao, "carpeta-1", "maestro-1",
                Clock.fixed(AHORA.plusMinutes(3).atZone(MADRID).toInstant(), MADRID));
        tresMinutosDespues.pasada();
        verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
    }

    @Test void laPrimeraVezTampocoTocaUnFicheroRecienSubido() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(subidoA("Items_2026.10.16_09.01.xlsx", "2026-10-16T07:04:30Z")));
        importador.pasada();
        verify(drive, never()).descargar(anyString());
    }

    @Test void conDosPendientesSoloSeProcesaElAnteriorAlRecienSubido() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(
                subidoA("Items_2026.10.16_09.31.xlsx", "2026-10-16T07:04:00Z"),
                subidoA("Items_2026.10.16_09.01.xlsx", "2026-10-16T07:00:00Z")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        verify(drive, never()).descargar("id-Items_2026.10.16_09.31.xlsx");
        verify(dao, times(1)).aplicar(any(), anyInt());
    }

    @Test void trasUnFicheroFrenadoUnoSimilarSeAcepta() throws Exception {
        // Escala 1:1000 de 70.000 -> 60.000 -> 60.500.
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK", "RECHAZADO"))).thenReturn(Optional.of(
                new Importacion(5, "previo", LocalDateTime.of(2026, 10, 16, 8, 31), "RECHAZADO", 60, "frenado")));
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(4, "previo", LocalDateTime.of(2026, 10, 16, 8, 1), "OK", 70, null)));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(61));

        importador.pasada();

        verify(dao).aplicar(any(), eq(1));
        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(61), anyInt(), any(), eq(AHORA));
    }

    @Test void unaBajadaGrandeTrasUnOkSigueRechazada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 70);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(30));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("RECHAZADO"), eq(30), eq(0), anyString(), eq(AHORA));
        verify(dao, never()).aplicar(any(), any());
    }

    @Test void conMaestroNuevoEItemsPendientesNoSeRetraduce() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar("id-Items_2026.10.16_09.01.xlsx")).thenReturn(items(2));

        importador.pasada();

        verify(dao).reemplazarProductos(any());
        verify(dao).aplicar(any(), anyInt());
        verify(dao, never()).aplicar(any(), isNull());
    }

    @Test void laFechaSaleDelNombre() {
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 16, 9, 1)), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.13.40_25.99.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01 (1).xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre(null));
    }
}
