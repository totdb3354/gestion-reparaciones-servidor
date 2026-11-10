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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** Una vuelta de lectura de ShippyPro: crea las filas de los pedidos nuevos y cierra las de los que ya no estan pendientes
 *  ({@link SincroShippyPro}). Si la lectura falla o falta el ajuste de canales no se cierra nada. */
@Service
public class LecturaFilas {

    private static final Logger log = LoggerFactory.getLogger(LecturaFilas.class);
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String RECHAZADA = "RECHAZADA";
    static final int DIAS_REGISTRO = 30;

    private final ClienteShippyPro shippyPro;
    private final FilasDAO dao;
    private final NsysDAO nsysDao;
    private final Map<String, Canal> canales;
    private final Clock clock;
    private final ReentrantLock enCurso = new ReentrantLock();

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
            SincroShippyPro.Plan plan = SincroShippyPro.de(leidos.pedidos(), canales, acepta, dao.vivasDeShippyPro(),
                    LocalDateTime.now(clock).toLocalDate());
            // Sin transaccion conjunta: primero se inserta y despues se cierra; si falla en medio, la siguiente vuelta lo rehace.
            dao.insertar(plan.insertar());
            dao.cerrar(plan.cerrar(), LocalDateTime.now(clock));
            String aviso = leidos.descartados().isEmpty() ? null
                    : "Pedidos sin fecha valida, saltados: " + String.join(", ", leidos.descartados());
            dao.terminarLectura(id, OK, leidos.pedidos().size(), plan.desconocidos(), aviso, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("Pedidos: no se pudo guardar la vuelta", e);
            terminar(id, ERROR, 0, Set.of(), "No se pudo guardar la vuelta (" + e.getClass().getSimpleName() + ")",
                    LocalDateTime.now(clock));
        }
    }

    /** Cierra la lectura sin dejar escapar un fallo de la base de datos: la tarea programada no debe morir por esto. */
    private void terminar(int id, String resultado, int filas, Set<String> desconocidos, String mensaje, LocalDateTime fin) {
        try {
            dao.terminarLectura(id, resultado, filas, desconocidos, mensaje, fin);
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo cerrar la lectura {}: {}", id, e.getClass().getSimpleName());
        }
    }
}
