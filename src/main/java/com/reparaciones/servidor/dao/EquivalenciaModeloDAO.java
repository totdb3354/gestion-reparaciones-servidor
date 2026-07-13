package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EquivalenciaModeloDAO {

    private final JdbcTemplate jdbc;

    public EquivalenciaModeloDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, String>> getAll() {
        return jdbc.query("SELECT TEXTO_EXTERNO, MODELO_INTERNO FROM Modelo_equivalencia",
            (rs, row) -> Map.of("textoExterno", rs.getString("TEXTO_EXTERNO"),
                                "modeloInterno", rs.getString("MODELO_INTERNO")));
    }

    public void guardar(String textoExterno, String modeloInterno) {
        jdbc.update("INSERT INTO Modelo_equivalencia (TEXTO_EXTERNO, MODELO_INTERNO) VALUES (?, ?)" +
                    " ON DUPLICATE KEY UPDATE MODELO_INTERNO = ?",
            textoExterno, modeloInterno, modeloInterno);
    }
}
