package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** «Descargar para buscar»: el Excel de los moviles que aun hay que buscar de una plataforma, en dos tablas (para hoy y pueden esperar); las webs, en un bloque por web y cliente.
 *  SUPERTECNICO y ADMIN. */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class BuscarController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Cuantos moviles lleva el Excel entre las dos tablas (mismo nombre que en las plantillas de «Por pasar a NSYS»). */
    static final String CABECERA_FILAS = "X-Filas";
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd-MM");

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
                schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "400", content = @Content)
    })
    public ResponseEntity<byte[]> buscar(@RequestParam String plataforma) {
        List<Canal> canales = switch (plataforma) {
            case "AMZ" -> List.of(Canal.AMAZON);
            case "FNSK" -> List.of(Canal.AMAZON_STOCKROOM);
            case "WEBS" -> List.of(Canal.DISTRIBUIDORES, Canal.PARTICULARES, Canal.BACKIPHONE);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La plataforma debe ser AMZ, FNSK o WEBS");
        };
        LocalDate hoy = LocalDate.now(clock);
        String nombre = "Buscar " + plataforma + " " + hoy.format(DIA_MES) + ".xlsx";
        if (plataforma.equals("WEBS")) {
            // En las webs se envia todo en el dia: sin dividir por fecha, un bloque por web con el cliente.
            var webs = ExcelBuscar.webs(dao.porBuscar(canales));
            return respuesta(ExcelBuscar.escribirWebs(plataforma, webs), nombre, ExcelBuscar.unidades(webs));
        }
        List<FilasDAO.PorBuscar> paraHoy = new ArrayList<>();
        List<FilasDAO.PorBuscar> puedenEsperar = new ArrayList<>();
        for (FilasDAO.PorBuscar f : dao.porBuscar(canales)) {
            if (f.fechaSalida() == null || !f.fechaSalida().isAfter(hoy)) paraHoy.add(f); else puedenEsperar.add(f);
        }
        byte[] xlsx = ExcelBuscar.escribir(plataforma, ExcelBuscar.tabla(paraHoy), ExcelBuscar.tabla(puedenEsperar));
        return respuesta(xlsx, nombre, paraHoy.size() + puedenEsperar.size());
    }

    private static ResponseEntity<byte[]> respuesta(byte[] xlsx, String nombre, int filas) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .header(CABECERA_FILAS, String.valueOf(filas))
                .body(xlsx);
    }
}
