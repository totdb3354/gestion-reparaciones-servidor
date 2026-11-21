package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ClienteDrive;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El lector programado de los libros de ventas: Drive simulado (sin red), la asociacion simulada y libros sinteticos (datos inventados). */
@SuppressWarnings("unchecked")
class LecturaLibrosTest {

    private static final String AMAZON = "id-libro-amazon";
    private static final String STOCKROOM = "id-libro-stockroom";
    /** 16/11/2026 10:00 en Madrid. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-16T09:00:00Z"), ImportadorNsys.MADRID);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 10, 0);
    /** 15/11/2026 08:30:12.345 UTC = 09:30:12 en Madrid (DATETIME guarda segundos). */
    private static final Instant MODIFICADO = Instant.parse("2026-11-15T08:30:12.345Z");
    private static final LocalDateTime MODIFICADO_MADRID = LocalDateTime.of(2026, 11, 15, 9, 30, 12);

    private final ClienteDrive drive = mock(ClienteDrive.class);
    private final AsociacionAmazon asociacion = mock(AsociacionAmazon.class);
    private final List<Path> descargados = new ArrayList<>();

    private static byte[] libro(String pestana, String... filasImeiPedidoFecha) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(pestana);
            for (int i = 0; i < 5; i++) hoja.createRow(i).createCell(0).setCellValue("relleno");
            Row cab = hoja.createRow(5);
            cab.createCell(0).setCellValue("order id");
            cab.createCell(1).setCellValue("purchase-date");
            cab.createCell(2).setCellValue("IMEI");
            for (int n = 0; n < filasImeiPedidoFecha.length; n += 3) {
                Row r = hoja.createRow(6 + n / 3);
                r.createCell(2).setCellValue(filasImeiPedidoFecha[n]);
                r.createCell(0).setCellValue(filasImeiPedidoFecha[n + 1]);
                r.createCell(1).setCellValue(filasImeiPedidoFecha[n + 2]);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private void enDrive(String id, Instant modificado, byte[] contenido) throws Exception {
        when(drive.metadatos(id)).thenReturn(new ClienteDrive.FicheroDrive(id, "Libro ventas.xlsm", modificado));
        doAnswer(inv -> {
            Path destino = inv.getArgument(1);
            descargados.add(destino);
            Files.write(destino, contenido);
            return null;
        }).when(drive).descargarA(eq(id), any(Path.class));
    }

    private LecturaLibros lectura(String amazon, String stockroom) {
        return new LecturaLibros(drive, asociacion, amazon, stockroom, RELOJ);
    }

    @BeforeEach void sinPasadasPrevias() {
        when(asociacion.libroLeido(any())).thenReturn(null);
    }

    @Test void unLibroQueCambioSeDescargaSeLeeYSeAplicanLosParesDeLosUltimos30Dias() throws Exception {
        enDrive(AMAZON, MODIFICADO, libro(LibroVentas.PESTANA,
                "350000000000001", "402-0000000-0000001", "2026-11-15T10:00:00+00:00",
                "350000000000002", "402-0000000-0000002", "2026-10-16",
                "350000000000003", "402-0000000-0000003", "17/10/2026"));
        lectura(AMAZON, "").pasada();
        verify(asociacion).aplicarLibro(Canal.AMAZON, List.of(
                new PeticionAsociaciones.Par("402-0000000-0000001", "350000000000001"),
                new PeticionAsociaciones.Par("402-0000000-0000003", "350000000000003")), AHORA, MODIFICADO_MADRID);
        verify(asociacion, never()).apuntarError(any(), any(), any());
        assertEquals(1, descargados.size());
        assertFalse(Files.exists(descargados.get(0)), "el temporal se borra");
    }

    @Test void siLaFechaDelLibroNoCambioNoSeDescargaNiSeAplica() throws Exception {
        enDrive(AMAZON, MODIFICADO, libro(LibroVentas.PESTANA));
        when(asociacion.libroLeido(Canal.AMAZON)).thenReturn(MODIFICADO_MADRID);
        lectura(AMAZON, "").pasada();
        verify(drive).metadatos(AMAZON);
        verify(drive, never()).descargarA(anyString(), any());
        verify(asociacion, never()).aplicarLibro(any(), anyList(), any(), any());
        verify(asociacion, never()).apuntarError(any(), any(), any());
    }

    @Test void siElLibroNoCambioYDriveRespondeSeBorraElErrorDeUnaVueltaAnterior() throws Exception {
        enDrive(AMAZON, MODIFICADO, libro(LibroVentas.PESTANA));
        when(asociacion.libroLeido(Canal.AMAZON)).thenReturn(MODIFICADO_MADRID);
        lectura(AMAZON, "").pasada();
        verify(asociacion).borrarError(Canal.AMAZON);
        verify(drive, never()).descargarA(anyString(), any());
    }

    @Test void siDriveFallaNoSeBorraElError() throws Exception {
        when(drive.metadatos(AMAZON)).thenThrow(new IOException("Network is unreachable"));
        lectura(AMAZON, "").pasada();
        verify(asociacion, never()).borrarError(any());
        verify(asociacion).apuntarError(eq(Canal.AMAZON), startsWith("no se pudo leer el libro de Drive"), eq(AHORA));
    }

    @Test void sinLaPestanaSeApuntaElErrorYNoSeTocaNada() throws Exception {
        enDrive(STOCKROOM, MODIFICADO, libro("Hoja1", "350000000000001", "402-0000000-0000001", "2026-11-15"));
        lectura("", STOCKROOM).pasada();
        verify(asociacion, never()).aplicarLibro(any(), anyList(), any(), any());
        verify(asociacion).apuntarError(Canal.AMAZON_STOCKROOM, "falta la pestaña Paso 1. Meter IMEI", AHORA);
        assertFalse(Files.exists(descargados.get(0)), "el temporal se borra tambien con error");
    }

    @Test void sinUnaCabeceraSeApuntaElError() throws Exception {
        byte[] sinImei;
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Row cab = libro.createSheet(LibroVentas.PESTANA).createRow(5);
            cab.createCell(0).setCellValue("order id");
            cab.createCell(1).setCellValue("purchase-date");
            libro.write(salida);
            sinImei = salida.toByteArray();
        }
        enDrive(AMAZON, MODIFICADO, sinImei);
        lectura(AMAZON, "").pasada();
        verify(asociacion).apuntarError(Canal.AMAZON, "falta la columna IMEI en Paso 1. Meter IMEI", AHORA);
        verify(asociacion, never()).aplicarLibro(any(), anyList(), any(), any());
    }

