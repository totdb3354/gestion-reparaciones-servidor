package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Persistencia del borrador (JSON) del modal de reparación, 1:0..1 con Reparacion. */
@Repository
public class BorradorDAO {

    private final JdbcTemplate jdbc;

    public BorradorDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return el JSON del borrador de esa asignación, o null si no hay. */
    public String get(String idRep) {
        List<String> filas = jdbc.query(
                "SELECT CONTENIDO FROM Reparacion_borrador WHERE ID_REP = ?",
                (rs, n) -> rs.getString("CONTENIDO"), idRep);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** Inserta o actualiza el borrador (upsert por PK). */
    public void guardar(String idRep, String contenidoJson) {
        jdbc.update(
                "INSERT INTO Reparacion_borrador (ID_REP, CONTENIDO) VALUES (?, ?)" +
                " ON DUPLICATE KEY UPDATE CONTENIDO = VALUES(CONTENIDO)",
                idRep, contenidoJson);
    }

    public void eliminar(String idRep) {
        jdbc.update("DELETE FROM Reparacion_borrador WHERE ID_REP = ?", idRep);
    }
}
