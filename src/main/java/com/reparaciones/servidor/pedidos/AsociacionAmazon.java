package com.reparaciones.servidor.pedidos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** La asociacion IMEI -> pedido de Amazon que llega de los libros de ventas (spec «asociación de IMEI y pedido en Amazon», plan B). En
 *  Amazon y Amazon Stockroom el ERP no da por bueno el pedido de una fila hasta que el libro lo confirma (ASOCIADA_EN); hasta entonces la
 *  fila sale «sin asociar». Cada pasada de un canal aplica, en el orden recibido y sobre las filas de ShippyPro de ESE canal, cada par
 *  «IMEI X -> pedido B» (numero normalizado como la lectura de ShippyPro):
 *  <ol>
 *  <li>X ya esta en una fila de B: se confirma (si ya lo estaba, «ya estaba» y no se toca nada).</li>
 *  <li>Si B no tiene ninguna fila sin etiqueta ni confirmar (no cancelada) y si filas con etiqueta sin confirmar ni cancelar: el libro
 *  manda (spec «el libro manda» §2.3). Antes, {@link ComprobadorLibro} mira que el movil X pueda ser el del pedido (el SKU de la fila
 *  destino); si no, a «para revisar» con NO_EN_NSYS u OTRO_MODELO (y su detalle) y no se toca nada. La fila destino es la primera por
 *  posicion con etiqueta, sin confirmar ni cancelar, cuyo IMEI (o ninguno) no lo pone el libro en B en esta pasada; sin ella, SIN_HUECO.
 *  Si X esta en una fila de otro pedido O: O confirmada → YA_SALIO; O cancelada → CANCELADO; O con etiqueta se queda sin IMEI y sin
 *  asociar (el ERP no inventa que fue en O); O pendiente (sin etiqueta) recibe por intercambio el IMEI que tenia la fila de B. La fila
 *  de B recibe X y se confirma. Las reglas siguientes solo valen si no se da este caso.</li>
 *  <li>X esta en una fila de otro pedido sin etiqueta: se intercambian los IMEI con la primera fila de B sin confirmar (por posicion);
 *  X queda confirmado en B y la otra fila recibe el IMEI que tenia la de B (o ninguno) y queda sin confirmar, aunque lo estuviera.</li>
 *  <li>X no esta en ninguna fila: va a la primera fila LIBRE de B (sin IMEI, sin confirmar, sin etiqueta) y se confirma. Nunca saca de
 *  la tabla un IMEI que alguien leyo: sin fila libre no se toca nada, aunque B tenga filas sin confirmar con otro IMEI.</li>
 *  <li>Si B no tiene filas en el ERP (salio antes de que el ERP leyera ShippyPro, o es antiguo) no se toca nada y se cuenta aparte
 *  en {@code ajenos}: no va a «para revisar». Si no se puede por otra causa, no se toca nada y queda «para revisar»: YA_SALIO (X esta en
 *  una fila con etiqueta de otro pedido), CANCELADO (las unicas filas posibles, la de X o los huecos de B, estan canceladas) o SIN_HUECO.</li>
 *  </ol>
 *  Un par puede quedar SIN_HUECO solo porque el hueco de B lo ocupa un IMEI que un par posterior de la misma pasada saca de ahi (regla 2):
 *  al terminar de recorrer los pares se vuelven a intentar los SIN_HUECO, mientras alguno avance y hasta 3 vueltas, en la misma transaccion;
 *  solo van a «para revisar» los que siguen sin hueco al final.
 *  Solo cambia el IMEI de una fila con la etiqueta generada (ENVIO_FECHA) por la regla «el libro manda», y lo apunta con DETALLE = LIBRO;
 *  nunca el de una fila con la marca CANCELADO, y una cancelada tampoco se confirma. Nunca toca el historico. Poner un IMEI en una fila «En espera de stock» le quita la
 *  marca como el PUT del IMEI. Todo cambio va al historial sin usuario: IMEI, IMEI_INTERCAMBIADO («con la fila n»), ASOCIADA (LIBRO) y
 *  MARCA_QUITADA. Si el libro pone un IMEI en dos pedidos distintos, sus lineas van a «para revisar» (REPETIDO_EN_LIBRO) y no se aplica ninguna;
 *  el mismo par repetido cuenta una vez: asi repetir la misma pasada no cambia nada. Cada pasada rehace la lista «para revisar» de su canal y guarda sus recuentos. */
