package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Una vuelta de lectura de ShippyPro: crea las filas de los pedidos nuevos, reabre las cerradas de los que vuelven a estar
 *  pendientes, marca como enviadas (con fecha, transportista y seguimiento) las de los pedidos que ShippyPro da por enviados y
 *  cierra las de los que ya no estan pendientes ({@link SincroShippyPro}). Una fila solo se cierra por faltar si su pedido falta
 *  en dos lecturas buenas seguidas; un envio es definitivo y la cierra al momento. Si la lectura falla, falta el ajuste de
 *  canales o ShippyPro trajo pedidos que no se pudieron leer, no se cierra nada; si fallan solo los envios, la vuelta sigue
 *  como buena, sin marcar ninguno, y lo anota. */
@Service
public class LecturaFilas {

    private static final Logger log = LoggerFactory.getLogger(LecturaFilas.class);
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String RECHAZADA = "RECHAZADA";
    static final int DIAS_REGISTRO = 30;
    /** Cuantos dias hacia atras se piden los envios: cubre un corte de la tarea de unos dias y una etiqueta impresa dias despues
     *  de generarse. */
    static final int DIAS_ENVIOS = 10;
    static final String AVISO_SIN_ENVIOS = "no se pudieron leer los envíos";
    /** Principio del aviso de la lectura con pedidos descartados; {@link VistaFilas#fuentes()} lo busca en el mensaje guardado. */
    static final String AVISO_DESCARTADOS = "Pedidos sin fecha valida, saltados";

    private final ClienteShippyPro shippyPro;
    private final FilasDAO dao;
    private final NsysDAO nsysDao;
    private final Map<String, Canal> canales;
    private final Clock clock;
    private final ReentrantLock enCurso = new ReentrantLock();
    /** Filas vivas cuyo pedido falto en la ultima lectura buena (solo en memoria: tras reiniciar, la primera lectura no cierra). */
    private Set<Integer> candidatasAntes = Set.of();

    @Autowired
    public LecturaFilas(ClienteShippyPro shippyPro, FilasDAO dao, NsysDAO nsysDao,
                        @Value("${pedidos.shippypro.canales:}") String canales) {
        this(shippyPro, dao, nsysDao, canales, Clock.system(ImportadorNsys.MADRID));
    }

    LecturaFilas(ClienteShippyPro shippyPro, FilasDAO dao, NsysDAO nsysDao, String canales, Clock clock) {
        this.shippyPro = shippyPro;
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.canales = Canal.deAjuste(canales);
        this.clock = clock;
    }

    /** Ejecuta {@code trabajo} con las vueltas de lectura detenidas: espera a que acabe la que esté en curso y, mientras dura, las
     *  vueltas programadas se saltan (como si hubiera otra en curso) y vuelven a los 5 minutos. Lo usa la carga del histórico. */
    public <T> T sinVueltas(Supplier<T> trabajo) throws InterruptedException {
        enCurso.lockInterruptibly();
        try {
            return trabajo.get();
        } finally {
            enCurso.unlock();
        }
    }

    public void pasada() {
        if (!enCurso.tryLock()) {
            log.info("Pedidos: ya hay una vuelta en curso; se salta esta");
            return;
        }
        try {
            vuelta();
        } finally {
            enCurso.unlock();
        }
    }

