package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraComponenteDAO dao;

    public CompraController(CompraComponenteDAO dao) {
        this.dao = dao;
    }

    @GetMapping
    public List<CompraComponente> getAll() {
        return dao.getAll();
    }

    @GetMapping("/pendientes")
    public List<CompraComponente> getPendientes() {
        return dao.getPendientes();
    }

    @GetMapping("/cantidad-pendiente/{idCom}")
    public Map<String, Object> getCantidadPendiente(@PathVariable int idCom) {
        return Map.of("value", dao.getCantidadPendientePorComponente(idCom));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req) {
        dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur());
    }

    @PutMapping("/{idCompra}")
    public void editar(@PathVariable int idCompra, @RequestBody EditarRequest req) {
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur(), req.updatedAt());
    }

    @PatchMapping("/{idCompra}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req) {
        dao.confirmarRecibido(idCompra, req.updatedAt());
    }

    @PatchMapping("/{idCompra}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int idCompra, @RequestBody ConfirmarParcialRequest req) {
        dao.confirmarParcial(idCompra, req.cantidadRecibida(), req.updatedAt());
    }

    @PatchMapping("/{idCompra}/recibir-resto")
    public void recibirResto(@PathVariable int idCompra, @RequestBody RecibirRestoRequest req) {
        dao.recibirResto(idCompra, req.cantidadExtra(), req.updatedAt());
    }

    @PatchMapping("/{idCompra}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req) {
        dao.confirmarAlterado(idCompra, req.updatedAt());
    }

    @PatchMapping("/{idCompra}/cancelar")
    public void cancelar(@PathVariable int idCompra, @RequestBody UpdatedAtRequest req) {
        dao.cancelar(idCompra, req.updatedAt());
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