@Service
public class AsociacionAmazon {

    public static final String LIBRO = "LIBRO";
    public static final String HISTORICO = "HISTORICO";
    public static final String SIN_HUECO = "SIN_HUECO";
    public static final String YA_SALIO = "YA_SALIO";
    /** Ya no se guarda (un pedido sin filas en el ERP se cuenta en {@code ajenos}); queda por compatibilidad. */
    public static final String PEDIDO_AJENO = "PEDIDO_AJENO";
    public static final String CANCELADO = "CANCELADO";
    /** El libro pone el mismo IMEI en dos pedidos distintos: no se aplica ninguno (spec «el libro manda» §1). */
    public static final String REPETIDO_EN_LIBRO = "REPETIDO_EN_LIBRO";

    private static final String HUECOS = "SELECT ID_FILA, PEDIDO, POSICION, SKU, IMEI, ENVIO_FECHA, MARCA, ASOCIADA_EN, ASOCIADA_ORIGEN"
            + " FROM Pedido_fila WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND ";
    static final String SQL_DE_PEDIDOS = HUECOS + "PEDIDO IN (%s) FOR UPDATE";
    static final String SQL_CON_IMEI = HUECOS + "IMEI IN (%s) FOR UPDATE";
    /** Lo primero de la pasada: bloquea la fila de su canal (o la crea) para que dos pasadas del mismo canal vayan una detras de otra. */
    static final String SQL_TOMAR_PASADA = "INSERT INTO Pedido_asociacion_pasada (CANAL, CUANDO, CONFIRMADAS, INTERCAMBIADAS, PUESTAS,"
            + " YA_ESTABAN, PARA_REVISAR, DESCARTADAS) VALUES (?, ?, 0, 0, 0, 0, 0, 0) ON DUPLICATE KEY UPDATE CANAL = CANAL";
    static final String SQL_GUARDAR_PASADA = "UPDATE Pedido_asociacion_pasada SET CUANDO = ?, CONFIRMADAS = ?, INTERCAMBIADAS = ?,"
            + " PUESTAS = ?, YA_ESTABAN = ?, PARA_REVISAR = ?, DESCARTADAS = ? WHERE CANAL = ?";
    static final String SQL_ACTUALIZAR = "UPDATE Pedido_fila SET IMEI = ?, ASOCIADA_EN = ?, ASOCIADA_ORIGEN = ? WHERE ID_FILA = ?";
    static final String SQL_QUITAR_ESPERA = "UPDATE Pedido_fila SET MARCA = NULL, MARCA_NOTA = NULL, MARCA_EN = NULL, MARCA_USU = NULL"
            + " WHERE ID_FILA = ? AND MARCA = 'ESPERA_STOCK'";
    static final String SQL_APUNTE = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_BORRAR_REVISAR = "DELETE FROM Pedido_asociacion_revisar WHERE CANAL = ?";
    static final String SQL_INSERTAR_REVISAR = "INSERT INTO Pedido_asociacion_revisar (CANAL, PEDIDO, IMEI, MOTIVO, DETALLE, VISTO_EN)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    static final String SQL_PASADAS = "SELECT CANAL, CUANDO, CONFIRMADAS, INTERCAMBIADAS, PUESTAS, YA_ESTABAN, PARA_REVISAR, DESCARTADAS,"
            + " LIBRO_MODIFICADO, ERROR, ERROR_EN FROM Pedido_asociacion_pasada ORDER BY CANAL";
    /** El lector de los libros: la fecha de modificacion del libro de su ultima lectura buena. */
    static final String SQL_LIBRO_LEIDO = "SELECT LIBRO_MODIFICADO FROM Pedido_asociacion_pasada WHERE CANAL = ?";
    static final String SQL_GUARDAR_LIBRO = "UPDATE Pedido_asociacion_pasada SET LIBRO_MODIFICADO = ?, ERROR = NULL, ERROR_EN = NULL"
            + " WHERE CANAL = ?";
    /** Un fallo del lector: no toca nada mas. Si el canal aun no tenia pasada, la crea con los recuentos a 0 y CUANDO = la hora del error. */
    static final String SQL_APUNTAR_ERROR = "INSERT INTO Pedido_asociacion_pasada (CANAL, CUANDO, CONFIRMADAS, INTERCAMBIADAS, PUESTAS,"
            + " YA_ESTABAN, PARA_REVISAR, DESCARTADAS, ERROR, ERROR_EN) VALUES (?, ?, 0, 0, 0, 0, 0, 0, ?, ?)"
            + " ON DUPLICATE KEY UPDATE ERROR = VALUES(ERROR), ERROR_EN = VALUES(ERROR_EN)";
    /** El libro no cambio y Drive respondio: el error de una vuelta anterior ya no vale (no toca nada mas). */
    static final String SQL_BORRAR_ERROR = "UPDATE Pedido_asociacion_pasada SET ERROR = NULL, ERROR_EN = NULL WHERE CANAL = ? AND ERROR IS NOT NULL";
    static final String SQL_REVISAR = "SELECT CANAL, PEDIDO, IMEI, MOTIVO, DETALLE, VISTO_EN FROM Pedido_asociacion_revisar ORDER BY CANAL, ID";

