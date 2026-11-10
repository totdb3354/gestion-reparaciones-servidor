package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** «Pedidos» como la hoja (spec «Pedidos como la hoja» §7): SUPERTECNICO y ADMIN (no hay jerarquia de roles: van los dos nombrados). */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class FilasController {

    static final int MIN_BUSCAR = 2;
    static final String MSG_BUSCAR_CORTO = "Escribe al menos 2 caracteres";
    static final String MSG_IMEI = "Un IMEI son 15 cifras";
    static final String MSG_ENVIADA = "La fila ya está enviada";

    private final VistaFilas vista;
    private final FilasDAO dao;
    private final RegistroIdempotencia idempotencia;
    private final Clock clock;

    @Autowired
    public FilasController(VistaFilas vista, FilasDAO dao, RegistroIdempotencia idempotencia) {
        this(vista, dao, idempotencia, Clock.system(ImportadorNsys.MADRID));
    }

    FilasController(VistaFilas vista, FilasDAO dao, RegistroIdempotencia idempotencia, Clock clock) {
        this.vista = vista;
        this.dao = dao;
        this.idempotencia = idempotencia;
        this.clock = clock;
    }

    /** Sin {@code buscar}: el periodo (por defecto, el ultimo mes hasta hoy) mas las vivas. Con {@code buscar} (2 caracteres o mas):
     *  todo el historico, las 500 mas recientes. */
    @GetMapping("/filas")
    public ListaFilas filas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                            @RequestParam(required = false) String buscar) {
        String texto = buscar == null ? "" : buscar.strip();
        if (!texto.isEmpty() && texto.length() < MIN_BUSCAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_BUSCAR_CORTO);
        return vista.filas(desde, hasta, texto);
    }

    @GetMapping("/filas/{id}/recomendados")
    public List<Recomendador.Candidato> recomendados(@PathVariable int id) {
        return vista.recomendados(id).orElseThrow(() -> noExiste(id));
    }

    /** Pone, cambia o quita (imei nulo o en blanco) el IMEI de una fila sin enviar. Devuelve la fila releida con su aviso. */
    @PutMapping("/filas/{id}/imei")
    public FilaVista ponerImei(@PathVariable int id, @RequestBody PeticionImei req,
                               @AuthenticationPrincipal UsuarioPrincipal principal,
                               @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        String imei = req.imei() == null || req.imei().isBlank() ? null : req.imei().strip();
        if (imei != null && !imei.matches("\\d{15}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_IMEI);
        FilaPedido fila = dao.porId(id).orElseThrow(() -> noExiste(id));
        if (!fila.viva() || VistaFilas.HISTORICO.equals(fila.origen()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_ENVIADA);
        return idempotencia.ejecutar(principal.getIdUsu(), "pedido-imei", claveIdempotencia, new Cambio(id, imei), () -> {
            dao.ponerImei(id, imei, principal.getIdUsu(), LocalDateTime.now(clock));
            return vista.fila(id).orElseThrow(() -> noExiste(id));
        });
    }

    @GetMapping("/fuentes")
    public List<FuenteVista> fuentes() {
        return vista.fuentes();
    }

    private record Cambio(int id, String imei) {}

    private static ResponseStatusException noExiste(int id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la fila " + id);
    }
}
