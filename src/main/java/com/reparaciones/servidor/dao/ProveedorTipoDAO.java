package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Tipos de pieza que vende cada proveedor y los modelos que no vende de cada uno (0.9.9, spec 2026-10-12 §3). Una fila
 *  de Proveedor_tipo: vende ese tipo; sus filas de Proveedor_tipo_excluido: los modelos de ese tipo que no vende. */
@Repository
public class ProveedorTipoDAO {

    static final String SQL_TIPOS = "SELECT ID_PROV, PREFIJO FROM Proveedor_tipo";
    static final String SQL_EXCLUIDOS = "SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido";
    static final String SQL_TIPOS_DE = "SELECT ID_PROV, PREFIJO FROM Proveedor_tipo WHERE ID_PROV = ?";
    static final String SQL_EXCLUIDOS_DE = "SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido WHERE ID_PROV = ?";
    static final String SQL_BORRAR = "DELETE FROM Proveedor_tipo WHERE ID_PROV = ?";
    static final String SQL_INSERTAR_TIPO = "INSERT INTO Proveedor_tipo (ID_PROV, PREFIJO) VALUES (?, ?)";
    static final String SQL_INSERTAR_EXCLUIDO = "INSERT INTO Proveedor_tipo_excluido (ID_PROV, PREFIJO, MODELO) VALUES (?, ?, ?)";
    static final String SQL_PIEZAS = "SELECT TIPO, ACTIVO FROM Componente WHERE TIPO NOT LIKE 'otro%'";

    /** Una referencia del catálogo (no «otro»), activa o no: de ella salen los tipos que existen y sus modelos. */
    public record Pieza(String tipo, boolean activo) {}

    private final JdbcTemplate jdbc;

    public ProveedorTipoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Proveedor → (prefijo → modelos excluidos) de todos los proveedores con algún tipo. */
    public Map<Integer, Map<String, Set<String>>> todos() {
        Map<Integer, Map<String, Set<String>>> mapa = new TreeMap<>();
        jdbc.query(SQL_TIPOS, (RowCallbackHandler) rs -> mapa.computeIfAbsent(rs.getInt("ID_PROV"), k -> new TreeMap<>())
                .putIfAbsent(rs.getString("PREFIJO"), new TreeSet<>()));
        jdbc.query(SQL_EXCLUIDOS, (RowCallbackHandler) rs -> mapa.computeIfAbsent(rs.getInt("ID_PROV"), k -> new TreeMap<>())
                .computeIfAbsent(rs.getString("PREFIJO"), k -> new TreeSet<>()).add(rs.getString("MODELO")));
        return mapa;
    }

    /** Prefijo → modelos excluidos de un proveedor; vacío si no vende nada. */
    public Map<String, Set<String>> deProveedor(int idProv) {
        Map<String, Set<String>> mapa = new TreeMap<>();
        jdbc.query(SQL_TIPOS_DE, (RowCallbackHandler) rs -> mapa.putIfAbsent(rs.getString("PREFIJO"), new TreeSet<>()), idProv);
        jdbc.query(SQL_EXCLUIDOS_DE, (RowCallbackHandler) rs ->
                mapa.computeIfAbsent(rs.getString("PREFIJO"), k -> new TreeSet<>()).add(rs.getString("MODELO")), idProv);
        return mapa;
    }

    /** Sustituye todo lo del proveedor: borra sus tipos (sus excepciones caen en cascada) e inserta los nuevos. Lo llama
     *  el servicio dentro de su transacción, con el proveedor ya bloqueado. */
    public void reemplazar(int idProv, Map<String, Set<String>> tipos) {
        jdbc.update(SQL_BORRAR, idProv);
        if (tipos.isEmpty()) return;
        jdbc.batchUpdate(SQL_INSERTAR_TIPO, tipos.keySet().stream().map(p -> new Object[]{idProv, p}).toList());
        List<Object[]> excluidos = new ArrayList<>();
        tipos.forEach((p, modelos) -> modelos.forEach(m -> excluidos.add(new Object[]{idProv, p, m})));
        if (!excluidos.isEmpty()) jdbc.batchUpdate(SQL_INSERTAR_EXCLUIDO, excluidos);
    }

    public List<Pieza> piezas() {
        return jdbc.query(SQL_PIEZAS, (rs, n) -> new Pieza(rs.getString("TIPO"), rs.getBoolean("ACTIVO")));
    }
}