    private final JdbcTemplate jdbc;
    private final ComprobadorLibro comprobador;

    public AsociacionAmazon(JdbcTemplate jdbc, ComprobadorLibro comprobador) {
        this.jdbc = jdbc;
        this.comprobador = comprobador;
    }

    /** Una pasada del libro de ventas de un canal de Amazon, en una transaccion (READ COMMITTED: MariaDB 11.6+ da el error 1020 al leer
     *  y luego actualizar con aislamiento de instantanea; las filas afectadas se leen con FOR UPDATE). {@code pares} sin limpiar. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RespuestaAsociaciones aplicar(Canal canal, List<PeticionAsociaciones.Par> pares, LocalDateTime ahora) {
        return pasada(canal, pares, ahora);
    }

    /** Lo mismo que {@link #aplicar} para el lector de los libros: en la misma transaccion guarda la fecha de modificacion del libro
     *  leido ({@code libroModificado}, hora de Madrid) y borra el error de la pasada del canal. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RespuestaAsociaciones aplicarLibro(Canal canal, List<PeticionAsociaciones.Par> pares, LocalDateTime ahora,
                                              LocalDateTime libroModificado) {
        RespuestaAsociaciones r = pasada(canal, pares, ahora);
        jdbc.update(SQL_GUARDAR_LIBRO, Timestamp.valueOf(libroModificado), canal.name());
        return r;
    }

    /** La fecha de modificacion del libro de la ultima lectura buena del canal; null si el lector aun no lo leyo. */
    public LocalDateTime libroLeido(Canal canal) {
        List<LocalDateTime> f = jdbc.query(SQL_LIBRO_LEIDO, (rs, n) -> hora(rs, "LIBRO_MODIFICADO"), canal.name());
        return f.isEmpty() ? null : f.get(0);
    }

    /** Apunta el fallo de una vuelta del lector en la pasada del canal (sin tocar recuentos, lista ni filas). */
    public void apuntarError(Canal canal, String error, LocalDateTime ahora) {
        Timestamp hora = Timestamp.valueOf(ahora);
        jdbc.update(SQL_APUNTAR_ERROR, canal.name(), hora, error, hora);
    }

    /** Borra el error apuntado en la pasada del canal (Drive vuelve a responder y el libro no cambio): recuentos, lista y filas se quedan. */
    public void borrarError(Canal canal) {
        jdbc.update(SQL_BORRAR_ERROR, canal.name());
    }

