package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Una vuelta de lectura de pedidos (spec pieza 2 §4.4): las tres fuentes por separado, con su registro; si las tres tienen datos
 *  (de esta vuelta o de una anterior desde el arranque: ajuste 6), el cruce y su guardado. */
@Service
public class LecturaPedidos {

    private static final Logger log = LoggerFactory.getLogger(LecturaPedidos.class);
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String INCOMPLETA = "INCOMPLETA";
    static final double MINIMO_FILAS = 0.9;
    static final int DIAS_REGISTRO = 30;
    static final int MAX_MENSAJE = 1000;

    private final ClienteShippyPro shippyPro;
    private final ClienteSheets sheets;
    private final PedidosDAO dao;
    private final NsysDAO nsysDao;
    private final Map<String, Canal> canales;
    private final String hojaPedidos;
    private final String pestanaPedidos;
    private final String hojaSimulador;
    private final String pestanaSimulador;
    private final int filas;
    private final Clock clock;
    private List<PedidoShippyPro> ultimosShippyPro;
    private List<FilaPedidos2026> ultimasPedidos2026;
    private List<FilaSimulador> ultimasSimulador;

    @Autowired
    public LecturaPedidos(ClienteShippyPro shippyPro, ClienteSheets sheets, PedidosDAO dao, NsysDAO nsysDao,
                          @Value("${pedidos.shippypro.canales:}") String canales,
                          @Value("${pedidos.pedidos2026.hoja:}") String hojaPedidos,
                          @Value("${pedidos.pedidos2026.pestana:PEDIDOS 2026}") String pestanaPedidos,
                          @Value("${pedidos.simulador.hoja:}") String hojaSimulador,
                          @Value("${pedidos.simulador.pestana:Registro actividad}") String pestanaSimulador,
                          @Value("${pedidos.filas:4000}") int filas) {
        this(shippyPro, sheets, dao, nsysDao, canales, hojaPedidos, pestanaPedidos, hojaSimulador, pestanaSimulador, filas,
                Clock.system(ImportadorNsys.MADRID));
    }

    LecturaPedidos(ClienteShippyPro shippyPro, ClienteSheets sheets, PedidosDAO dao, NsysDAO nsysDao, String canales,
                   String hojaPedidos, String pestanaPedidos, String hojaSimulador, String pestanaSimulador, int filas, Clock clock) {
        this.shippyPro = shippyPro;
        this.sheets = sheets;
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.canales = Canal.deAjuste(canales);
        this.hojaPedidos = hojaPedidos == null ? "" : hojaPedidos;
        this.pestanaPedidos = pestanaPedidos;
        this.hojaSimulador = hojaSimulador == null ? "" : hojaSimulador;
        this.pestanaSimulador = pestanaSimulador;
        this.filas = filas;
        this.clock = clock;
    }

    private record Leida<T>(Integer id, T datos) {}

    public void pasada() {
        LocalDateTime ahora = ahora();
        try {
            dao.podarLecturas(ahora.minusDays(DIAS_REGISTRO));
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo podar el registro de lecturas", e);
        }
        Leida<List<PedidoShippyPro>> s = leerShippyPro();
        if (Thread.currentThread().isInterrupted()) return;
        Leida<List<FilaPedidos2026>> p = leerHoja(FuentePedidos.PEDIDOS_2026, hojaPedidos, "pedidos.pedidos2026.hoja",
                pestanaPedidos, LectorPedidos2026.FORMATO, LectorPedidos2026::filas);
        if (Thread.currentThread().isInterrupted()) return;
        Leida<List<FilaSimulador>> m = leerHoja(FuentePedidos.SIMULADOR, hojaSimulador, "pedidos.simulador.hoja",
                pestanaSimulador, LectorSimulador.FORMATO, LectorSimulador::filas);
        if (Thread.currentThread().isInterrupted()) return;
        if (s.datos() != null) ultimosShippyPro = s.datos();
        if (p.datos() != null) ultimasPedidos2026 = p.datos();
        if (m.datos() != null) ultimasSimulador = m.datos();
        if (ultimosShippyPro == null || ultimasPedidos2026 == null || ultimasSimulador == null) {
            log.warn("Pedidos: falta alguna fuente desde el arranque; no se aplica nada en esta vuelta");
            return;
        }
        try {
            Set<String> imeis = new LinkedHashSet<>();
            ultimasPedidos2026.forEach(f -> imeis.add(f.imei()));
            ultimasSimulador.stream().map(FilaSimulador::imei).filter(i -> i != null).forEach(imeis::add);
            Map<String, MovilNsys> moviles = new HashMap<>();
            if (!imeis.isEmpty())
                for (MovilNsys mv : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(mv.imeiSn()), mv);
            List<String> skus = new ArrayList<>();
            ultimosShippyPro.forEach(x -> x.articulos().forEach(a -> skus.add(a.sku())));
            ultimasPedidos2026.forEach(f -> skus.add(f.sku()));
            ultimasSimulador.forEach(f -> skus.add(f.sku()));
            Set<String> todos = AceptaSku.conVariantes(skus);
            AceptaSku acepta = new AceptaSku(todos.isEmpty() ? List.of() : nsysDao.productosPorSkuDeCanal(todos));
            CrucePedidos.Resultado r = CrucePedidos.cruzar(new CrucePedidos.Entrada(ultimosShippyPro, canales,
                    ultimasPedidos2026, ultimasSimulador, acepta, moviles));
            dao.aplicar(r.pedidos(), ahora);
            if (s.id() != null && s.datos() != null) dao.anotarDesconocidos(s.id(), r.desconocidosShippyPro());
            if (m.id() != null && m.datos() != null) dao.anotarDesconocidos(m.id(), r.desconocidosSimulador());
        } catch (RuntimeException e) {
            log.error("Pedidos: no se pudo cruzar o guardar la vuelta", e);
            List<Integer> buenas = new ArrayList<>();
            for (Leida<?> l : List.of(s, p, m)) if (l.datos() != null && l.id() != null) buenas.add(l.id());
            try {
                dao.marcarError(buenas, corta("No se pudo cruzar o guardar: " + e));
            } catch (RuntimeException e2) {
                log.error("Pedidos: no se pudieron marcar las lecturas como ERROR", e2);
            }
        }
    }

