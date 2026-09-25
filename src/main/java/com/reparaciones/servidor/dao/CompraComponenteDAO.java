package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraComponente;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class CompraComponenteDAO {

    /** 409 de estado (spec 4b §4.1). Los comparte CompraOtroDAO. Hasta el 4b el servidor solo protegía "pendiente"
     *  en confirmar y borrar: con un GET fresco y una llamada directa se podía recibir dos veces (sumando stock). */
    static final String MSG_NO_PENDIENTE = "El pedido ya no está pendiente";
    static final String MSG_NO_EN_CAMINO = "El pedido ya no está en camino";
    static final String MSG_NO_PARCIAL   = "El pedido ya no está en recepción parcial";
    static final String MSG_NO_RECIBIDO  = "El pedido ya no está recibido";
    static final String MSG_NO_EDITABLE  = "El pedido no se puede editar en su estado actual";

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

    public List<CompraComponente> getEnCamino() {
        return jdbc.query(SELECT_BASE +
                " WHERE cc.ESTADO = 'en_camino'" +
                " ORDER BY cc.ES_URGENTE DESC, cc.FECHA_PEDIDO ASC", MAPPER);
    }

    /** Cantidad pendiente de llegar del SKU, resuelta al master del grupo compartido: las compras se insertan
     *  siempre en el master, así que preguntar por un slave sin resolver devolvía 0 (sub-proyecto 4a). */
    public int getCantidadEnCaminoPorComponente(int idCom) {
        idCom = resolveToMasterId(idCom);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CANTIDAD - COALESCE(CANTIDAD_RECIBIDA, 0)), 0)" +
                " FROM Compra_componente WHERE ID_COM = ? AND ESTADO IN ('en_camino','parcial')",
                Integer.class, idCom);
    }

    /** Devuelve el ID_COMPRA generado (el lote lo devuelve a la web, sub-proyecto 4b; el POST suelto lo ignora).
     *  El pedido se guarda siempre en el master del SKU compartido. */
    public int insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur) {
        int idMaster = resolveToMasterId(idCom);
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_componente" +
                    " (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idMaster);
            ps.setInt(2, idProv);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA del pedido insertado");
        return id.intValue();
    }

    /** Editable en pendiente, en_camino y recibido; en recibido solo si la cantidad no cambia (el 422 de P2 lo da el
     *  controlador antes; esta condición cubre la carrera). El WHERE se evalúa sobre la fila de antes del SET. */
    public void editar(int idCompra, int idProv, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ID_PROV=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))",
                idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, idCompra, cantidad);
        exigir(n, MSG_NO_EDITABLE);
    }

    @Transactional
    public void confirmarRecibido(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                row.cantidad(), row.idCom());
    }

    @Transactional
    public void confirmarParcial(int idCompra, int cantidadRecibida, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                cantidadRecibida, idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadRecibida, row.idCom());
    }

    @Transactional
    public void recibirResto(int idCompra, int cantidadExtra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int nuevaRecibida = (row.cantidadRecibida() != null ? row.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= row.cantidad();
        String sql = completo
                ? "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'"
                : "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA=? AND ESTADO='parcial'";
        int n = jdbc.update(sql, cantidadExtra, idCompra);
        exigir(n, MSG_NO_PARCIAL);
        jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                cantidadExtra, row.idCom());
    }

    public void confirmarAlterado(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_componente SET ESTADO='recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'",
                idCompra);
        exigir(n, MSG_NO_PARCIAL);
    }

    public void cancelar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update("UPDATE Compra_componente SET ESTADO='cancelado' WHERE ID_COMPRA=? AND ESTADO='en_camino'",
                idCompra);
        exigir(n, MSG_NO_EN_CAMINO);
    }

    /** Confirma un pedido pendiente: pasa a 'en_camino' (entra en el flujo de recepción). */
    public void confirmar(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='en_camino' WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        exigir(n, MSG_NO_PENDIENTE);
    }

    /** Borra un pedido en estado 'pendiente' (aún no se pidió nada). */
    public void borrarPendiente(int idCompra) {
        int n = jdbc.update(
                "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'",
                idCompra);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente (no se puede borrar)");
        }
    }

    /** Primero el UPDATE condicionado a 'recibido' (409 si ya no lo está); después el 409 de stock de siempre, que
     *  deshace ese UPDATE porque el método es @Transactional; por último la resta. */
    @Transactional
    public void desrecibir(int idCompra, LocalDateTime updatedAt) {
        CompraRow row = getCompraRow(idCompra);
        checkUpdatedAt(row.updatedAt(), updatedAt);
        int cantidadARevertir = row.cantidadRecibida() != null ? row.cantidadRecibida() : row.cantidad();
        int n = jdbc.update(
                "UPDATE Compra_componente SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA=? AND ESTADO='recibido'",
                idCompra);
        exigir(n, MSG_NO_RECIBIDO);
        Integer stockActual = jdbc.queryForObject(
                "SELECT STOCK FROM Componente WHERE ID_COM = ?", Integer.class, row.idCom());
        if (stockActual == null || stockActual < cantidadARevertir) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente para deshacer la recepción (" +
                    "stock actual: " + stockActual + ", a descontar: " + cantidadARevertir + ")");
        }
        jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?",
                cantidadARevertir, row.idCom());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int resolveToMasterId(int idCom) {
        Integer master = jdbc.queryForObject(
                "SELECT COALESCE(ID_COM_MASTER, ID_COM) FROM Componente WHERE ID_COM = ?",
                Integer.class, idCom);
        return master != null ? master : idCom;
    }

    /** Package-private: los tests de la matriz de estados la devuelven desde el JdbcTemplate mockeado. */
    record CompraRow(int idCom, int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

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

    /** Guard de estado: el UPDATE condicionado no tocó ninguna fila → el pedido ya no está en el estado exigido. */
    static void exigir(int filas, String mensaje) {
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
        }
    }

    public java.util.Optional<CompraComponente> getById(int idCompra) {
        List<CompraComponente> rows = jdbc.query(
                SELECT_BASE + " WHERE cc.ID_COMPRA = ?", MAPPER, idCompra);
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }
}
