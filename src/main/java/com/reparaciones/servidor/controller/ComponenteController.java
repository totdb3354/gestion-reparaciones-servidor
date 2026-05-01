package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.PuntoStock;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/componentes")
public class ComponenteController {

    private final ComponenteDAO dao;

    public ComponenteController(ComponenteDAO dao) {
        this.dao = dao;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req) {
        dao.insertar(req.tipo(), req.stock(), req.stockMinimo());
    }

    @PutMapping("/{idCom}")
    public void actualizar(@PathVariable int idCom, @RequestBody ActualizarRequest req) {
        dao.actualizar(idCom, req.tipo(), req.stock(), req.stockMinimo(), req.updatedAt());
    }

    @PatchMapping("/{idCom}/stock-minimo")
    public void setStockMinimo(@PathVariable int idCom, @RequestBody StockMinimoRequest req) {
        dao.setStockMinimo(idCom, req.stockMinimo());
    }

    @PatchMapping("/{idCom}/stock")
    public void actualizarStock(@PathVariable int idCom, @RequestBody DeltaRequest req) {
        dao.actualizarStock(idCom, req.delta());
    }

    @PatchMapping("/{idCom}/activo")
    public void setActivo(@PathVariable int idCom, @RequestBody ActivoRequest req) {
        dao.setActivo(idCom, req.activo());
    }

    @DeleteMapping("/{idCom}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int idCom) {
        dao.eliminar(idCom);
    }

    private record InsertarRequest(String tipo, int stock, int stockMinimo) {}
    private record ActualizarRequest(String tipo, int stock, int stockMinimo, LocalDateTime updatedAt) {}
    private record StockMinimoRequest(int stockMinimo) {}
    private record DeltaRequest(int delta) {}
    private record ActivoRequest(boolean activo) {}
}