    private RespuestaAsociaciones pasada(Canal canal, List<PeticionAsociaciones.Par> pares, LocalDateTime ahora) {
        if (canal == null || !canal.esAmazon()) throw new IllegalArgumentException("La asociacion solo es de Amazon");
        Timestamp hora = Timestamp.valueOf(ahora);
        jdbc.update(SQL_TOMAR_PASADA, canal.name(), hora);

        // Limpiar: numero normalizado, IMEI de 15 cifras; un IMEI en dos pedidos va a revisar.
        List<String[]> validos = new ArrayList<>();
        int descartadas = 0;
        for (PeticionAsociaciones.Par p : pares) {
            String pedido = p == null || p.pedido() == null || p.pedido().isBlank() ? null : SincroShippyPro.numeroGuardado(p.pedido());
            String imei = p == null || p.imei() == null ? "" : p.imei().strip();
            if (pedido == null || !imei.matches("[0-9]{15}")) descartadas++;
            else validos.add(new String[]{pedido, imei});
        }
        // Un IMEI en dos pedidos distintos: todas sus lineas a revisar y no se aplica ninguna. El mismo par repetido cuenta una vez.
        Map<String, Set<String>> pedidosPorImei = new HashMap<>();
        for (String[] v : validos) pedidosPorImei.computeIfAbsent(v[1], k -> new LinkedHashSet<>()).add(v[0]);
        List<Object[]> repetidos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        List<String[]> aplicables = new ArrayList<>();
        for (String[] v : validos) {
            if (!vistos.add(v[0] + "|" + v[1])) {
                descartadas++;
                continue;
            }
            if (pedidosPorImei.get(v[1]).size() > 1) repetidos.add(new Object[]{canal.name(), v[0], v[1], REPETIDO_EN_LIBRO, null, hora});
            else aplicables.add(v);
        }

        Set<String> pedidos = new LinkedHashSet<>();
        Set<String> imeis = new LinkedHashSet<>();
        for (String[] p : aplicables) {
            pedidos.add(p[0]);
            imeis.add(p[1]);
        }
        Reparto reparto = new Reparto(canal, cargar(canal, pedidos, imeis), ahora);
        reparto.revisar.addAll(repetidos);
        // Los IMEI que el libro pone en cada pedido, y la comprobacion de los que pueden ir a una fila con etiqueta (una lectura).
        Map<String, Set<String>> delLibro = new HashMap<>();
        for (String[] p : aplicables) delLibro.computeIfAbsent(p[0], k -> new HashSet<>()).add(p[1]);
        Map<String, String> aComprobar = new LinkedHashMap<>();
        for (String[] p : aplicables) {
            Hueco destino = reparto.destinoConEtiqueta(p[0], p[1], delLibro);
            if (destino != null) aComprobar.put(p[1], destino.sku);
        }
        reparto.preparar(delLibro, aComprobar, comprobador.rechazos(canal, aComprobar), m -> comprobador.rechazos(canal, m));
        for (String[] p : aplicables) reparto.aplicar(p[0], p[1]);
        reparto.reintentarSinHueco();

        List<Object[]> filas = new ArrayList<>();
        for (Hueco h : reparto.cambiadas)
            filas.add(new Object[]{h.imei, h.asociadaEn == null ? null : Timestamp.valueOf(h.asociadaEn), h.asociadaOrigen, h.id});
        if (!filas.isEmpty()) jdbc.batchUpdate(SQL_ACTUALIZAR, filas);
        if (!reparto.esperasQuitadas.isEmpty()) {
            List<Object[]> lote = new ArrayList<>();
            for (Integer id : reparto.esperasQuitadas) lote.add(new Object[]{id});
            jdbc.batchUpdate(SQL_QUITAR_ESPERA, lote);
        }
        if (!reparto.apuntes.isEmpty()) jdbc.batchUpdate(SQL_APUNTE, reparto.apuntes);
        jdbc.update(SQL_BORRAR_REVISAR, canal.name());
        if (!reparto.revisar.isEmpty()) jdbc.batchUpdate(SQL_INSERTAR_REVISAR, reparto.revisar);
        RespuestaAsociaciones r = new RespuestaAsociaciones(reparto.confirmadas, reparto.intercambiadas, reparto.puestas,
                reparto.yaEstaban, reparto.revisar.size(), descartadas, reparto.ajenos);
        jdbc.update(SQL_GUARDAR_PASADA, hora, r.confirmadas(), r.intercambiadas(), r.puestas(), r.yaEstaban(), r.paraRevisar(),
                r.descartadas(), canal.name());
        return r;
    }

