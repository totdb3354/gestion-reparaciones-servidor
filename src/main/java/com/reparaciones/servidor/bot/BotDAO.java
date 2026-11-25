package com.reparaciones.servidor.bot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Bot_movimiento y las lecturas de la lista de devoluciones (spec 2026-11-24-bot-devoluciones §4 y §5). */
@Repository
public class BotDAO {

    static final String SQL_GUARDAR = "INSERT IGNORE INTO Bot_movimiento (RUN_ID, IMEI, ACCION, RESULTADO, ORIGEN_NSYS, DESTINO_NSYS, MOTIVO, FECHA, RECIBIDO)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_APUNTAR = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, NULL, ?, ?, NULL, ?, ?)";

    private final JdbcTemplate jdbc;

    public BotDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Guarda un movimiento; un repetido (misma vuelta, IMEI y accion) no entra. 1 si entro, 0 si ya estaba. */
    public int guardar(String run, MovimientoBot m, LocalDateTime recibido) {
        return jdbc.update(SQL_GUARDAR, run, m.imei(), m.accion(), m.resultado(), m.origen(), m.destino(), m.motivo(),
                Timestamp.valueOf(m.fecha()), Timestamp.valueOf(recibido));
    }

    /** Un apunte del historial de la fila sin usuario, con el IMEI que salio de ella en IMEI_ANTERIOR. */
    public void apuntar(int idFila, LocalDateTime fecha, String imei, String tipo, String detalle) {
        jdbc.update(SQL_APUNTAR, idFila, Timestamp.valueOf(fecha), imei, tipo, detalle);
    }
}
