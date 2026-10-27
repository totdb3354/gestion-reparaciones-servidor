package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3): las listas, «Hecho», «Revisado», el repaso de las marcas tras cada
 *  importación y los descartes de «No está en NSYS» (pieza 3). Las reglas están en TarjetasNsys; aquí se lee cada cosa una vez por petición. */
@Service
public class PorPasarNsys {

    private static final Logger log = LoggerFactory.getLogger(PorPasarNsys.class);
    static final String ACCION_REVISADO = "REVISAR_AVISO_TALLER";
    /** Como mucho los IMEI de un «Marcar todo» (como «Pegar varios IMEIs»). */
    static final int MAX_MARCAR = 500;
    /** El largo de Taller_hecho.IMEI_SN. */
    static final int MAX_IMEI = 40;
    static final String ACCION_DESCARTAR = "DESCARTAR_ALTA_NSYS";
    static final String ACCION_DESHACER_DESCARTE = "DESHACER_DESCARTE_NSYS";
    /** El largo de Taller_descarte.MOTIVO. */
    static final int MAX_MOTIVO = 500;

    private final TallerNsys taller;
    private final TallerDAO tallerDao;
    private final NsysDAO nsysDao;
    private final HechoDAO hechoDao;
    private final DescarteDAO descarteDao;
    private final LogDAO logDao;
    private final Clock clock;

