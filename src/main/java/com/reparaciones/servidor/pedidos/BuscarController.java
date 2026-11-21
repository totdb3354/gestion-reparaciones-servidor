package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** El «Listado» (antes «Descargar para buscar»): un Excel con una pestaña por plataforma (AMZ, FNSK y Webs) de los moviles que aun hay
 *  que buscar; AMZ y FNSK en dos tablas (para hoy y pueden esperar), las webs en un bloque por web y cliente. SUPERTECNICO y ADMIN. */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class BuscarController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Cuantos moviles lleva el Excel entre todas las pestañas (mismo nombre que en las plantillas de «Por pasar a NSYS»). */
    static final String CABECERA_FILAS = "X-Filas";
    /** «Listado 18-11 09h30.xlsx»: dia y hora de Madrid, sin «:» (Windows no lo admite en nombres de fichero). */
    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("'Listado 'dd-MM HH'h'mm'.xlsx'");
    private static final List<Canal> WEBS = List.of(Canal.DISTRIBUIDORES, Canal.PARTICULARES, Canal.BACKIPHONE);

    private final FilasDAO dao;
    private final Clock clock;

    @Autowired
    public BuscarController(FilasDAO dao) {
        this(dao, Clock.system(ImportadorNsys.MADRID));
    }

    BuscarController(FilasDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @GetMapping(value = "/buscar", produces = XLSX)
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(mediaType = XLSX,
                schema = @Schema(type = "string", format = "binary")))
    })
    public ResponseEntity<byte[]> buscar() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDate hoy = ahora.toLocalDate();
        List<Canal> canales = new ArrayList<>(List.of(Canal.AMAZON, Canal.AMAZON_STOCKROOM));
        canales.addAll(WEBS);
        List<FilasDAO.PorBuscar> todas = dao.porBuscar(canales);
        Partes amz = partir(todas, Canal.AMAZON, hoy);
        Partes fnsk = partir(todas, Canal.AMAZON_STOCKROOM, hoy);
        // En las webs se envia todo en el dia: sin dividir por fecha, un bloque por web con el cliente.
        var bloques = ExcelBuscar.webs(todas.stream().filter(f -> WEBS.contains(f.canal())).toList());
        byte[] xlsx = ExcelBuscar.escribirListado(ExcelBuscar.tabla(amz.hoy()), ExcelBuscar.tabla(amz.esperar()),
                ExcelBuscar.tabla(fnsk.hoy()), ExcelBuscar.tabla(fnsk.esperar()), bloques);
        int filas = amz.hoy().size() + amz.esperar().size() + fnsk.hoy().size() + fnsk.esperar().size() + ExcelBuscar.unidades(bloques);
        return respuesta(xlsx, ahora.format(NOMBRE), filas);
    }

    private record Partes(List<FilasDAO.PorBuscar> hoy, List<FilasDAO.PorBuscar> esperar) {}

    /** Las filas de una cuenta de Amazon, partidas por su fecha de salida: hasta hoy (o sin fecha), para hoy; despues, pueden esperar. */
    private static Partes partir(List<FilasDAO.PorBuscar> todas, Canal canal, LocalDate hoy) {
        List<FilasDAO.PorBuscar> paraHoy = new ArrayList<>();
        List<FilasDAO.PorBuscar> puedenEsperar = new ArrayList<>();
        for (FilasDAO.PorBuscar f : todas) {
            if (f.canal() != canal) continue;
            if (f.fechaSalida() == null || !f.fechaSalida().isAfter(hoy)) paraHoy.add(f); else puedenEsperar.add(f);
        }
        return new Partes(paraHoy, puedenEsperar);
    }

    private static ResponseEntity<byte[]> respuesta(byte[] xlsx, String nombre, int filas) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .header(CABECERA_FILAS, String.valueOf(filas))
                .body(xlsx);
    }
}
