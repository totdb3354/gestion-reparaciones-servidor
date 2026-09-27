package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.SolicitudStock;
import com.reparaciones.servidor.model.ValorEntero;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes-stock")
public class SolicitudStockController {

    /** Nombre de la operación en {@link RegistroIdempotencia}. */
    static final String OP_ALTA = "alta-solicitud-stock";

    private final SolicitudStockDAO    dao;
    private final LogDAO               logDao;
    private final RegistroIdempotencia idempotencia;

    public SolicitudStockController(SolicitudStockDAO dao, LogDAO logDao, RegistroIdempotencia idempotencia) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping
    public List<SolicitudStock> getSolicitudes(
            @RequestParam(required = false) String estado) {
        return dao.getSolicitudes(estado);
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/count")
    public ValorEntero count() {
        return new ValorEntero(dao.contarPendientes());
    }

    @PreAuthorize("hasAnyRole('TECNICO','SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal,
                         @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        // Con clave, un reintento con el mismo cuerpo devuelve el 201 de la primera vez sin crear otra solicitud.
        idempotencia.ejecutar(principal.getIdUsu(), OP_ALTA, claveIdempotencia, req,
                () -> {
                    dao.insertar(req.idCom(), principal.getIdUsu(), req.descripcion());
                    return null;
                },
                ignorado -> logDao.insertar(principal.getIdUsu(), "SOLICITAR_STOCK",
                        "ID_COM: " + req.idCom()));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idSol}/estado")
    public void actualizarEstado(@PathVariable int idSol,
                                  @RequestBody EstadoRequest body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.estado();
        dao.actualizarEstado(idSol, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado)
                ? "RECHAZAR_SOLICITUD_STOCK" : "GESTIONAR_SOLICITUD_STOCK";
        logDao.insertar(principal.getIdUsu(), accion, "ID_SOL: " + idSol + ", ESTADO: " + estado);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idSol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idSol,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrar(idSol);
        logDao.insertar(principal.getIdUsu(), "BORRAR_SOLICITUD_STOCK", "ID_SOL: " + idSol);
    }

    private record InsertarRequest(int idCom, @Schema(nullable = true) String descripcion) {}
    private record EstadoRequest(String estado) {}
}
