package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraOtro;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
public class CompraOtroDAO {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT co.ID_COMPRA_OTRO, co.ID_PROV, p.NOMBRE AS NOMBRE_PROVEEDOR, co.CONCEPTO," +
            " co.CANTIDAD, co.CANTIDAD_RECIBIDA, co.ES_URGENTE, co.FECHA_PEDIDO, co.FECHA_LLEGADA," +
            " co.PRECIO_UNIDAD_PEDIDO, co.DIVISA, co.PRECIO_EUR, co.ESTADO, co.UPDATED_AT" +
            " FROM Compra_otro co JOIN Proveedor p ON co.ID_PROV = p.ID_PROV";

    private static final RowMapper<CompraOtro> MAPPER = (rs, row) -> new CompraOtro(
            rs.getInt("ID_COMPRA_OTRO"),
            rs.getInt("ID_PROV"),
            rs.getString("NOMBRE_PROVEEDOR"),
            rs.getString("CONCEPTO"),
            rs.getInt("CANTIDAD"),
            rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
            rs.getBoolean("ES_URGENTE"),
            rs.getTimestamp("FECHA_PEDIDO") != null ? rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime() : null,
            rs.getTimestamp("FECHA_LLEGADA") != null ? rs.getTimestamp("FECHA_LLEGADA").toLocalDateTime() : null,
            rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
            rs.getString("DIVISA"),
            rs.getDouble("PRECIO_EUR"),
            rs.getString("ESTADO"),
            rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS));

    public CompraOtroDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompraOtro> getAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY co.FECHA_PEDIDO DESC", MAPPER);
    }

    public Optional<CompraOtro> getById(int id) {
        List<CompraOtro> rows = jdbc.query(SELECT_BASE + " WHERE co.ID_COMPRA_OTRO = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public void insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) {
        jdbc.update(
                "INSERT INTO Compra_otro" +
                " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur);
    }

    public void editar(int id, int idProv, String concepto, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?," +
                " PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=?",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur, id);
    }

    public void confirmar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente");
    }

    public void confirmarRecibido(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=?", id);
    }

    public void confirmarParcial(int id, int cantidadRecibida, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=?",
                cantidadRecibida, id);
    }

    public void recibirResto(int id, int cantidadExtra, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int nuevaRecibida = (r.cantidadRecibida() != null ? r.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= r.cantidad();
        jdbc.update(
                "UPDATE Compra_otro" +
                " SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?" +
                (completo ? ", ESTADO = 'recibido'" : "") +
                " WHERE ID_COMPRA_OTRO=?",
                cantidadExtra, id);
    }

    public void confirmarAlterado(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=?", id);
    }

    public void cancelar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=?", id);
    }

    /** Revierte a 'en_camino'. Sin stock que descontar -> no hay chequeo. */
    public void desrecibir(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL" +
                " WHERE ID_COMPRA_OTRO=?", id);
    }

    public void borrarPendiente(int id) {
        int n = jdbc.update("DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El pedido ya no está pendiente (no se puede borrar)");
    }

    // -- helpers --

    private record Row(int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

    private Row getRow(int id) {
        return jdbc.queryForObject(
                "SELECT CANTIDAD, CANTIDAD_RECIBIDA, UPDATED_AT FROM Compra_otro WHERE ID_COMPRA_OTRO = ?",
                (rs, row) -> new Row(
                        rs.getInt("CANTIDAD"),
                        rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                id);
    }

    private void checkUpdatedAt(LocalDateTime bdAt, LocalDateTime clientAt) {
        if (!clientAt.truncatedTo(ChronoUnit.SECONDS).equals(bdAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }
}
