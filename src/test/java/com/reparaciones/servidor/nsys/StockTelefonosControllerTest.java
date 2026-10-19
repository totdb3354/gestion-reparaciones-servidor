package com.reparaciones.servidor.nsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La API de solo lectura con el DAO mockeado y la hora fija (16/10/2026 13:43 en Madrid). Las respuestas son records
 *  privados del controlador: se miran como JSON, igual que las ve la web. */
class StockTelefonosControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 13, 43);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final NsysDAO dao = mock(NsysDAO.class);
    private final StockTelefonosController api = new StockTelefonosController(dao,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static MovilNsys movil(String imei, String batch, String grade, String ubicacion, String ultima, EstadoMovil estado) {
        return new MovilNsys(imei, batch, "Apple iPhone 13 128GB Blue", grade, "Available", "x", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", grade, false, ubicacion,
                ultima, estado, AHORA.minusDays(15), AHORA.minusDays(1));
    }

    private static JsonNode json(Object respuesta) {
        return JSON.valueToTree(respuesta);
    }

    @Test void stockConGradoYBateriaSeparadosYLaUbicacionDeLosDePedido() {
        when(dao.enEmpresa()).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A-100", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", null, "Taller · Glass", EstadoMovil.DE_PEDIDO)));
        JsonNode stock = json(api.stock());
        assertEquals("A-", stock.get(0).get("grado").asText());
        assertTrue(stock.get(0).get("bateriaNueva").asBoolean());
        assertEquals("BT-9001", stock.get(0).get("lote").asText());
        assertEquals(15, stock.get(0).get("dias").asInt(), "del 1/10 al 16/10");
        assertEquals("Taller · Glass", stock.get(1).get("ubicacion").asText(), "un móvil de pedido sigue en su caja");
        assertEquals("DE_PEDIDO", stock.get(1).get("estado").asText());
    }

    @Test void stockConLosTrabajosAbiertosDeCadaMovil() {
        when(dao.enEmpresa()).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A", "Taller · Reparación", "Taller · Reparación", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        LocalDateTime asignada = LocalDateTime.of(2026, 10, 16, 9, 30);
        when(dao.trabajosAbiertos()).thenReturn(Map.of("350000000000001", List.of(
                new TrabajoAbierto("REPARACION", "Técnico 1", asignada, null),
                new TrabajoAbierto("GLASS", "Técnico 3", asignada.plusHours(1), asignada.plusHours(2)))));
        JsonNode stock = json(api.stock());
        JsonNode trabajos = stock.get(0).get("trabajos");
        assertEquals(2, trabajos.size(), "reparación y glass: dos trabajos");
        assertEquals("REPARACION", trabajos.get(0).get("categoria").asText());
        assertTrue(trabajos.get(0).get("entregado").isNull());
        assertEquals("Técnico 3", trabajos.get(1).get("tecnico").asText());
        assertTrue(trabajos.get(1).get("desde").asText().startsWith("2026-10-16T10:30"));
        assertTrue(trabajos.get(1).get("entregado").asText().startsWith("2026-10-16T11:30"));
        assertEquals(0, stock.get(1).get("trabajos").size(), "sin trabajos abiertos, lista vacía");
    }

    @Test void estadoConElUltimoOkYUnFalloPosterior() {
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(5, "a", LocalDateTime.of(2026, 10, 16, 13, 31), "OK", 70000, null)));
        when(dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "ERROR"))).thenReturn(Optional.of(
                new Importacion(6, "b", LocalDateTime.of(2026, 10, 16, 13, 40), "RECHAZADO", 10, "Trae 10 filas…")));
        JsonNode estado = json(api.estado());
        assertEquals("OK", estado.get("nivel").asText());
        assertTrue(estado.get("ultimaExportacion").asText().startsWith("2026-10-16T13:31"));
        assertEquals("Trae 10 filas…", estado.get("motivoFallo").asText());
    }

    @Test void unErrorSeEnsenaConUnTextoGenericoSinElDetalleTecnico() {
        // El texto de un ERROR es e.toString(): puede llevar ids de ficheros de Drive o el correo de la cuenta de servicio.
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(5, "a", LocalDateTime.of(2026, 10, 16, 13, 1), "OK", 70000, null)));
        when(dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "ERROR"))).thenReturn(Optional.of(
                new Importacion(6, "b", LocalDateTime.of(2026, 10, 16, 13, 31), "ERROR", 0,
                        "java.io.IOException: Drive respondió 403 a https://www.googleapis.com/drive/v3/files/id-secreto")));
        JsonNode estado = json(api.estado());
        assertEquals("Error al leer de Drive o al guardar; ver el registro del servidor", estado.get("motivoFallo").asText());
        assertTrue(estado.get("ultimoFallo").asText().startsWith("2026-10-16T13:31"));
    }

    @Test void unFalloAnteriorAlUltimoOkNoSeEnsena() {
        when(dao.ultimaImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(5, "a", LocalDateTime.of(2026, 10, 16, 8, 31), "OK", 70000, null)));
        when(dao.ultimaImportacion("ACTUAL", Set.of("RECHAZADO", "ERROR"))).thenReturn(Optional.of(
                new Importacion(4, "b", LocalDateTime.of(2026, 10, 16, 8, 1), "ERROR", 0, "viejo")));
        JsonNode estado = json(api.estado());
        assertEquals("SIN_DATOS", estado.get("nivel").asText());
        assertTrue(estado.get("motivoFallo").isNull());
    }

    @Test void buscarConMenosDeCuatroCaracteresNoConsulta() {
        assertTrue(api.buscar("350").isEmpty());
        verifyNoInteractions(dao);
        when(dao.buscar("3500", 10)).thenReturn(List.of(movil("350000000000001", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        assertEquals("350000000000001", json(api.buscar("3500")).get(0).get("imeiSn").asText());
    }

    @Test void fichaConElLoteMasRecienteSusEntradasYElGradoAnterior() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9002", "ID", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000001", "BT-9001", "A100", null, "Listos", EstadoMovil.BAJA)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(30), false, TipoEvento.GRADO, "C100", "A100", null),
                new EventoNsys("350000000000001", "BT-9002", AHORA.minusDays(16), false, TipoEvento.GRADO, "A100", "ID", null)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("BT-9002", ficha.get("lote").asText());
        assertEquals(2, ficha.get("entradas").asInt());
        assertEquals("A", ficha.get("gradoAnterior").asText(), "normalizado como grado: sin el 100 de la batería");
        assertEquals("ID", ficha.get("grado").asText());
    }

    @Test void elGradoAnteriorSaltaLosCambiosQueSoloSonDeBateria() {
        // C → A y después cambio de batería A → A100: el grado anterior de verdad es C, no A (que la web escondería
        // por ser igual al actual).
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A100", "Listos", "Listos", EstadoMovil.STOCK)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(9), false, TipoEvento.GRADO, "C100", "A", null),
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(3), false, TipoEvento.GRADO, "A", "A100", null)));
        assertEquals("C", json(api.ficha("350000000000001")).get("gradoAnterior").asText());
    }

    @Test void unSoloCambioDeBateriaNoDaGradoAnterior() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A100", "Listos", "Listos", EstadoMovil.STOCK)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(3), false, TipoEvento.GRADO, "A", "A100", null)));
        assertTrue(json(api.ficha("350000000000001")).get("gradoAnterior").isNull());
    }

    @Test void fichaConLaUltimaRevisionDelLote() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(new EventoNsys("350000000000001", "BT-9001",
                AHORA.minusDays(3), false, TipoEvento.UBICACION, "En revisión", "Taller · Pulir", "CAJA PULIR")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T13:43"));
        assertFalse(ficha.get("revisadoDevolucion").asBoolean());
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
    }

    @Test void fichaDeUnMovilEnRevisionSinEventosPendienteDesdeLaRecepcion() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "n/a", "En revisión", "En revisión", EstadoMovil.STOCK)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("2026-10-01", ficha.get("pendienteRevisionDesde").asText());
        assertTrue(ficha.get("revisadoEl").isNull());
    }

    @Test void fichaDeUnImeiQueNsysNoConoceEs404() {
        when(dao.porImei("x")).thenReturn(List.of());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> api.ficha("x"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test void rastroUneNsysYTaller() {
        when(dao.eventos("350000000000001")).thenReturn(List.of());
        when(dao.trabajos("350000000000001")).thenReturn(List.of(new TrabajoTaller("G1", AHORA.minusDays(3), AHORA.minusDays(2), "Técnico 3")));
        assertEquals("TRABAJO_TERMINADO", api.rastro("350000000000001").get(0).tipo());
    }

    // ---------------------------------------------------------------- 0.9.18: comprobar una lista (spec §6.2)

    private static StockTelefonosController.PeticionComprobar pedir(String... imeis) {
        return new StockTelefonosController.PeticionComprobar(List.of(imeis));
    }

    @Test void comprobarDaUnaEntradaPorImeiConElLoteMasRecienteSusTrabajosYLaFechaDeVenta() {
        MovilNsys vendido = new MovilNsys("350000000000003", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released", "x",
                "Proveedor A", LocalDate.of(2023, 4, 1), LocalDate.of(2023, 5, 2), null, "13", "13", "128GB", "Azul", "A",
                false, null, null, EstadoMovil.VENDIDO, AHORA.minusDays(900), AHORA.minusDays(800));
        List<String> imeis = List.of("350000000000001", "350000000000002", "350000000000003");
        when(dao.porImeis(imeis)).thenReturn(List.of(
                movil("350000000000001", "BT-9002", "B", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000001", "BT-9001", "A", null, "Listos", EstadoMovil.BAJA), vendido));
        when(dao.trabajosAbiertos(imeis)).thenReturn(Map.of("350000000000001",
                List.of(new TrabajoAbierto("REPARACION", "Técnico 1", AHORA.minusHours(3), null))));
        JsonNode r = json(api.comprobar(pedir("350000000000001", "350000000000002", "350000000000003")));
        assertEquals(3, r.size(), "una entrada por IMEI pedido, en su orden");
        assertEquals("350000000000001", r.get(0).get("imei").asText());
        assertTrue(r.get(0).get("encontrado").asBoolean());
        assertEquals("STOCK", r.get(0).get("estado").asText(), "del IMEI, el lote más reciente");
        assertEquals("B", r.get(0).get("grado").asText());
        assertEquals("Listos", r.get(0).get("ubicacion").asText());
        assertEquals("Técnico 1", r.get(0).get("trabajos").get(0).get("tecnico").asText());
        assertEquals("350000000000002", r.get(1).get("imei").asText());
        assertFalse(r.get(1).get("encontrado").asBoolean());
        assertTrue(r.get(1).get("estado").isNull());
        assertEquals(0, r.get(1).get("trabajos").size());
        assertEquals("VENDIDO", r.get(2).get("estado").asText(), "también los vendidos");
        assertEquals("2023-05-02", r.get(2).get("fechaVenta").asText());
    }

    @Test void comprobarMasDeQuinientosEs400SinConsultar() {
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> api.comprobar(new StockTelefonosController.PeticionComprobar(imeis)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(dao);
    }

    @Test void comprobarUnaListaVaciaNoConsulta() {
        assertTrue(api.comprobar(pedir()).isEmpty());
        assertTrue(api.comprobar(new StockTelefonosController.PeticionComprobar(null)).isEmpty());
        verifyNoInteractions(dao);
    }
}
