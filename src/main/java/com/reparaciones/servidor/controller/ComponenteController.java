package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.PuntoStock;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/componentes")
public class ComponenteController {

    private final ComponenteDAO dao;
    private final LogDAO        logDao;

    public ComponenteController(ComponenteDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<Componente> getAll() {
        return dao.getAll();
    }

    @GetMapping("/gestionados")
    public List<Componente> getAllGestionados() {
        return dao.getAllGestionados();
    }

    @GetMapping("/stock-bajo")
    public List<Componente> getStockBajo() {
        return dao.getStockBajo();
    }

    @GetMapping("/agrupados")
    public Map<String, List<Componente>> getAgrupadosPorTipo() {
        return dao.getAgrupadosPorTipo();
    }

    @GetMapping("/chasis")
    public List<Componente> getChasisPorColor(@RequestParam String color) {
        return dao.getChasisPorColor(color);
    }

    @GetMapping("/evolucion-stock")
    public List<PuntoStock> getEvolucionStock(
            @RequestParam String granularidad,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dao.getEvolucionStock(granularidad, desde, hasta);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.tipo(), req.stock(), req.stockMinimo());
        logDao.insertar(principal.getIdUsu(), "CREAR_COMPONENTE", "TIPO: " + req.tipo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idCom}")
    public void actualizar(@PathVariable int idCom, @RequestBody ActualizarRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        int stockAnt = dao.getStockById(idCom);
        dao.actualizar(idCom, req.tipo(), req.stock(), req.stockMinimo(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
                "ID_COM: " + idCom + ", TIPO: " + req.tipo() +
                ", STOCK_ANT: " + stockAnt + " → STOCK_NUE: " + req.stock());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCom}/stock-minimo")
    public void setStockMinimo(@PathVariable int idCom, @RequestBody StockMinimoRequest req,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.setStockMinimo(idCom, req.stockMinimo());
        logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
                "ID_COM: " + idCom + ", STOCK_MINIMO: " + req.stockMinimo());
    }

    @PatchMapping("/{idCom}/stock")
    public void actualizarStock(@PathVariable int idCom, @RequestBody DeltaRequest req) {
        dao.actualizarStock(idCom, req.delta());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCom}/activo")
    public void setActivo(@PathVariable int idCom, @RequestBody ActivoRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.setActivo(idCom, req.activo());
        String accion = req.activo() ? "ALTA_COMPONENTE" : "BAJA_COMPONENTE";
        logDao.insertar(principal.getIdUsu(), accion, "ID_COM: " + idCom);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idCom}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idCom,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        String tipo = dao.getTipoById(idCom);
        dao.eliminar(idCom);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_COMPONENTE",
                "ID_COM: " + idCom + ", TIPO: " + tipo);
    }

    private record InsertarRequest(String tipo, int stock, int stockMinimo) {}
    private record ActualizarRequest(String tipo, int stock, int stockMinimo, LocalDateTime updatedAt) {}
    private record StockMinimoRequest(int stockMinimo) {}
    private record DeltaRequest(int delta) {}
    private record ActivoRequest(boolean activo) {}
}
