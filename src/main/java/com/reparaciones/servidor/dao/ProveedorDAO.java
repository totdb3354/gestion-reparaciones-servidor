package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Proveedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProveedorDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Proveedor> MAPPER = (rs, row) -> new Proveedor(
            rs.getInt("ID_PROV"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO"),
            rs.getString("DIVISA")
    );

    public ProveedorDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Proveedor> getAll() {
        return jdbc.query(
                "SELECT ID_PROV, NOMBRE, ACTIVO, DIVISA FROM Proveedor ORDER BY NOMBRE",
                MAPPER);
    }

    public List<Proveedor> getActivos() {
        return jdbc.query(
                "SELECT ID_PROV, NOMBRE, ACTIVO, DIVISA FROM Proveedor WHERE ACTIVO = 1 ORDER BY NOMBRE",
                MAPPER);
    }

    public boolean tienePedidos(int idProv) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Compra_componente WHERE ID_PROV = ?",
                Integer.class, idProv);
        return count != null && count > 0;
    }

    public void insertar(String nombre) {
        jdbc.update("INSERT INTO Proveedor (NOMBRE, ACTIVO, DIVISA) VALUES (?, 1, 'EUR')", nombre);
    }

    public void setActivo(int idProv, boolean activo) {
        jdbc.update("UPDATE Proveedor SET ACTIVO = ? WHERE ID_PROV = ?", activo, idProv);
    }

    public void setDivisa(int idProv, String divisa) {
        jdbc.update("UPDATE Proveedor SET DIVISA = ? WHERE ID_PROV = ?", divisa, idProv);
    }

    public void borrar(int idProv) {
        jdbc.update("DELETE FROM Proveedor WHERE ID_PROV = ?", idProv);
    }
}