    @Autowired
    public PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, DescarteDAO descarteDao,
                        LogDAO logDao) {
        this(taller, tallerDao, nsysDao, hechoDao, descarteDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, DescarteDAO descarteDao, LogDAO logDao,
                 Clock clock) {
        this.taller = taller;
        this.tallerDao = tallerDao;
        this.nsysDao = nsysDao;
        this.hechoDao = hechoDao;
        this.descarteDao = descarteDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    /** Las cuatro tarjetas (§3), los descartados de «No está en NSYS» (pieza 3) y las filas «Sin grado en NSYS» (pieza 4), con una
     *  lectura de cada cosa. */
    public TarjetasPorPasar tarjetas() {
        Salen salen = movilesQueSalen();
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        List<AltaTaller> altas = altas();
        Map<String, DescarteTaller> descartes = descartes(altas);
        return new TarjetasPorPasar(TarjetasNsys.color(salen.moviles(), marcas, salen.vueltas()),
                TarjetasNsys.grado(salen.moviles(), marcas, salen.vueltas()), TarjetasNsys.altas(altas, marcas, descartes),
                revisar(avisos), TarjetasNsys.descartadas(altas, descartes), sinGrado());
    }

    /** «Sin grado en NSYS» (pieza 4 §4.1): los trabajos desde el 22/10 12:49 (una consulta) y, solo si hay, la fila más reciente
     *  del espejo de sus IMEI, sus cambios vigentes, sus vueltas y sus baterías recordadas. */
    List<FilaSinGradoNsys> sinGrado() {
        List<NsysDAO.TrabajoFecha> trabajos = nsysDao.trabajosDesde(TarjetasNsys.DESDE_SIN_GRADO);
        if (trabajos.isEmpty()) return List.of();
        Map<String, List<NsysDAO.TrabajoFecha>> porImei = new LinkedHashMap<>();
        for (NsysDAO.TrabajoFecha t : trabajos) porImei.computeIfAbsent(NsysDAO.claveCruce(t.imei()), k -> new ArrayList<>()).add(t);
        Map<String, MovilNsys> espejo = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(trabajos.stream().map(NsysDAO.TrabajoFecha::imei).distinct().toList()))
            espejo.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        if (espejo.isEmpty()) return List.of();
        List<String> imeis = espejo.values().stream().map(MovilNsys::imeiSn).toList();
        Map<String, List<CambioTaller>> vigentes = TallerNsys.porImei(tallerDao.vigentes(imeis));
        Map<String, List<BateriaRecordada>> baterias = new HashMap<>();
        for (BateriaRecordada b : tallerDao.bateriasRecordadas(imeis))
            baterias.computeIfAbsent(NsysDAO.claveCruce(b.imeiSn()), k -> new ArrayList<>()).add(b);
        return TarjetasNsys.sinGrado(porImei, espejo, vigentes, vueltas(espejo.values()), baterias);
    }

    /** Los descartes por IMEI (pieza 3); sin filas en «No está en NSYS» no los lee. */
    private Map<String, DescarteTaller> descartes(List<AltaTaller> altas) {
        return altas.isEmpty() ? Map.of() : TarjetasNsys.descartesPorClave(descarteDao.todos());
    }

    /** Los móviles de «Cambio de color» y «Cambio de grado» con su vuelta (pieza 4 §6). */
    private record Salen(List<TallerNsys.MovilPorPasar> moviles, Map<String, VueltaNsys> vueltas) {}

    /** Los de «Por pasar a NSYS» que NSYS tiene en la empresa y los vendidos o de baja que han vuelto (pieza 4 §6). Un vendido o
     *  de baja que no ha vuelto no se vuelve a subir (sería stock que no hay) ni se le cambia el grado; su cambio sigue vigente y,
     *  si vuelve, sale otra vez solo. */
    private Salen movilesQueSalen() {
        List<TallerNsys.MovilPorPasar> todos = taller.movilesPorPasar();
        Map<String, VueltaNsys> vueltas = vueltas(todos.stream().map(TallerNsys.MovilPorPasar::movil).toList());
        return new Salen(todos.stream().filter(p -> vueltas.containsKey(NsysDAO.claveCruce(p.movil().imeiSn()))).toList(), vueltas);
    }

    /** La vuelta de cada móvil que sale, por clave de cruce (pieza 4 §6): los de la empresa sin leer nada; los vendidos o de baja,
     *  con sus trabajos (una consulta, solo si hay alguno). Un vendido o de baja que no ha vuelto no está. */
    Map<String, VueltaNsys> vueltas(Collection<MovilNsys> moviles) {
        Map<String, VueltaNsys> vueltas = new HashMap<>();
        List<MovilNsys> fuera = new ArrayList<>();
        for (MovilNsys m : moviles) {
            if (m.estado().enEmpresa()) vueltas.put(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            else fuera.add(m);
        }
        if (fuera.isEmpty()) return vueltas;
        Map<String, List<LocalDateTime>> asignaciones = VueltaNsys.asignacionesPorImei(
                nsysDao.trabajosDe(fuera.stream().map(MovilNsys::imeiSn).toList()));
        for (MovilNsys m : fuera) {
            String clave = NsysDAO.claveCruce(m.imeiSn());
            VueltaNsys.de(m, asignaciones.getOrDefault(clave, List.of())).ifPresent(v -> vueltas.put(clave, v));
        }
        return vueltas;
    }

    /** «Revisar a mano»: los avisos de móviles que salen (en la empresa o que han vuelto; uno que ya no está en el espejo, como
     *  antes), con su vuelta. */
    private List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos) {
        Map<String, MovilNsys> espejo = espejo(avisos);
        Map<String, VueltaNsys> vueltas = vueltas(espejo.values());
        List<AvisoTaller> salen = avisos.stream().filter(a -> {
            String clave = NsysDAO.claveCruce(a.imeiSn());
            return !espejo.containsKey(clave) || vueltas.containsKey(clave);
        }).toList();
        return TarjetasNsys.revisar(salen, espejo, vueltas);
    }

    /** «No está en NSYS» (§3.4): los IMEI del taller que no están en el espejo, cruzados en Java por claveCruce (las tablas tienen
     *  intercalaciones distintas). Sus trabajos solo se leen si hay alguno. */
    List<AltaTaller> altas() {
        Set<String> espejo = nsysDao.clavesDelEspejo();
        List<String> fuera = nsysDao.imeisDelTaller().stream().filter(i -> !espejo.contains(NsysDAO.claveCruce(i))).toList();
        return fuera.isEmpty() ? List.of() : nsysDao.altas(fuera);
    }

    /** La fila más reciente del espejo de los IMEI de estos avisos (para su modelo); sin avisos no pregunta. */
    private Map<String, MovilNsys> espejo(List<AvisoTaller> avisos) {
        if (avisos.isEmpty()) return Map.of();
        Map<String, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(avisos.stream().map(AvisoTaller::imeiSn).distinct().toList()))
            mapa.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        return mapa;
    }

    /** «Hecho» y «Marcar todo» (§3.6): {@code tarjeta} COLOR, GRADO o ALTA; de 1 a {@value #MAX_MARCAR} IMEI sin vacíos (se
     *  limpian los espacios y los repetidos). No va al registro de actividad. */
    public void marcar(String tarjeta, List<String> imeis, int idUsu) {
        TarjetaHecho t = tarjeta(tarjeta);
        if (imeis == null || imeis.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay móviles que marcar");
        if (imeis.size() > MAX_MARCAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Como mucho " + MAX_MARCAR + " móviles por vez");
        if (imeis.stream().anyMatch(i -> i == null || i.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista lleva IMEIs vacíos");
        List<String> limpios = imeis.stream().map(String::strip).distinct().toList();
        if (limpios.stream().anyMatch(i -> i.length() > MAX_IMEI))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un IMEI no puede pasar de " + MAX_IMEI + " caracteres");
        hechoDao.marcar(t, limpios, idUsu, ahora());
    }

    /** El IMEI de una petición, sin espacios a los lados: 400 si falta o pasa de {@value #MAX_IMEI} caracteres. */
    private static String imei(String imeiSn) {
        if (imeiSn == null || imeiSn.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el IMEI");
        String limpio = imeiSn.strip();
        if (limpio.length() > MAX_IMEI)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un IMEI no puede pasar de " + MAX_IMEI + " caracteres");
        return limpio;
    }

    /** «deshacer» de una marca. */
    public void desmarcar(String tarjeta, String imeiSn) {
        TarjetaHecho t = tarjeta(tarjeta);
        hechoDao.desmarcar(t, imei(imeiSn));
    }

    /** «descartar» de «No está en NSYS» (pieza 3 §4.3): guarda el descarte (o sustituye el que hubiera), quita la marca «Hecho» de
     *  esa fila y lo apunta en el registro de actividad con el motivo. READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void descartar(String imeiSn, String motivo, int idUsu) {
        String imei = imei(imeiSn);
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el motivo");
        if (limpio.length() > MAX_MOTIVO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
        descarteDao.descartar(imei, idUsu, limpio, ahora());
        hechoDao.desmarcar(TarjetaHecho.ALTA, imei);
        logDao.insertar(idUsu, ACCION_DESCARTAR, "IMEI: " + imei, limpio);
    }

    /** «deshacer» de «Ver descartados»: quita el descarte y, si había uno, lo apunta. READ COMMITTED, como «Revisado». */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deshacerDescarte(String imeiSn, int idUsu) {
        String imei = imei(imeiSn);
        if (descarteDao.deshacer(imei) > 0) logDao.insertar(idUsu, ACCION_DESHACER_DESCARTE, "IMEI: " + imei, null);
    }

    /** Tras cada importación ACTUAL que acaba en OK (pieza 3 §4.4): fuera los descartes de los IMEI que NSYS ya conoce. Los
     *  superados por un trabajo nuevo se quedan (dan el aviso de la fila). Sin descartes no lee el espejo. */
    public void repasarDescartes() {
        List<DescarteTaller> descartes = descarteDao.todos();
        if (descartes.isEmpty()) return;
        Set<String> espejo = nsysDao.clavesDelEspejo();
        List<String> enNsys = descartes.stream().map(DescarteTaller::imeiSn).filter(i -> espejo.contains(NsysDAO.claveCruce(i))).toList();
        if (enNsys.isEmpty()) return;
        descarteDao.quitar(enNsys);
        log.info("NSYS: descartes de «No está en NSYS» quitados: {} (ya en NSYS)", enNsys.size());
    }

    /** «Revisado» (§3.5): quién y cuándo en el aviso y una línea en el registro de actividad. 404 si el aviso no existe; 400 si no
     *  es de «Revisar a mano»; si ya estaba revisado no hace nada (un segundo clic). READ COMMITTED: con REPEATABLE READ, MariaDB
     *  11.6+ (innodb_snapshot_isolation) rechaza con el error 1020 el UPDATE del segundo clic, que leyó el aviso antes de que el
     *  primero lo revisara; así espera al primero y su UPDATE no cambia nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revisado(int idAviso, int idUsu) {
        AvisoTaller a = tallerDao.avisoPorId(idAviso).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el aviso " + idAviso));
        if (a.revisadoEn() != null) return;
        if (tallerDao.revisar(idAviso, idUsu, ahora()) == 0) {
            if (!AvisoTaller.POR_REVISAR.contains(a.tipo()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese aviso no es de «Revisar a mano»");
            return; // lo acaba de revisar otro entre medias
        }
        logDao.insertar(idUsu, ACCION_REVISADO, "IMEI: " + a.imeiSn() + ", " + a.tipo().name() + " de " + a.idRep(), null);
    }

    /** Tras cada importación ACTUAL que acaba en OK, con la hora de exportación del fichero (§3.6): borra las marcas que ya no
     *  hacen falta y hace «volver» las que NSYS no ha seguido. Sin marcas no lee nada más. */
    public void repasarHechos(LocalDateTime exportacion) {
        List<HechoTaller> marcas = hechoDao.todas();
        if (marcas.isEmpty()) return;
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(marcas, TarjetasNsys.filas(movilesQueSalen().moviles(), altas()), exportacion);
        if (r.borrar().isEmpty() && r.vuelven().isEmpty()) return;
        hechoDao.repasar(r.borrar(), r.vuelven(), ahora());
        log.info("NSYS: marcas «Hecho» tras la importación: {} quitadas, {} vuelven a salir", r.borrar().size(), r.vuelven().size());
    }

    private static TarjetaHecho tarjeta(String texto) {
        try {
            return TarjetaHecho.valueOf(texto == null ? "" : texto.strip());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarjeta es COLOR, GRADO o ALTA");
        }
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Un fichero para descargar: su nombre, lo que lleva y cuántos móviles. */
    public record Plantilla(String nombre, byte[] contenido, int filas) {}

    /** La plantilla de color (spec §4): los móviles de «Cambio de color» con producto y sin marca «Hecho» que valga, en el orden de
     *  la lista, con el coste de su lote actual (una consulta, solo si hay alguno). Sin los que han vuelto (pieza 4 §6): antes, la devolución en
     *  NSYS. */
    public Plantilla plantillaColor() {
        Salen salen = movilesQueSalen();
        List<TallerNsys.MovilPorPasar> moviles = salen.moviles();
        List<FilaColorNsys> van = TarjetasNsys.color(moviles, TarjetasNsys.porClave(hechoDao.todas()), salen.vueltas()).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()) && f.ponerProducto() != null
                        && f.vueltoDesde() == null).toList();
        Map<String, MovilNsys> porImei = new HashMap<>();
        for (TallerNsys.MovilPorPasar p : moviles) porImei.put(NsysDAO.claveCruce(p.movil().imeiSn()), p.movil());
        List<MovilNsys> lotes = van.stream().map(f -> porImei.get(NsysDAO.claveCruce(f.imeiSn()))).toList();
        Map<ClaveMovil, BigDecimal> costes = lotes.isEmpty() ? Map.of() : nsysDao.costes(lotes.stream().map(MovilNsys::clave).toList());
        List<PlantillaNsys.FilaColor> filas = new ArrayList<>();
        for (int i = 0; i < van.size(); i++) {
            MovilNsys m = lotes.get(i);
            filas.add(PlantillaNsys.FilaColor.de(m, van.get(i).ponerProducto(), van.get(i).compruebalo(), costes.get(m.clave())));
        }
        return new Plantilla("NSYS_color_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.color(filas), filas.size());
    }

    /** La plantilla de altas (spec §4): los IMEI de «No está en NSYS» sin marca «Hecho» que valga ni descarte, con el modelo del
     *  taller. */
    public Plantilla plantillaAltas() {
        List<AltaTaller> altas = altas();
        List<PlantillaNsys.FilaAlta> filas = TarjetasNsys.altas(altas, TarjetasNsys.porClave(hechoDao.todas()), descartes(altas))
                .stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()))
                .map(f -> new PlantillaNsys.FilaAlta(f.imeiSn(), PlantillaNsys.nombreModelo(f.modelo()))).toList();
        return new Plantilla("NSYS_altas_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.altas(filas), filas.size());
    }
}
