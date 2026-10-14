package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraOtro;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.reparaciones.servidor.dao.CompraComponenteDAO.*;

@Repository
public class CompraOtroDAO {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT co.ID_COMPRA_OTRO, co.ID_PROV, p.NOMBRE AS NOMBRE_PROVEEDOR, co.CONCEPTO," +
            " co.CANTIDAD, co.CANTIDAD_RECIBIDA, co.ES_URGENTE, co.FECHA_PEDIDO, co.FECHA_LLEGADA," +
            " co.PRECIO_UNIDAD_PEDIDO, co.DIVISA, co.PRECIO_EUR, co.ESTADO, co.UPDATED_AT," +
            " co.ID_BLOQUE, b.FECHA AS FECHA_BLOQUE, b.NOTA AS NOTA_BLOQUE, b.UPDATED_AT AS UPDATED_AT_BLOQUE, b.RECONSTRUIDO" +
            " FROM Compra_otro co JOIN Proveedor p ON co.ID_PROV = p.ID_PROV" +
            " LEFT JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE";

    private static final RowMapper<CompraOtro> MAPPER = (rs, row) -> {
        Timestamp tsPedido = rs.getTimestamp("FECHA_PEDIDO");
        Timestamp tsLlegada = rs.getTimestamp("FECHA_LLEGADA");
        Timestamp tsBloque = rs.getTimestamp("FECHA_BLOQUE");
        Timestamp tsBloqueAt = rs.getTimestamp("UPDATED_AT_BLOQUE");
        return new CompraOtro(
                rs.getInt("ID_COMPRA_OTRO"),
                rs.getInt("ID_PROV"),
                rs.getString("NOMBRE_PROVEEDOR"),
                rs.getString("CONCEPTO"),
                rs.getInt("CANTIDAD"),
                rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                rs.getBoolean("ES_URGENTE"),
                tsPedido != null ? tsPedido.toLocalDateTime() : null,
                tsLlegada != null ? tsLlegada.toLocalDateTime() : null,
                rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
                rs.getString("DIVISA"),
                rs.getDouble("PRECIO_EUR"),
                rs.getString("ESTADO"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)
        ).conBloque(
                rs.getObject("ID_BLOQUE", Integer.class),
                tsBloque != null ? tsBloque.toLocalDateTime() : null,
                rs.getString("NOTA_BLOQUE"),
                tsBloqueAt != null ? tsBloqueAt.toLocalDateTime() : null,
                rs.getBoolean("RECONSTRUIDO"));
    };

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

    /** Devuelve el ID_COMPRA_OTRO generado (el lote lo devuelve a la web, sub-proyecto 4b; el POST suelto lo ignora). */
    public int insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_otro" +
                    " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idProv);
            ps.setString(2, concepto);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA_OTRO del pedido insertado");
        return id.intValue();
    }

    /** Alta de un otro en el bloque `idBloque` de su proveedor (0.9.15, como CompraComponenteDAO.insertar). Devuelve el
     *  ID_COMPRA_OTRO generado (el lote lo devuelve a la web; el POST suelto lo ignora). */
    public int insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur, int idBloque) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_otro" +
                    " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO, ID_BLOQUE)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idProv);
            ps.setString(2, concepto);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            ps.setInt(8, idBloque);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA_OTRO del pedido insertado");
        return id.intValue();
    }

    public void editar(int id, int idProv, String concepto, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur, id, cantidad);
        exigir(n, MSG_NO_EDITABLE);
    }

    public void confirmar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        exigir(n, MSG_NO_PENDIENTE);
    }

    public void confirmarRecibido(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'", id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    public void confirmarParcial(int id, int cantidadRecibida, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'",
                cantidadRecibida, id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    public void recibirResto(int id, int cantidadExtra, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int nuevaRecibida = (r.cantidadRecibida() != null ? r.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= r.cantidad();
        String sql = completo
                ? "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'"
                : "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
        int n = jdbc.update(sql, cantidadExtra, id);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void confirmarAlterado(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'", id);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void cancelar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'", id);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    /** Revierte a 'en_camino' desde 'recibido'. Sin stock que descontar -> no hay chequeo de stock. */
    public void desrecibir(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA_OTRO=? AND ESTADO='recibido'", id);
        exigir(n, MSG_NO_RECIBIDO);
    }

    public void borrarPendiente(int id) {
        int n = jdbc.update("DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El pedido ya no está pendiente (no se puede borrar)");
    }

    /** Borra un otro 'pendiente' solo si sigue en el bloque `origen` que leyó quien llama (nulo = sin bloque), como
     *  CompraComponenteDAO.borrarPendiente (0.9.15): si cambió de bloque en medio, 409 y el bloque no se vacía por error. */
    public void borrarPendiente(int id, Integer origen) {
        int n = jdbc.update(
                "DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente' AND ID_BLOQUE <=> ?", id, origen);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El pedido ya no está pendiente (no se puede borrar)");
    }

    /** Cambia el bloque de un otro (0.9.15), como CompraComponenteDAO.moverABloque: UPDATED_AT cambia (es un cambio real
     *  del otro); `origen` es el bloque que leyó quien llama (nulo = sin bloque) y si ya está en otro, 409. */
    public void moverABloque(int id, Integer origen, int idBloque) {
        int n = jdbc.update("UPDATE Compra_otro SET ID_BLOQUE=? WHERE ID_COMPRA_OTRO=? AND ID_BLOQUE <=> ?",
                idBloque, id, origen);
        if (n == 0) {
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Compra_otro WHERE ID_COMPRA_OTRO=?", Integer.class, id);
            boolean sigueExistiendo = existe != null && existe > 0;
            exigir(0, sigueExistiendo ? MSG_LINEA_CAMBIO_BLOQUE : MSG_LINEA_NO_EXISTE);
        }
    }

    // -- helpers --

    /** Package-private: los tests de la matriz de estados la devuelven desde el JdbcTemplate mockeado. */
    record Row(int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

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
