package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ProveedorTipoDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.util.CatalogoPiezas;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Tipos de pieza que vende cada proveedor (0.9.9, spec 2026-10-12 §4): leerlos con los modelos de cada tipo,
 *  sustituirlos (todo o nada) y traducirlos a la lista de proveedores de cada pieza del listado de Stock. */
@Service
public class ProveedorPiezasService {

    public static final String MSG_NO_ENCONTRADO = "Proveedor no encontrado.";
    public static final String MSG_SOLO_COMPONENTES = "Solo los proveedores de componentes tienen piezas.";
    public static final String MSG_SIN_TIPOS = "Falta la lista de tipos.";
    public static final String MSG_SIN_EXCLUIDOS = "Falta la lista de modelos excluidos.";
    public static final String MSG_TIPO_NO_EXISTE = "Algún tipo de pieza no existe.";
    public static final String MSG_TIPO_REPETIDO = "Un tipo de pieza está repetido.";
    public static final String MSG_MODELO_NO_EXISTE = "Algún modelo no existe.";
    public static final String MSG_NINGUN_MODELO = "Si no vende ningún modelo de un tipo, desmarca el tipo.";

    /** Un tipo que vende el proveedor, con los modelos que no vende. */
    public record Entrada(String prefijo, List<String> excluidos) {}

    /** Un tipo que existe, con sus modelos (los de sus piezas activas y los excluidos) y si el proveedor lo vende. */
    public record TipoProveedor(String prefijo, boolean vende, List<String> modelos, List<String> excluidos) {}

    /** Tipos que existen (en su orden) y modelos de las piezas activas de cada uno. */
    private record Catalogo(List<String> tipos, Map<String, Set<String>> modelosActivos) {}

    private final ProveedorDAO proveedorDao;
    private final ProveedorTipoDAO tiposDao;

    public ProveedorPiezasService(ProveedorDAO proveedorDao, ProveedorTipoDAO tiposDao) {
        this.proveedorDao = proveedorDao;
        this.tiposDao = tiposDao;
    }

    public List<TipoProveedor> leer(int idProv) {
        comprobar(proveedorDao.getById(idProv));
        Catalogo cat = catalogo();
        Map<String, Set<String>> suyos = tiposDao.deProveedor(idProv);
        return cat.tipos().stream().map(p -> {
            Set<String> excluidos = suyos.get(p);
            Set<String> modelos = new HashSet<>(cat.modelosActivos().getOrDefault(p, Set.of()));
            if (excluidos != null) modelos.addAll(excluidos);
            return new TipoProveedor(p, excluidos != null, CatalogoPiezas.ordenarModelos(modelos),
                    excluidos == null ? List.of() : CatalogoPiezas.ordenarModelos(excluidos));
        }).toList();
    }

    /** Sustituye los tipos del proveedor con su fila bloqueada (FOR UPDATE): si dos guardan a la vez, gana el último.
     *  Devuelve el resumen para el registro: «bat (todos), g (sin 12mini, 13mini)» o «ninguna». */
    @Transactional
    public String reemplazar(int idProv, List<Entrada> tipos) {
        comprobar(proveedorDao.getByIdParaEditar(idProv));
        // Sin contains(null): en una lista inmutable (List.of) lanza NullPointerException.
        if (tipos == null || tipos.stream().anyMatch(Objects::isNull)) throw regla(MSG_SIN_TIPOS);
        Catalogo cat = catalogo();
        Map<String, Set<String>> nuevos = new TreeMap<>();
        for (Entrada e : tipos) {
            if (e.excluidos() == null) throw regla(MSG_SIN_EXCLUIDOS);
            if (e.prefijo() == null || !cat.tipos().contains(e.prefijo())) throw regla(MSG_TIPO_NO_EXISTE);
            if (nuevos.containsKey(e.prefijo())) throw regla(MSG_TIPO_REPETIDO);
            if (e.excluidos().stream().anyMatch(m -> m == null || !CatalogoPiezas.esModelo(m))) throw regla(MSG_MODELO_NO_EXISTE);
            Set<String> excluidos = new TreeSet<>(e.excluidos());
            Set<String> activos = cat.modelosActivos().getOrDefault(e.prefijo(), Set.of());
            // Un tipo sin piezas activas se guarda tal cual (spec 2026-10-12 §6): sus excluidos no dejan fuera
            // ningún modelo que hoy se pueda pedir.
            if (!excluidos.isEmpty() && !activos.isEmpty() && excluidos.containsAll(activos)) throw regla(MSG_NINGUN_MODELO);
            nuevos.put(e.prefijo(), excluidos);
        }
        tiposDao.reemplazar(idProv, nuevos);
        return resumen(nuevos);
    }

    /** Pieza master → proveedores que venden alguna pieza de su grupo (spec §4.2), de menor a mayor. Las piezas de un
     *  grupo comparten lista; un slave no tiene entrada propia. */
    public Map<Integer, List<Integer>> proveedoresPorMaster(List<Componente> piezas) {
        Map<Integer, Map<String, Set<String>>> reglas = tiposDao.todos();
        Map<Integer, Set<Integer>> porMaster = new HashMap<>();
        for (Componente c : piezas) {
            int master = c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom();
            Set<Integer> provs = porMaster.computeIfAbsent(master, k -> new TreeSet<>());
            reglas.forEach((idProv, suyos) -> {
                if (CatalogoPiezas.vende(suyos, c.getTipo())) provs.add(idProv);
            });
        }
        Map<Integer, List<Integer>> r = new HashMap<>();
        porMaster.forEach((master, provs) -> r.put(master, List.copyOf(provs)));
        return r;
    }

    private Catalogo catalogo() {
        Set<String> tipos = new HashSet<>();
        Map<String, Set<String>> modelos = new HashMap<>();
        for (ProveedorTipoDAO.Pieza pieza : tiposDao.piezas()) {
            String p = CatalogoPiezas.prefijo(pieza.tipo());
            tipos.add(p);
            if (!pieza.activo()) continue;
            String m = CatalogoPiezas.modelo(pieza.tipo(), p);
            if (m != null) modelos.computeIfAbsent(p, k -> new HashSet<>()).add(m);
        }
        return new Catalogo(CatalogoPiezas.ordenarTipos(tipos), modelos);
    }

    private static String resumen(Map<String, Set<String>> tipos) {
        if (tipos.isEmpty()) return "ninguna";
        return CatalogoPiezas.ordenarTipos(tipos.keySet()).stream().map(p -> {
            Set<String> excluidos = tipos.get(p);
            return p + " (" + (excluidos.isEmpty() ? "todos" : "sin " + String.join(", ", CatalogoPiezas.ordenarModelos(excluidos))) + ")";
        }).collect(Collectors.joining(", "));
    }

    private static void comprobar(Optional<Proveedor> proveedor) {
        Proveedor p = proveedor.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADO));
        if (!ProveedorDAO.TIPO_COMPONENTES.equals(p.getTipo())) throw regla(MSG_SOLO_COMPONENTES);
    }

    private static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
