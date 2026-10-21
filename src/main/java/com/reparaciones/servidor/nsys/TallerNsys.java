package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** El SKU del taller (spec 0.9.21, pieza 1): lo que vale de cada móvil, la corrección a mano, la vuelta a lo de NSYS y el
 *  cierre automático tras cada importación. El ERP no escribe en NSYS: solo guarda lo del taller por encima. */
@Service
public class TallerNsys {

    private static final Logger log = LoggerFactory.getLogger(TallerNsys.class);
    /** El grado como lo escribe NSYS (spec §2.3): [Ms]<letra>[100], con la letra A, A-, B o C. */
    static final Pattern GRADO = Pattern.compile("(Ms)?(A|A-|B|C)(100)?");
    static final int MAX_MOTIVO = 500;
    static final String ACCION = "CORREGIR_MOVIL_TALLER";

    private final NsysDAO nsysDao;
    private final TallerDAO tallerDao;
    private final LogDAO logDao;
    private final Clock clock;

    @Autowired
    public TallerNsys(NsysDAO nsysDao, TallerDAO tallerDao, LogDAO logDao) {
        this(nsysDao, tallerDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    TallerNsys(NsysDAO nsysDao, TallerDAO tallerDao, LogDAO logDao, Clock clock) {
        this.nsysDao = nsysDao;
        this.tallerDao = tallerDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- lo que vale (spec §2.1)

    /** Lo que vale de los móviles de estos IMEI: lee una vez sus cambios vigentes y, solo si hay alguno, el maestro. */
    public Function<MovilNsys, LoQueVale.Valor> valedor(Collection<String> imeis) {
        return valedor(tallerDao.vigentes(imeis));
    }

    /** Lo mismo con todos los cambios vigentes del taller (el stock entero). */
    public Function<MovilNsys, LoQueVale.Valor> valedorDeTodos() {
        return valedor(tallerDao.vigentes());
    }

    private Function<MovilNsys, LoQueVale.Valor> valedor(List<CambioTaller> vigentes) {
        Map<String, List<CambioTaller>> porImei = porImei(vigentes);
        Supplier<Maestro> maestro = perezoso(() -> new Maestro(nsysDao.cargarProductos()));
        return m -> LoQueVale.de(m, porImei.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()), maestro);
    }

    /** El producto («Goods») que hay que poner en NSYS (spec §2.4): si el color no cambia, el de ahora; si cambia, el más
     *  usado de ese color entre los móviles del mismo modelo, capacidad y eSIM; null si ninguno lo tiene (la web dice
     *  «búscalo a mano»). */
    public String productoAPoner(MovilNsys m, LoQueVale.Valor v) {
        return v.colorNsys() == null ? m.goods() : nsysDao.goodsMasUsados(m).get(v.color());
    }

    /** Todos los cambios del taller de un IMEI, para el rastro. */
    public List<CambioTaller> cambios(String imeiSn) {
        return tallerDao.deImei(imeiSn);
    }

    // ---------------------------------------------------------------- corrección a mano (spec §2.3)

    /** «Corregir color, grado o batería…». {@code color} o {@code grado} null: ese dato no cambia. Por cada dato que cambia
     *  respecto a lo que vale: si el nuevo es el de NSYS, se quita lo del taller (QUITADO); si no, queda VIGENTE. Una línea
     *  del registro de actividad por dato. */
    public void corregir(String imeiSn, String color, String grado, String motivo, int idUsu) {
        MovilNsys m = delEspejo(imeiSn);
        String porQue = motivo == null ? "" : motivo.strip();
        if (porQue.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        comprobarLargo(porQue);
        if (grado != null && !GRADO.matcher(grado).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El grado se escribe como en NSYS: A, A-, B o C, con Ms delante y 100 detrás si toca");
        if (color != null && !new Maestro(nsysDao.cargarProductos()).colores(m).contains(color))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El color " + color + " no está en el maestro para este modelo");
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        List<Paso> pasos = new ArrayList<>();
        paso(DatoTaller.COLOR, color, m, vigentes).ifPresent(pasos::add);
        paso(DatoTaller.GRADO, grado, m, vigentes).ifPresent(pasos::add);
        if (pasos.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sin cambios: el color y el grado ya son esos");
        guardar(m, pasos, porQue, idUsu);
    }

    /** «Volver a lo de NSYS»: quita lo del taller de los datos que lo tengan, con el motivo si se ha escrito. */
    public void volver(String imeiSn, String motivo, int idUsu) {
        MovilNsys m = delEspejo(imeiSn);
        String porQue = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (porQue != null) comprobarLargo(porQue);
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        if (vigentes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este móvil no tiene nada del taller");
        List<Paso> pasos = vigentes.stream().map(c -> new Paso(new TallerDAO.Nuevo(c.dato(), c.dato().deNsys(m),
                c.dato().deNsys(m), CambioTaller.Estado.QUITADO), c.valor())).toList();
        guardar(m, pasos, porQue, idUsu);
    }

    /** Un dato que cambia y lo que valía antes (para el registro). */
    private record Paso(TallerDAO.Nuevo nuevo, String antes) {}

    private static Optional<Paso> paso(DatoTaller dato, String nuevo, MovilNsys m, List<CambioTaller> vigentes) {
        if (nuevo == null) return Optional.empty();
        String antes = LoQueVale.valor(vigentes, dato, m);
        if (Normalizacion.mismoTexto(nuevo, antes)) return Optional.empty();
        String nsys = dato.deNsys(m);
        CambioTaller.Estado estado = Normalizacion.mismoTexto(nuevo, nsys) ? CambioTaller.Estado.QUITADO
                : CambioTaller.Estado.VIGENTE;
        return Optional.of(new Paso(new TallerDAO.Nuevo(dato, nuevo.strip(), nsys, estado), antes));
    }

    private void guardar(MovilNsys m, List<Paso> pasos, String motivo, int idUsu) {
        tallerDao.guardar(m.imeiSn(), pasos.stream().map(Paso::nuevo).toList(), idUsu, motivo, ahora());
        for (Paso p : pasos) logDao.insertar(idUsu, ACCION, detalle(m.imeiSn(), p), motivo);
    }

    /** «IMEI: 3500…, COLOR: Azul → Rojo», con « (vuelve a lo de NSYS)» detrás si quita lo del taller. */
    private static String detalle(String imeiSn, Paso p) {
        return "IMEI: " + imeiSn + ", " + p.nuevo().dato().name() + ": " + texto(p.antes()) + " → " + texto(p.nuevo().valor())
                + (p.nuevo().estado() == CambioTaller.Estado.QUITADO ? " (vuelve a lo de NSYS)" : "");
    }

    private static void comprobarLargo(String motivo) {
        if (motivo.length() > MAX_MOTIVO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
    }

    // ---------------------------------------------------------------- cierre tras la importación (spec §2.2)

    /** Tras una importación ACTUAL que acaba en OK: cada VIGENTE se compara con lo que NSYS dice ahora del dato (la fila más
     *  reciente del IMEI). Un móvil que ya no está en el espejo deja sus cambios como estaban. Devuelve cuántos cierra. */
    public int cerrar() {
        List<CambioTaller> vigentes = tallerDao.vigentes();
        if (vigentes.isEmpty()) return 0;
        Map<String, MovilNsys> masReciente = masRecientes(vigentes);
        LocalDateTime ahora = ahora();
        int cerrados = 0;
        for (CambioTaller c : vigentes) {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(c.imeiSn()));
            if (m == null) continue;
            String nsys = c.dato().deNsys(m);
            CierreTaller.Decision d = CierreTaller.decidir(c, nsys);
            if (d == CierreTaller.Decision.PASADO) cerrados += tallerDao.cerrar(c.id(), CambioTaller.Estado.PASADO, nsys, ahora);
            else if (d == CierreTaller.Decision.MANDA_NSYS)
                cerrados += tallerDao.cerrar(c.id(), CambioTaller.Estado.MANDA_NSYS, nsys, ahora);
        }
        if (cerrados > 0) log.info("NSYS: {} cambios del taller cerrados tras la importación", cerrados);
        return cerrados;
    }

    // ---------------------------------------------------------------- utilidades

    private MovilNsys delEspejo(String imeiSn) {
        List<MovilNsys> lotes = nsysDao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        return lotes.get(0);
    }

    /** La fila más reciente del espejo de cada IMEI de estos cambios, por {@link NsysDAO#claveCruce}. */
    private Map<String, MovilNsys> masRecientes(List<CambioTaller> cambios) {
        Set<String> imeis = new LinkedHashSet<>();
        for (CambioTaller c : cambios) imeis.add(c.imeiSn());
        Map<String, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(imeis)) mapa.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        return mapa;
    }

    /** Los cambios por IMEI ({@link NsysDAO#claveCruce}), en el orden en que llegan (del más antiguo al más reciente). */
    static Map<String, List<CambioTaller>> porImei(List<CambioTaller> cambios) {
        Map<String, List<CambioTaller>> mapa = new LinkedHashMap<>();
        for (CambioTaller c : cambios) mapa.computeIfAbsent(NsysDAO.claveCruce(c.imeiSn()), k -> new ArrayList<>()).add(c);
        return mapa;
    }

    /** Un valor que se calcula la primera vez que se pide (el maestro, solo si algún móvil tiene algo del taller). */
    private static <T> Supplier<T> perezoso(Supplier<T> fuente) {
        return new Supplier<>() {
            private T valor;

            @Override public T get() {
                if (valor == null) valor = fuente.get();
                return valor;
            }
        };
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }
}
