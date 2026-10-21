package com.reparaciones.servidor.nsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final Clock reloj = Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID);
    private final StockTelefonosController api = new StockTelefonosController(dao, new TallerNsys(dao, tallerDao, logDao, reloj),
            reloj);

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

    @Test void fichaConUnaRevisionDeDevolucion() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(5), false, TipoEvento.UBICACION, "Listos",
                        "Devolución", "Funcionalidad pendiente"),
                new EventoNsys("350000000000001", "BT-9001", AHORA.minusDays(3), false, TipoEvento.UBICACION, "Devolución",
                        "Listos", "CAJA LISTOS")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T13:43"));
        assertTrue(ficha.get("revisadoDevolucion").asBoolean());
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
    }

    @Test void fichaDeUnMovilEnRevisionSinEventosPendienteDesdeLaRecepcion() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(
                movil("350000000000001", "BT-9001", "n/a", "En revisión", "En revisión", EstadoMovil.STOCK)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("2026-10-01", ficha.get("pendienteRevisionDesde").asText());
        assertTrue(ficha.get("revisadoEl").isNull());
    }

    @Test void laFichaDeUnVendidoNoEstaPendienteDeRevisarAunqueConserveUnaCajaPendiente() {
        // Una venta antigua, de cuando NSYS aún no vaciaba la caja (0.9.18.2).
        MovilNsys vendido = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released",
                "Funcionalidad pendiente", "Proveedor A", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 5, 2),
                "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Devolución", "Devolución", EstadoMovil.VENDIDO,
                AHORA.minusDays(15), AHORA.minusDays(1));
        when(dao.porImei("350000000000001")).thenReturn(List.of(vendido));
        when(dao.eventos("350000000000001")).thenReturn(List.of(new EventoNsys("350000000000001", "BT-9001",
                AHORA.minusDays(5), false, TipoEvento.UBICACION, "Listos", "Devolución", "Funcionalidad pendiente")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
        assertEquals("2024-05-02", ficha.get("fechaVenta").asText());
    }

    @Test void laFichaDeUnaBajaGuardadaConElFalloNoEstaPendienteDeRevisar() {
        // Una baja sin caja como la dejó la 0.9.18, antes de la limpieza (0.9.18.2).
        MovilNsys baja = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Removed", "",
                "Proveedor A", LocalDate.of(2026, 10, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false,
                "En revisión", "En revisión", EstadoMovil.BAJA, AHORA.minusDays(15), AHORA.minusDays(1));
        when(dao.porImei("350000000000001")).thenReturn(List.of(baja));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
        assertTrue(ficha.get("dias").isNull());
    }

    // 0.9.21 (corrección en NSYS): BT-1333 (recibido el 23/09, revisado el 13/10 y de pedido desde Listos) se da de baja el
    // 15/10 y vuelve como BT-1345, de pedido y sin última caja.

    private static MovilNsys loteNuevoCorregido() {
        return new MovilNsys("350000000000001", "BT-1345", "Apple iPhone 15 eSIM 512GB Black", "A100", "InProcess",
                "Pedidos realizados", "Proveedor A", LocalDate.of(2026, 10, 15), null, "i15eSIM512NegroA100", "15", "15",
                "512GB", "Negro", "A100", true, null, null, EstadoMovil.DE_PEDIDO, AHORA.minusDays(1), AHORA.minusDays(1));
    }

    private static MovilNsys loteViejoCorregido() {
        return new MovilNsys("350000000000001", "BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "A", "Removed", "",
                "Proveedor A", LocalDate.of(2026, 9, 23), null, "i15eSIM512AmarilloA", "15", "15", "512GB", "Amarillo", "A",
                true, null, "Listos", EstadoMovil.BAJA, AHORA.minusDays(20), AHORA.minusDays(1));
    }

    private static EventoNsys eventoCorregido(String lote, LocalDateTime fecha, TipoEvento tipo, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, false, tipo, de, a, aNsys);
    }

    private static final LocalDateTime BAJA = LocalDateTime.of(2026, 10, 15, 10, 1);

    @Test void laFichaDeUnLoteCorregidoCuentaDesdeElPrimerLoteConSuRevisionYSuCaja() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteNuevoCorregido(), loteViejoCorregido()));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 13, 8, 46), TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 14, 13, 31), TipoEvento.UBICACION, "Listos", null, "Pedidos realizados"),
                eventoCorregido("BT-1333", BAJA, TipoEvento.ESTADO, "InProcess", "Removed", null),
                eventoCorregido("BT-1345", BAJA, TipoEvento.ENTRADA, null, null, "Pedidos realizados"),
                eventoCorregido("BT-1345", BAJA, TipoEvento.REENTRADA, null, "BT-1345", null)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("BT-1345", ficha.get("lote").asText());
        assertEquals(23, ficha.get("dias").asInt(), "del 23/09 (primer lote) al 16/10");
        assertEquals(1, ficha.get("entradas").asInt());
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T08:46"));
        assertEquals("Listos", ficha.get("ubicacion").asText(), "la caja del lote de antes");
    }

    @Test void elStockCuentaLosDiasDesdeElPrimerLoteYLaCajaDelAnterior() {
        when(dao.enEmpresa()).thenReturn(List.of(loteNuevoCorregido()));
        when(dao.lotesDeImeisConVarios()).thenReturn(List.of(loteNuevoCorregido(), loteViejoCorregido()));
        when(dao.bajasConHora()).thenReturn(List.of(eventoCorregido("BT-1333", BAJA, TipoEvento.ESTADO, "InProcess", "Removed", null)));
        JsonNode stock = json(api.stock());
        assertEquals(23, stock.get(0).get("dias").asInt());
        assertEquals("Listos", stock.get(0).get("ubicacion").asText());
    }

    // Revisión final: el lote nuevo entra sin caja (en stock, «En revisión»).
    private static MovilNsys loteNuevoSinCaja(String grado) {
        return new MovilNsys("350000000000001", "BT-1345", "Apple iPhone 15 eSIM 512GB Black", grado, "InProcess", "",
                "Proveedor A", LocalDate.of(2026, 10, 15), null, "i15eSIM512NegroA", "15", "15", "512GB", "Negro", grado, true,
                "En revisión", "En revisión", EstadoMovil.STOCK, AHORA.minusDays(1), AHORA.minusDays(1));
    }

    private static List<EventoNsys> correccionSinCaja(EventoNsys... antes) {
        List<EventoNsys> eventos = new java.util.ArrayList<>(List.of(antes));
        eventos.add(eventoCorregido("BT-1333", BAJA, TipoEvento.ESTADO, "InProcess", "Removed", null));
        eventos.add(eventoCorregido("BT-1345", BAJA, TipoEvento.ENTRADA, null, "En revisión", ""));
        eventos.add(eventoCorregido("BT-1345", BAJA, TipoEvento.REENTRADA, null, "BT-1345", null));
        return eventos;
    }

    @Test void siElViejoEstabaEnUnaCajaLaEsperaEmpiezaConLaCorreccion() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteNuevoSinCaja("A"), loteViejoCorregido()));
        when(dao.eventos("350000000000001")).thenReturn(correccionSinCaja(eventoCorregido("BT-1333",
                LocalDateTime.of(2026, 10, 13, 8, 46), TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("2026-10-15", ficha.get("pendienteRevisionDesde").asText());
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T08:46"));
    }

    @Test void siElViejoYaEstabaPendienteLaEsperaSigue() {
        MovilNsys viejo = new MovilNsys("350000000000001", "BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "A", "Removed", "",
                "Proveedor A", LocalDate.of(2026, 9, 23), null, "i15eSIM512AmarilloA", "15", "15", "512GB", "Amarillo", "A",
                true, null, "En revisión", EstadoMovil.BAJA, AHORA.minusDays(20), AHORA.minusDays(1));
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteNuevoSinCaja("A"), viejo));
        when(dao.eventos("350000000000001")).thenReturn(correccionSinCaja(
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 13, 8, 46), TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 14, 9, 1), TipoEvento.UBICACION, "Listos", "En revisión", "")));
        assertEquals("2026-10-14", json(api.ficha("350000000000001")).get("pendienteRevisionDesde").asText());
    }

    @Test void elGradoAnteriorCuentaElCambioDeGradoDeLaCorreccion() {
        MovilNsys viejo = new MovilNsys("350000000000001", "BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "B", "Removed", "",
                "Proveedor A", LocalDate.of(2026, 9, 23), null, "i15eSIM512AmarilloB", "15", "15", "512GB", "Amarillo", "B",
                true, null, "Listos", EstadoMovil.BAJA, AHORA.minusDays(20), AHORA.minusDays(1));
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteNuevoSinCaja("A"), viejo));
        when(dao.eventos("350000000000001")).thenReturn(correccionSinCaja(
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 5, 9, 1), TipoEvento.GRADO, "C", "B", null)));
        assertEquals("B", json(api.ficha("350000000000001")).get("gradoAnterior").asText());
    }

    @Test void laFichaEnsenaElUltimoLoteDeLaCadenaAunqueElOrdenEmpate() {
        // En la primera carga los lotes pueden llegar con el viejo delante.
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteViejoCorregido(), loteNuevoSinCaja("A")));
        when(dao.eventos("350000000000001")).thenReturn(correccionSinCaja());
        assertEquals("BT-1345", json(api.ficha("350000000000001")).get("lote").asText());
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

    @Test void comprobarConUnImeiNuloOVacioEs400SinConsultar() {
        for (List<String> imeis : List.of(Arrays.asList("350000000000001", null), List.of("350000000000001", "  "),
                List.of(""))) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> api.comprobar(new StockTelefonosController.PeticionComprobar(imeis)), String.valueOf(imeis));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
        verifyNoInteractions(dao);
    }

    @Test void comprobarUnaListaVaciaNoConsulta() {
        assertTrue(api.comprobar(pedir()).isEmpty());
        assertTrue(api.comprobar(new StockTelefonosController.PeticionComprobar(null)).isEmpty());
        verifyNoInteractions(dao);
    }

    // ---------------------------------------------------------------- 0.9.18: lista de importaciones (spec §6.5)

    private static RegistroImportacion registro(int id, LocalDateTime fecha, String resultado, String mensaje, Integer segundos) {
        LocalDateTime empezada = fecha.plusMinutes(3);
        return new RegistroImportacion(id, "Items_" + id + ".xlsx", fecha, resultado, 70000, 12, empezada,
                segundos == null ? null : empezada.plusSeconds(segundos), mensaje);
    }

    @Test void importacionesDelActualDeLaMasRecienteALaMasAntiguaConSusSegundosYSuMotivo() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        when(dao.importaciones("ACTUAL", 20)).thenReturn(List.of(
                registro(8, AHORA.minusMinutes(12), "EN_CURSO", null, null),
                registro(7, AHORA.minusMinutes(42), "OK", "Cajas sin traducir: CAJA NUEVA", 17),
                registro(6, AHORA.minusMinutes(72), "RECHAZADO", "Trae 10 filas…", 2)));
        JsonNode r = json(api.importaciones(20));
        JsonNode filas = r.get("importaciones");
        assertEquals(3, filas.size());
        assertEquals("EN_CURSO", filas.get(0).get("resultado").asText());
        assertTrue(filas.get(0).get("segundos").isNull(), "en curso: sin segundos");
        assertTrue(filas.get(0).get("motivo").isNull());
        assertEquals("Items_7.xlsx", filas.get(1).get("fichero").asText());
        assertEquals(12, filas.get(1).get("cambios").asInt());
        assertEquals(17, filas.get(1).get("segundos").asInt());
        assertEquals("Cajas sin traducir: CAJA NUEVA", filas.get(1).get("motivo").asText());
        assertEquals("Trae 10 filas…", filas.get(2).get("motivo").asText());
        assertEquals(0, r.get("historico").get("total").asInt(), "sin filas HISTORICO la web no pinta la línea");
    }

    @Test void unErrorDeLaListaSaleConElMotivoGenericoYNuncaConSuMensaje() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        when(dao.importaciones("ACTUAL", 20)).thenReturn(List.of(registro(5, AHORA.minusMinutes(30), "ERROR",
                "java.io.IOException: Drive respondió 403 a https://www.googleapis.com/drive/v3/files/id-secreto", 4)));
        JsonNode fila = json(api.importaciones(20)).get("importaciones").get(0);
        assertEquals("Error al leer de Drive o al guardar; ver el registro del servidor", fila.get("motivo").asText());
        assertFalse(fila.toString().contains("id-secreto"));
    }

    @Test void elLimiteSeQuedaEntreUnoYCincuenta() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        api.importaciones(200);
        api.importaciones(0);
        api.importaciones(-3);
        verify(dao).importaciones("ACTUAL", 50);
        verify(dao, times(2)).importaciones("ACTUAL", 1);
    }

    @Test void elProgresoDelHistoricoConHechosUltimoYHasta() {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(140, 138, LocalDateTime.of(2025, 5, 3, 22, 31)));
        when(dao.primeraImportacion("ACTUAL", Set.of("OK"))).thenReturn(Optional.of(
                new Importacion(1, "Items_2026.10.16_14.31.xlsx", LocalDateTime.of(2026, 10, 16, 14, 31), "OK", 70000, null)));
        JsonNode h = json(api.importaciones(20)).get("historico");
        assertEquals(140, h.get("total").asInt());
        assertEquals(138, h.get("hechos").asInt());
        assertTrue(h.get("ultimo").asText().startsWith("2025-05-03T22:31"));
        assertTrue(h.get("hasta").asText().startsWith("2026-10-16T14:31"));
    }

    // ---------------------------------------------------------------- 0.9.21: lo que vale (spec «SKU del taller» §2.1 y §2.4)

    private static final String IMEI = "350000000000001";

    private void conMaestro() {
        when(dao.cargarProductos()).thenReturn(List.of(TallerDePrueba.producto("Blue", "Azul", "A"),
                TallerDePrueba.producto("(PRODUCT)RED", "Rojo", "A"), TallerDePrueba.producto("Blue", "Azul", "A100")));
    }

    @Test void elStockEnsenaLoQueValeYLoDeNsysAlLado() {
        conMaestro();
        when(dao.enEmpresa()).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK),
                movil("350000000000002", "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes()).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        JsonNode stock = json(api.stock());
        assertEquals("Rojo", stock.get(0).get("color").asText());
        assertEquals("Azul", stock.get(0).get("colorNsys").asText());
        assertEquals("i13128RojoA", stock.get(0).get("sku").asText());
        assertEquals("i13128AzulA", stock.get(0).get("skuNsys").asText());
        assertTrue(stock.get(0).get("gradoNsys").isNull());
        assertTrue(stock.get(1).get("colorNsys").isNull(), "sin nada del taller, como hoy");
        assertEquals("i13128AzulA", stock.get(1).get("sku").asText());
    }

    @Test void unStockSinNadaDelTallerNoLeeElMaestro() {
        when(dao.enEmpresa()).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        api.stock();
        verify(dao, never()).cargarProductos();
    }

    @Test void laFichaConElGradoDelTallerYLoQueHayQuePonerEnNsys() {
        conMaestro();
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "A")));
        JsonNode ficha = json(api.ficha(IMEI));
        assertEquals("A", ficha.get("grado").asText());
        assertTrue(ficha.get("bateriaNueva").asBoolean());
        assertEquals("A", ficha.get("gradoNsys").asText());
        assertEquals("i13128AzulA100", ficha.get("sku").asText());
        assertTrue(ficha.get("porPasar").asBoolean());
        assertEquals("Apple iPhone 13 128GB Blue", ficha.get("ponerProducto").asText(), "solo cambia el grado: el producto de ahora");
        assertEquals("A100", ficha.get("ponerGrado").asText());
        verify(dao, never()).goodsMasUsados(any());
    }

    @Test void laFichaConElColorDelTallerPideElProductoMasUsadoDeEseColor() {
        conMaestro();
        MovilNsys m = movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK);
        when(dao.porImei(IMEI)).thenReturn(List.of(m));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        when(dao.goodsMasUsados(m)).thenReturn(Map.of("Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        JsonNode ficha = json(api.ficha(IMEI));
        assertEquals("Rojo", ficha.get("color").asText());
        assertEquals("Azul", ficha.get("colorNsys").asText());
        assertEquals("Apple iPhone 13 128GB (PRODUCT)RED", ficha.get("ponerProducto").asText());
        when(dao.goodsMasUsados(m)).thenReturn(Map.of());
        assertTrue(json(api.ficha(IMEI)).get("ponerProducto").isNull(), "ningún móvil de ese color: la web dice «búscalo a mano»");
    }

    @Test void laFichaSinNadaDelTallerNoTieneNadaPorPasar() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        JsonNode ficha = json(api.ficha(IMEI));
        assertFalse(ficha.get("porPasar").asBoolean());
        assertTrue(ficha.get("ponerProducto").isNull());
        assertTrue(ficha.get("ponerGrado").isNull());
        assertTrue(ficha.get("colorNsys").isNull());
    }

    @Test void comprobarConElColorYElGradoQueValen() {
        conMaestro();
        List<String> imeis = List.of(IMEI);
        when(dao.porImeis(imeis)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(imeis)).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul"),
                TallerDePrueba.vigente(2, IMEI, DatoTaller.GRADO, "A100", "A")));
        JsonNode r = json(api.comprobar(pedir(IMEI))).get(0);
        assertEquals("Rojo", r.get("color").asText());
        assertEquals("Azul", r.get("colorNsys").asText());
        assertEquals("A", r.get("grado").asText());
        assertTrue(r.get("bateriaNueva").asBoolean());
        assertEquals("A", r.get("gradoNsys").asText());
    }

    @Test void buscarConElColorQueVale() {
        when(dao.buscar("3500", 10)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.COLOR, "Rojo", "Azul")));
        JsonNode r = json(api.buscar("3500")).get(0);
        assertEquals("Rojo", r.get("color").asText());
        assertEquals("Azul", r.get("colorNsys").asText());
    }

    @Test void elRastroLlevaLosCambiosDelTaller() {
        when(tallerDao.deImei(IMEI)).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "B")));
        List<EventoRastro> rastro = api.rastro(IMEI);
        assertEquals("TALLER_CAMBIO", rastro.get(0).tipo());
        assertEquals("TALLER", rastro.get(0).fuente());
        assertEquals("GRADO", rastro.get(0).dato());
    }

    // ---------------------------------------------------------------- 0.9.21: corrección, vuelta, lista y opciones

    private static final UsuarioPrincipal SUPER = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    @Test void corregirGuardaConElUsuarioDeLaSesion() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        api.corregir(IMEI, new StockTelefonosController.PeticionTaller(null, "A100", "batería nueva"), SUPER);
        verify(tallerDao).guardar(eq(IMEI), eq(List.of(new TallerDAO.Nuevo(DatoTaller.GRADO, "A100", "A",
                CambioTaller.Estado.VIGENTE))), eq(7), eq("batería nueva"), any());
        verify(logDao).insertar(7, "CORREGIR_MOVIL_TALLER", "IMEI: 350000000000001, GRADO: A → A100", "batería nueva");
    }

    @Test void volverSinCuerpoVaSinMotivo() {
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(TallerDePrueba.vigente(1, IMEI, DatoTaller.GRADO, "A100", "A")));
        api.volver(IMEI, null, SUPER);
        verify(tallerDao).guardar(eq(IMEI), eq(List.of(new TallerDAO.Nuevo(DatoTaller.GRADO, "A", "A",
                CambioTaller.Estado.QUITADO))), eq(7), isNull(), any());
    }

    @Test void laListaYLasOpcionesSalenDelServicio() {
        assertTrue(api.porPasar().isEmpty());
        when(dao.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "BT-9001", "A", "Listos", "Listos", EstadoMovil.STOCK)));
        OpcionesTaller o = api.colores(IMEI);
        assertEquals("Apple iPhone 13 128GB Blue", o.productoActual());
        assertTrue(o.colores().isEmpty(), "el maestro del mock está vacío");
    }
}
