package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraComponente;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class CompraComponenteDAO {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT cc.ID_COMPRA, cc.ID_COM, c.TIPO, cc.ID_PROV, p.NOMBRE AS NOMBRE_PROV," +
            " cc.CANTIDAD, cc.CANTIDAD_RECIBIDA, cc.ES_URGENTE," +
            " cc.FECHA_PEDIDO, cc.FECHA_LLEGADA," +
            " cc.PRECIO_UNIDAD_PEDIDO, cc.DIVISA, cc.PRECIO_EUR," +
            " cc.ESTADO, cc.UPDATED_AT" +
            " FROM Compra_componente cc" +
            " JOIN Componente c ON cc.ID_COM = c.ID_COM" +
            " JOIN Proveedor p ON cc.ID_PROV = p.ID_PROV";

    private static final RowMapper<CompraComponente> MAPPER = (rs, row) -> {
        Timestamp tsLlegada = rs.getTimestamp("FECHA_LLEGADA");
        return new CompraComponente(
                rs.getInt("ID_COMPRA"),
                rs.getInt("ID_COM"),
                rs.getString("TIPO"),
                rs.getInt("ID_PROV"),
                rs.getString("NOMBRE_PROV"),
                rs.getInt("CANTIDAD"),
                rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                rs.getBoolean("ES_URGENTE"),
                rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime(),
                tsLlegada != null ? tsLlegada.toLocalDateTime() : null,
                rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
                rs.getString("DIVISA"),
                rs.getDouble("PRECIO_EUR"),
                rs.getString("ESTADO"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        );
    };

    public CompraComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompraComponente> getAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY cc.FECHA_PEDIDO DESC", MAPPER);
    }

    public List<CompraComponente> getPendientes() {
        return jdbc.query(SELECT_BASE +
                " WHERE cc.ESTADO = 'pendiente'" +
                " ORDER BY cc.ES_URGENTE DESC, cc.FECHA_PEDIDO ASC", MAPPER);
    }

    public int getCantidadPendientePorComponente(int idCom) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0)), 0)" +
                " FROM Compra_componente WHERE ID_COM = ? AND ESTADO IN ('pendiente','parcial')",
                Integer.class, idCom);
    }

    public void insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) {
        jdbc.update(
                "INSERT INTO Compra_componente" +
                " (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                idCom, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur);
    }

    public void editar(int idCompra, int idProv, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_componente SET ID_PROV=?, CANTIDAD=?, ES_URGENTE=?," +
                " PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA=?",
                idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, idCompra);
    }

    @Transactional
    public void confirmarRecibido(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_componente SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA=?",
                idCompra);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                row.cantidad(), row.idCom());
    }

    @Transactional
    public void confirmarParcial(int idCompra, int cantidadRecibida, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_componente SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW()" +
                " WHERE ID_COMPRA=?",
                cantidadRecibida, idCompra);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadRecibida, row.idCom());
    }

    @Transactional
    public void recibirResto(int idCompra, int cantidadExtra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int nuevaRecibida = (row.cantidadRecibida() != null ? row.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= row.cantidad();
        jdbc.update(
                "UPDATE Compra_componente" +
                " SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?" +
                (completo ? ", ESTADO = 'recibido'" : "") +
                " WHERE ID_COMPRA=?",
                cantidadExtra, idCompra);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadExtra, row.idCom());
    }

    public void confirmarAlterado(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_componente SET ESTADO='recibido' WHERE ID_COMPRA=?", idCompra);
    }

    public void cancelar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_componente SET ESTADO='cancelado' WHERE ID_COMPRA=?", idCompra);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record CompraRow(int idCom, int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

    private CompraRow getCompraRow(int idCompra) {
        return jdbc.queryForObject(
                "SELECT ID_COM, CANTIDAD, CANTIDAD_RECIBIDA, UPDATED_AT" +
                " FROM Compra_componente WHERE ID_COMPRA = ?",
                (rs, row) -> new CompraRow(
                        rs.getInt("ID_COM"),
                        rs.getInt("CANTIDAD"),
                        rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idCompra);
    }

    private void checkUpdatedAt(LocalDateTime bdAt, LocalDateTime clientAt) {
        if (!clientAt.truncatedTo(ChronoUnit.SECONDS).equals(bdAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }
}
