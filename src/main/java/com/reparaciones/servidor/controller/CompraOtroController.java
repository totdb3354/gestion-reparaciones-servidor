package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ConversionEur;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private final ConversionEur conversion;

    public CompraOtroController(CompraOtroDAO dao, LogDAO logDao, ProveedorDAO proveedorDao,
                                ConversionEur conversion) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.proveedorDao = proveedorDao;
        this.conversion   = conversion;
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
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        // P3: el importe en euros lo calcula el servidor; req.precioEur() se ignora
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.insertar(req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO_OTRO",
                "CONCEPTO: " + req.concepto() + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{id}")
    public void editar(@PathVariable int id, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        ValidacionPedidos.conceptoInformado(req.concepto());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        dao.getById(id).ifPresent(c ->
                ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        dao.editar(id, req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur, req.updatedAt());
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
        dao.getById(id).ifPresent(c -> ValidacionPedidos.rangoParcial(req.cantidadRecibida(), c.getCantidad()));
        dao.confirmarParcial(id, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL_OTRO",
                "ID_COMPRA_OTRO: " + id + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/recibir-resto")
    public void recibirResto(@PathVariable int id, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(id).ifPresent(c ->
                ValidacionPedidos.rangoResto(req.cantidadExtra(), c.getCantidadRecibida(), c.getCantidad()));
        dao.recibirResto(id, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: " + id);
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

    record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa,
                           @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                           Double precioEur) {}
    record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa,
                         @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                         Double precioEur,
                         LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
}