    private void vuelta() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        try {
            dao.podarLecturas(ahora.minusDays(DIAS_REGISTRO));
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo podar el registro de lecturas", e);
        }
        int id;
        try {
            id = dao.empezarLectura(ahora);
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo abrir la lectura: {}", e.getClass().getSimpleName());
            return;
        }
        if (canales.isEmpty()) {
            terminar(id, RECHAZADA, 0, Set.of(), "Falta el ajuste pedidos.shippypro.canales", LocalDateTime.now(clock));
            return;
        }
        ClienteShippyPro.Leidos leidos;
        try {
            leidos = shippyPro.pendientes();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminar(id, ERROR, 0, Set.of(), "Interrumpida", LocalDateTime.now(clock));
            return;
        } catch (IOException | RuntimeException e) {
            // Solo la clase de la excepcion: su mensaje puede traer la URL o ids.
            log.warn("Pedidos: no se pudo leer ShippyPro: {}", e.getClass().getSimpleName());
            terminar(id, ERROR, 0, Set.of(), "No se pudo leer ShippyPro (" + e.getClass().getSimpleName() + ")",
                    LocalDateTime.now(clock));
            return;
        }
        try {
            List<String> skus = new ArrayList<>();
            leidos.pedidos().forEach(p -> p.articulos().forEach(a -> skus.add(a.sku())));
            Set<String> todos = AceptaSku.conVariantes(skus);
            AceptaSku acepta = new AceptaSku(todos.isEmpty() ? List.of() : nsysDao.productosPorSkuDeCanal(todos));
            List<FilaPedido> vivas = dao.vivasDeShippyPro();
            Set<SincroShippyPro.ClavePedido> claves = SincroShippyPro.clavesPendientes(leidos.pedidos(), canales);
            List<FilaPedido> cerradas = claves.isEmpty() ? List.of() : dao.cerradasDeShippyPro(claves);
            // Con pedidos descartados no se sabe cuales de los enviados siguen pendientes: no se marca ningun envio.
            boolean hayDescartados = !leidos.descartados().isEmpty();
            boolean enviosLeidos = true;
            List<SincroShippyPro.MarcaEnvio> marcas = List.of();
            List<SincroShippyPro.MarcaEtiqueta> etiquetas = List.of();
            if (!hayDescartados) {
                List<ClienteShippyPro.EnvioShippyPro> enviados = leerEnvios();
                if (enviados == null) enviosLeidos = false;
                else {
                    Set<SincroShippyPro.ClavePedido> conEnvio = SincroShippyPro.clavesEnviadas(enviados, canales);
                    if (!conEnvio.isEmpty()) {
                        List<FilaPedido> porMarcar = dao.porMarcarDeShippyPro(conEnvio);
                        marcas = SincroShippyPro.marcarEnviadas(enviados, canales, claves, porMarcar);
                        etiquetas = SincroShippyPro.completarEtiquetas(enviados, canales, claves, porMarcar);
                    }
                }
            }
            SincroShippyPro.Plan plan = SincroShippyPro.de(leidos.pedidos(), canales, acepta, vivas, cerradas,
                    LocalDateTime.now(clock).toLocalDate());
            // Cerrar solo lo que falta en dos lecturas buenas seguidas. Con pedidos descartados (sin fecha valida) no se puede
            // saber cuales de las filas ausentes son suyos: no se cierra nada.
            List<Integer> cerrar = new ArrayList<>();
            if (!hayDescartados) for (Integer fila : plan.cerrar()) if (candidatasAntes.contains(fila)) cerrar.add(fila);
            // Sin transaccion conjunta: se inserta, se reabre, se marcan los envios y despues se cierra; si falla en medio, la
            // siguiente vuelta lo rehace.
            dao.insertar(plan.insertar());
            dao.reabrir(plan.reabrir(), ahora); // el inicio de la vuelta, no despues de las llamadas a ShippyPro: un envio creado entre medias es posterior
            dao.marcarEnviadas(marcas, LocalDateTime.now(clock));
            dao.marcarEtiquetas(etiquetas);
            dao.cerrar(cerrar, LocalDateTime.now(clock));
            candidatasAntes = hayDescartados ? Set.of() : new HashSet<>(plan.cerrar());
            List<String> avisos = new ArrayList<>();
            if (!plan.reabrir().isEmpty()) avisos.add(plan.reabrir().size() + " filas reabiertas");
            if (!enviosLeidos) avisos.add(AVISO_SIN_ENVIOS);
            if (hayDescartados) avisos.add(AVISO_DESCARTADOS + " (no se cierra nada): "
                    + String.join(", ", leidos.descartados()));
            if (leidos.pedidos().isEmpty() && !vivas.isEmpty())
                avisos.add("ShippyPro no trae pendientes y hay " + vivas.size() + " filas vivas");
            String aviso = avisos.isEmpty() ? null : String.join("; ", avisos);
            dao.terminarLectura(id, OK, leidos.pedidos().size(), plan.desconocidos(), aviso, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("Pedidos: no se pudo guardar la vuelta", e);
            terminar(id, ERROR, 0, Set.of(), "No se pudo guardar la vuelta (" + e.getClass().getSimpleName() + ")",
                    LocalDateTime.now(clock));
        }
    }

    /** Los envios de los ultimos dias, o null si no se pudieron leer (la vuelta sigue sin ellos). Sin datos en el registro: solo la
     *  clase de la excepcion. */
    private List<ClienteShippyPro.EnvioShippyPro> leerEnvios() {
        Instant fin = clock.instant();
        try {
            return shippyPro.enviados(fin.minus(DIAS_ENVIOS, ChronoUnit.DAYS), fin);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.warn("Pedidos: no se pudieron leer los envios de ShippyPro: {}", e.getClass().getSimpleName());
        }
        return null;
    }

    /** Cierra la lectura sin dejar escapar un fallo de la base de datos: la tarea programada no debe morir por esto. */
    private void terminar(int id, String resultado, int filas, Set<String> desconocidos, String mensaje, LocalDateTime fin) {
        candidatasAntes = Set.of(); // tras un fallo o un rechazo la lectura anterior ya no cuenta como «seguida»
        try {
            dao.terminarLectura(id, resultado, filas, desconocidos, mensaje, fin);
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo cerrar la lectura {}: {}", id, e.getClass().getSimpleName());
        }
    }
}
