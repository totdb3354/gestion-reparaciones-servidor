package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Proveedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProveedorDAO {

    public static final String TIPO_COMPONENTES = "COMPONENTES";
    public static final String TIPO_TELEFONOS   = "TELEFONOS";

    private static final String SELECT_BASE =
            "SELECT ID_PROV, NOMBRE, ACTIVO, DIVISA, COMENTARIO, TIPO FROM Proveedor";

    private final JdbcTemplate jdbc;

    private static final RowMapper<Proveedor> MAPPER = (rs, row) -> new Proveedor(
            rs.getInt("ID_PROV"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO"),
            rs.getString("DIVISA"),
            rs.getString("COMENTARIO"),
            rs.getString("TIPO")
    );

    public ProveedorDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Proveedor> getAll(String tipo) {
        if (tipo == null) {
            return jdbc.query(SELECT_BASE + " ORDER BY NOMBRE", MAPPER);
        }
        return jdbc.query(SELECT_BASE + " WHERE TIPO = ? ORDER BY NOMBRE", MAPPER, tipo);
    }

    public List<Proveedor> getActivos(String tipo) {
        if (tipo == null) {
            return jdbc.query(SELECT_BASE + " WHERE ACTIVO = 1 ORDER BY NOMBRE", MAPPER);
        }
        return jdbc.query(SELECT_BASE + " WHERE ACTIVO = 1 AND TIPO = ? ORDER BY NOMBRE", MAPPER, tipo);
    }

    public boolean tienePedidos(int idProv) {
        Integer count = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM Compra_componente WHERE ID_PROV = ?)" +
                "     + (SELECT COUNT(*) FROM Compra_otro       WHERE ID_PROV = ?)" +
                "     + (SELECT COUNT(*) FROM Lote              WHERE ID_PROV = ?)",
                Integer.class, idProv, idProv, idProv);
        return count != null && count > 0;
    }

    public void insertar(String nombre, String divisa, String tipo) {
        String divisaFinal = (divisa == null || divisa.isBlank()) ? "EUR" : divisa;
        String tipoFinal   = (tipo == null || tipo.isBlank()) ? TIPO_COMPONENTES : tipo;
        jdbc.update("INSERT INTO Proveedor (NOMBRE, ACTIVO, DIVISA, TIPO) VALUES (?, 1, ?, ?)",
                nombre, divisaFinal, tipoFinal);
    }

    public void setActivo(int idProv, boolean activo) {
        jdbc.update("UPDATE Proveedor SET ACTIVO = ? WHERE ID_PROV = ?", activo, idProv);
    }

    public void editar(int idProv, String nombre, String divisa, String comentario) {
        jdbc.update("UPDATE Proveedor SET NOMBRE = ?, DIVISA = ?, COMENTARIO = ? WHERE ID_PROV = ?",
                nombre, divisa, comentario, idProv);
    }

    public void borrar(int idProv) {
        jdbc.update("DELETE FROM Proveedor WHERE ID_PROV = ?", idProv);
    }

    public String getNombreById(int idProv) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Proveedor WHERE ID_PROV = ?", String.class, idProv);
    }
}
