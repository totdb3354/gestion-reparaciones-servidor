package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.ClaveAsociacionesFilter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

/** La asociacion IMEI -> pedido de Amazon (spec «asociación de IMEI y pedido en Amazon»). POST: las pasadas de los libros de ventas; no es
 *  una ruta de usuario: solo entra la clave de maquina que comprueba {@link ClaveAsociacionesFilter}. GET: la ultima pasada de cada canal y
 *  su lista «para revisar», para la web (SUPERTECNICO y ADMIN, con su token). */
@RestController
@RequestMapping(ClaveAsociacionesFilter.RUTA)
public class AsociacionesController {

    static final int MAX_PARES = 10_000;
    static final String MSG_CANAL = "canal debe ser AMAZON o AMAZON_STOCKROOM";
    static final String MSG_PARES = "pares es obligatorio y admite hasta 10000";

    private final AsociacionAmazon asociacion;
    private final Clock clock;

    @Autowired
    public AsociacionesController(AsociacionAmazon asociacion) {
        this(asociacion, Clock.system(ImportadorNsys.MADRID));
    }

    AsociacionesController(AsociacionAmazon asociacion, Clock clock) {
        this.asociacion = asociacion;
        this.clock = clock;
    }

    /** Una pasada del libro de ventas de un canal de Amazon: aplica las asociaciones y rehace la lista «para revisar» de ese canal. */
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RespuestaAsociaciones.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "401", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasRole('" + ClaveAsociacionesFilter.ROL + "')")
    public RespuestaAsociaciones asociar(@RequestBody PeticionAsociaciones req) {
        Canal canal = null;
        if (req.canal() != null) {
            try {
                canal = Canal.valueOf(req.canal());
            } catch (IllegalArgumentException e) {
                // cae en el 400 de abajo
            }
        }
        if (canal == null || !canal.esAmazon()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CANAL);
        if (req.pares() == null || req.pares().size() > MAX_PARES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_PARES);
        return asociacion.aplicar(canal, req.pares(), LocalDateTime.now(clock));
    }

    /** La ultima pasada de cada canal que ha tenido alguna, con su lista «para revisar». */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    public EstadoAsociaciones estado() {
        return asociacion.estado();
    }
}
