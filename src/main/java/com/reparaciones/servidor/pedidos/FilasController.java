package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    static final String MSG_CAMBIADO = "Otra persona ha cambiado el IMEI de esta fila mientras tanto. Recarga y vuelve a intentarlo.";
    static final String MSG_HISTORICO = "La fila es del histórico y no se edita";
    static final String MSG_FALTA = "Indica si falta enviar (true o false)";
    static final String MSG_NO_MARCABLE = "Solo se puede marcar en un pedido de web con la etiqueta ya generada";

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

    /** Quien ha tocado el IMEI de la fila, del mas reciente al mas antiguo; lista vacia en una fila del historico (no hay registro). */
    @GetMapping("/filas/{id}/cambios")
    public List<CambioImei> cambios(@PathVariable int id) {
        FilaPedido fila = dao.porId(id).orElseThrow(() -> noExiste(id));
        if (VistaFilas.HISTORICO.equals(fila.origen())) return List.of();
        return dao.cambiosDeFila(id);
    }

    /** Pone, cambia o quita (imei nulo o en blanco) el IMEI de una fila de ShippyPro (también enviada o no pendiente). Devuelve la fila releida con su aviso. */
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FilaVista.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    @PutMapping("/filas/{id}/imei")
    public FilaVista ponerImei(@PathVariable int id, @RequestBody PeticionImei req,
                               @AuthenticationPrincipal UsuarioPrincipal principal,
                               @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        String imei = req.imei() == null || req.imei().isBlank() ? null : req.imei().strip();
        if (imei != null && !imei.matches("\\d{15}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_IMEI);
        String anterior = req.imeiAnterior() == null ? null : req.imeiAnterior().strip();
        if (anterior != null && !anterior.isEmpty() && !anterior.matches("[0-9]{15}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_IMEI);
        FilaPedido fila = dao.porId(id).orElseThrow(() -> noExiste(id));
        if (VistaFilas.HISTORICO.equals(fila.origen()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_HISTORICO);
        return idempotencia.ejecutar(principal.getIdUsu(), "pedido-imei", claveIdempotencia, new Cambio(id, imei, anterior), () -> {
            // El origen se decide en la transaccion del DAO, con la fila bloqueada.
            FilasDAO.Resultado r = dao.ponerImei(id, imei, anterior, principal.getIdUsu(), LocalDateTime.now(clock));
            if (r != FilasDAO.Resultado.PUESTO) {
                if (r == FilasDAO.Resultado.NO_EXISTE) throw noExiste(id);
                if (r == FilasDAO.Resultado.CAMBIADA) throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_CAMBIADO);
                throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_HISTORICO);
            }
            return vista.fila(id).orElseThrow(() -> noExiste(id));
        });
    }

    /** Marca ({@code falta} true) o desmarca el movil de una fila como «falta enviar» (envio parcial de un pedido de web: la etiqueta salio
     *  con todos los moviles y este todavia no). Marcar exige una fila de ShippyPro de un canal web con la etiqueta generada; marcar lo ya
     *  marcado o desmarcar lo no marcado no hace nada. Devuelve la fila releida. */
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FilaVista.class))),
        @ApiResponse(responseCode = "400", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
    @PutMapping("/filas/{id}/falta-enviar")
    public FilaVista ponerFaltaEnviar(@PathVariable int id, @RequestBody PeticionFaltaEnviar req,
                                      @AuthenticationPrincipal UsuarioPrincipal principal,
                                      @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        if (req.falta() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_FALTA);
        boolean falta = req.falta();
        FilaPedido fila = dao.porId(id).orElseThrow(() -> noExiste(id));
        if (VistaFilas.HISTORICO.equals(fila.origen()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_HISTORICO);
        return idempotencia.ejecutar(principal.getIdUsu(), "pedido-falta-enviar", claveIdempotencia, new CambioFalta(id, falta), () -> {
            // Las reglas se deciden en la transaccion del DAO, con la fila bloqueada.
            FilasDAO.Resultado r = dao.ponerFaltaEnviar(id, falta, principal.getIdUsu(), LocalDateTime.now(clock));
            if (r == FilasDAO.Resultado.NO_EXISTE) throw noExiste(id);
            if (r == FilasDAO.Resultado.CERRADA) throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_HISTORICO);
            if (r == FilasDAO.Resultado.NO_MARCABLE) throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_NO_MARCABLE);
            return vista.fila(id).orElseThrow(() -> noExiste(id));
        });
    }

    @GetMapping("/fuentes")
    public List<FuenteVista> fuentes() {
        return vista.fuentes();
    }

    private record Cambio(int id, String imei, String imeiAnterior) {}

    private record CambioFalta(int id, boolean falta) {}

    private static ResponseStatusException noExiste(int id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la fila " + id);
    }
}
