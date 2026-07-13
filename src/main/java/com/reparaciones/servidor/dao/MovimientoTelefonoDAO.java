package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Trazabilidad append-only de ubicaciones (spec F2 §2.2). Solo inserciones; la consulta llega en F2c. */
@Repository
public class MovimientoTelefonoDAO {

    private final JdbcTemplate jdbc;

    public MovimientoTelefonoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insertar(String imei, String origen, String destino, int idUsu, String motivo, String referencia) {
        jdbc.update(
            "INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA)" +
            " VALUES (?, ?, ?, ?, ?, ?)",
            imei, origen, destino, idUsu, motivo, referencia);
    }
}