    /** La ultima pasada de cada canal con su lista «para revisar». */
    public EstadoAsociaciones estado() {
        Map<String, List<EstadoAsociaciones.Revisar>> porCanal = new HashMap<>();
        record ConCanal(String canal, EstadoAsociaciones.Revisar revisar) {}
        for (ConCanal c : jdbc.query(SQL_REVISAR, (rs, n) -> new ConCanal(rs.getString("CANAL"), new EstadoAsociaciones.Revisar(
                rs.getString("PEDIDO"), rs.getString("IMEI"), rs.getString("MOTIVO"), hora(rs, "VISTO_EN"), rs.getString("DETALLE")))))
            porCanal.computeIfAbsent(c.canal(), k -> new ArrayList<>()).add(c.revisar());
        List<EstadoAsociaciones.Pasada> pasadas = jdbc.query(SQL_PASADAS, (rs, n) -> new EstadoAsociaciones.Pasada(rs.getString("CANAL"),
                hora(rs, "CUANDO"), rs.getInt("CONFIRMADAS"), rs.getInt("INTERCAMBIADAS"), rs.getInt("PUESTAS"), rs.getInt("YA_ESTABAN"),
                rs.getInt("PARA_REVISAR"), rs.getInt("DESCARTADAS"), hora(rs, "LIBRO_MODIFICADO"), rs.getString("ERROR"),
                hora(rs, "ERROR_EN"), List.copyOf(porCanal.getOrDefault(rs.getString("CANAL"), List.of()))));
        return new EstadoAsociaciones(List.copyOf(pasadas));
    }

    private static LocalDateTime hora(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toLocalDateTime();
    }

    /** Las filas de ShippyPro del canal de esos pedidos o con esos IMEI, bloqueadas, en tandas. */
    private List<Hueco> cargar(Canal canal, Collection<String> pedidos, Collection<String> imeis) {
        Map<Integer, Hueco> porId = new LinkedHashMap<>();
        leer(SQL_DE_PEDIDOS, canal, pedidos, porId);
        leer(SQL_CON_IMEI, canal, imeis, porId);
        return new ArrayList<>(porId.values());
    }

    private void leer(String sql, Canal canal, Collection<String> valores, Map<Integer, Hueco> porId) {
        for (List<String> tanda : FilasDAO.trozos(List.copyOf(valores))) {
            List<Object> args = new ArrayList<>();
            args.add(canal.name());
            args.addAll(tanda);
            for (Hueco h : jdbc.query(String.format(sql, FilasDAO.marcas(tanda.size())), AsociacionAmazon::hueco, args.toArray()))
                porId.putIfAbsent(h.id, h);
        }
    }

    private static Hueco hueco(ResultSet rs, int n) throws SQLException {
        return new Hueco(rs.getInt("ID_FILA"), rs.getString("PEDIDO"), rs.getObject("POSICION", Integer.class), rs.getString("SKU"),
                rs.getString("IMEI"), rs.getTimestamp("ENVIO_FECHA") != null, rs.getString("MARCA"), hora(rs, "ASOCIADA_EN"),
                rs.getString("ASOCIADA_ORIGEN"));
    }

    /** Una fila de ShippyPro del canal, con su IMEI y su asociacion tal como van quedando en la pasada. */
    private static final class Hueco {
        final int id;
        final String pedido;
        final Integer posicion;
        final String sku;
        final boolean conEtiqueta;
        String imei;
        String marca;
        LocalDateTime asociadaEn;
        String asociadaOrigen;

        Hueco(int id, String pedido, Integer posicion, String sku, String imei, boolean conEtiqueta, String marca,
              LocalDateTime asociadaEn, String asociadaOrigen) {
            this.id = id;
            this.pedido = pedido == null ? null : pedido.strip();
            this.posicion = posicion;
            this.sku = sku;
            this.imei = imei == null || imei.isBlank() ? null : imei.strip();
            this.conEtiqueta = conEtiqueta;
            this.marca = marca;
            this.asociadaEn = asociadaEn;
            this.asociadaOrigen = asociadaOrigen;
        }

        boolean cancelada() {
            return Marca.CANCELADO.name().equals(marca);
        }

        boolean confirmada() {
            return asociadaEn != null;
        }

        /** Un hueco del pedido: sin confirmar y sin etiqueta (cancelado o no; eso se mira aparte). */
        boolean abierto() {
            return !confirmada() && !conEtiqueta;
        }

