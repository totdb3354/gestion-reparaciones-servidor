package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/compras-otros")
public class CompraOtroController {

    private final CompraOtroDAO dao;
    private final LogDAO        logDao;
    private final ProveedorDAO  proveedorDao;

    public CompraOtroController(CompraOtroDAO dao, LogDAO logDao, ProveedorDAO proveedorDao) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.proveedorDao = proveedorDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<CompraOtro> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO_OTRO",
                "CONCEPTO: " + req.concepto() + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{id}")
    public void editar(@PathVariable int id, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.editar(id, req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar")
    public void confirmar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        CompraOtro c = dao.getById(id).orElse(null);
        dao.confirmarRecibido(id, req.updatedAt());
        String detalle = c != null
                ? "ID_COMPRA_OTRO: " + id + ", CONCEPTO: " + c.getConcepto() + ", CANT: " + c.getCantidad()
                : "ID_COMPRA_OTRO: " + id;
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO_OTRO", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int id, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarParcial(id, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL_OTRO",
                "ID_COMPRA_OTRO: " + id + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/recibir-resto")
    public void recibirResto(@PathVariable int id, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.recibirResto(id, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int id, @RequestBody UpdatedAtRequest req) {
        dao.confirmarAlterado(id, req.updatedAt());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/desrecibir")
    public void desrecibir(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "DESRECIBIR ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/cancelar")
    public void cancelar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{id}")
    public void borrar(@PathVariable int id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrarPendiente(id);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    private record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                                   double precioUnidad, String divisa, double precioEur) {}
    private record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                                 double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {}
    private record UpdatedAtRequest(LocalDateTime updatedAt) {}
    private record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    private record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
}
