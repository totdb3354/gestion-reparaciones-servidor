package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Piezas que vende cada proveedor (0.9.9, spec 2026-10-11 §3): una fila por (proveedor, pieza master). La mantiene el
 *  supertécnico desde Proveedores y la usa la previsión por proveedor de «Nuevo pedido». */
@Repository
public class ProveedorComponenteDAO {

    static final String SQL_TODAS = "SELECT ID_PROV, ID_COM FROM Proveedor_componente";
    static final String SQL_DE_PROVEEDOR = "SELECT ID_COM FROM Proveedor_componente WHERE ID_PROV = ?";
    static final String SQL_INSERTAR = "INSERT IGNORE INTO Proveedor_componente (ID_PROV, ID_COM) VALUES (?, ?)";
    static final String SQL_QUITAR = "DELETE FROM Proveedor_componente WHERE ID_PROV = ? AND ID_COM = ?";
    /** `%s` = una marca `?` por id. */
    static final String SQL_MASTERS = "SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER FROM Componente WHERE ID_COM IN (%s)";

    private final JdbcTemplate jdbc;

    public ProveedorComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pieza master → ids de los proveedores que la venden, de menor a mayor. */
    public Map<Integer, List<Integer>> proveedoresPorPieza() {
        Map<Integer, List<Integer>> mapa = new HashMap<>();
        jdbc.query(SQL_TODAS, (RowCallbackHandler) rs ->
                mapa.computeIfAbsent(rs.getInt("ID_COM"), k -> new ArrayList<>()).add(rs.getInt("ID_PROV")));
        mapa.values().forEach(Collections::sort);
        return mapa;
    }

    public Set<Integer> piezasDe(int idProv) {
        return new HashSet<>(jdbc.queryForList(SQL_DE_PROVEEDOR, Integer.class, idProv));
    }

    /** Master de cada id que existe; los que no existen no salen en el mapa. */
    public Map<Integer, Integer> mastersDe(Collection<Integer> idsCom) {
        if (idsCom.isEmpty()) return Map.of();
        String marcas = String.join(", ", Collections.nCopies(idsCom.size(), "?"));
        Map<Integer, Integer> mapa = new HashMap<>();
        jdbc.query(String.format(SQL_MASTERS, marcas),
                (RowCallbackHandler) rs -> mapa.put(rs.getInt("ID_COM"), rs.getInt("ID_MASTER")), idsCom.toArray());
        return mapa;
    }

    public void anadir(int idProv, Collection<Integer> idsCom) {
        jdbc.batchUpdate(SQL_INSERTAR, idsCom.stream().map(id -> new Object[]{idProv, id}).toList());
    }

    public void quitar(int idProv, Collection<Integer> idsCom) {
        jdbc.batchUpdate(SQL_QUITAR, idsCom.stream().map(id -> new Object[]{idProv, id}).toList());
    }
}
