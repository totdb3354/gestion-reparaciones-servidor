package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** La carga a mano del histórico de la hoja de pedidos (spec «Pedidos como la hoja» §8). Nunca se enciende sola: la lanza un
 *  ADMIN y trabaja en un hilo aparte. Lee la hoja entera (sin bloquear nada), y después, en UNA transacción y con las vueltas de
 *  lectura de ShippyPro detenidas ({@link LecturaFilas#sinVueltas}): borra las filas HISTORICO, traspasa a las filas del ERP los
 *  IMEI de la hoja que son de un pedido que ya tiene ({@link Traspaso}) y crea el resto como histórico. Repetirla da el mismo
 *  resultado. El estado de la última carga de este arranque está solo en memoria. Ni el informe ni el log llevan datos de filas
 *  concretas, y el motivo de un error es un texto fijo. */
@Service
public class CargaHistorico {

    private static final Logger log = LoggerFactory.getLogger(CargaHistorico.class);

    public enum Estado { NUNCA, EN_CURSO, OK, ERROR }

    /** {@code informe} solo en OK; {@code motivo} solo en ERROR. */
    public record EstadoCarga(Estado estado, @Schema(nullable = true) LocalDateTime empezo, @Schema(nullable = true) LocalDateTime acabo,
                              InformeCarga informe, @Schema(nullable = true) String motivo) {}

    /** Falta el ajuste pedidos.historico.hoja. */
    public static class NoConfigurada extends RuntimeException {
        public NoConfigurada(String mensaje) {
            super(mensaje);
        }
    }

    /** Ya hay una carga en marcha. */
    public static class YaEnCurso extends RuntimeException {
        public YaEnCurso(String mensaje) {
            super(mensaje);
        }
    }

    static final String MSG_NO_CONFIGURADA = "No está configurada la hoja del histórico (pedidos.historico.hoja)";
    static final String MSG_EN_CURSO = "Ya hay una carga del histórico en marcha";
    static final String MOTIVO_FORMATO = "La hoja no tiene la pestaña o las columnas que hacen falta (Fecha, Plataforma, SKU e IMEI)";
    static final String MOTIVO_LEER = "No se pudo leer la hoja de Google; no se ha cambiado nada";
    static final String MOTIVO_GUARDAR = "No se pudo guardar el histórico; no se ha cambiado nada";
    static final String MOTIVO_INTERRUMPIDA = "La carga se interrumpió; no se ha cambiado nada";
    static final String MOTIVO_POCAS = "La hoja ha devuelto muy pocas filas: no se ha tocado nada";
    static final String MOTIVO_CAMBIOS = "Hay apuntes de cambios de IMEI en filas del histórico: no se ha tocado nada";
    static final int PLATAFORMAS_EN_INFORME = 15;

    /** Se aborta la carga, sin escribir nada, con este motivo fijo. */
    static class CargaAbortada extends RuntimeException {
        CargaAbortada(String motivo) {
            super(motivo);
        }
    }

    private final ClienteSheets sheets;
    private final FilasDAO dao;
    private final NsysDAO nsysDao;
    private final int minimo;
    private final LecturaFilas lectura;
    private final TransactionTemplate transaccion;
    private final String hoja;
    private final String pestana;
    private final Clock clock;
    private final Executor hilo;
    private final AtomicReference<EstadoCarga> estado = new AtomicReference<>(new EstadoCarga(Estado.NUNCA, null, null, null, null));

    @Autowired
    public CargaHistorico(ClienteSheets sheets, FilasDAO dao, NsysDAO nsysDao, LecturaFilas lectura,
                          PlatformTransactionManager transacciones,
                          @Value("${pedidos.historico.hoja:}") String hoja,
                          @Value("${pedidos.historico.pestana:PEDIDOS 2026}") String pestana,
                          @Value("${pedidos.historico.minimo:1000}") int minimo) {
        this(sheets, dao, nsysDao, lectura, transacciones, hoja, pestana, minimo, Clock.system(ImportadorNsys.MADRID),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "carga-historico-pedidos");
                    t.setDaemon(true);
                    return t;
                }));
    }

    CargaHistorico(ClienteSheets sheets, FilasDAO dao, NsysDAO nsysDao, LecturaFilas lectura, PlatformTransactionManager transacciones,
                   String hoja, String pestana, int minimo, Clock clock, Executor hilo) {
        this.sheets = sheets;
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.minimo = minimo;
        this.lectura = lectura;
        // READ COMMITTED: MariaDB 11.6+ da el error 1020 al leer y luego actualizar con aislamiento de instantanea.
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.hoja = hoja == null ? "" : hoja.strip();
        this.pestana = pestana == null || pestana.isBlank() ? "PEDIDOS 2026" : pestana.strip();
        this.clock = clock;
        this.hilo = hilo;
    }

    public EstadoCarga estado() {
        return estado.get();
    }

    /** Arranca la carga en segundo plano y devuelve el estado EN_CURSO. {@link NoConfigurada} si falta el ajuste de la hoja,
     *  {@link YaEnCurso} si hay otra en marcha. */
    public EstadoCarga iniciar() {
        if (hoja.isEmpty()) throw new NoConfigurada(MSG_NO_CONFIGURADA);
        LocalDateTime empezo = LocalDateTime.now(clock);
        EstadoCarga nuevo = new EstadoCarga(Estado.EN_CURSO, empezo, null, null, null);
        while (true) {
            EstadoCarga actual = estado.get();
            if (actual.estado() == Estado.EN_CURSO) throw new YaEnCurso(MSG_EN_CURSO);
            if (estado.compareAndSet(actual, nuevo)) break;
        }
        try {
            hilo.execute(() -> ejecutar(empezo));
        } catch (RuntimeException e) {
            terminar(empezo, null, MOTIVO_GUARDAR);
            log.warn("Pedidos: no se pudo lanzar la carga del historico: {}", e.getClass().getSimpleName());
        }
        return nuevo;
    }

    private void ejecutar(LocalDateTime empezo) {
        try {
            TablaHoja tabla = sheets.leerTodo(hoja, pestana, LectorHistorico.FORMATO);
            LectorHistorico.Lectura leida = LectorHistorico.leer(tabla);
            // Una lectura vacia o cortada no debe vaciar el historico: se aborta antes de tocar nada.
            if (leida.filas().size() < minimo) throw new CargaAbortada(MOTIVO_POCAS);
            // Lo que solo depende de la hoja (y NSYS) se calcula antes de tomar los bloqueos de la transaccion.
            Set<String> vendidos = vendidosEnNsys(Traspaso.imeisCandidatos(leida.filas(), LocalDate.now(clock)));
            InformeCarga informe = lectura.sinVueltas(() -> transaccion.execute(s -> guardar(leida, vendidos)));
            terminar(empezo, informe, null);
            log.info("Pedidos: historico cargado ({} filas, {} IMEI traspasados)", informe.cargadasHistorico(),
                    informe.traspasadasA() + informe.traspasadasB() + informe.traspasadasC());
        } catch (CargaAbortada e) {
            log.warn("Pedidos: carga del historico abortada sin tocar nada");
            terminar(empezo, null, e.getMessage());
        } catch (FormatoHojaException e) {
            log.warn("Pedidos: la hoja del historico no tiene el formato esperado");
            terminar(empezo, null, MOTIVO_FORMATO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminar(empezo, null, MOTIVO_INTERRUMPIDA);
        } catch (IOException e) {
            // Solo la clase: el mensaje puede traer la URL o el id de la hoja.
            log.warn("Pedidos: no se pudo leer la hoja del historico: {}", e.getClass().getSimpleName());
            terminar(empezo, null, MOTIVO_LEER);
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo guardar el historico: {}", e.getClass().getSimpleName());
            terminar(empezo, null, MOTIVO_GUARDAR);
        } catch (Error e) {
            // Que el estado no se quede en EN_CURSO para siempre (y bloquee otra carga) si el hilo muere.
            terminar(empezo, null, MOTIVO_GUARDAR);
            throw e;
        }
    }

    private void terminar(LocalDateTime empezo, InformeCarga informe, String motivo) {
        estado.set(new EstadoCarga(motivo == null ? Estado.OK : Estado.ERROR, empezo, LocalDateTime.now(clock), informe, motivo));
    }

    /** Dentro de la transacción: las filas de ShippyPro (bloqueadas), el reparto, el borrado, los IMEI y las filas del histórico. */
    private InformeCarga guardar(LectorHistorico.Lectura leida, Set<String> vendidos) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<FilaPedido> erp = dao.todasDeShippyProBloqueadas();
        Traspaso.Plan plan = Traspaso.planear(leida.filas(), erp, ahora.toLocalDate(), vendidos);
        // Borrar las filas HISTORICO borraria en cascada sus apuntes de cambios: no deberia haber ninguno.
        if (dao.contarCambiosDeHistorico() > 0) throw new CargaAbortada(MOTIVO_CAMBIOS);
        dao.borrarHistorico();
        for (Traspaso.Traslado t : plan.traslados())
            if (!dao.traspasarImei(t.idFila(), t.imei(), ahora))
                throw new IllegalStateException("Una fila del ERP cambio de IMEI durante la carga");
        List<FilaPedido> filas = new ArrayList<>();
        for (LectorHistorico.FilaHoja f : plan.historico())
            filas.add(new FilaPedido(0, f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), "HISTORICO", null,
                    null, f.fecha().atStartOfDay()));
        dao.insertarHistorico(filas);
        return informe(leida, plan);
    }

    /** De esos IMEI, los que NSYS da por vendidos (el lote mas reciente de cada IMEI), con el texto del IMEI de la hoja. En tandas. */
    private Set<String> vendidosEnNsys(Set<String> imeis) {
        Set<String> vendidos = new HashSet<>();
        if (imeis.isEmpty()) return vendidos;
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        for (String imei : imeis) {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(imei));
            if (m != null && m.estado() == EstadoMovil.VENDIDO) vendidos.add(imei);
        }
        return vendidos;
    }

    static InformeCarga informe(LectorHistorico.Lectura leida, Traspaso.Plan plan) {
        Traspaso.Recuentos r = plan.recuentos();
        Map<String, Integer> plataformas = new LinkedHashMap<>();
        leida.plataformas().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(PLATAFORMAS_EN_INFORME).forEach(e -> plataformas.put(e.getKey(), e.getValue()));
        return new InformeCarga(leida.leidas(), plan.historico().size(), leida.descartadasVacias(), leida.sinFechaHeredada(),
                leida.sinFechaDescartada(), leida.incidenciasDescartadas(), leida.imeiNoImei(), r.a(), r.b(), r.c(), r.yaPuesto(), r.conflicto(), r.casadasSinImei(),
                r.sinPareja(), r.yaVendido(), r.ventaAnterior(), plataformas);
    }
}
