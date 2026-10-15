package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.TipoLinea;
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
 *  de sus líneas. Desde la 0.9.15 (spec «Añadir otro» §4.1) sus líneas son piezas (Compra_componente) y otros
 *  (Compra_otro): todas las consultas de líneas cuentan las dos tablas. */
@Repository
public class BloqueCompraDAO {

    public record Bloque(int idBloque, int idProv, LocalDateTime fecha, String nota, boolean reconstruido,
                         LocalDateTime updatedAt) {}

    /** Lo que las acciones de bloque comprueban y registran de cada línea: `idCompra` es el id en su tabla (ID_COMPRA o
     *  ID_COMPRA_OTRO), `tipo` el nombre que sale en los logs (el TIPO del componente o el CONCEPTO del otro) y
     *  `tipoLinea` si es pieza u otro (0.9.15). `updatedAt` truncado a segundos, como CompraComponenteDAO.getCompraRow. */
    public record LineaBloque(int idCompra, String tipo, int cantidad, String estado, LocalDateTime updatedAt,
                              TipoLinea tipoLinea) {
        /** Una pieza, con la forma de la 0.9.9. */
        public LineaBloque(int idCompra, String tipo, int cantidad, String estado, LocalDateTime updatedAt) {
            this(idCompra, tipo, cantidad, estado, updatedAt, TipoLinea.PIEZA);
        }
    }

    /** Abierto = tiene alguna línea (pieza u otro) y ninguna distinta de pendiente, en ninguna de las dos tablas. Los
     *  bloques actuales van antes que los antiguos (RECONSTRUIDO): uno antiguo solo si no hay otro abierto (la
     *  reconstrucción de los otros puede crear uno antiguo solo con otros pendientes y con el ID más alto). */
    static final String SQL_ABIERTO =
            "SELECT b.ID_BLOQUE FROM Bloque_compra b" +
            " WHERE b.ID_PROV = ?" +
            " AND (EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)" +
            " OR EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_BLOQUE = b.ID_BLOQUE))" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE AND cc.ESTADO <> 'pendiente')" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_BLOQUE = b.ID_BLOQUE AND co.ESTADO <> 'pendiente')" +
            " ORDER BY b.RECONSTRUIDO, b.ID_BLOQUE DESC LIMIT 1";
    /** Piezas y otros del bloque, por FECHA_PEDIDO y, a igual fecha, piezas antes que otros ('PIEZA' > 'OTRO') y por id. */
    static final String SQL_LINEAS =
            "SELECT 'PIEZA' AS TIPO_LINEA, cc.ID_COMPRA AS ID_LINEA, c.TIPO AS NOMBRE, cc.CANTIDAD, cc.ESTADO, cc.UPDATED_AT," +
            " cc.FECHA_PEDIDO FROM Compra_componente cc JOIN Componente c ON c.ID_COM = cc.ID_COM WHERE cc.ID_BLOQUE = ?" +
            " UNION ALL" +
            " SELECT 'OTRO', co.ID_COMPRA_OTRO, co.CONCEPTO, co.CANTIDAD, co.ESTADO, co.UPDATED_AT, co.FECHA_PEDIDO" +
            " FROM Compra_otro co WHERE co.ID_BLOQUE = ?" +
            " ORDER BY FECHA_PEDIDO, TIPO_LINEA DESC, ID_LINEA";
    static final String SQL_CONTAR =
            "SELECT (SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ?)" +
            " + (SELECT COUNT(*) FROM Compra_otro WHERE ID_BLOQUE = ?)";
    static final String SQL_VIVAS =
            "SELECT (SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial'))" +
            " + (SELECT COUNT(*) FROM Compra_otro WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial'))";
    static final String SQL_BORRAR_SI_VACIO =
            "DELETE FROM Bloque_compra WHERE ID_BLOQUE = ?" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente WHERE ID_BLOQUE = ?)" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_otro WHERE ID_BLOQUE = ?)";
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

    /** El bloque abierto (todas sus líneas, piezas y otros, pendientes) más reciente del proveedor, si lo hay: los
     *  bloques actuales antes que los antiguos (RECONSTRUIDO); uno antiguo solo si no hay otro abierto. */
    public Optional<Integer> abiertoMasReciente(int idProv) {
        return jdbc.query(SQL_ABIERTO, (rs, row) -> rs.getInt("ID_BLOQUE"), idProv).stream().findFirst();
    }

    /** Las piezas y los otros del bloque, cada uno con su tipo (0.9.15). */
    public List<LineaBloque> lineas(int idBloque) {
        return jdbc.query(SQL_LINEAS,
                (rs, row) -> new LineaBloque(rs.getInt("ID_LINEA"), rs.getString("NOMBRE"), rs.getInt("CANTIDAD"),
                        rs.getString("ESTADO"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS),
                        TipoLinea.valueOf(rs.getString("TIPO_LINEA"))),
                idBloque, idBloque);
    }

    /** Piezas más otros del bloque. */
    public int contarLineas(int idBloque) {
        Integer n = jdbc.queryForObject(SQL_CONTAR, Integer.class, idBloque, idBloque);
        return n == null ? 0 : n;
    }

    /** Terminado = sin ninguna línea (pieza u otro) pendiente, en camino ni parcial: todo recibido o cancelado (spec
     *  2026-10-12 «un proveedor por pedido» §4). Un bloque terminado está cerrado: no entra ni sale ninguna línea. */
    public boolean estaTerminado(int idBloque) {
        Integer n = jdbc.queryForObject(SQL_VIVAS, Integer.class, idBloque, idBloque);
        return n == null || n == 0;
    }

    /** Borra el bloque si se ha quedado sin piezas ni otros (regla §3.2). Devuelve si lo borró. */
    public boolean borrarSiVacio(int idBloque) {
        return jdbc.update(SQL_BORRAR_SI_VACIO, idBloque, idBloque, idBloque) > 0;
    }

    /** Nota nula = sin nota. Cambia UPDATED_AT del bloque (control de cambios de la nota). */
    public void cambiarNota(int idBloque, String nota) {
        jdbc.update(SQL_CAMBIAR_NOTA, nota, idBloque);
    }
}
