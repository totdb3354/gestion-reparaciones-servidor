package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3): las listas, «Hecho», «Revisado» y el repaso de las marcas tras cada
 *  importación. Las reglas están en TarjetasNsys; aquí se lee cada cosa una vez por petición. */
@Service
public class PorPasarNsys {

    private static final Logger log = LoggerFactory.getLogger(PorPasarNsys.class);
    static final String ACCION_REVISADO = "REVISAR_AVISO_TALLER";
    /** Como mucho los IMEI de un «Marcar todo» (como «Pegar varios IMEIs»). */
    static final int MAX_MARCAR = 500;
    /** El largo de Taller_hecho.IMEI_SN. */
    static final int MAX_IMEI = 40;

    private final TallerNsys taller;
    private final TallerDAO tallerDao;
    private final NsysDAO nsysDao;
    private final HechoDAO hechoDao;
    private final LogDAO logDao;
    private final Clock clock;

    @Autowired
    public PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, LogDAO logDao) {
        this(taller, tallerDao, nsysDao, hechoDao, logDao, Clock.system(ImportadorNsys.MADRID));
    }

    PorPasarNsys(TallerNsys taller, TallerDAO tallerDao, NsysDAO nsysDao, HechoDAO hechoDao, LogDAO logDao, Clock clock) {
        this.taller = taller;
        this.tallerDao = tallerDao;
        this.nsysDao = nsysDao;
        this.hechoDao = hechoDao;
        this.logDao = logDao;
        this.clock = clock;
    }

    /** Las cuatro tarjetas (§3), con una lectura de cada cosa. */
    public TarjetasPorPasar tarjetas() {
        List<TallerNsys.MovilPorPasar> moviles = movilesEnEmpresa();
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        return new TarjetasPorPasar(TarjetasNsys.color(moviles, marcas), TarjetasNsys.grado(moviles, marcas),
                TarjetasNsys.altas(altas(), marcas), TarjetasNsys.revisar(avisos, espejo(avisos)));
    }

    /** Los móviles de «Cambio de color» y «Cambio de grado»: los de «Por pasar a NSYS» que NSYS tiene en la empresa. Un vendido o de
     *  baja no se vuelve a subir (sería stock que no hay) ni se le cambia el grado; su cambio sigue vigente y, si el móvil vuelve,
     *  sale otra vez solo. */
    private List<TallerNsys.MovilPorPasar> movilesEnEmpresa() {
        return taller.movilesPorPasar().stream().filter(p -> p.movil().estado().enEmpresa()).toList();
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

    /** «deshacer» de una marca. */
    public void desmarcar(String tarjeta, String imeiSn) {
        TarjetaHecho t = tarjeta(tarjeta);
        if (imeiSn == null || imeiSn.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el IMEI");
        if (imeiSn.strip().length() > MAX_IMEI)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un IMEI no puede pasar de " + MAX_IMEI + " caracteres");
        hechoDao.desmarcar(t, imeiSn.strip());
    }

    /** «Revisado» (§3.5): quién y cuándo en el aviso y una línea en el registro de actividad. 404 si el aviso no existe; 400 si no
     *  es de «Revisar a mano»; si ya estaba revisado no hace nada (un segundo clic). */
    @Transactional
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
        TarjetasNsys.Repaso r = TarjetasNsys.repasar(marcas, TarjetasNsys.filas(movilesEnEmpresa(), altas()), exportacion);
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
     *  la lista, con el coste de su lote actual (una consulta, solo si hay alguno). */
    public Plantilla plantillaColor() {
        List<TallerNsys.MovilPorPasar> moviles = movilesEnEmpresa();
        List<FilaColorNsys> van = TarjetasNsys.color(moviles, TarjetasNsys.porClave(hechoDao.todas())).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()) && f.ponerProducto() != null).toList();
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

    /** La plantilla de altas (spec §4): los IMEI de «No está en NSYS» sin marca «Hecho» que valga, con el modelo del taller. */
    public Plantilla plantillaAltas() {
        List<PlantillaNsys.FilaAlta> filas = TarjetasNsys.altas(altas(), TarjetasNsys.porClave(hechoDao.todas())).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()))
                .map(f -> new PlantillaNsys.FilaAlta(f.imeiSn(), PlantillaNsys.nombreModelo(f.modelo()))).toList();
        return new Plantilla("NSYS_altas_" + LocalDate.now(clock) + ".xlsx", PlantillaNsys.altas(filas), filas.size());
    }
}
