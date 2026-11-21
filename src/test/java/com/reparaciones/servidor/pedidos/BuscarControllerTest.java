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
    @Test void pideLasCincoTiendasDeUnaVez() {
        when(dao.porBuscar(any())).thenReturn(List.of());
        controller.buscar();
        ArgumentCaptor<Collection<Canal>> c = ArgumentCaptor.forClass(Collection.class);
        verify(dao).porBuscar(c.capture());
        assertThat(c.getValue()).containsExactly(Canal.AMAZON, Canal.AMAZON_STOCKROOM, Canal.DISTRIBUIDORES, Canal.PARTICULARES, Canal.BACKIPHONE);
    }

    @Test void unSoloFicheroConUnaPestanaPorPlataformaYElNombreConDiaYHora() throws Exception {
        var d = LocalDate.of(2026, 11, 17);
        when(dao.porBuscar(any())).thenReturn(List.of(
                new FilasDAO.PorBuscar("a", "ES", null, Canal.AMAZON, null, d),
                new FilasDAO.PorBuscar("b", "ES", LocalDate.of(2026, 11, 17), Canal.AMAZON, null, d),
                new FilasDAO.PorBuscar("c", "ES", LocalDate.of(2026, 11, 18), Canal.AMAZON, null, d),
                new FilasDAO.PorBuscar("d", "ES", LocalDate.of(2026, 11, 19), Canal.AMAZON, null, d),
                new FilasDAO.PorBuscar("f", "DE", LocalDate.of(2026, 11, 25), Canal.AMAZON_STOCKROOM, null, d),
                new FilasDAO.PorBuscar("i13128AzulA", "ES", null, Canal.DISTRIBUIDORES, "Alfa SL", d),
                new FilasDAO.PorBuscar("i13128AzulA", "FR", LocalDate.of(2026, 11, 20), Canal.DISTRIBUIDORES, "Alfa SL", d),
                new FilasDAO.PorBuscar("i12128AzulA", "ES", null, Canal.BACKIPHONE, "Ana", d)));
        ResponseEntity<byte[]> r = controller.buscar();
        assertThat(r.getHeaders().getFirst("Content-Disposition")).contains("attachment").contains("Listado 18-11 10h00.xlsx");
        assertThat(r.getHeaders().getFirst("X-Filas")).isEqualTo("8");
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(r.getBody()))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(3);
            assertThat(List.of(libro.getSheetName(0), libro.getSheetName(1), libro.getSheetName(2))).containsExactly("AMZ", "FNSK", "Webs");
            var amz = libro.getSheet("AMZ");
            assertThat(amz.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Para hoy");
            assertThat(amz.getRow(2).getCell(0).getStringCellValue()).isEqualTo("a");
            assertThat(amz.getRow(4).getCell(0).getStringCellValue()).isEqualTo("c");
            assertThat(amz.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Total general");
            assertThat(amz.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Pueden esperar");
            assertThat(amz.getRow(9).getCell(0).getStringCellValue()).isEqualTo("d");
            var fnsk = libro.getSheet("FNSK");
            assertThat(fnsk.getRow(1).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
            assertThat(fnsk.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Pueden esperar");
            assertThat(fnsk.getRow(5).getCell(0).getStringCellValue()).isEqualTo("f");
            var webs = libro.getSheet("Webs");
            assertThat(webs.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fonestockroom (distribuidores)");
            assertThat(webs.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(webs.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Total");
            for (int i = 0; i < 3; i++) {
                assertThat(libro.getSheetAt(i).getPrintSetup().getPaperSize()).isEqualTo(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
                assertThat(libro.getSheetAt(i).getFitToPage()).isTrue();
            }
        }
    }

    @Test void sinNadaQueBuscarLasTresPestanasLoDicen() throws Exception {
        when(dao.porBuscar(any())).thenReturn(List.of());
        ResponseEntity<byte[]> r = controller.buscar();
        assertThat(r.getHeaders().getFirst("X-Filas")).isEqualTo("0");
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(r.getBody()))) {
            assertThat(libro.getSheet("AMZ").getRow(1).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
            assertThat(libro.getSheet("FNSK").getRow(1).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
            assertThat(libro.getSheet("Webs").getRow(1).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
        }
    }
}
