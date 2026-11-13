package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** La carga a mano del histórico de la hoja de pedidos (spec «Pedidos como la hoja» §8): solo ADMIN. */
@RestController
@RequestMapping("/api/pedidos/historico")
@PreAuthorize("hasRole('ADMIN')")
public class HistoricoController {

    private final CargaHistorico carga;

    @Autowired
    public HistoricoController(CargaHistorico carga) {
        this.carga = carga;
    }

    /** Arranca la carga en segundo plano: {@code 202} con el estado EN_CURSO; {@code 409} si ya hay una en marcha; {@code 400} si falta
     *  el ajuste de la hoja. */
    @ApiResponses({
        @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = CargaHistorico.EstadoCarga.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CargaHistorico.EstadoCarga> cargar() {
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(carga.iniciar());
        } catch (CargaHistorico.NoConfigurada e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (CargaHistorico.YaEnCurso e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** El estado de la última carga de este arranque: NUNCA, EN_CURSO, OK (con el informe) o ERROR (con el motivo). */
    @GetMapping
    public CargaHistorico.EstadoCarga estado() {
        return carga.estado();
    }
}
