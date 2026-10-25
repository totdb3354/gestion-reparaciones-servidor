package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.io.IOException;
import java.math.BigDecimal;
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
    private final TallerNsys taller = mock(TallerNsys.class);
    private final ImportadorNsys importador = new ImportadorNsys(drive, dao, taller, "carpeta-1", "maestro-1",
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
                "Trae 50 filas frente a las 100 del fichero anterior: se trata como incompleto", AHORA);
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
        verify(taller, never()).cerrar();
    }

    @Test void sinCarpetaNoHaceNada() {
        new ImportadorNsys(drive, dao, taller, "", "", Clock.systemUTC()).pasada();
        verifyNoInteractions(drive);
        verifyNoInteractions(dao);
        verifyNoInteractions(taller);
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

        ImportadorNsys tresMinutosDespues = new ImportadorNsys(drive, dao, taller, "carpeta-1", "maestro-1",
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
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul"));
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

    @Test void unRecienSubidoRetieneTambienALosPosterioresAunqueEstenAsentados() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(
                subidoA("Items_2026.10.16_09.31.xlsx", "2026-10-16T07:00:00Z"),
                subidoA("Items_2026.10.16_09.01.xlsx", "2026-10-16T07:04:00Z")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();
        verify(drive, never()).descargar(anyString());
        verify(dao, never()).empezarImportacion(anyString(), any(), anyString(), any());

        new ImportadorNsys(drive, dao, taller, "carpeta-1", "maestro-1",
                Clock.fixed(AHORA.plusMinutes(3).atZone(MADRID).toInstant(), MADRID)).pasada();
        InOrder orden = inOrder(drive);
        orden.verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        orden.verify(drive).descargar("id-Items_2026.10.16_09.31.xlsx");
    }

    @Test void unYaImportadoConHoraRecienteNoBloqueaALosPendientes() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(
                subidoA("Items_2026.10.16_08.31.xlsx", "2026-10-16T07:04:30Z"),
                subidoA("Items_2026.10.16_09.01.xlsx", "2026-10-16T07:00:00Z")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        verify(drive, never()).descargar("id-Items_2026.10.16_08.31.xlsx");
    }

    @Test void unMaestroSinProductosSeRechazaYNoVaciaElCatalogo() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());

        importador.pasada();

        verify(dao).terminarImportacion(1, "RECHAZADO", 0, 0, "El maestro no trae productos", AHORA);
        verify(dao, never()).reemplazarProductos(any());
    }

    /** {@code filas} filas, de las que las {@code repetidas} últimas repiten IMEI/SN y lote de la primera. */
    private static byte[] itemsConRepetidas(int filas, int repetidas) throws IOException {
        List<List<Object>> lista = new ArrayList<>();
        for (int i = 0; i < filas; i++)
            lista.add(XlsxDePrueba.fila(String.format("3500000000%05d", i < filas - repetidas ? i : 0),
                    "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null));
        return XlsxDePrueba.items(lista);
    }

    @Test void unasPocasFilasRepetidasSeDescartanYSeApuntan() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 300);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(itemsConRepetidas(300, 2));

        importador.pasada();

        ArgumentCaptor<ResultadoComparacion> r = ArgumentCaptor.forClass(ResultadoComparacion.class);
        verify(dao).aplicar(r.capture(), eq(1));
        assertEquals(298, r.getValue().aGuardar().size());
        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(300), anyInt(), eq("2 filas repetidas descartadas"), eq(AHORA));
    }

    @Test void conMasDelUnoPorCientoDeFilasRepetidasSeRechazaSinAplicarNada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 100);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(itemsConRepetidas(100, 2));

        importador.pasada();

        verify(dao).terminarImportacion(1, "RECHAZADO", 100, 0,
                "2 de las 100 filas repiten IMEI/SN y lote: se trata como un fichero mal leído", AHORA);
        verify(dao, never()).aplicar(any(), any());
        verify(dao, never()).rellenarModelosTelefono();
    }

    @Test void unMaestroQueLaBaseDeDatosNoAdmiteSeRechazaYNoSeReintenta() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        doThrow(new DataIntegrityViolationException("PreparedStatementCallback; SQL [INSERT INTO Nsys_producto ...]",
                new java.sql.SQLDataException("Data too long for column 'COLOR' at row 1")))
                .when(dao).reemplazarProductos(any());
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());

        importador.pasada();

        // RECHAZADO cuenta como resuelto: el mismo maestro no se vuelve a descargar en cada pasada.
        verify(dao).terminarImportacion(1, "RECHAZADO", 0, 0,
                "La base de datos no admite el maestro: Data too long for column 'COLOR' at row 1", AHORA);
    }

    private static UncategorizedSQLException colacionesMezcladas() {
        return new UncategorizedSQLException("rellenar modelos", NsysDAO.SQL_RELLENAR_MODELOS, new java.sql.SQLException(
                "Illegal mix of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_unicode_ci,IMPLICIT) for operation '='"));
    }

    @Test void siFallaElRellenoDeModelosDelTallerLaImportacionSigueOk() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        when(dao.rellenarModelosTelefono()).thenThrow(colacionesMezcladas());

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), eq("Modelos del taller no rellenados: Illegal mix"
                + " of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_unicode_ci,IMPLICIT) for operation '='"), eq(AHORA));
        verify(dao).aplicar(any(), eq(2));
        verify(dao).terminarImportacion(eq(2), eq("OK"), eq(2), anyInt(), startsWith("Modelos del taller no rellenados"), eq(AHORA));
        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
    }

    @Test void siFallaElRellenoDeModelosAlRetraducirLaPasadaNoRevienta() throws Exception {
        when(drive.metadatos("maestro-1")).thenReturn(new ClienteDrive.FicheroDrive("maestro-1", "Maestro.xlsx",
                Instant.parse("2026-10-16T07:00:00Z")));
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        MovilNsys guardado = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available",
                "CAJA LISTOS", "Proveedor A", LocalDate.of(2026, 9, 2), null, null, null, null, null, null, null, false,
                "Listos", "Listos", EstadoMovil.STOCK, AHORA.minusDays(10), AHORA.minusDays(10));
        when(dao.cargarMoviles()).thenReturn(Map.of(guardado.clave(), guardado));
        ultimaActual(LocalDateTime.of(2026, 10, 16, 9, 1), 1);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(dao.rellenarModelosTelefono()).thenThrow(colacionesMezcladas());

        assertDoesNotThrow(importador::pasada);

        verify(dao).aplicar(any(), isNull());
        verify(dao).rellenarModelosTelefono();
    }

    @Test void unFicheroConFechaDeMasDeUnDiaEnElFuturoSeSalta() throws Exception {
        // Un PC con el reloj adelantado: si se importara, el puntero «último procesado» se quedaría en el futuro y
        // ningún fichero real volvería a entrar, mientras el aviso dice que todo va bien.
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.17_09.06.xlsx")));                  // AHORA + 1 día y 1 minuto
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        verify(drive, never()).descargar("id-Items_2026.10.17_09.06.xlsx");
        verify(dao, never()).empezarImportacion(eq("Items_2026.10.17_09.06.xlsx"), any(), anyString(), any());
    }

    @Test void laPrimeraVezTampocoTomaUnFicheroDelFuturo() throws Exception {
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.20_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        verify(drive).descargar("id-Items_2026.10.16_09.01.xlsx");
        verify(drive, times(1)).descargar(anyString());
    }

    @Test void laFechaSaleDelNombre() {
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 16, 9, 1)), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.13.40_25.99.xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre("Items_2026.10.16_09.01 (1).xlsx"));
        assertEquals(Optional.empty(), ImportadorNsys.fechaDelNombre(null));
    }

    // ---------------------------------------------------------------- 0.9.21: cierre de lo del taller (spec §2.2)

    @Test void trasCadaItemsOkSeCierraLoDelTaller() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(dao, taller);
        orden.verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        orden.verify(taller).cerrar();
        orden.verify(dao).terminarImportacion(eq(2), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        orden.verify(taller).cerrar();
    }

    @Test void unItemsRechazadoNoCierraNada() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 70);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(30));

        importador.pasada();

        verify(taller, never()).cerrar();
    }

    @Test void siElCierreFallaLaImportacionSigueOkYSigueConElSiguiente() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        when(taller.cerrar()).thenThrow(new RuntimeException("la base de datos no responde"));

        importador.pasada();

        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
        verify(dao, times(2)).aplicar(any(), anyInt());
    }

    @Test void sinNombresEnElMaestroGuardadoSeRecargaUnaVezAunqueNoHayaCambiado() throws Exception {
        when(dao.productosSinNombre()).thenReturn(true);
        List<List<Object>> hoja = new ArrayList<>();
        List<Object> cabecera = new ArrayList<>(LectorMaestro.COLUMNAS);
        cabecera.add(LectorMaestro.NOMBRE);
        hoja.add(cabecera);
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul", "Apple iPhone 13 128GB Blue"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());

        importador.pasada();
        importador.pasada();

        verify(drive, times(1)).descargar("maestro-1");
        verify(dao).reemplazarProductos(List.of(new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13",
                "13", "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false, "Apple iPhone 13 128GB Blue")));
        verify(dao, times(1)).productosSinNombre();
    }

    // ---------------------------------------------------------------- 0.9.22: coste de cada móvil

    @SuppressWarnings("unchecked")
    @Test void trasGuardarLaFotoSeGuardanLosCostesDelFichero() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(dao);
        orden.verify(dao).aplicar(any(), eq(1));
        ArgumentCaptor<List<FilaItems>> filas = ArgumentCaptor.forClass(List.class);
        orden.verify(dao).guardarCostes(filas.capture());
        assertEquals(2, filas.getValue().size());
        assertEquals(new BigDecimal("123.45"), filas.getValue().get(0).coste());
        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), isNull(), eq(AHORA));
    }

    @SuppressWarnings("unchecked")
    @Test void siFallanLosCostesLaImportacionSigueOk() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        when(dao.guardarCostes(any())).thenThrow(new UncategorizedSQLException("costes", "UPDATE Nsys_movil",
                new java.sql.SQLException("fallo")));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
    }
}
