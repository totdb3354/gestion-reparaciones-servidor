package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.SolicitudStock;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/solicitudes-stock")
public class SolicitudStockController {

    private final SolicitudStockDAO dao;
    private final LogDAO            logDao;

    public SolicitudStockController(SolicitudStockDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<SolicitudStock> getSolicitudes(
            @RequestParam(required = false) String estado) {
        return dao.getSolicitudes(estado);
    }

    @GetMapping("/count")
    public Map<String, Object> count() {
        return Map.of("value", dao.contarPendientes());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.idCom(), principal.getIdUsu(), req.descripcion());
        logDao.insertar(principal.getIdUsu(), "SOLICITAR_STOCK",
                "ID_COM: " + req.idCom());
    }

    @PatchMapping("/{idSol}/estado")
    public void actualizarEstado(@PathVariable int idSol,
                                  @RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.get("estado");
        dao.actualizarEstado(idSol, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado)
                ? "RECHAZAR_SOLICITUD_STOCK" : "GESTIONAR_SOLICITUD_STOCK";
        logDao.insertar(principal.getIdUsu(), accion, "ID_SOL: " + idSol + ", ESTADO: " + estado);
    }

    @DeleteMapping("/{idSol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idSol,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrar(idSol);
        logDao.insertar(principal.getIdUsu(), "BORRAR_SOLICITUD_STOCK", "ID_SOL: " + idSol);
    }

    private record InsertarRequest(int idCom, String descripcion) {}
}
