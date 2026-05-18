package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraComponenteDAO dao;
    private final LogDAO              logDao;

    public CompraController(CompraComponenteDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping
    public List<CompraComponente> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/pendientes")
    public List<CompraComponente> getPendientes() {
        return dao.getPendientes();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-pendiente/{idCom}")
    public Map<String, Object> getCantidadPendiente(@PathVariable int idCom) {
        return Map.of("value", dao.getCantidadPendientePorComponente(idCom));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO",
                "ID_COM: " + req.idCom() + ", ID_PROV: " + req.idProv() + ", CANTIDAD: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCompra}")
    public void editar(@PathVariable int idCompra, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarRecibido(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int idCompra, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarParcial(idCompra, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL",
                "ID_COMPRA: " + idCompra + ", CANTIDAD: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/recibir-resto")
    public void recibirResto(@PathVariable int idCompra, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.recibirResto(idCompra, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req) {
        dao.confirmarAlterado(idCompra, req.updatedAt());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/cancelar")
    public void cancelar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/desrecibir")
    public void desrecibir(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "DESRECIBIR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    private record InsertarRequest(int idCom, int idProv, int cantidad, boolean esUrgente,
                                   double precioUnidad, String divisa, double precioEur) {}
    private record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                                 double precioUnidad, String divisa, double precioEur,
                                 LocalDateTime updatedAt) {}
    private record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    private record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
    private record UpdatedAtRequest(LocalDateTime updatedAt) {}
}
