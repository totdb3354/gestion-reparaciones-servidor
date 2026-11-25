package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Pedido_fila (la tabla de pedidos como la hoja), su registro de cambios de IMEI y Pedido_lectura. Horas de Madrid sin zona. */
@Repository
public class FilasDAO {

    static final int TANDA = 500;

    static final String SQL_FILAS = "SELECT ID_FILA, FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION, CERRADA_EN,"
            + " ENVIO_FECHA, TRANSPORTISTA, SEGUIMIENTO, REABIERTA_EN, ENVIO_ID, IMPRESA_EN, FALTA_ENVIAR_EN, SINCRONIZADA_EN,"
            + " MARCA, MARCA_NOTA, MARCA_EN, (SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = Pedido_fila.MARCA_USU) AS MARCA_POR,"
            + " ASOCIADA_EN, ASOCIADA_ORIGEN, VALIDADA_IMEI, VALIDADA_EN,"
            + " (SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = Pedido_fila.VALIDADA_USU) AS VALIDADA_POR,"
            + " FECHA_SALIDA, PAIS, IMEI_RESERVADO, RESERVADA_EN, SIN_STOCK_EN FROM Pedido_fila";
    /** La salida de un pedido: ultimo dia para enviarlo y pais (se recalculan en cada vuelta). */
    public record Salida(Canal canal, String pedido, LocalDate fechaSalida, String pais) {}
    static final String SQL_PONER_SALIDA = "UPDATE Pedido_fila SET FECHA_SALIDA = ?, PAIS = ? WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ?"
            + " AND PEDIDO = ? AND CERRADA_EN IS NULL";
    static final String SQL_VIVAS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    static final String SQL_CERRADAS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NOT NULL AND (%s)";
    static final String SQL_POR_MARCAR_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO'"
            + " AND (ENVIO_FECHA IS NULL OR IMPRESA_EN IS NULL OR ENVIO_ID IS NULL) AND (%s)";
    static final String SQL_INSERTAR = "INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Pedido_fila SET CERRADA_EN = ? WHERE CERRADA_EN IS NULL AND ID_FILA IN (%s)";
    /** Reabrir borra tambien los datos del envio: un pedido que vuelve a estar pendiente ya no esta enviado. */
    static final String SQL_REABRIR = "UPDATE Pedido_fila SET CERRADA_EN = NULL, ENVIO_FECHA = NULL, TRANSPORTISTA = NULL,"
            + " SEGUIMIENTO = NULL, ENVIO_ID = NULL, IMPRESA_EN = NULL, REABIERTA_EN = ? WHERE CERRADA_EN IS NOT NULL AND ID_FILA IN (%s)";
    /** Un envio es definitivo: si la fila estaba viva se cierra ahora; si ya estaba cerrada conserva su hora de cierre. */
    static final String SQL_MARCAR_ENVIO = "UPDATE Pedido_fila SET ENVIO_FECHA = ?, TRANSPORTISTA = ?, SEGUIMIENTO = ?,"
            + " CERRADA_EN = COALESCE(CERRADA_EN, ?), ENVIO_ID = ?, IMPRESA_EN = ? WHERE ID_FILA = ? AND ENVIO_FECHA IS NULL";
    /** Una etiqueta regenerada: la fila aun sin imprimir pasa al envio mas reciente. Una fila ya impresa no se toca. */
    static final String SQL_CAMBIAR_ENVIO = "UPDATE Pedido_fila SET ENVIO_ID = ?, ENVIO_FECHA = ?, TRANSPORTISTA = ?,"
            + " SEGUIMIENTO = ?, IMPRESA_EN = ? WHERE ID_FILA = ? AND ENVIO_FECHA IS NOT NULL AND IMPRESA_EN IS NULL";
    /** Completa el id del envio y la impresion de una fila ya enviada; lo que ya tiene no se toca ni se borra. */
    static final String SQL_MARCAR_ETIQUETA = "UPDATE Pedido_fila SET ENVIO_ID = COALESCE(ENVIO_ID, ?),"
            + " IMPRESA_EN = COALESCE(IMPRESA_EN, ?) WHERE ID_FILA = ? AND ENVIO_FECHA IS NOT NULL";
    static final String SQL_DEL_PERIODO = SQL_FILAS + " WHERE (FECHA BETWEEN ? AND ?) OR CERRADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    /** Escapa con la barra invertida: en MariaDB el literal '\\' es una sola barra. El numero de pedido de una fila de Amazon sin
     *  asociar no se busca (la web no lo enseña); el del historico (sin canal), el de las webs y el de las de Amazon asociadas, si. */
    static final String SQL_BUSCAR = SQL_FILAS + " WHERE PLATAFORMA LIKE ? ESCAPE '\\\\' OR SKU LIKE ? ESCAPE '\\\\'"
            + " OR IMEI LIKE ? ESCAPE '\\\\' OR (PEDIDO LIKE ? ESCAPE '\\\\' AND (ASOCIADA_EN IS NOT NULL OR CANAL IS NULL"
            + " OR CANAL NOT IN ('AMAZON', 'AMAZON_STOCKROOM'))) ORDER BY FECHA DESC, ID_FILA DESC LIMIT ?";
    static final String SQL_POR_ID = SQL_FILAS + " WHERE ID_FILA = ?";
    /** Filas de ShippyPro con IMEI: las vivas (de cualquier fecha) y las cerradas con FECHA desde la fecha dada. */
    static final String SQL_SHIPPYPRO_CON_IMEI_DESDE = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND IMEI IS NOT NULL AND IMEI <> ''"
            + " AND (CERRADA_EN IS NULL OR FECHA >= ?)";
    static final String SQL_SHIPPYPRO_CON_IMEI = SQL_SHIPPYPRO_CON_IMEI_DESDE + " AND IMEI IN (%s) ORDER BY FECHA, ID_FILA";
    static final String SQL_FILA_BLOQUEADA = "SELECT IMEI, ORIGEN, MARCA, VALIDADA_IMEI, IMEI_RESERVADO FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_FILA_VALIDACION_BLOQUEADA = "SELECT IMEI, ORIGEN, VALIDADA_IMEI FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_PONER_VALIDACION = "UPDATE Pedido_fila SET VALIDADA_IMEI = ?, VALIDADA_EN = ?, VALIDADA_USU = ? WHERE ID_FILA = ?";
    static final String SQL_QUITAR_VALIDACION = "UPDATE Pedido_fila SET VALIDADA_IMEI = NULL, VALIDADA_EN = NULL, VALIDADA_USU = NULL WHERE ID_FILA = ?";
    /** Otra fila de ShippyPro con ese IMEI que sigue pendiente (y no cancelada: ese movil no sale en ese pedido) o enviada con «falta por enviar» (spec 2026-11-18 §5). */
    static final String SQL_DUPLICADO = "SELECT PEDIDO, FECHA, CANAL, ASOCIADA_EN FROM Pedido_fila WHERE IMEI = ? AND ID_FILA <> ?"
            + " AND ORIGEN = 'SHIPPYPRO' AND ((CERRADA_EN IS NULL AND (MARCA IS NULL OR MARCA <> 'CANCELADO')) OR (ENVIO_FECHA IS NOT NULL AND FALTA_ENVIAR_EN IS NOT NULL))"
            + " ORDER BY FECHA, ID_FILA LIMIT 1";
    static final String SQL_PONER_IMEI = "UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?";
    static final String SQL_FILA_MARCA_BLOQUEADA = "SELECT ORIGEN, MARCA, MARCA_NOTA, IMEI, FALTA_ENVIAR_EN FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_PONER_MARCA = "UPDATE Pedido_fila SET MARCA = ?, MARCA_NOTA = ?, MARCA_EN = ?, MARCA_USU = ? WHERE ID_FILA = ?";
    static final String SQL_QUITAR_MARCA = "UPDATE Pedido_fila SET MARCA = NULL, MARCA_NOTA = NULL, MARCA_EN = NULL, MARCA_USU = NULL WHERE ID_FILA = ?";
    static final String SQL_CAMBIO_DETALLE = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO, DETALLE) VALUES (?, ?, ?, ?, ?)";
    static final String SQL_FILA_FALTA_BLOQUEADA = "SELECT ORIGEN, CANAL, ENVIO_FECHA, FALTA_ENVIAR_EN, MARCA FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_PONER_FALTA = "UPDATE Pedido_fila SET FALTA_ENVIAR_EN = ?, FALTA_ENVIAR_USU = ? WHERE ID_FILA = ?";
    static final String SQL_CAMBIO_TIPO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, TIPO) VALUES (?, ?, ?, ?)";
    static final String SQL_FILAS_DE_PEDIDO_BLOQUEADAS = "SELECT ID_FILA, IMEI, FALTA_ENVIAR_EN FROM Pedido_fila"
            + " WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND PEDIDO = ? FOR UPDATE";
    static final String SQL_SINCRONIZAR = "UPDATE Pedido_fila SET SINCRONIZADA_EN = ? WHERE ID_FILA = ?";
    static final String SQL_QUITAR_FALTA = "UPDATE Pedido_fila SET FALTA_ENVIAR_EN = NULL, FALTA_ENVIAR_USU = NULL WHERE ID_FILA = ?";
    static final String SQL_CAMBIO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)";
    /** Tope defensivo de apuntes que sirve {@link #cambiosDeFila}. */
    static final int TOPE_CAMBIOS = 200;
    static final String SQL_CAMBIOS_DE_FILA = "SELECT u.NOMBRE_USUARIO, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE FROM Pedido_fila_cambio c"
            + " LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU WHERE c.ID_FILA = ? ORDER BY c.FECHA DESC, c.ID_CAMBIO DESC LIMIT " + TOPE_CAMBIOS;
    /** Los vendidos para el bot: filas de ShippyPro pendientes o enviadas, con IMEI de 15 cifras, cuyo ULTIMO apunte de cambio de IMEI es
     *  de {@code ?} en adelante; del mas reciente al mas antiguo. Una cerrada sin envio (NO_PENDIENTE) solo entra si su ultimo cambio de
     *  IMEI es POSTERIOR al cierre (alguien tecleo el IMEI sabiendo que ya no estaba pendiente: el movil esta vendido); si el IMEI ya
     *  estaba antes de cerrarse (pedido cancelado) no sale. Una fila con CUALQUIER marca no sale. La fecha que cuenta es la mas reciente entre el
     *  ultimo apunte IMEI y el ultimo MARCA_QUITADA (al quitar la marca la fila vuelve como recien puesta). Una sola consulta, con tope.
     *  <p>Pedidos web juntados en una sola etiqueta (spec §8): una fila {@link #JUNTADA_SINCRONIZADA} (de una web, ya no pendiente, sin
     *  envio propio y sincronizada por el plugin) sale aunque el IMEI sea de antes del cierre, y su fecha es la mas reciente entre su
     *  ultimo apunte y {@code SINCRONIZADA_EN}. Las demas filas no miran {@code SINCRONIZADA_EN}. Los dos {@code ?} de la ventana llevan
     *  el mismo valor. */
    static final String JUNTADA_SINCRONIZADA = "f.CANAL IN ('DISTRIBUIDORES', 'PARTICULARES', 'BACKIPHONE') AND f.CERRADA_EN IS NOT NULL"
            + " AND f.ENVIO_FECHA IS NULL AND f.SINCRONIZADA_EN IS NOT NULL";
    static final String SQL_VENDIDOS = "SELECT f.IMEI, f.SKU, f.CANAL,"
            // Amazon sin asociar: el pedido va nulo (el IMEI se sirve igual; el bot solo lee el IMEI).
            + " CASE WHEN f.CANAL IN ('AMAZON', 'AMAZON_STOCKROOM') AND f.ASOCIADA_EN IS NULL THEN NULL ELSE f.PEDIDO END AS PEDIDO,"
            // Juntada: GREATEST(ultimo apunte, sincronizacion). Con u nulo (ningun apunte en la ventana) el ultimo apunte es anterior a la
            // ventana y la sincronizacion, si entra, es posterior: la mas reciente es SINCRONIZADA_EN. Para que todas las filas miren la
            // sincronizacion bastaria cambiar esta linea y la condicion del WHERE.
            + " CASE WHEN " + JUNTADA_SINCRONIZADA + " THEN GREATEST(COALESCE(u.ULTIMO, f.SINCRONIZADA_EN), f.SINCRONIZADA_EN)"
            + " ELSE u.ULTIMO END AS FECHA_IMEI FROM Pedido_fila f"
            // El filtro dentro de la subconsulta da lo mismo que HAVING MAX(FECHA) >= ?: entra si algun apunte es de la ventana y el MAX de los de la ventana es el MAX de todos.
            // LEFT JOIN: la juntada con el IMEI antiguo (sin apunte en la ventana) entra por la sincronizacion.
            + " LEFT JOIN (SELECT ID_FILA, MAX(FECHA) AS ULTIMO FROM Pedido_fila_cambio WHERE TIPO IN ('IMEI', 'MARCA_QUITADA') AND FECHA >= ? GROUP BY ID_FILA) u"
            + " ON u.ID_FILA = f.ID_FILA"
            + " WHERE f.ORIGEN = 'SHIPPYPRO' AND f.MARCA IS NULL"
            + " AND ((u.ID_FILA IS NOT NULL AND (f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL OR u.ULTIMO > f.CERRADA_EN))"
            + " OR (" + JUNTADA_SINCRONIZADA + " AND (u.ID_FILA IS NOT NULL OR f.SINCRONIZADA_EN >= ?)))"
            + " AND (? IS NULL OR f.CANAL = ?)"
            + " AND f.IMEI REGEXP '^[0-9]{15}$' ORDER BY FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?";
    static final String SQL_BORRAR_HISTORICO = "DELETE FROM Pedido_fila WHERE ORIGEN = 'HISTORICO'";
    static final String SQL_INSERTAR_HISTORICO = "INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN,"
            + " CANAL, POSICION, CERRADA_EN) VALUES (?, ?, ?, ?, ?, ?, 'HISTORICO', NULL, NULL, ?)";
    static final String SQL_SHIPPYPRO_BLOQUEADAS = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' ORDER BY FECHA, ID_FILA FOR UPDATE";
    static final String SQL_TRASPASAR_IMEI = "UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ? AND ORIGEN = 'SHIPPYPRO'"
            + " AND (IMEI IS NULL OR IMEI = '')";
    /** La regla A de la carga del historico confirma la asociacion de la fila (solo si aun no lo estaba). */
    static final String SQL_ASOCIAR_HISTORICO = "UPDATE Pedido_fila SET ASOCIADA_EN = ?, ASOCIADA_ORIGEN = 'HISTORICO'"
            + " WHERE ID_FILA = ? AND ORIGEN = 'SHIPPYPRO' AND ASOCIADA_EN IS NULL";
    static final String SQL_CAMBIOS_DE_HISTORICO = "SELECT COUNT(*) FROM Pedido_fila_cambio c JOIN Pedido_fila f ON f.ID_FILA = c.ID_FILA"
            + " WHERE f.ORIGEN = 'HISTORICO'";
    static final String SQL_EMPEZAR_LECTURA = "INSERT INTO Pedido_lectura (INICIO) VALUES (?)";
    static final String SQL_TERMINAR_LECTURA = "UPDATE Pedido_lectura SET FIN = ?, RESULTADO = ?, FILAS = ?, SKU_DESCONOCIDOS = ?, MENSAJE = ?"
            + " WHERE ID_LECTURA = ?";
    static final String SQL_ULTIMA_LECTURA = "SELECT ID_LECTURA, INICIO, FIN, RESULTADO, FILAS, SKU_DESCONOCIDOS, MENSAJE"
            + " FROM Pedido_lectura WHERE RESULTADO IN (%s) ORDER BY ID_LECTURA DESC LIMIT 1";
    static final String SQL_PODAR = "DELETE FROM Pedido_lectura WHERE INICIO < ?";
    /** Reserva de IMEI (spec reserva §5): las filas de ShippyPro que esperan telefono (vivas, sin marca y sin IMEI) y todas las que
     *  tienen una reserva, esten como esten. */
    static final String SQL_CON_RESERVA_O_ESPERANDO = SQL_FILAS + " WHERE (ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NULL AND MARCA IS NULL"
            + " AND (IMEI IS NULL OR IMEI = '')) OR IMEI_RESERVADO IS NOT NULL ORDER BY ID_FILA";
    static final String SQL_FILA_RESERVA_BLOQUEADA = "SELECT ORIGEN, IMEI, MARCA, CERRADA_EN, IMEI_RESERVADO, SIN_STOCK_EN FROM Pedido_fila"
            + " WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_RESERVAR = "UPDATE Pedido_fila SET IMEI_RESERVADO = ?, RESERVADA_EN = ?, SIN_STOCK_EN = NULL WHERE ID_FILA = ?";
    static final String SQL_QUITAR_RESERVA = "UPDATE Pedido_fila SET IMEI_RESERVADO = NULL, RESERVADA_EN = NULL WHERE ID_FILA = ?";
    static final String SQL_SIN_STOCK = "UPDATE Pedido_fila SET SIN_STOCK_EN = ? WHERE ID_FILA = ?";
    static final String SQL_CAMBIO_RESERVA = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    /** Ancho de Pedido_fila.IMEI_RESERVADO: un IMEI/SN mas largo no se reserva (cortarlo lo haria otro movil). */
    static final int ANCHO_RESERVADO = 20;
    /** Ancho de Pedido_fila_cambio.DETALLE. */
    static final int ANCHO_DETALLE = 250;

    /** Reservados al bot (spec reserva §6): las filas vivas sin IMEI con IMEI_RESERVADO cuya reserva cae en la ventana. Mismo
     *  formato de columnas que {@link #SQL_VENDIDOS}; la fecha es RESERVADA_EN. Solo con {@code pedidos.reserva.al-bot=true}. */
    static final String SQL_VENDIDOS_RESERVADOS = "SELECT f.IMEI_RESERVADO AS IMEI, f.SKU, f.CANAL,"
            + " CASE WHEN f.CANAL IN ('AMAZON', 'AMAZON_STOCKROOM') AND f.ASOCIADA_EN IS NULL THEN NULL ELSE f.PEDIDO END AS PEDIDO,"
            + " f.RESERVADA_EN AS FECHA_IMEI FROM Pedido_fila f"
            + " WHERE f.ORIGEN = 'SHIPPYPRO' AND f.CERRADA_EN IS NULL AND f.MARCA IS NULL AND (f.IMEI IS NULL OR f.IMEI = '')"
            + " AND f.IMEI_RESERVADO REGEXP '^[0-9]{15}$' AND f.RESERVADA_EN >= ?"
            + " AND (? IS NULL OR f.CANAL = ?) ORDER BY f.RESERVADA_EN DESC, f.ID_FILA DESC LIMIT ?";

    private final JdbcTemplate jdbc;
    private final boolean reservadosAlBot;

    public FilasDAO(JdbcTemplate jdbc) {
        this(jdbc, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FilasDAO(JdbcTemplate jdbc, @org.springframework.beans.factory.annotation.Value("${pedidos.reserva.al-bot:false}") boolean reservadosAlBot) {
        this.jdbc = jdbc;
        this.reservadosAlBot = reservadosAlBot;
    }

    static final String SQL_RESERVAS_VIVAS = "SELECT ID_FILA, IMEI_RESERVADO FROM Pedido_fila WHERE IMEI_RESERVADO IS NOT NULL";

    /** Las reservas de ahora, por fila (una reserva solo existe en filas vivas: al soltarla se borra). */
    public Map<Integer, String> reservasVivas() {
        Map<Integer, String> r = new LinkedHashMap<>();
        jdbc.query(SQL_RESERVAS_VIVAS, (java.sql.ResultSet rs) -> { r.put(rs.getInt("ID_FILA"), rs.getString("IMEI_RESERVADO")); });
        return r;
    }

    /** {@code pedidos.reserva.al-bot}: el bot descuenta tambien los reservados (los pasa a «Pedidos realizados»). */
    public boolean reservadosAlBot() {
        return reservadosAlBot;
    }

    // ---------------------------------------------------------------- filas

    public List<FilaPedido> vivasDeShippyPro() {
        return jdbc.query(SQL_VIVAS_SHIPPYPRO, FilasDAO::fila);
    }

    /** Las filas cerradas de origen ShippyPro de esos pedidos (canal y numero), en tandas. */
    public List<FilaPedido> cerradasDeShippyPro(Collection<SincroShippyPro.ClavePedido> pedidos) {
        return deShippyPro(SQL_CERRADAS_SHIPPYPRO, pedidos);
    }

    private List<FilaPedido> deShippyPro(String sql, Collection<SincroShippyPro.ClavePedido> pedidos) {
        List<FilaPedido> filas = new ArrayList<>();
        for (List<SincroShippyPro.ClavePedido> tanda : trozos(List.copyOf(pedidos))) {
            List<Object> args = new ArrayList<>();
            for (SincroShippyPro.ClavePedido c : tanda) {
                args.add(c.canal().name());
                args.add(c.pedido());
            }
            String condicion = String.join(" OR ", Collections.nCopies(tanda.size(), "(CANAL = ? AND PEDIDO = ?)"));
            filas.addAll(jdbc.query(String.format(sql, condicion), FilasDAO::fila, args.toArray()));
        }
        return filas;
    }

    /** Las filas de origen ShippyPro (vivas o cerradas) de esos pedidos (canal y numero) a las que aun puede faltar algo del envio:
     *  sin envio conocido, sin id de envio o sin impresion de la etiqueta; en tandas. */
    public List<FilaPedido> porMarcarDeShippyPro(Collection<SincroShippyPro.ClavePedido> pedidos) {
        return deShippyPro(SQL_POR_MARCAR_SHIPPYPRO, pedidos);
    }

    /** Pone a cada fila los datos de su envio y, si estaba viva, la cierra ahora; las que ya tenian envio no se tocan. */
    public void marcarEnviadas(List<SincroShippyPro.MarcaEnvio> marcas, LocalDateTime ahora) {
        if (marcas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (SincroShippyPro.MarcaEnvio m : marcas)
            lote.add(new Object[]{Timestamp.valueOf(m.fecha()), AnchosNsys.corta(m.transportista(), AnchosPedidos.TRANSPORTISTA),
                    AnchosNsys.corta(m.seguimiento(), AnchosPedidos.SEGUIMIENTO), Timestamp.valueOf(ahora), m.envioId(),
                    m.impresaEn() == null ? null : Timestamp.valueOf(m.impresaEn()), m.fila()});
        jdbc.batchUpdate(SQL_MARCAR_ENVIO, lote);
    }

    /** Completa el id del envio y la impresion de las filas ya enviadas; nunca borra lo ya puesto. */
    public void marcarEtiquetas(List<SincroShippyPro.MarcaEtiqueta> marcas) {
        if (marcas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        List<Object[]> cambios = new ArrayList<>();
        for (SincroShippyPro.MarcaEtiqueta m : marcas) {
            Timestamp impresa = m.impresaEn() == null ? null : Timestamp.valueOf(m.impresaEn());
            if (m.nuevo() == null) lote.add(new Object[]{m.envioId(), impresa, m.fila()});
            else cambios.add(new Object[]{m.envioId(), Timestamp.valueOf(m.nuevo().fecha()),
                    AnchosNsys.corta(m.nuevo().transportista(), AnchosPedidos.TRANSPORTISTA),
                    AnchosNsys.corta(m.nuevo().seguimiento(), AnchosPedidos.SEGUIMIENTO), impresa, m.fila()});
        }
        if (!lote.isEmpty()) jdbc.batchUpdate(SQL_MARCAR_ETIQUETA, lote);
        if (!cambios.isEmpty()) jdbc.batchUpdate(SQL_CAMBIAR_ENVIO, cambios);
    }

    /** Inserta las filas nuevas (el id del record se ignora), con los textos cortados a los anchos del DDL. */
    public void insertar(List<FilaPedido> nuevas) {
        if (nuevas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (FilaPedido f : nuevas)
            lote.add(new Object[]{fecha(f.fecha()), AnchosNsys.corta(f.plataforma(), AnchosPedidos.PLATAFORMA),
                    AnchosNsys.corta(f.sku(), AnchosPedidos.SKU), AnchosNsys.corta(f.imei(), AnchosPedidos.IMEI),
                    AnchosNsys.corta(f.pedido(), AnchosPedidos.PEDIDO), fecha(f.fechaCompra()), f.origen(),
                    f.canal() == null ? null : f.canal().name(), f.posicion()});
        jdbc.batchUpdate(SQL_INSERTAR, lote);
    }

    /** Cierra (ya no pendientes) las filas indicadas; las que ya lo estaban no se tocan. */
    public void cerrar(Collection<Integer> ids, LocalDateTime ahora) {
        for (List<Integer> tanda : trozos(List.copyOf(ids))) {
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(ahora));
            args.addAll(tanda);
            jdbc.update(String.format(SQL_CERRAR, marcas(tanda.size())), args.toArray());
        }
    }

    /** Vuelve a dejar vivas (sin hora de cierre ni datos de envio, y apunta cuando se reabren) las filas indicadas, con su IMEI intacto; las que ya lo estaban no se tocan. */
    public void reabrir(Collection<Integer> ids, LocalDateTime ahora) {
        for (List<Integer> tanda : trozos(List.copyOf(ids))) {
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(ahora));
            args.addAll(tanda);
            jdbc.update(String.format(SQL_REABRIR, marcas(tanda.size())), args.toArray());
        }
    }

    /** Las filas con FECHA entre las dos fechas y, sea cual sea su fecha, las vivas; en orden de hoja. */
    public List<FilaPedido> delPeriodo(LocalDate desde, LocalDate hasta) {
        return jdbc.query(SQL_DEL_PERIODO, FilasDAO::fila, fecha(desde), fecha(hasta));
    }

    /** Las {@code tope + 1} filas mas recientes que contienen el texto en plataforma, SKU, IMEI o pedido, en orden de hoja
     *  (la mas antigua primero). Pedir una de mas permite saber si hay mas de {@code tope}. */
    public List<FilaPedido> buscar(String texto, int tope) {
        String patron = "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<FilaPedido> filas = new ArrayList<>(
                jdbc.query(SQL_BUSCAR, FilasDAO::fila, patron, patron, patron, patron, tope + 1));
        Collections.reverse(filas);
        return filas;
    }

    public Optional<FilaPedido> porId(int id) {
        return jdbc.query(SQL_POR_ID, FilasDAO::fila, id).stream().findFirst();
    }

    /** Los apuntes del historial de una fila (IMEI y marca de falta enviar), del mas reciente al mas antiguo (hasta 200), con el nombre de quien los hizo (nulo si no hay usuario). */
    public List<CambioImei> cambiosDeFila(int id) {
        return jdbc.query(SQL_CAMBIOS_DE_FILA, (rs, n) -> new CambioImei(rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("IMEI_ANTERIOR"), rs.getString("IMEI_NUEVO"), rs.getString("TIPO"), rs.getString("DETALLE")), id);
    }

    /** Las filas de ShippyPro con alguno de esos IMEI: las vivas y las cerradas con FECHA desde {@code desde}. */
    public List<FilaPedido> shippyProConImei(Collection<String> imeis, LocalDate desde) {
        List<FilaPedido> filas = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis))) {
            List<Object> args = new ArrayList<>();
            args.add(fecha(desde));
            args.addAll(tanda);
            filas.addAll(jdbc.query(String.format(SQL_SHIPPYPRO_CON_IMEI, marcas(tanda.size())), FilasDAO::fila, args.toArray()));
        }
        return filas;
    }

    /** Todas las filas de ShippyPro con IMEI: las vivas y las cerradas con FECHA desde {@code desde}. */
    public List<FilaPedido> shippyProConImeiDesde(LocalDate desde) {
        return jdbc.query(SQL_SHIPPYPRO_CON_IMEI_DESDE + " ORDER BY FECHA, ID_FILA", FilasDAO::fila, fecha(desde));
    }

    /** Un movil que aun hay que buscar: su SKU, el pais del pedido, el ultimo dia para enviarlo (los dos pueden faltar), el canal, el
     *  cliente (columna PLATAFORMA) y el dia en que el ERP vio el pedido (FECHA, «Entro»). */
    public record PorBuscar(String sku, String pais, LocalDate fechaSalida, Canal canal, String cliente, LocalDate entro) {}

    /** Filas de ShippyPro sin IMEI y sin marca CANCELADO que aun hay que buscar: las vivas y las enviadas con falta por enviar. {@code %s}: un {@code ?} por canal
     *  (los canales salen del enum, nunca del usuario). */
    static final String SQL_POR_BUSCAR = "SELECT SKU, PAIS, FECHA_SALIDA, CANAL, PLATAFORMA, FECHA FROM Pedido_fila WHERE ORIGEN = 'SHIPPYPRO' AND CANAL IN (%s)"
            + " AND (IMEI IS NULL OR IMEI = '') AND (MARCA IS NULL OR MARCA <> 'CANCELADO')"
            + " AND (CERRADA_EN IS NULL OR (ENVIO_FECHA IS NOT NULL AND FALTA_ENVIAR_EN IS NOT NULL))";

    /** Lo que hay que buscar en esos canales (ver {@link #SQL_POR_BUSCAR}); sin canales, nada. */
    public List<PorBuscar> porBuscar(Collection<Canal> canales) {
        if (canales.isEmpty()) return List.of();
        Object[] args = canales.stream().map(Canal::name).toArray();
        return jdbc.query(String.format(SQL_POR_BUSCAR, marcas(canales.size())),
                (rs, n) -> {
                    String c = rs.getString("CANAL");
                    return new PorBuscar(rs.getString("SKU"), rs.getString("PAIS"), fechaLocal(rs.getDate("FECHA_SALIDA")),
                            c == null ? null : Canal.valueOf(c), rs.getString("PLATAFORMA"), fechaLocal(rs.getDate("FECHA")));
                }, args);
    }

    private static LocalDate fechaLocal(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    /** Los IMEI vendidos (ver {@link #SQL_VENDIDOS}) con ultimo cambio de IMEI (o, en la web juntada, sincronizacion) desde {@code desde}, a lo sumo {@code tope} filas, sin
     *  IMEI repetidos: si esta en dos filas sale la del cambio mas reciente. Un IMEI quitado de la fila no sale (la fila ya no lo tiene). */
    public List<Vendido> vendidosDesde(LocalDateTime desde, Canal canal, int tope) {
        String nombreCanal = canal == null ? null : canal.name();
        // El canal filtra en la consulta; el dedup por IMEI va despues, sobre lo ya filtrado.
        List<Vendido> filas = jdbc.query(SQL_VENDIDOS, (rs, n) -> new Vendido(rs.getString("IMEI"), rs.getString("SKU"),
                rs.getString("CANAL"), rs.getString("PEDIDO"), hora(rs, "FECHA_IMEI")),
                Timestamp.valueOf(desde), Timestamp.valueOf(desde), nombreCanal, nombreCanal, tope);
        if (reservadosAlBot) {
            List<Vendido> reservados = jdbc.query(SQL_VENDIDOS_RESERVADOS, (rs, n) -> new Vendido(rs.getString("IMEI"), rs.getString("SKU"),
                    rs.getString("CANAL"), rs.getString("PEDIDO"), hora(rs, "FECHA_IMEI")),
                    Timestamp.valueOf(desde), nombreCanal, nombreCanal, tope);
            // Las dos listas vienen de la mas reciente a la mas antigua: se mezclan (estable) y, tras el dedup, vuelve el tope.
            List<Vendido> todas = new ArrayList<>(filas);
            todas.addAll(reservados);
            todas.sort(java.util.Comparator.comparing(Vendido::fechaImei, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
            filas = todas;
        }
        java.util.Map<String, Vendido> porImei = new java.util.LinkedHashMap<>();
        for (Vendido v : filas) porImei.putIfAbsent(v.imei(), v);
        List<Vendido> r = new ArrayList<>(porImei.values());
        return reservadosAlBot && r.size() > tope ? new ArrayList<>(r.subList(0, tope)) : r;
    }

    /** Resultado de {@link #ponerImei}: PUESTO, NO_EXISTE, CERRADA (del historico: no se toca) o CAMBIADA (el IMEI actual no es el que la persona esperaba). */
    public enum Resultado { PUESTO, NO_EXISTE, CERRADA, CAMBIADA,
        /** Solo de {@link #ponerFaltaEnviar}: la fila ya estaba como se pedia (no se escribe ni apunta nada). */
        SIN_CAMBIO,
        /** Solo de {@link #ponerFaltaEnviar}: no se puede marcar (no es de ShippyPro, no es de un canal web o no tiene la etiqueta generada). */
        NO_MARCABLE,
        /** Solo de {@link #ponerFaltaEnviar}: la fila esta cancelada; hay que quitar la cancelacion antes de marcar (spec 2026-11-23). */
        CANCELADA_NO_MARCABLE }

    private record Bloqueada(String imei, boolean historico, String marca, String validadaImei, String reservado) {}

    /** Lanza {@link ImeiDuplicadoException} (sin escribir nada) si el IMEI esta en otra fila que cuenta segun {@link #SQL_DUPLICADO}. */
    private void comprobarDuplicado(String imei, int idFila) {
        List<ImeiDuplicadoException> otras = jdbc.query(SQL_DUPLICADO, (rs, n) -> {
            String c = rs.getString("CANAL");
            Canal canal = c == null ? null : Canal.valueOf(c);
            return new ImeiDuplicadoException(rs.getString("PEDIDO"), rs.getDate("FECHA").toLocalDate(), canal,
                    canal != null && canal.esAmazon() && rs.getTimestamp("ASOCIADA_EN") == null);
        }, imei, idFila);
        if (!otras.isEmpty()) throw otras.get(0);
    }

    /** Cambia el IMEI de una fila de ShippyPro (pendiente, no pendiente o enviada) y apunta el cambio con el IMEI anterior. READ COMMITTED y la fila bloqueada al leerla
     *  (MariaDB 11.6+: leer y luego actualizar con aislamiento de instantanea da el error 1020); el estado de la fila se decide
     *  con el bloqueo puesto. {@code imeiAnterior} nulo no comprueba nada; si no, debe coincidir con el IMEI actual (nulo cuenta como ""), y
     *  si no, no escribe nada ni apunta cambio (CAMBIADA). Un IMEI que ya esta en otra fila pendiente lanza {@link ImeiDuplicadoException} sin escribir. Si no existe o es del historico, tampoco escribe nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerImei(int idFila, String imei, String imeiAnterior, Integer idUsu, LocalDateTime ahora) {
        List<Bloqueada> filas = jdbc.query(SQL_FILA_BLOQUEADA,
                (rs, n) -> new Bloqueada(rs.getString("IMEI"), "HISTORICO".equals(rs.getString("ORIGEN")), rs.getString("MARCA"), rs.getString("VALIDADA_IMEI"),
                        rs.getString("IMEI_RESERVADO")), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        if (filas.get(0).historico()) return Resultado.CERRADA;
        if (imeiAnterior != null && !imeiAnterior.equals(filas.get(0).imei() == null ? "" : filas.get(0).imei()))
            return Resultado.CAMBIADA;
        String nuevo = AnchosNsys.corta(imei, AnchosPedidos.IMEI);
        if (nuevo != null) comprobarDuplicado(nuevo, idFila);
        jdbc.update(SQL_PONER_IMEI, nuevo, idFila);
        jdbc.update(SQL_CAMBIO, idFila, idUsu, Timestamp.valueOf(ahora), filas.get(0).imei(), nuevo);
        // La validacion vale para ese IMEI: al cambiarlo o quitarlo se borra (y se apunta), para que no reviva si el IMEI vuelve.
        if (filas.get(0).validadaImei() != null && !filas.get(0).validadaImei().equals(nuevo)) {
            jdbc.update(SQL_QUITAR_VALIDACION, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, Timestamp.valueOf(ahora), CambioImei.VALIDACION_QUITADA, "al cambiar el IMEI");
        }
        // Un IMEI puesto resuelve «En espera de stock»; las demas marcas se quedan.
        if (nuevo != null && Marca.ESPERA_STOCK.name().equals(filas.get(0).marca())) {
            jdbc.update(SQL_QUITAR_MARCA, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, Timestamp.valueOf(ahora), CambioImei.MARCA_QUITADA,
                    Marca.ESPERA_STOCK.name() + SEP_DETALLE + "al poner el IMEI");
        }
        // Una fila con reserva a la que una persona pone IMEI: se confirma o se sustituye con su usuario (spec reserva §7).
        if (nuevo != null && !nuevo.isBlank() && filas.get(0).reservado() != null) apuntarReservaResuelta(idFila, idUsu, ahora, filas.get(0).reservado(), nuevo);
        return Resultado.PUESTO;
    }

    static final String SEP_DETALLE = " \u2014 ";

    private record BloqueadaValidacion(String imei, String origen, String validadaImei) {}

    /** Da por bueno ({@code validar}) el aviso de SKU del IMEI de una fila, o quita la validacion, y lo apunta (VALIDADA con el motivo como
     *  detalle, o VALIDACION_QUITADA). Misma transaccion que {@link #ponerImei}: READ COMMITTED y la fila bloqueada al leerla. Orden:
     *  NO_EXISTE; CERRADA (historico); CAMBIADA ({@code imeiAnterior} no nulo y distinto del IMEI actual, o, al validar, el IMEI actual no es
     *  {@code imeiVisto}, el que se miro para decidir el motivo); SIN_CAMBIO (validar lo ya validado para ese IMEI; quitar sin validacion: una
     *  validacion de otro IMEI se borra en silencio); si no, PUESTO. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerValidacion(int idFila, boolean validar, String imeiVisto, String motivo, String imeiAnterior, Integer idUsu,
                                     LocalDateTime ahora) {
        List<BloqueadaValidacion> filas = jdbc.query(SQL_FILA_VALIDACION_BLOQUEADA, (rs, n) -> new BloqueadaValidacion(rs.getString("IMEI"),
                rs.getString("ORIGEN"), rs.getString("VALIDADA_IMEI")), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        BloqueadaValidacion f = filas.get(0);
        if ("HISTORICO".equals(f.origen())) return Resultado.CERRADA;
        if (imeiAnterior != null && !imeiAnterior.equals(f.imei() == null ? "" : f.imei())) return Resultado.CAMBIADA;
        Timestamp t = Timestamp.valueOf(ahora);
        if (!validar) {
            if (f.validadaImei() == null) return Resultado.SIN_CAMBIO;
            jdbc.update(SQL_QUITAR_VALIDACION, idFila);
            if (!f.validadaImei().equals(f.imei())) return Resultado.SIN_CAMBIO;
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.VALIDACION_QUITADA, null);
            return Resultado.PUESTO;
        }
        if (f.imei() == null || !f.imei().equals(imeiVisto)) return Resultado.CAMBIADA;
        if (f.imei().equals(f.validadaImei())) return Resultado.SIN_CAMBIO;
        jdbc.update(SQL_PONER_VALIDACION, f.imei(), t, idUsu, idFila);
        jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.VALIDADA, motivo);
        return Resultado.PUESTO;
    }

    private record BloqueadaMarca(String origen, String marca, String nota, String imei, boolean conFalta) {}

    /** Pone, cambia o quita (marca nula) la marca de una fila de ShippyPro y la apunta en el historial: MARCA (detalle «MARCA» o «MARCA — nota»)
     *  al poner o cambiar (tambien si solo cambia la nota), MARCA_QUITADA (detalle = la marca que tenia) al quitar. Misma transaccion que
     *  {@link #ponerImei}: READ COMMITTED y la fila bloqueada al leerla. Orden: NO_EXISTE; CERRADA (historico); CAMBIADA ({@code marcaAnterior}
     *  no nula y distinta de la actual; nula cuenta como ""); salir de CANCELADO (a sin marca o a otra marca) con IMEI comprueba el duplicado
     *  como {@link #ponerImei} y lanza {@link ImeiDuplicadoException} sin escribir; SIN_CAMBIO (misma marca y misma nota, o quitar sin marca:
     *  no escribe ni apunta); si no, PUESTO. Poner CANCELADO en una fila con «falta por enviar» la quita y apunta YA_ENVIADO «al cancelar»
     *  (spec 2026-11-23). La nota ya viene validada y recortada (nula = sin nota); se ignora al quitar. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerMarca(int idFila, String marca, String nota, String marcaAnterior, Integer idUsu, LocalDateTime ahora) {
        List<BloqueadaMarca> filas = jdbc.query(SQL_FILA_MARCA_BLOQUEADA, (rs, n) -> new BloqueadaMarca(rs.getString("ORIGEN"),
                rs.getString("MARCA"), rs.getString("MARCA_NOTA"), rs.getString("IMEI"), rs.getTimestamp("FALTA_ENVIAR_EN") != null), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        BloqueadaMarca f = filas.get(0);
        if ("HISTORICO".equals(f.origen())) return Resultado.CERRADA;
        if (marcaAnterior != null && !marcaAnterior.equals(f.marca() == null ? "" : f.marca())) return Resultado.CAMBIADA;
        String cancelado = Marca.CANCELADO.name();
        // Mientras estaba cancelada su IMEI estaba libre: al quitar la cancelacion no puede quedar en dos filas vivas.
        if (cancelado.equals(f.marca()) && !cancelado.equals(marca) && f.imei() != null && !f.imei().isBlank())
            comprobarDuplicado(f.imei().strip(), idFila);
        Timestamp t = Timestamp.valueOf(ahora);
        if (marca == null) {
            if (f.marca() == null) return Resultado.SIN_CAMBIO;
            jdbc.update(SQL_PONER_MARCA, null, null, null, null, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.MARCA_QUITADA, f.marca());
            return Resultado.PUESTO;
        }
        if (marca.equals(f.marca()) && java.util.Objects.equals(nota, f.nota())) return Resultado.SIN_CAMBIO;
        jdbc.update(SQL_PONER_MARCA, marca, nota, t, idUsu, idFila);
        jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.MARCA, nota == null ? marca : marca + SEP_DETALLE + nota);
        // Cancelado y «falta por enviar» no pueden ir juntos: cancelar se la come (no vuelve al quitar la cancelacion).
        if (cancelado.equals(marca) && f.conFalta()) {
            jdbc.update(SQL_QUITAR_FALTA, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.YA_ENVIADO, "al cancelar");
        }
        return Resultado.PUESTO;
    }

    private record BloqueadaFalta(String origen, String canal, boolean conEtiqueta, LocalDateTime marca, boolean cancelada) {}

    /** Pone ({@code falta}) o quita la marca «falta enviar» de una fila y la apunta en el historial (FALTA_ENVIAR o YA_ENVIADO) con el usuario
     *  y la hora. Misma transaccion que {@link #ponerImei}: READ COMMITTED y la fila bloqueada al leerla; todo se decide con el bloqueo puesto.
     *  Orden: NO_EXISTE; CERRADA (historico); SIN_CAMBIO (ya marcada y se marca, o sin marca y se desmarca); CANCELADA_NO_MARCABLE (solo al
     *  poner: la fila tiene la marca CANCELADO); NO_MARCABLE (solo al poner: la fila debe ser de ShippyPro, de un canal web y con la etiqueta generada, ENVIO_FECHA no nula); si no, PUESTO. Quitar la marca
     *  no exige nada mas. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerFaltaEnviar(int idFila, boolean falta, Integer idUsu, LocalDateTime ahora) {
        List<BloqueadaFalta> filas = jdbc.query(SQL_FILA_FALTA_BLOQUEADA, (rs, n) -> new BloqueadaFalta(rs.getString("ORIGEN"),
                rs.getString("CANAL"), rs.getTimestamp("ENVIO_FECHA") != null, hora(rs, "FALTA_ENVIAR_EN"),
                Marca.CANCELADO.name().equals(rs.getString("MARCA"))), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        BloqueadaFalta f = filas.get(0);
        if ("HISTORICO".equals(f.origen())) return Resultado.CERRADA;
        if (falta == (f.marca() != null)) return Resultado.SIN_CAMBIO;
        if (falta && f.cancelada()) return Resultado.CANCELADA_NO_MARCABLE;
        if (falta && !(f.conEtiqueta() && "SHIPPYPRO".equals(f.origen()) && esWeb(f.canal()))) return Resultado.NO_MARCABLE;
        jdbc.update(SQL_PONER_FALTA, falta ? Timestamp.valueOf(ahora) : null, falta ? idUsu : null, idFila);
        jdbc.update(SQL_CAMBIO_TIPO, idFila, idUsu, Timestamp.valueOf(ahora), falta ? CambioImei.FALTA_ENVIAR : CambioImei.YA_ENVIADO);
        return Resultado.PUESTO;
    }

    private static boolean esWeb(String canal) {
        if (canal == null) return false;
        try {
            return Canal.valueOf(canal).esWeb();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Un pedido del aviso del plugin de la web: el numero ya normalizado como lo guarda la lectura de ShippyPro y sus IMEI (sin repetidos). */
    public record PedidoSincronizado(String pedido, Set<String> imeis) {}

    /** Los recuentos de {@link #sincronizar}: filas puestas al dia, marcas «falta enviar» quitadas e IMEI enviados que no casaron con ninguna fila. */
    public record InformeSincronizacion(int filasSincronizadas, int marcasQuitadas, int sinFila) {}

    /** El aviso del plugin de la web, en una transaccion (READ COMMITTED, filas de cada pedido bloqueadas): para cada pedido, las filas
     *  de ShippyPro de ese canal y numero cuyo IMEI viene en la lista se marcan con SINCRONIZADA_EN = {@code ahora} y, si tenian la marca
     *  «falta enviar», se les quita con un apunte YA_ENVIADO sin usuario. El resto de filas del pedido no se tocan. Repetir la misma
     *  llamada solo vuelve a poner la hora: no apunta nada que no quite. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InformeSincronizacion sincronizar(Canal canal, List<PedidoSincronizado> pedidos, LocalDateTime ahora) {
        int sincronizadas = 0, quitadas = 0, sinFila = 0;
        Timestamp hora = Timestamp.valueOf(ahora);
        for (PedidoSincronizado p : pedidos) {
            record Cand(int id, String imei, boolean marcada) {}
            List<Cand> filas = jdbc.query(SQL_FILAS_DE_PEDIDO_BLOQUEADAS,
                    (rs, n) -> new Cand(rs.getInt("ID_FILA"), rs.getString("IMEI"), rs.getTimestamp("FALTA_ENVIAR_EN") != null),
                    canal.name(), p.pedido());
            Set<String> casados = new TreeSet<>();
            for (Cand c : filas) {
                if (c.imei() == null || !p.imeis().contains(c.imei())) continue;
                casados.add(c.imei());
                jdbc.update(SQL_SINCRONIZAR, hora, c.id());
                sincronizadas++;
                if (c.marcada()) {
                    jdbc.update(SQL_QUITAR_FALTA, c.id());
                    jdbc.update(SQL_CAMBIO_TIPO, c.id(), null, hora, CambioImei.YA_ENVIADO);
                    quitadas++;
                }
            }
            sinFila += p.imeis().size() - casados.size();
        }
        return new InformeSincronizacion(sincronizadas, quitadas, sinFila);
    }

    // ---------------------------------------------------------------- carga del historico (se llaman dentro de una transaccion)

    /** Todas las filas de origen ShippyPro, vivas o cerradas, en orden de hoja y bloqueadas hasta el fin de la transaccion. */
    public List<FilaPedido> todasDeShippyProBloqueadas() {
        return jdbc.query(SQL_SHIPPYPRO_BLOQUEADAS, FilasDAO::fila);
    }

    /** Cuantos apuntes de Pedido_fila_cambio cuelgan de filas HISTORICO (deberian ser 0: borrar esas filas los borraria en cascada). */
    public int contarCambiosDeHistorico() {
        Integer n = jdbc.queryForObject(SQL_CAMBIOS_DE_HISTORICO, Integer.class);
        return n == null ? 0 : n;
    }

    /** Borra las filas de origen HISTORICO y devuelve cuantas. No toca las de ShippyPro (ni Pedido_fila_cambio: el historico no se edita). */
    public int borrarHistorico() {
        return jdbc.update(SQL_BORRAR_HISTORICO);
    }

    /** Inserta las filas del historico en el orden dado y en lotes de {@value #TANDA}. Origen HISTORICO, sin canal ni posicion, y
     *  cerradas a la medianoche de su fecha; los textos se cortan a los anchos del DDL. */
    public void insertarHistorico(List<FilaPedido> filas) {
        for (List<FilaPedido> tanda : trozos(filas)) {
            List<Object[]> lote = new ArrayList<>();
            for (FilaPedido f : tanda)
                lote.add(new Object[]{fecha(f.fecha()), AnchosNsys.corta(f.plataforma(), AnchosPedidos.PLATAFORMA),
                        AnchosNsys.corta(f.sku(), AnchosPedidos.SKU), AnchosNsys.corta(f.imei(), AnchosPedidos.IMEI),
                        AnchosNsys.corta(f.pedido(), AnchosPedidos.PEDIDO), fecha(f.fechaCompra()),
                        Timestamp.valueOf(f.fecha().atStartOfDay())});
            jdbc.batchUpdate(SQL_INSERTAR_HISTORICO, lote);
        }
    }

    /** Pone el IMEI en una fila de ShippyPro que no lo tiene y lo apunta (sin usuario, IMEI anterior nulo). Falso, sin escribir nada
     *  mas, si la fila ya tenia IMEI o no existe. */
    public boolean traspasarImei(int idFila, String imei, LocalDateTime ahora) {
        String nuevo = AnchosNsys.corta(imei, AnchosPedidos.IMEI);
        if (jdbc.update(SQL_TRASPASAR_IMEI, nuevo, idFila) == 0) return false;
        jdbc.update(SQL_CAMBIO, idFila, null, Timestamp.valueOf(ahora), null, nuevo);
        return true;
    }

    /** Confirma la asociacion de las filas (la regla A de la carga del historico: el numero de pedido de Amazon de la hoja) con
     *  ASOCIADA_EN = {@code ahora} y ASOCIADA_ORIGEN HISTORICO, y apunta ASOCIADA (detalle HISTORICO) sin usuario. Quien llama ya las tiene
     *  bloqueadas y solo pasa las que no estaban asociadas. En lotes de {@value #TANDA}. */
    public void asociarHistorico(List<Integer> ids, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        for (List<Integer> tanda : trozos(ids)) {
            List<Object[]> filas = new ArrayList<>();
            List<Object[]> apuntes = new ArrayList<>();
            for (Integer id : tanda) {
                filas.add(new Object[]{t, id});
                apuntes.add(new Object[]{id, null, t, CambioImei.ASOCIADA, AsociacionAmazon.HISTORICO});
            }
            jdbc.batchUpdate(SQL_ASOCIAR_HISTORICO, filas);
            jdbc.batchUpdate(SQL_CAMBIO_DETALLE, apuntes);
        }
    }

    // ---------------------------------------------------------------- lecturas

    public int empezarLectura(LocalDateTime inicio) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR_LECTURA, Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, Timestamp.valueOf(inicio));
            return ps;
        }, claves);
        Number n = claves.getKey();
        if (n == null) throw new IllegalStateException("La base de datos no devolvio el id");
        return n.intValue();
    }

    /** Cierra la lectura. Los SKU desconocidos van ordenados y separados por «, », cortados a su ancho; nulo si no hay. */
    public void terminarLectura(int id, String resultado, int filas, Collection<String> desconocidos, String mensaje, LocalDateTime fin) {
        String skus = desconocidos.isEmpty() ? null
                : AnchosNsys.corta(String.join(", ", new TreeSet<>(desconocidos)), AnchosPedidos.SKU_DESCONOCIDOS);
        jdbc.update(SQL_TERMINAR_LECTURA, Timestamp.valueOf(fin), resultado, filas, skus,
                AnchosNsys.corta(mensaje, AnchosPedidos.MENSAJE), id);
    }

    public Optional<LecturaGuardada> ultimaLectura(Set<String> resultados) {
        if (resultados.isEmpty()) return Optional.empty();
        return jdbc.query(String.format(SQL_ULTIMA_LECTURA, marcas(resultados.size())), FilasDAO::lectura, resultados.toArray())
                .stream().findFirst();
    }

    public void podarLecturas(LocalDateTime antesDe) {
        jdbc.update(SQL_PODAR, Timestamp.valueOf(antesDe));
    }

    /** Pone a las filas vivas de cada pedido su fecha de salida y su pais (se recalcula en cada vuelta). */
    public void ponerSalidas(List<Salida> salidas) {
        if (salidas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (Salida s : salidas)
            lote.add(new Object[]{s.fechaSalida() == null ? null : java.sql.Date.valueOf(s.fechaSalida()), s.pais(), s.canal().name(), s.pedido()});
        jdbc.batchUpdate(SQL_PONER_SALIDA, lote);
    }

    // ---------------------------------------------------------------- reserva de IMEI (spec reserva §5 y §8)

    /** Las filas que mira la pasada de reservas (ver {@link #SQL_CON_RESERVA_O_ESPERANDO}), por id. */
    public List<FilaPedido> conReservaOEsperando() {
        return jdbc.query(SQL_CON_RESERVA_O_ESPERANDO, FilasDAO::fila);
    }

    private record BloqueadaReserva(String origen, String imei, String marca, LocalDateTime cerradaEn, String reservado,
                                    LocalDateTime sinStockEn) {
        boolean sinImei() {
            return imei == null || imei.isBlank();
        }

        /** Viva, de ShippyPro, sin marca, sin IMEI y sin reserva: la que puede reservar. */
        boolean esperando() {
            return "SHIPPYPRO".equals(origen) && cerradaEn == null && marca == null && sinImei() && reservado == null;
        }
    }

    /** La fila bloqueada para la reserva (READ COMMITTED en el metodo que llama, como {@link #ponerImei}); vacio si no existe. */
    private Optional<BloqueadaReserva> bloquearParaReserva(int idFila) {
        return jdbc.query(SQL_FILA_RESERVA_BLOQUEADA, (rs, n) -> new BloqueadaReserva(rs.getString("ORIGEN"), rs.getString("IMEI"),
                rs.getString("MARCA"), hora(rs, "CERRADA_EN"), rs.getString("IMEI_RESERVADO"), hora(rs, "SIN_STOCK_EN")), idFila)
                .stream().findFirst();
    }

    /** Aparta {@code imei} para la fila: solo si, con la fila bloqueada, sigue esperando telefono (viva, de ShippyPro, sin marca, sin
     *  IMEI y sin reserva) y el IMEI cabe en la columna. Pone IMEI_RESERVADO y RESERVADA_EN, borra SIN_STOCK_EN y apunta RESERVADO
     *  (IMEI_NUEVO = el reservado, DETALLE = el porque). Sin usuario: lo hace la pasada. true si reservo. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reservar(int idFila, String imei, String porque, LocalDateTime ahora) {
        String limpio = imei == null ? "" : imei.strip();
        if (limpio.isEmpty() || limpio.length() > ANCHO_RESERVADO) return false;
        Optional<BloqueadaReserva> f = bloquearParaReserva(idFila);
        if (f.isEmpty() || !f.get().esperando()) return false;
        Timestamp t = Timestamp.valueOf(ahora);
        jdbc.update(SQL_RESERVAR, limpio, t, idFila);
        jdbc.update(SQL_CAMBIO_RESERVA, idFila, null, t, null, limpio, CambioImei.RESERVADO, AnchosNsys.corta(porque, ANCHO_DETALLE));
        return true;
    }

    /** Suelta la reserva de la fila sin poner IMEI: borra IMEI_RESERVADO y RESERVADA_EN y apunta RESERVA_QUITADA (IMEI_ANTERIOR = el
     *  reservado, DETALLE = {@code motivo}: uno de los de {@link Reservas}). Se decide con la fila
     *  bloqueada: no hace nada si no existe, si su reserva ya no es {@code imeiEsperado} (el que vio la pasada; por
     *  {@link NsysDAO#claveCruce}) o si ya tiene IMEI (una persona lo acaba de poner: la pasada siguiente lo resuelve como
     *  RESERVA_CONFIRMADA o RESERVA_SUSTITUIDA). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void soltarReserva(int idFila, String imeiEsperado, String motivo, LocalDateTime ahora) {
        Optional<BloqueadaReserva> f = bloquearParaReserva(idFila);
        if (f.isEmpty() || f.get().reservado() == null || !f.get().sinImei()) return;
        if (!NsysDAO.claveCruce(f.get().reservado()).equals(NsysDAO.claveCruce(imeiEsperado))) return;
        jdbc.update(SQL_QUITAR_RESERVA, idFila);
        jdbc.update(SQL_CAMBIO_RESERVA, idFila, null, Timestamp.valueOf(ahora), f.get().reservado(), null, CambioImei.RESERVA_QUITADA,
                motivo);
    }

    /** Una fila con IMEI puesto y reserva: RESERVA_CONFIRMADA si el puesto es el reservado (por {@link NsysDAO#claveCruce}),
     *  RESERVA_SUSTITUIDA si no (el reservado vuelve a stock); en los dos, IMEI_ANTERIOR = el reservado e IMEI_NUEVO = el puesto, y se
     *  borra la reserva. {@code idUsu} nulo cuando lo resuelve la pasada; con usuario, desde la celda (spec §7). Sin IMEI o sin
     *  reserva no hace nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resolverReservaPuesta(int idFila, Integer idUsu, LocalDateTime ahora) {
        Optional<BloqueadaReserva> f = bloquearParaReserva(idFila);
        if (f.isEmpty() || f.get().reservado() == null || f.get().sinImei()) return;
        apuntarReservaResuelta(idFila, idUsu, ahora, f.get().reservado(), f.get().imei());
    }

    /** Sin bloqueo propio (lo pone quien llama): borra la reserva y apunta RESERVA_CONFIRMADA o RESERVA_SUSTITUIDA. */
    private void apuntarReservaResuelta(int idFila, Integer idUsu, LocalDateTime ahora, String reservado, String puesto) {
        String tipo = NsysDAO.claveCruce(puesto).equals(NsysDAO.claveCruce(reservado)) ? CambioImei.RESERVA_CONFIRMADA
                : CambioImei.RESERVA_SUSTITUIDA;
        jdbc.update(SQL_QUITAR_RESERVA, idFila);
        jdbc.update(SQL_CAMBIO_RESERVA, idFila, idUsu, Timestamp.valueOf(ahora), reservado, puesto, tipo, null);
    }

    /** Apunta una sola vez que la fila no encontro telefono: solo si, con la fila bloqueada, sigue esperando (como en
     *  {@link #reservar}) y SIN_STOCK_EN es nulo. Pone SIN_STOCK_EN y apunta SIN_STOCK sin usuario. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apuntarSinStock(int idFila, LocalDateTime ahora) {
        Optional<BloqueadaReserva> f = bloquearParaReserva(idFila);
        if (f.isEmpty() || !f.get().esperando() || f.get().sinStockEn() != null) return;
        Timestamp t = Timestamp.valueOf(ahora);
        jdbc.update(SQL_SIN_STOCK, t, idFila);
        jdbc.update(SQL_CAMBIO_TIPO, idFila, null, t, CambioImei.SIN_STOCK);
    }

    // ---------------------------------------------------------------- ayudas

    private static FilaPedido fila(ResultSet rs, int n) throws SQLException {
        String canal = rs.getString("CANAL");
        int posicion = rs.getInt("POSICION");
        Integer pos = rs.wasNull() ? null : posicion;
        long idEnvio = rs.getLong("ENVIO_ID");
        Long envioId = rs.wasNull() ? null : idEnvio;
        return new FilaPedido(rs.getInt("ID_FILA"), dia(rs, "FECHA"), rs.getString("PLATAFORMA"), rs.getString("SKU"),
                rs.getString("IMEI"), rs.getString("PEDIDO"), dia(rs, "FECHA_COMPRA"), rs.getString("ORIGEN"),
                canal == null ? null : Canal.valueOf(canal), pos, hora(rs, "CERRADA_EN"), hora(rs, "ENVIO_FECHA"),
                rs.getString("TRANSPORTISTA"), rs.getString("SEGUIMIENTO"), hora(rs, "REABIERTA_EN"), envioId, hora(rs, "IMPRESA_EN"),
                hora(rs, "FALTA_ENVIAR_EN"), hora(rs, "SINCRONIZADA_EN"), rs.getString("MARCA"), rs.getString("MARCA_NOTA"), hora(rs, "MARCA_EN"),
                rs.getString("MARCA_POR"), hora(rs, "ASOCIADA_EN"), rs.getString("ASOCIADA_ORIGEN"), rs.getString("VALIDADA_IMEI"),
                hora(rs, "VALIDADA_EN"), rs.getString("VALIDADA_POR"), dia(rs, "FECHA_SALIDA"), rs.getString("PAIS"),
                rs.getString("IMEI_RESERVADO"), hora(rs, "RESERVADA_EN"), hora(rs, "SIN_STOCK_EN"));
    }

    private static LecturaGuardada lectura(ResultSet rs, int n) throws SQLException {
        return new LecturaGuardada(rs.getInt("ID_LECTURA"), hora(rs, "INICIO"), hora(rs, "FIN"), rs.getString("RESULTADO"),
                rs.getInt("FILAS"), rs.getString("SKU_DESCONOCIDOS"), rs.getString("MENSAJE"));
    }

    private static LocalDate dia(ResultSet rs, String columna) throws SQLException {
        Date d = rs.getDate(columna);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime hora(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Date fecha(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += TANDA) trozos.add(lista.subList(i, Math.min(lista.size(), i + TANDA)));
        return trozos;
    }
}
