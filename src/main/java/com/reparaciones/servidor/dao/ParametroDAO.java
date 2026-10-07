package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parámetros que edita el administrador (tabla Parametro, 0.9.5). Hoy, los pesos de la previsión de pedidos. */
@Repository
public class ParametroDAO {

    private static final Logger log = LoggerFactory.getLogger(ParametroDAO.class);

    static final String PESO_1 = "PREVISION_PESO_1";
    static final String PESO_2 = "PREVISION_PESO_2";
    static final String PESO_3 = "PREVISION_PESO_3";

    private final JdbcTemplate jdbc;

    public ParametroDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los pesos guardados, o 50/30/20 si no se puede leer la tabla (p. ej. servidor desplegado antes que la migración),
     *  falta alguna clave o no son válidos: la previsión de Stock nunca rompe el listado. */
    public Pesos getPesosPrevision() {
        Map<String, Integer> valores = new HashMap<>();
        try {
            for (Map<String, Object> fila : jdbc.queryForList(
                    "SELECT CLAVE, VALOR FROM Parametro WHERE CLAVE LIKE 'PREVISION_PESO_%'")) {
                valores.put((String) fila.get("CLAVE"), ((Number) fila.get("VALOR")).intValue());
            }
        } catch (DataAccessException e) {
            log.warn("No se pudieron leer los pesos de Parametro ({}): la previsión usa los pesos por defecto", e.getMessage());
            return Pesos.POR_DEFECTO;
        }
        if (!valores.keySet().containsAll(List.of(PESO_1, PESO_2, PESO_3))) {
            log.warn("Faltan pesos de la previsión en Parametro: se usan los pesos por defecto");
            return Pesos.POR_DEFECTO;
        }
        Pesos pesos = new Pesos(valores.get(PESO_1), valores.get(PESO_2), valores.get(PESO_3));
        if (!pesos.validos()) {
            log.warn("Pesos de la previsión no válidos en Parametro ({}): se usan los pesos por defecto", pesos.texto());
            return Pesos.POR_DEFECTO;
        }
        return pesos;
    }

    public void guardarPesosPrevision(Pesos p) {
        String sql = "INSERT INTO Parametro (CLAVE, VALOR) VALUES (?, ?) ON DUPLICATE KEY UPDATE VALOR = VALUES(VALOR)";
        jdbc.update(sql, PESO_1, p.p1());
        jdbc.update(sql, PESO_2, p.p2());
        jdbc.update(sql, PESO_3, p.p3());
    }
}
