package com.reparaciones.servidor.pedidos;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelBuscarTest {

    private static FilasDAO.PorBuscar f(String sku, String pais) { return new FilasDAO.PorBuscar(sku, pais, null, null, null, null); }

    @Test void agrupaPorSkuSinDistinguirMayusculasConLaFormaMasRepetidaYCuentaPorPais() {
        var t = ExcelBuscar.tabla(List.of(
            new FilasDAO.PorBuscar("i13128AzulA", "FR", null, null, null, null), new FilasDAO.PorBuscar("i13128AzuLA", "FR", null, null, null, null),
            new FilasDAO.PorBuscar("i13128AzulA", "DE", null, null, null, null), new FilasDAO.PorBuscar("i12128AzulA", null, null, null, null, null)));
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

    private static FilasDAO.PorBuscar w(Canal canal, String cliente, String sku, LocalDate entro) {
        return new FilasDAO.PorBuscar(sku, "ES", null, canal, cliente, entro);
    }

    private static final LocalDate D16 = LocalDate.of(2026, 11, 16);
    private static final LocalDate D17 = LocalDate.of(2026, 11, 17);

    @Test void websAgrupaPorClienteSkuSinMayusculasYDiaYOrdenaPorDiaClienteSku() {
        var r = ExcelBuscar.webs(List.of(
            w(Canal.DISTRIBUIDORES, "Zeta SL", "i13128AzulA", D17), w(Canal.DISTRIBUIDORES, "Alfa SL", "i13128AzulA", D17),
            w(Canal.DISTRIBUIDORES, "Alfa SL", "i13128AzuLA", D17), w(Canal.DISTRIBUIDORES, "Alfa SL", "i13128AzulA", D17),
            w(Canal.DISTRIBUIDORES, "Zeta SL", "i13128AzulA", D16), w(Canal.DISTRIBUIDORES, "Alfa SL", "i12128AzulA", D17),
            w(Canal.PARTICULARES, "Ana", null, D16), w(Canal.PARTICULARES, null, "x", D16)));
        assertThat(r.get(Canal.DISTRIBUIDORES)).containsExactly(
            new ExcelBuscar.FilaWeb("Zeta SL", "i13128AzulA", D16, 1),
            new ExcelBuscar.FilaWeb("Alfa SL", "i12128AzulA", D17, 1),
            new ExcelBuscar.FilaWeb("Alfa SL", "i13128AzulA", D17, 3),
            new ExcelBuscar.FilaWeb("Zeta SL", "i13128AzulA", D17, 1));
        assertThat(r.get(Canal.PARTICULARES)).containsExactly(
            new ExcelBuscar.FilaWeb("(sin cliente)", "x", D16, 1), new ExcelBuscar.FilaWeb("Ana", "(sin SKU)", D16, 1));
        assertThat(r.get(Canal.BACKIPHONE)).isEmpty();
    }

    @Test void escribeLasWebsUnBloquePorWeb() throws Exception {
        var datos = ExcelBuscar.webs(List.of(
            w(Canal.PARTICULARES, "Ana", "i13128AzulA", D17), w(Canal.PARTICULARES, "Ana", "i13128AzulA", D17),
            w(Canal.DISTRIBUIDORES, "Alfa SL", "i12128AzulA", D16)));
        byte[] xlsx = ExcelBuscar.escribirWebs("WEBS", datos);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet h = libro.getSheetAt(0);
            assertThat(h.getSheetName()).isEqualTo("WEBS");
            assertThat(h.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fonestockroom (distribuidores)");
            assertThat(h.getRow(0).getCell(0).getCellStyle().getFont().getBold()).isTrue();
            var cab = h.getRow(1);
            assertThat(List.of(cab.getCell(0).getStringCellValue(), cab.getCell(1).getStringCellValue(),
                    cab.getCell(2).getStringCellValue(), cab.getCell(3).getStringCellValue()))
                    .containsExactly("Cliente", "SKU", "Uds", "Entró");
            var a = h.getRow(2);
            assertThat(a.getCell(0).getStringCellValue()).isEqualTo("Alfa SL");
            assertThat(a.getCell(1).getStringCellValue()).isEqualTo("i12128AzulA");
            assertThat(a.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(a.getCell(2).getNumericCellValue()).isEqualTo(1);
            assertThat(a.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(a.getCell(3))).isTrue();
            assertThat(a.getCell(3).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(a.getCell(3).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(D16);
            var tot = h.getRow(3);
            assertThat(tot.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(tot.getCell(2).getNumericCellValue()).isEqualTo(1);
            var enBlanco = h.getRow(4);
            assertThat(enBlanco == null || enBlanco.getCell(0) == null).isTrue();
            assertThat(h.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Fonestore (particulares)");
            assertThat(h.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Cliente");
            assertThat(h.getRow(7).getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(h.getRow(8).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(h.getRow(8).getCell(2).getNumericCellValue()).isEqualTo(2);
            var blanco2 = h.getRow(9);
            assertThat(blanco2 == null || blanco2.getCell(0) == null).isTrue();
            assertThat(h.getRow(10).getCell(0).getStringCellValue()).isEqualTo("BackiPhone");
            assertThat(h.getRow(11).getCell(0).getStringCellValue()).isEqualTo("No hay móviles que buscar");
            assertThat(h.getLastRowNum()).isEqualTo(11);
            assertThat(h.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
            assertThat(h.getPrintSetup().getLandscape()).isFalse();
            assertThat(h.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(h.getPrintSetup().getFitHeight()).isEqualTo((short) 0);
            assertThat(h.getFitToPage()).isTrue();
        }
    }
}
