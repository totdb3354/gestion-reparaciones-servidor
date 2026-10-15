package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.ValorEntero;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import com.reparaciones.servidor.service.ConversionEur;
import com.reparaciones.servidor.service.DestinoBloque;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraComponenteDAO dao;
    private final LogDAO              logDao;
    private final ComponenteDAO       componenteDao;
    private final ProveedorDAO        proveedorDao;
    private final ConversionEur       conversion;
    private final BloqueCompraService bloques;
    private final RegistroBloques     registroBloques;

    public CompraController(CompraComponenteDAO dao, LogDAO logDao,
                            ComponenteDAO componenteDao, ProveedorDAO proveedorDao,
                            ConversionEur conversion, BloqueCompraService bloques,
                            RegistroBloques registroBloques) {
        this.dao           = dao;
        this.logDao        = logDao;
        this.componenteDao = componenteDao;
        this.proveedorDao  = proveedorDao;
        this.conversion    = conversion;
        this.bloques       = bloques;
        this.registroBloques = registroBloques;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping
    public List<CompraComponente> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/en-camino")
    public List<CompraComponente> getEnCamino() {
        return dao.getEnCamino();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/cantidad-en-camino/{idCom}")
    public ValorEntero getCantidadEnCamino(@PathVariable int idCom) {
        return new ValorEntero(dao.getCantidadEnCaminoPorComponente(idCom));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.componenteActivo(componenteDao, req.idCom());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        // P3: el importe en euros lo calcula el servidor; req.precioEur() se ignora
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        // 0.9.9: el alta cae en el bloque abierto del proveedor o en uno nuevo (regla por defecto).
        DestinoBloque.Destino destino = bloques.altaSuelta(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String tipo = componenteDao.getTipoById(req.idCom());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        if (destino.nuevo()) registroBloques.creado(principal.getIdUsu(), destino.idBloque(), req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO",
                "COMPONENTE: " + tipo + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCompra}")
    public void editar(@PathVariable int idCompra, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        Optional<CompraComponente> actual = dao.getById(idCompra);
        actual.ifPresent(c -> ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        // 0.9.9: otro proveedor saca la línea de su bloque (regla 1: un proveedor por bloque), en la misma transacción.
        if (actual.isPresent() && actual.get().getIdProv() != req.idProv()) {
            BloqueCompraService.Movimiento m = bloques.editarCambiandoProveedor(idCompra, req.idProv(), req.cantidad(),
                    req.esUrgente(), req.precioUnidad(), divisa, precioEur, req.updatedAt(), req.destino());
            logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
            registroBloques.movimiento(principal.getIdUsu(), m);
            return;
        }
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        CompraComponente compra = dao.getById(idCompra).orElse(null);
        dao.confirmarRecibido(idCompra, req.updatedAt());
        String detalle = compra != null
                ? "ID_COMPRA: " + idCompra + ", COMPONENTE: " + compra.getTipoComponente() +
                  ", CANT: " + compra.getCantidad()
                : "ID_COMPRA: " + idCompra;
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int idCompra, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.rangoParcial(req.cantidadRecibida(), c.getCantidad()));
        dao.confirmarParcial(idCompra, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL",
                "ID_COMPRA: " + idCompra + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/recibir-resto")
    public void recibirResto(@PathVariable int idCompra, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.getById(idCompra).ifPresent(c ->
                ValidacionPedidos.rangoResto(req.cantidadExtra(), c.getCantidadRecibida(), c.getCantidad()));
        dao.recibirResto(idCompra, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarAlterado(idCompra, req.updatedAt());
        // Hasta el 4b era la única transición sin log (inventario de Pedidos §8)
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_ALTERADO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/cancelar")
    public void cancelar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/confirmar")
    public void confirmar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmar(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idCompra}")
    public void borrar(@PathVariable int idCompra,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        Optional<Integer> vaciado = bloques.borrarLineaPendiente(idCompra);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO", "ID_COMPRA: " + idCompra);
        vaciado.ifPresent(idBloque -> registroBloques.vaciado(principal.getIdUsu(), idBloque));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCompra}/desrecibir")
    public void desrecibir(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(idCompra, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "DESRECIBIR_PEDIDO", "ID_COMPRA: " + idCompra);
    }

    record InsertarRequest(int idCom, int idProv, int cantidad, boolean esUrgente,
                           double precioUnidad, String divisa,
                           @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                           Double precioEur) {}
    record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa,
                         @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                         Double precioEur,
                         LocalDateTime updatedAt,
                         @Schema(nullable = true, description = "0.9.9: bloque destino si cambia el proveedor; nulo = regla por defecto")
                         DestinoPedido destino) {}
    record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
    record UpdatedAtRequest(LocalDateTime updatedAt) {}
}
