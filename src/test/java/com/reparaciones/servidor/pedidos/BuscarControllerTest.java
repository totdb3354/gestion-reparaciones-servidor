package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuscarControllerTest {

    private final FilasDAO dao = mock(FilasDAO.class);
    private final Clock reloj = Clock.fixed(ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ImportadorNsys.MADRID).toInstant(), ImportadorNsys.MADRID);
    private final BuscarController controller = new BuscarController(dao, reloj);

    @SuppressWarnings("unchecked")
    private Collection<Canal> canalesPedidos(String plataforma) {
        when(dao.porBuscar(any())).thenReturn(List.of());
        controller.buscar(plataforma);
        ArgumentCaptor<Collection<Canal>> c = ArgumentCaptor.forClass(Collection.class);
        verify(dao).porBuscar(c.capture());
        return c.getValue();
    }

    @Test void amzPideAmazon() { assertThat(canalesPedidos("AMZ")).containsExactly(Canal.AMAZON); }
    @Test void fnskPideStockroom() { assertThat(canalesPedidos("FNSK")).containsExactly(Canal.AMAZON_STOCKROOM); }
    @Test void websPideLosTresCanalesWeb() {
        assertThat(canalesPedidos("WEBS")).containsExactly(Canal.DISTRIBUIDORES, Canal.PARTICULARES, Canal.BACKIPHONE);
    }

    @Test void otraPlataformaEs400() {
        assertThatThrownBy(() -> controller.buscar("XXX")).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400);
    }

    @Test void repartePorFechaDeSalidaYNombraElFichero() throws Exception {
        when(dao.porBuscar(any())).thenReturn(List.of(
                new FilasDAO.PorBuscar("a", "ES", null, null, null, null),
                new FilasDAO.PorBuscar("b", "ES", LocalDate.of(2026, 11, 17), null, null, null),
                new FilasDAO.PorBuscar("c", "ES", LocalDate.of(2026, 11, 18), null, null, null),
                new FilasDAO.PorBuscar("d", "ES", LocalDate.of(2026, 11, 19), null, null, null)));
        ResponseEntity<byte[]> r = controller.buscar("FNSK");
        assertThat(r.getHeaders().getFirst("Content-Disposition")).contains("attachment").contains("Buscar FNSK 18-11.xlsx");
        assertThat(r.getHeaders().getFirst("X-Filas")).isEqualTo("4");
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(r.getBody()))) {
            var h = libro.getSheetAt(0);
            assertThat(h.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Para hoy");
            assertThat(h.getRow(2).getCell(0).getStringCellValue()).isEqualTo("a");
            assertThat(h.getRow(4).getCell(0).getStringCellValue()).isEqualTo("c");
            assertThat(h.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Total general");
            assertThat(h.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Pueden esperar");
            assertThat(h.getRow(9).getCell(0).getStringCellValue()).isEqualTo("d");
        }
    }

    @Test void websVaPorWebYClienteSinDividirPorFechaYCuentaUnidades() throws Exception {
        var d = LocalDate.of(2026, 11, 17);
        when(dao.porBuscar(any())).thenReturn(List.of(
                new FilasDAO.PorBuscar("i13128AzulA", "ES", null, Canal.DISTRIBUIDORES, "Alfa SL", d),
                new FilasDAO.PorBuscar("i13128AzulA", "FR", LocalDate.of(2026, 11, 20), Canal.DISTRIBUIDORES, "Alfa SL", d),
                new FilasDAO.PorBuscar("i12128AzulA", "ES", null, Canal.BACKIPHONE, "Ana", d)));
        ResponseEntity<byte[]> r = controller.buscar("WEBS");
        assertThat(r.getHeaders().getFirst("Content-Disposition")).contains("Buscar WEBS 18-11.xlsx");
        assertThat(r.getHeaders().getFirst("X-Filas")).isEqualTo("3");
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(r.getBody()))) {
            var h = libro.getSheetAt(0);
            assertThat(h.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fonestockroom (distribuidores)");
            assertThat(h.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(h.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Total");
        }
    }
}
