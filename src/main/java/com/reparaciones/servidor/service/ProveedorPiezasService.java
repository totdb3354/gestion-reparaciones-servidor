package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Sustituye las piezas que vende un proveedor (spec 2026-10-11 §4.3): todo o nada, guardando el master de cada pieza. */
@Service
public class ProveedorPiezasService {

    public static final String MSG_NO_ENCONTRADO = "Proveedor no encontrado.";
    public static final String MSG_SOLO_COMPONENTES = "Solo los proveedores de componentes tienen piezas.";
    public static final String MSG_PIEZA_NO_EXISTE = "Alguna pieza ya no existe.";
    public static final String MSG_SIN_LISTA = "Falta la lista de piezas.";

    public record Cambios(int anadidas, int quitadas) {}

    private final ProveedorDAO proveedorDao;
    private final ProveedorComponenteDAO catalogo;

    public ProveedorPiezasService(ProveedorDAO proveedorDao, ProveedorComponenteDAO catalogo) {
        this.proveedorDao = proveedorDao;
        this.catalogo = catalogo;
    }

    @Transactional
    public Cambios reemplazar(int idProv, List<Integer> idsCom) {
        Proveedor p = proveedorDao.getByIdParaEditar(idProv)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADO));
        if (!ProveedorDAO.TIPO_COMPONENTES.equals(p.getTipo())) throw regla(MSG_SOLO_COMPONENTES);
        if (idsCom == null) throw regla(MSG_SIN_LISTA);
        // Sin contains(null): en una lista inmutable (List.of) lanza NullPointerException.
        if (idsCom.stream().anyMatch(Objects::isNull)) throw regla(MSG_PIEZA_NO_EXISTE);
        Set<Integer> distintos = new LinkedHashSet<>(idsCom);
        Map<Integer, Integer> masters = catalogo.mastersDe(distintos);
        if (masters.size() != distintos.size()) throw regla(MSG_PIEZA_NO_EXISTE);
        Set<Integer> nuevas = new TreeSet<>(masters.values());
        Set<Integer> actuales = catalogo.piezasDe(idProv);
        List<Integer> anadir = nuevas.stream().filter(id -> !actuales.contains(id)).toList();
        List<Integer> quitar = actuales.stream().filter(id -> !nuevas.contains(id)).sorted().toList();
        if (!quitar.isEmpty()) catalogo.quitar(idProv, quitar);
        if (!anadir.isEmpty()) catalogo.anadir(idProv, anadir);
        return new Cambios(anadir.size(), quitar.size());
    }

    private static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
