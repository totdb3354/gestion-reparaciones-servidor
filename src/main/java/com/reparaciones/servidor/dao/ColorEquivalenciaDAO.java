package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ColorEquivalenciaDAO {

    private final JdbcTemplate jdbc;

    public ColorEquivalenciaDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, String>> getAll() {
        return jdbc.query("SELECT TEXTO_EXTERNO, COLOR_OFICIAL FROM Color_equivalencia",
            (rs, row) -> Map.of("textoExterno", rs.getString("TEXTO_EXTERNO"),
                                "colorOficial", rs.getString("COLOR_OFICIAL")));
    }

    public void guardar(String textoExterno, String colorOficial) {
        jdbc.update("INSERT INTO Color_equivalencia (TEXTO_EXTERNO, COLOR_OFICIAL) VALUES (?, ?)" +
                    " ON DUPLICATE KEY UPDATE COLOR_OFICIAL = ?",
            textoExterno, colorOficial, colorOficial);
    }
}
