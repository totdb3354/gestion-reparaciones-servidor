package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.ClaveSincronizadosFilter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** El aviso del plugin de la web: «estos moviles ya van en el pedido de la web». No es una ruta de usuario: solo entra la clave de maquina
 *  que comprueba {@link ClaveSincronizadosFilter}. Pone SINCRONIZADA_EN en las filas y quita la marca «falta enviar» de las que la tengan.
 *  Idempotente: repetir la llamada no apunta nada nuevo. */
@RestController
@RequestMapping(ClaveSincronizadosFilter.RUTA)
@PreAuthorize("hasRole('" + ClaveSincronizadosFilter.ROL + "')")
public class SincronizadosController {

    static final int MAX_PEDIDOS = 500;
    static final int MAX_IMEIS = 50;
    static final String MSG_CANAL = "canal debe ser DISTRIBUIDORES, PARTICULARES o BACKIPHONE";
    static final String MSG_PEDIDOS = "pedidos es obligatorio y admite hasta 500";
    static final String MSG_PEDIDO = "Cada pedido necesita su numero";
    static final String MSG_IMEIS = "imeis es obligatorio, hasta 50 por pedido, de 15 cifras cada uno";

    private final FilasDAO dao;
    private final Clock clock;

    @Autowired
    public SincronizadosController(FilasDAO dao) {
        this(dao, Clock.system(ImportadorNsys.MADRID));
    }

    SincronizadosController(FilasDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Cuerpo: canal web y la lista de pedidos con los IMEI que ya van en cada uno. */
    public record Peticion(String canal, List<PedidoImeis> pedidos) {}

    public record PedidoImeis(String pedido, List<String> imeis) {}

    /** Recuentos: filas puestas al dia, marcas quitadas e IMEI enviados que no casaron con ninguna fila. */
    public record Respuesta(int filasSincronizadas, int marcasQuitadas, int sinFila) {}

    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Respuesta.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "401", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content)
    })
    @PostMapping
    public Respuesta sincronizar(@RequestBody Peticion req) {
        Canal canal = null;
        if (req.canal() != null) {
            try {
                canal = Canal.valueOf(req.canal());
            } catch (IllegalArgumentException e) {
                // cae en el 400 de abajo
            }
        }
        if (canal == null || !canal.esWeb()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CANAL);
        if (req.pedidos() == null || req.pedidos().size() > MAX_PEDIDOS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_PEDIDOS);
        // Un mismo numero repetido se junta en uno (con la union de sus IMEI): el numero se normaliza como lo guarda la lectura de ShippyPro.
        Map<String, Set<String>> porPedido = new LinkedHashMap<>();
        for (PedidoImeis p : req.pedidos()) {
            if (p == null || p.pedido() == null || p.pedido().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_PEDIDO);
            if (p.imeis() == null || p.imeis().size() > MAX_IMEIS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_IMEIS);
            Set<String> imeis = porPedido.computeIfAbsent(SincroShippyPro.numeroGuardado(p.pedido()), k -> new LinkedHashSet<>());
            for (String imei : p.imeis()) {
                String limpio = imei == null ? "" : imei.strip();
                if (!limpio.matches("[0-9]{15}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_IMEIS);
                imeis.add(limpio);
            }
        }
        List<FilasDAO.PedidoSincronizado> pedidos = new ArrayList<>();
        porPedido.forEach((numero, imeis) -> pedidos.add(new FilasDAO.PedidoSincronizado(numero, imeis)));
        FilasDAO.InformeSincronizacion r = dao.sincronizar(canal, pedidos, LocalDateTime.now(clock));
        return new Respuesta(r.filasSincronizadas(), r.marcasQuitadas(), r.sinFila());
    }
}