    @Test void siDriveFallaSeApuntaElErrorYElOtroCanalSeLeeIgual() throws Exception {
        when(drive.metadatos(AMAZON)).thenThrow(new IOException("Drive respondió 404 a https://www.googleapis.com/drive/v3/files/x"));
        enDrive(STOCKROOM, MODIFICADO, libro(LibroVentas.PESTANA, "350000000000001", "402-0000000-0000001", "2026-11-15"));
        lectura(AMAZON, STOCKROOM).pasada();
        verify(asociacion).apuntarError(eq(Canal.AMAZON), startsWith("no se pudo leer el libro de Drive: Drive respondió 404"), eq(AHORA));
        verify(asociacion).aplicarLibro(Canal.AMAZON_STOCKROOM,
                List.of(new PeticionAsociaciones.Par("402-0000000-0000001", "350000000000001")), AHORA, MODIFICADO_MADRID);
    }

    @Test void trasUnErrorLaVueltaSiguienteReintenta() throws Exception {
        enDrive(AMAZON, MODIFICADO, libro(LibroVentas.PESTANA));
        when(drive.metadatos(AMAZON)).thenThrow(new IOException("Drive no respondió a tiempo"))
                .thenReturn(new ClienteDrive.FicheroDrive(AMAZON, "Libro ventas.xlsm", MODIFICADO));
        LecturaLibros l = lectura(AMAZON, "");
        l.pasada();
        verify(asociacion).apuntarError(eq(Canal.AMAZON), anyString(), eq(AHORA));
        l.pasada();
        verify(asociacion).aplicarLibro(Canal.AMAZON, List.of(), AHORA, MODIFICADO_MADRID);
    }

    @Test void unErrorAlAplicarTambienSeApunta() throws Exception {
        enDrive(AMAZON, MODIFICADO, libro(LibroVentas.PESTANA));
        when(asociacion.aplicarLibro(any(), anyList(), any(), any())).thenThrow(new IllegalStateException("base caida"));
        lectura(AMAZON, "").pasada();
        verify(asociacion).apuntarError(Canal.AMAZON, "fallo al leer o aplicar el libro: base caida", AHORA);
    }

    @Test void sinIdentificadoresNoSeLeeNada() {
        lectura("", "  ").pasada();
        lectura(null, null).pasada();
        verifyNoInteractions(drive, asociacion);
    }

    @Test void elMensajeDeErrorSeRecortaA500() {
        String largo = "x".repeat(800);
        assertEquals(LecturaLibros.MAX_ERROR, LecturaLibros.mensaje(new LibroVentasException(largo)).length());
    }
}
