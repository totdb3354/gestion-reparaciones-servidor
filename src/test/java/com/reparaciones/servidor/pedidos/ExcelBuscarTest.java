package com.reparaciones.servidor.pedidos;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBuscarTest {

    private static FilasDAO.PorBuscar f(String sku, String pais) { return new FilasDAO.PorBuscar(sku, pais, null); }

    @Test void agrupaPorSkuSinDistinguirMayusculasConLaFormaMasRepetidaYCuentaPorPais() {
        var t = ExcelBuscar.tabla(List.of(
            new FilasDAO.PorBuscar("i13128AzulA", "FR", null), new FilasDAO.PorBuscar("i13128AzuLA", "FR", null),
            new FilasDAO.PorBuscar("i13128AzulA", "DE", null), new FilasDAO.PorBuscar("i12128AzulA", null, null)));
        assertThat(t.paises()).containsExactly("DE", "FR", "—");
        assertThat(t.filas()).extracting(ExcelBuscar.FilaTabla::sku).containsExactly("i12128AzulA", "i13128AzulA");
        assertThat(t.filas().get(1).porPais()).containsEntry("FR", 2).containsEntry("DE", 1);
        assertThat(t.filas().get(1).total()).isEqualTo(3);
    }

    @Test void skuNuloVaComoSinSkuYElEmpateGanaLaPrimeraEnOrdenAlfabetico() {
        var t = ExcelBuscar.tabla(List.of(f(null, "ES"), f("b", "ES"), f("B", "ES")));
        assertThat(t.filas()).extracting(ExcelBuscar.FilaTabla::sku).containsExactly("(sin SKU)", "B");
    }

    @Test void escribeLasDosTablasEnUnaHoja() throws Exception {
        var hoy = ExcelBuscar.tabla(List.of(f("i13128AzulA", "FR"), f("i13128AzulA", "DE"), f("i12128AzulA", null), f("i13128AzulA", "FR")));
        var despues = ExcelBuscar.tabla(List.of());
        byte[] xlsx = ExcelBuscar.escribir("AMZ", hoy, despues);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet h = libro.getSheetAt(0);
            assertThat(h.getSheetName()).isEqualTo("AMZ");
            assertThat(h.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Para hoy");
            var cab = h.getRow(1);
            assertThat(List.of(cab.getCell(0).getStringCellValue(), cab.getCell(1).getStringCellValue(), cab.getCell(2).getStringCellValue(),
                    cab.getCell(3).getStringCellValue(), cab.getCell(4).getStringCellValue()))
                    .containsExactly("SKU", "DE", "FR", "—", "Total general");
            var a = h.getRow(2);
            assertThat(a.getCell(0).getStringCellValue()).isEqualTo("i12128AzulA");
            assertThat(a.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(a.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(a.getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(a.getCell(4).getNumericCellValue()).isEqualTo(1);
            var b = h.getRow(3);
            assertThat(b.getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(b.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(b.getCell(3).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(b.getCell(4).getNumericCellValue()).isEqualTo(3);
            var tot = h.getRow(4);
            assertThat(tot.getCell(0).getStringCellValue()).isEqualTo("Total general");
            assertThat(tot.getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(tot.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(tot.getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(tot.getCell(4).getNumericCellValue()).isEqualTo(4);
            var enBlanco = h.getRow(5);
            assertThat(enBlanco == null || enBlanco.getCell(0) == null).as("fila en blanco entre las tablas").isTrue();
            assertThat(h.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Pueden esperar");
            assertThat(h.getRow(7).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
            assertThat(h.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
            assertThat(h.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(h.getPrintSetup().getFitHeight()).isEqualTo((short) 0);
            assertThat(h.getFitToPage()).isTrue();
        }
    }
}
