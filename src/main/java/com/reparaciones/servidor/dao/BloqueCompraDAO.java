package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Cabeceras de pedido de compra (0.9.9, spec «Pedidos por bloques» §3-§4). Un bloque es de un proveedor y nunca está
 *  vacío: quien lo vacía lo borra en la misma transacción ({@link #borrarSiVacio}). Su estado no se guarda: se deriva
 *  de sus líneas. */
@Repository
public class BloqueCompraDAO {

    public record Bloque(int idBloque, int idProv, LocalDateTime fecha, String nota, boolean reconstruido,
                         LocalDateTime updatedAt) {}

    /** Lo que las acciones de bloque comprueban y registran de cada línea. `updatedAt` truncado a segundos, como
     *  CompraComponenteDAO.getCompraRow. */
    public record LineaBloque(int idCompra, String tipo, int cantidad, String estado, LocalDateTime updatedAt) {}

    static final String SQL_ABIERTO =
            "SELECT b.ID_BLOQUE FROM Bloque_compra b" +
            " WHERE b.ID_PROV = ?" +
            " AND EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE AND cc.ESTADO <> 'pendiente')" +
            " ORDER BY b.FECHA DESC, b.ID_BLOQUE DESC LIMIT 1";
    static final String SQL_LINEAS =
            "SELECT cc.ID_COMPRA, c.TIPO, cc.CANTIDAD, cc.ESTADO, cc.UPDATED_AT FROM Compra_componente cc" +
            " JOIN Componente c ON c.ID_COM = cc.ID_COM WHERE cc.ID_BLOQUE = ? ORDER BY cc.FECHA_PEDIDO, cc.ID_COMPRA";
    static final String SQL_CONTAR = "SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ?";
    static final String SQL_BORRAR_SI_VACIO =
            "DELETE FROM Bloque_compra WHERE ID_BLOQUE = ?" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente WHERE ID_BLOQUE = ?)";
    static final String SQL_CAMBIAR_NOTA = "UPDATE Bloque_compra SET NOTA = ? WHERE ID_BLOQUE = ?";

    private final JdbcTemplate jdbc;

    public BloqueCompraDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int crear(int idProv) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Bloque_compra (ID_PROV, FECHA) VALUES (?, NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idProv);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_BLOQUE del bloque creado");
        return id.intValue();
    }

    public Optional<Bloque> getById(int idBloque) {
        return jdbc.query(
                "SELECT ID_BLOQUE, ID_PROV, FECHA, NOTA, RECONSTRUIDO, UPDATED_AT FROM Bloque_compra WHERE ID_BLOQUE = ?",
                (rs, row) -> new Bloque(rs.getInt("ID_BLOQUE"), rs.getInt("ID_PROV"),
                        rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("NOTA"), rs.getBoolean("RECONSTRUIDO"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idBloque).stream().findFirst();
    }

    /** El bloque abierto (todas sus líneas pendientes) más reciente del proveedor, si lo hay (spec §3.3 y §3.7). */
    public Optional<Integer> abiertoMasReciente(int idProv) {
        return jdbc.query(SQL_ABIERTO, (rs, row) -> rs.getInt("ID_BLOQUE"), idProv).stream().findFirst();
    }

    public List<LineaBloque> lineas(int idBloque) {
        return jdbc.query(SQL_LINEAS,
                (rs, row) -> new LineaBloque(rs.getInt("ID_COMPRA"), rs.getString("TIPO"), rs.getInt("CANTIDAD"),
                        rs.getString("ESTADO"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idBloque);
    }

    public int contarLineas(int idBloque) {
        Integer n = jdbc.queryForObject(SQL_CONTAR, Integer.class, idBloque);
        return n == null ? 0 : n;
    }

    /** Borra el bloque si se ha quedado sin líneas (regla §3.2). Devuelve si lo borró. */
    public boolean borrarSiVacio(int idBloque) {
        return jdbc.update(SQL_BORRAR_SI_VACIO, idBloque, idBloque) > 0;
    }

    /** Nota nula = sin nota. Cambia UPDATED_AT del bloque (control de cambios de la nota). */
    public void cambiarNota(int idBloque, String nota) {
        jdbc.update(SQL_CAMBIAR_NOTA, nota, idBloque);
    }
}