    private Leida<List<PedidoShippyPro>> leerShippyPro() {
        int id = dao.empezarLectura(FuentePedidos.SHIPPYPRO, ahora());
        if (canales.isEmpty()) {
            dao.terminarLectura(id, RECHAZADA, 0, "Falta el ajuste pedidos.shippypro.canales", ahora());
            return new Leida<>(id, null);
        }
        try {
            ClienteShippyPro.Leidos leidos = shippyPro.pendientes();
            // Un pedido sin fecha válida se salta (decisión del usuario, 04/11): la lectura es buena y lo dice.
            String aviso = leidos.descartados().isEmpty() ? null
                    : corta("Pedidos sin fecha válida, saltados: " + String.join(", ", leidos.descartados()));
            dao.terminarLectura(id, OK, leidos.pedidos().size(), aviso, ahora());
            return new Leida<>(id, leidos.pedidos());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarLectura(id, ERROR, 0, "Interrumpida", ahora());
        } catch (IOException | RuntimeException e) {
            log.warn("Pedidos: no se pudo leer ShippyPro: {}", e.toString());
            dao.terminarLectura(id, ERROR, 0, corta(e.toString()), ahora());
        }
        return new Leida<>(id, null);
    }

    private <T> Leida<List<T>> leerHoja(FuentePedidos fuente, String hoja, String ajuste, String pestana, FormatoHoja formato,
                                        Function<TablaHoja, List<T>> traducir) {
        int id = dao.empezarLectura(fuente, ahora());
        if (hoja.isBlank()) {
            dao.terminarLectura(id, RECHAZADA, 0, "Falta el ajuste " + ajuste, ahora());
            return new Leida<>(id, null);
        }
        try {
            TablaHoja t = sheets.leer(hoja, pestana, formato, filas);
            Optional<LecturaGuardada> previa = dao.ultimaLecturaConFilas(fuente, Set.of(OK, INCOMPLETA));
            if (previa.isPresent() && t.total() < previa.get().filas() * MINIMO_FILAS) {
                dao.terminarLectura(id, INCOMPLETA, t.total(), String.format(
                        "Trae %d filas frente a las %d de la lectura anterior: se trata como incompleta", t.total(),
                        previa.get().filas()), ahora());
                return new Leida<>(id, null);
            }
            List<T> datos = traducir.apply(t);
            dao.terminarLectura(id, OK, t.total(), null, ahora());
            return new Leida<>(id, datos);
        } catch (FormatoHojaException e) {
            dao.terminarLectura(id, RECHAZADA, 0, corta(e.getMessage()), ahora());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarLectura(id, ERROR, 0, "Interrumpida", ahora());
        } catch (IOException | RuntimeException e) {
            log.warn("Pedidos: no se pudo leer {}: {}", fuente, e.toString());
            dao.terminarLectura(id, ERROR, 0, corta(e.toString()), ahora());
        }
        return new Leida<>(id, null);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String corta(String s) {
        return s.length() > MAX_MENSAJE ? s.substring(0, MAX_MENSAJE) : s;
    }
}
