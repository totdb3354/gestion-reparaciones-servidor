package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** La etiqueta de envio ya generada por ShippyPro, para verla en un dialogo (solo lectura, no se guarda). SUPERTECNICO y ADMIN. */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class EtiquetaFilaController {

    static final String MSG_SIN_ETIQUETA = "Este pedido no tiene etiqueta generada";

    private final FilasDAO dao;
    private final EtiquetaShippyPro etiquetas;

    @Autowired
    public EtiquetaFilaController(FilasDAO dao, EtiquetaShippyPro etiquetas) {
        this.dao = dao;
        this.etiquetas = etiquetas;
    }

    @GetMapping(value = "/filas/{id}/etiqueta", produces = MediaType.APPLICATION_PDF_VALUE)
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "404", content = @Content),
        @ApiResponse(responseCode = "502", content = @Content)
    })
    public ResponseEntity<byte[]> etiqueta(@PathVariable int id) {
        FilaPedido fila = dao.porId(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la fila " + id));
        if (fila.envioId() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_SIN_ETIQUETA);
        byte[] pdf = etiquetas.pdf(id, fila.envioId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("etiqueta.pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }
}
