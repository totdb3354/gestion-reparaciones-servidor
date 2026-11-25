package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.security.ClaveBotFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Las rutas del bot de stock de NSYS (spec 2026-11-24-bot-devoluciones §6). No son de usuario: solo entra la clave de maquina que
 *  comprueba {@link ClaveBotFilter}. */
@RestController
@RequestMapping(ClaveBotFilter.RUTA_BASE)
@PreAuthorize("hasRole('" + ClaveBotFilter.ROL + "')")
public class BotController {

    static final int MAX_MOVIMIENTOS = 2000;

    private final Devoluciones devoluciones;

    public BotController(Devoluciones devoluciones) {
        this.devoluciones = devoluciones;
    }

    /** Los IMEI a devolver, por caja de destino. */
    @GetMapping("/devoluciones")
    public ListaDevoluciones devoluciones() {
        return devoluciones.lista();
    }

    /** Lo que el bot movio en una vuelta; los repetidos (misma vuelta, IMEI y accion) no se guardan dos veces. */
    @PostMapping("/movimientos")
    public InformeMovimientos movimientos(@RequestBody MovimientosBot cuerpo) {
        validar(cuerpo);
        return devoluciones.registrar(cuerpo);
    }

    static void validar(MovimientosBot c) {
        if (c == null || vacio(c.run()) || c.run().length() > 40) throw mal("run es obligatorio (40 caracteres como mucho)");
        if (c.movimientos() == null || c.movimientos().size() > MAX_MOVIMIENTOS)
            throw mal("movimientos es obligatorio (" + MAX_MOVIMIENTOS + " como mucho)");
        for (MovimientoBot m : c.movimientos()) {
            if (m == null || vacio(m.imei()) || m.imei().length() > 40) throw mal("imei es obligatorio (40 caracteres como mucho)");
            if (!ReglaDevoluciones.ACCIONES.contains(m.accion())) throw mal("accion debe ser DESCONTADO o DEVUELTO");
            if (!ReglaDevoluciones.RESULTADOS.contains(m.resultado())) throw mal("resultado debe ser OK, NO_APARECE, ERROR o VENDIDO_PANEL");
            if (ReglaDevoluciones.DEVUELTO.equals(m.accion()) && vacio(m.destino())) throw mal("destino es obligatorio en DEVUELTO");
            if (m.fecha() == null) throw mal("fecha es obligatoria");
            if (largo(m.origen(), 80) || largo(m.destino(), 80) || largo(m.motivo(), 30)) throw mal("origen o destino de mas de 80 caracteres, o motivo de mas de 30");
        }
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static boolean largo(String s, int max) { return s != null && s.length() > max; }
    private static ResponseStatusException mal(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
}