        /** Una fila con etiqueta en la que el libro puede corregir el IMEI: sin confirmar y sin cancelar. */
        boolean corregible() {
            return conEtiqueta && !confirmada() && !cancelada();
        }
    }

    private static final Comparator<Hueco> POR_POSICION = Comparator.<Hueco, Integer>comparing(h -> h.posicion,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(h -> h.id);

    /** Las reglas sobre las filas cargadas; acumula lo que hay que escribir. */
    private static final class Reparto {
        final Canal canal;
        final LocalDateTime ahora;
        final Timestamp hora;
        final Map<String, List<Hueco>> porPedido = new HashMap<>();
        final Map<String, Set<Hueco>> porImei = new HashMap<>();
        final Set<Hueco> cambiadas = new LinkedHashSet<>();
        final Set<Integer> esperasQuitadas = new LinkedHashSet<>();
        final List<Object[]> apuntes = new ArrayList<>();
        final List<Object[]> revisar = new ArrayList<>();
        int confirmadas, intercambiadas, puestas, yaEstaban, ajenos;
        final List<String[]> sinHueco = new ArrayList<>();
        Map<String, Set<String>> delLibro = Map.of();
        /** IMEI → SKU con el que ya se comprobo; y lo que no valio. */
        final Map<String, String> comprobadoCon = new HashMap<>();
        final Map<String, ComprobadorLibro.Rechazo> rechazos = new HashMap<>();
        /** Para un IMEI que llega al camino «con etiqueta» sin comprobar (o con otra fila destino de otro SKU) a mitad de pasada. */
        Function<Map<String, String>, Map<String, ComprobadorLibro.Rechazo>> comprobar = m -> Map.of();

        Reparto(Canal canal, List<Hueco> filas, LocalDateTime ahora) {
            this.canal = canal;
            this.ahora = ahora;
            this.hora = Timestamp.valueOf(ahora);
            for (Hueco h : filas) {
                if (h.pedido != null) porPedido.computeIfAbsent(h.pedido, k -> new ArrayList<>()).add(h);
                if (h.imei != null) conImei(h.imei).add(h);
            }
            porPedido.values().forEach(l -> l.sort(POR_POSICION));
        }

        private Set<Hueco> conImei(String imei) {
            return porImei.computeIfAbsent(imei, k -> new TreeSet<>(Comparator.comparingInt(h -> h.id)));
        }

        /** {@code comprobados}: IMEI → SKU con el que se comprobo de una vez antes de aplicar; {@code rechazos}, lo que no valio;
         *  {@code comprobar}, el comprobador para los que haga falta comprobar despues. */
        void preparar(Map<String, Set<String>> delLibro, Map<String, String> comprobados, Map<String, ComprobadorLibro.Rechazo> rechazos,
                      Function<Map<String, String>, Map<String, ComprobadorLibro.Rechazo>> comprobar) {
            this.delLibro = delLibro;
            this.comprobadoCon.putAll(comprobados);
            this.rechazos.putAll(rechazos);
            this.comprobar = comprobar;
        }

        /** Si el par va por el camino «con etiqueta» (spec «el libro manda» §2.3): el pedido existe, el IMEI no esta ya en el, no tiene
         *  ninguna fila pendiente sin confirmar ni cancelar, y si una fila con etiqueta corregible cuyo IMEI (o ninguno) no lo pone el
         *  libro en el pedido. Devuelve esa fila (la primera por posicion) o null. */
        Hueco destinoConEtiqueta(String pedido, String imei, Map<String, Set<String>> delLibro) {
            List<Hueco> deB = porPedido.getOrDefault(pedido, List.of());
            if (deB.isEmpty() || deB.stream().anyMatch(h -> imei.equals(h.imei))) return null;
            if (deB.stream().anyMatch(h -> h.abierto() && !h.cancelada())) return null;
            Set<String> delPedido = delLibro.getOrDefault(pedido, Set.of());
            return deB.stream().filter(h -> h.corregible() && (h.imei == null || !delPedido.contains(h.imei))).findFirst().orElse(null);
        }

        /** Lo que dice el comprobador de X para la fila destino: lo comprobado antes de aplicar si fue con el SKU de esa fila; si no
         *  (el par llego a este camino a mitad de pasada, o la fila destino es otra de otro SKU), se comprueba ahora. Sin destino, lo
         *  comprobado antes (si lo hubo). */
        private ComprobadorLibro.Rechazo rechazo(String imei, Hueco destino) {
            if (destino == null || (comprobadoCon.containsKey(imei) && Objects.equals(comprobadoCon.get(imei), destino.sku)))
                return rechazos.get(imei);
            Map<String, String> uno = new HashMap<>();
            uno.put(imei, destino.sku);
            ComprobadorLibro.Rechazo r = comprobar.apply(uno).get(imei);
            comprobadoCon.put(imei, destino.sku);
            if (r == null) rechazos.remove(imei);
            else rechazos.put(imei, r);
            return r;
        }

        /** Aplica un par de la pasada; los SIN_HUECO se dejan para el reintento del final. */
        void aplicar(String pedido, String imei) {
            String motivo = intentar(pedido, imei);
            if (SIN_HUECO.equals(motivo)) sinHueco.add(new String[]{pedido, imei});
            else if (motivo != null) paraRevisar(pedido, imei, motivo);
        }

        /** Vuelve a intentar los SIN_HUECO mientras alguno avance (como mucho 3 vueltas); los que siguen sin hueco van a «para revisar». */
        void reintentarSinHueco() {
            List<String[]> pendientes = new ArrayList<>(sinHueco);
            for (int vuelta = 0; vuelta < 3 && !pendientes.isEmpty(); vuelta++) {
                List<String[]> siguen = new ArrayList<>();
                boolean avanzo = false;
                for (String[] p : pendientes) {
                    String motivo = intentar(p[0], p[1]);
                    if (motivo == null) avanzo = true;
                    else if (SIN_HUECO.equals(motivo)) siguen.add(p);
                    else paraRevisar(p[0], p[1], motivo);
                }
                pendientes = siguen;
                if (!avanzo) break;
            }
            for (String[] p : pendientes) paraRevisar(p[0], p[1], SIN_HUECO);
        }

        /** Aplica las reglas a un par: null si se resolvio (o es un pedido ajeno, que solo se cuenta); si no, el motivo y no toca nada. */
        private String intentar(String pedido, String imei) {
            List<Hueco> deB = porPedido.getOrDefault(pedido, List.of());
            if (deB.isEmpty()) {
                ajenos++;
                return null;
            }
            // 1. Ya esta en una fila del pedido.
            List<Hueco> enB = deB.stream().filter(h -> imei.equals(h.imei)).toList();
            if (!enB.isEmpty()) {
                Hueco h = enB.stream().filter(f -> !f.cancelada()).findFirst().orElse(null);
                if (h == null) return CANCELADO;
                if (h.confirmada()) yaEstaban++;
                else {
                    confirmar(h);
                    confirmadas++;
                }
                return null;
            }
            // El libro manda en un pedido sin fila pendiente y con filas con etiqueta (spec «el libro manda» §2.3).
            if (deB.stream().noneMatch(h -> h.abierto() && !h.cancelada()) && deB.stream().anyMatch(Hueco::corregible))
                return conEtiqueta(pedido, imei);
            // 2. Esta en una fila de otro pedido.
            Set<Hueco> fuera = porImei.getOrDefault(imei, Set.of());
            if (!fuera.isEmpty()) {
                if (fuera.stream().anyMatch(h -> h.conEtiqueta)) {
                    return YA_SALIO;
                }
                Hueco origen = fuera.stream().filter(h -> !h.cancelada()).findFirst().orElse(null);
                if (origen == null) {
                    return CANCELADO;
                }
                Hueco destino = deB.stream().filter(h -> h.abierto() && !h.cancelada()).findFirst().orElse(null);
                if (destino == null) {
                    return sinHueco(deB);
                }
                String delDestino = destino.imei;
                ponerImei(destino, imei);
                ponerImei(origen, delDestino);
                apunte(destino.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + origen.id);
                apunte(origen.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + destino.id);
                if (origen.confirmada()) {
                    origen.asociadaEn = null;
                    origen.asociadaOrigen = null;
                }
                confirmar(destino);
                intercambiadas++;
                return null;
            }
            // 3. No esta en ninguna fila: solo a una fila libre. Un IMEI que alguien leyo nunca sale de la tabla: sin fila libre, a revisar.
            List<Hueco> libres = deB.stream().filter(h -> h.abierto() && h.imei == null).toList();
            Hueco destino = libres.stream().filter(h -> !h.cancelada()).findFirst().orElse(null);
            if (destino == null) {
                return sinHueco(libres);
            }
            ponerImei(destino, imei);
            confirmar(destino);
            puestas++;
            return null;
        }

        /** La regla con etiqueta: comprobacion del movil, la fila de B y lo que pasa con la fila de otro pedido que tenia X. */
        private String conEtiqueta(String pedido, String imei) {
            Hueco destino = destinoConEtiqueta(pedido, imei, delLibro);
            ComprobadorLibro.Rechazo rechazo = rechazo(imei, destino);
            if (rechazo != null) {
                paraRevisar(pedido, imei, rechazo.motivo(), rechazo.detalle());
                return null;
            }
            Set<Hueco> fuera = porImei.getOrDefault(imei, Set.of());
            if (fuera.stream().anyMatch(Hueco::confirmada)) return YA_SALIO;
            if (fuera.stream().anyMatch(Hueco::cancelada)) return CANCELADO;
            if (destino == null) return SIN_HUECO;
            String delDestino = destino.imei;
            List<Hueco> otros = List.copyOf(fuera);                       // copia: ponerImei cambia porImei
            ponerImei(destino, imei);
            Hueco pendiente = null;
            for (Hueco o : otros) {
                if (o.conEtiqueta) ponerImei(o, null);                     // el ERP no inventa que fue en O
                else if (pendiente == null) pendiente = o;
                else ponerImei(o, null);
            }
            if (pendiente != null) {
                ponerImei(pendiente, delDestino);
                apunte(destino.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + pendiente.id);
                apunte(pendiente.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + destino.id);
            }
            confirmar(destino);
            if (otros.isEmpty()) puestas++;
            else intercambiadas++;
            return null;
        }

        /** De los huecos posibles del pedido (los sin confirmar ni etiqueta en la regla 2; los libres en la 3): CANCELADO si alguno lo
         *  seria contando las canceladas; si no, SIN_HUECO. */
        private static String sinHueco(List<Hueco> deB) {
            return deB.stream().anyMatch(h -> h.abierto() && h.cancelada()) ? CANCELADO : SIN_HUECO;
        }

        private void ponerImei(Hueco h, String nuevo) {
            String anterior = h.imei;
            if (anterior != null) {
                Set<Hueco> con = porImei.get(anterior);
                if (con != null) con.remove(h);
            }
            h.imei = nuevo;
            if (nuevo != null) conImei(nuevo).add(h);
            cambiadas.add(h);
            apunte(h.id, anterior, nuevo, CambioImei.IMEI, h.conEtiqueta ? LIBRO : null);
            // Un IMEI puesto resuelve «En espera de stock», como en el PUT del IMEI; las demas marcas se quedan.
            if (nuevo != null && Marca.ESPERA_STOCK.name().equals(h.marca)) {
                h.marca = null;
                esperasQuitadas.add(h.id);
                apunte(h.id, null, null, CambioImei.MARCA_QUITADA, Marca.ESPERA_STOCK.name() + FilasDAO.SEP_DETALLE + "al poner el IMEI");
            }
        }

        private void confirmar(Hueco h) {
            h.asociadaEn = ahora;
            h.asociadaOrigen = LIBRO;
            cambiadas.add(h);
            apunte(h.id, null, null, CambioImei.ASOCIADA, LIBRO);
        }

        private void apunte(int id, String anterior, String nuevo, String tipo, String detalle) {
            apuntes.add(new Object[]{id, null, hora, anterior, nuevo, tipo, detalle});
        }

        private void paraRevisar(String pedido, String imei, String motivo) {
            paraRevisar(pedido, imei, motivo, null);
        }

        private void paraRevisar(String pedido, String imei, String motivo, String detalle) {
            revisar.add(new Object[]{canal.name(), pedido, imei, motivo, detalle, hora});
        }
    }
}
