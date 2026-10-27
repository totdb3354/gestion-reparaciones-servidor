package com.reparaciones.servidor.nsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Una pasada de importación del espejo de NSYS (spec pieza 1 §5): el maestro si ha cambiado y, después, los ficheros
 *  Items pendientes, en orden. La primera vez solo importa el más reciente (esqueleto). */
@Service
public class ImportadorNsys {

    private static final Logger log = LoggerFactory.getLogger(ImportadorNsys.class);

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    public static final double MINIMO_FILAS = 0.9;
    /** Más filas repetidas (mismo IMEI/SN y lote) que esta parte del fichero: se ha leído mal y no se aplica. */
    public static final double MAXIMO_REPETIDAS = 0.01;
    /** Drive puede listar un fichero que el PC de producción aún está escribiendo: no se toca hasta que lleva esto quieto. */
    static final Duration ESPERA_DRIVE = Duration.ofMinutes(2);
    /** Un Items cuyo nombre lleva una fecha más allá de esto sobre la hora de Madrid se salta (reloj adelantado). */
    static final Duration MARGEN_FUTURO = Duration.ofDays(1);
    public static final String ACTUAL = "ACTUAL";
    public static final String HISTORICO = "HISTORICO";
    public static final String MAESTRO = "MAESTRO";
    public static final String OK = "OK";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ERROR = "ERROR";
    private static final Pattern NOMBRE = Pattern.compile("Items_(\\d{4})\\.(\\d{2})\\.(\\d{2})_(\\d{2})\\.(\\d{2})\\.xlsx");

    /** Un fichero Items de Drive con la fecha que lleva en el nombre (hora de Madrid). */
    public record FicheroItems(String id, String nombre, LocalDateTime fecha, Instant modificado) {}

    private final ClienteDrive drive;
    private final NsysDAO dao;
    private final TallerNsys taller;
    private final PorPasarNsys porPasar;
    private final String carpeta;
    private final String maestro;
    private final Clock clock;
    /** Ya no hace falta mirar en este arranque si al maestro guardado le faltan los nombres (0.9.22): se gasta cuando están o
     *  cuando la recarga por ellos termina (OK o rechazada); si la mirada o la recarga fallan, se reintenta en la siguiente pasada. */
    private boolean nombresMirados;

    @Autowired
    public ImportadorNsys(ClienteDrive drive, NsysDAO dao, TallerNsys taller, PorPasarNsys porPasar,
                          @Value("${nsys.drive.carpeta-items:}") String carpeta,
                          @Value("${nsys.drive.maestro:}") String maestro) {
        this(drive, dao, taller, porPasar, carpeta, maestro, Clock.system(MADRID));
    }

    ImportadorNsys(ClienteDrive drive, NsysDAO dao, TallerNsys taller, PorPasarNsys porPasar, String carpeta, String maestro,
                   Clock clock) {
        this.drive = drive;
        this.dao = dao;
        this.taller = taller;
        this.porPasar = porPasar;
        this.carpeta = carpeta == null ? "" : carpeta;
        this.maestro = maestro == null ? "" : maestro;
        this.clock = clock;
    }

    public static Optional<LocalDateTime> fechaDelNombre(String nombre) {
        Matcher m = NOMBRE.matcher(nombre == null ? "" : nombre);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))));
        } catch (DateTimeException fechaImposible) {
            return Optional.empty();
        }
    }

    /** Los Items de la carpeta con una fecha válida en el nombre, del más antiguo al más reciente. */
    public static List<FicheroItems> items(ClienteDrive drive, String carpeta) throws IOException, InterruptedException {
        List<FicheroItems> ficheros = new ArrayList<>();
        for (ClienteDrive.FicheroDrive f : drive.listarItems(carpeta))
            fechaDelNombre(f.nombre()).ifPresent(fecha -> ficheros.add(new FicheroItems(f.id(), f.nombre(), fecha, f.modificado())));
        ficheros.sort(Comparator.comparing(FicheroItems::fecha));
        return ficheros;
    }

    public void pasada() {
        if (carpeta.isBlank()) {
            log.warn("NSYS: falta el ajuste nsys.drive.carpeta-items; no se importa nada");
            return;
        }
        boolean maestroNuevo = actualizarMaestro();
        List<FicheroItems> pendientes;
        try {
            pendientes = pendientes();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.warn("NSYS: no se pudieron buscar los Items pendientes: {}", e.toString());
            return;
        }
        if (pendientes.isEmpty()) {
            if (maestroNuevo) retraducir();
            return;
        }
        for (FicheroItems f : pendientes) if (!procesar(f)) return;
    }

    private List<FicheroItems> pendientes() throws IOException, InterruptedException {
        // Un nombre con fecha de más de un día en el futuro es de un PC con el reloj adelantado: importarlo dejaría el
        // «último procesado» en el futuro, sin entrar ningún fichero más, mientras el aviso dice que todo va bien.
        LocalDateTime futuro = LocalDateTime.now(clock).plus(MARGEN_FUTURO);
        List<FicheroItems> todos = new ArrayList<>();
        for (FicheroItems f : items(drive, carpeta)) {
            if (f.fecha().isAfter(futuro)) log.warn("NSYS: {} se salta porque su fecha está en el futuro", f.nombre());
            else todos.add(f);
        }
        if (todos.isEmpty()) return List.of();
        Optional<Importacion> ultima = dao.ultimaImportacion(ACTUAL, Set.of(OK, RECHAZADO));
        // Candidatos: la primera vez, todos (y se toma el más reciente que ya esté asentado); después, los posteriores
        // al último procesado. La espera solo mira a los candidatos: un ya importado con hora reciente no bloquea nada.
        List<FicheroItems> candidatos = ultima.isEmpty() ? todos
                : todos.stream().filter(f -> f.fecha().isAfter(ultima.get().fechaExportacion())).toList();
        // Un fichero recién modificado puede estar a medio escribir: se corta ahí para no romper el orden del rastro.
        Instant ahora = clock.instant();
        Instant limite = ahora.minus(ESPERA_DRIVE);
        for (int i = 0; i < candidatos.size(); i++) {
            FicheroItems f = candidatos.get(i);
            if (f.modificado().isAfter(limite)) {
                if (f.modificado().isAfter(ahora))
                    log.warn("NSYS: {} se retiene porque su hora en Drive ({}) está en el futuro", f.nombre(), f.modificado());
                candidatos = candidatos.subList(0, i);
                break;
            }
        }
        if (candidatos.isEmpty()) return List.of();
        return ultima.isEmpty() ? List.of(candidatos.get(candidatos.size() - 1)) : candidatos;
    }

    /** Procesa un fichero. Devuelve false si hay que parar la pasada (error de Drive o de la base de datos): el mismo
     *  fichero se reintenta en la siguiente. */
    private boolean procesar(FicheroItems f) {
        int id = dao.empezarImportacion(f.nombre(), f.fecha(), ACTUAL, ahora());
        try {
            List<FilaItems> filas = LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id())));
            // Se compara con el fichero anterior, aceptado o frenado, para que el freno se recupere solo tras una bajada
            // real. Con 0 filas (rechazo de formato) no hay referencia: es seguro, porque un fichero incompleto nunca
            // borra móviles, solo deja sin tocar los que faltan.
            Optional<Importacion> previa = dao.ultimaImportacion(ACTUAL, Set.of(OK, RECHAZADO))
                    .filter(i -> i.filas() > 0);
            if (previa.isPresent() && filas.size() < previa.get().filas() * MINIMO_FILAS) {
                dao.terminarImportacion(id, RECHAZADO, filas.size(), 0, String.format(
                        "Trae %d filas frente a las %d del fichero anterior: se trata como incompleto",
                        filas.size(), previa.get().filas()), ahora());
                return true;
            }
            // Una exportación sana no repite claves: muchas repetidas es un fichero mal leído, que juntaría móviles.
            int repetidas = Comparador.repetidas(filas);
            if (repetidas > filas.size() * MAXIMO_REPETIDAS) {
                dao.terminarImportacion(id, RECHAZADO, filas.size(), 0, String.format(
                        "%d de las %d filas repiten IMEI/SN y lote: se trata como un fichero mal leído",
                        repetidas, filas.size()), ahora());
                return true;
            }
            Map<ClaveMovil, MovilNsys> anteriores = dao.cargarMoviles();
            Comparador.Modo modo = anteriores.isEmpty() ? Comparador.Modo.PRIMERA_CARGA : Comparador.Modo.NORMAL;
            ResultadoComparacion r = Comparador.comparar(anteriores, filas, f.fecha(), modo, traductor());
            dao.aplicar(r, id);
            guardarCostes(filas);
            String modelos = rellenarModelos();
            dao.terminarImportacion(id, OK, filas.size(), r.eventos().size(), resumen(r, repetidas, modelos), ahora());
            if (cerrarTaller()) {
                repasarHechos(f.fecha());
                repasarDescartes();
            }
            return true;
        } catch (FormatoItemsException e) {
            dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
            return false;
        } catch (Exception e) {
            log.warn("NSYS: error al importar {}", f.nombre(), e);
            dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
            return false;
        }
    }

    /** Recarga el maestro si en Drive hay uno más nuevo que el último cargado o rechazado. true si lo ha recargado. */
    private boolean actualizarMaestro() {
        if (maestro.isBlank()) return false;
        Integer id = null;
        try {
            ClienteDrive.FicheroDrive meta = drive.metadatos(maestro);
            LocalDateTime modificado = LocalDateTime.ofInstant(meta.modificado(), MADRID).truncatedTo(ChronoUnit.SECONDS);
            Optional<Importacion> ultimo = dao.ultimaImportacion(MAESTRO, Set.of(OK, RECHAZADO));
            boolean sinNombres = faltanNombres();
            if (ultimo.isPresent() && !modificado.isAfter(ultimo.get().fechaExportacion()) && !sinNombres) return false;
            id = dao.empezarImportacion(meta.nombre(), modificado, MAESTRO, ahora());
            ResultadoMaestro m = LectorMaestro.leer(new ByteArrayInputStream(drive.descargar(maestro)));
            if (m.productos().isEmpty()) { // vaciar Nsys_producto dejaría todos los móviles sin SKU ni modelo
                dao.terminarImportacion(id, RECHAZADO, 0, 0, "El maestro no trae productos", ahora());
                nombresMirados = true;
                return false;
            }
            dao.reemplazarProductos(m.productos());
            dao.terminarImportacion(id, OK, m.productos().size(), 0, resumen(m), ahora());
            nombresMirados = true;
            if (sinNombres) log.info("NSYS: maestro recargado para los nombres de producto: {} de {} con nombre",
                    m.productos().stream().filter(p -> p.nombre() != null).count(), m.productos().size());
            return true;
        } catch (FormatoItemsException e) {
            if (id != null) dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
            nombresMirados = true;
            return false;
        } catch (DataIntegrityViolationException e) {
            // La base de datos no admite este maestro (un dato que no cabe…): reintentarlo daría lo mismo cada pasada.
            log.warn("NSYS: la base de datos no admite el maestro: {}", motivo(e));
            if (id != null)
                dao.terminarImportacion(id, RECHAZADO, 0, 0, "La base de datos no admite el maestro: " + motivo(e), ahora());
            nombresMirados = true;
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (id != null) dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
            return false;
        } catch (Exception e) {
            log.warn("NSYS: no se pudo actualizar el maestro: {}", e.toString());
            if (id != null) dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
            return false;
        }
    }

    /** Al maestro guardado le faltan los nombres de producto (0.9.22: la columna es nueva): se recarga una vez aunque no haya
     *  cambiado en Drive. Una vez recargado (o si ya los tiene) no se vuelve a mirar en este arranque, para no descargarlo en cada
     *  pasada si el Excel no trae la columna; si la mirada falla, se vuelve a mirar en la siguiente pasada. */
    private boolean faltanNombres() {
        if (nombresMirados) return false;
        try {
            if (dao.productosSinNombre()) return true;
            nombresMirados = true;
            return false;
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudo mirar si al maestro le faltan los nombres: {}", e.toString());
            return false;
        }
    }

    /** Con un maestro nuevo y ningún Items pendiente, vuelve a traducir la foto guardada (SKU, modelo…) sin eventos. */
    private void retraducir() {
        Map<ClaveMovil, MovilNsys> anteriores = dao.cargarMoviles();
        if (anteriores.isEmpty()) return;
        List<FilaItems> filas = anteriores.values().stream().map(MovilNsys::comoFila).toList();
        dao.aplicar(Comparador.comparar(anteriores, filas, ahora(), Comparador.Modo.NORMAL, traductor()), null);
        rellenarModelos();
    }

    /** Rellena el modelo de los teléfonos del taller que no lo tienen. La foto ya está guardada: si esto falla (por
     *  ejemplo, intercalaciones distintas entre Nsys_movil y Telefono), la importación sigue OK y se avisa en el
     *  registro del servidor y en el mensaje. Devuelve la nota para el mensaje, o null si no hay nada que contar. */
    private String rellenarModelos() {
        try {
            int n = dao.rellenarModelosTelefono();
            return n > 0 ? "Modelo rellenado en " + n + " teléfonos del taller" : null;
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudo rellenar el modelo de los teléfonos del taller", e);
            return "Modelos del taller no rellenados: " + motivo(e);
        }
    }

    /** Tras guardar la foto, el coste de cada lote (spec 0.9.22 §2.3), por lotes de mil, cada uno en su transacción (la primera
     *  vez son decenas de miles y una sola transacción bloquearía los móviles que un guardado de reparación necesita). Si falla,
     *  la importación sigue OK y se avisa en el registro del servidor: los lotes ya escritos quedan y con el siguiente fichero se
     *  vuelven a mirar todos. */
    private void guardarCostes(List<FilaItems> filas) {
        try {
            List<Object[]> cambios = dao.costesCambiados(filas);
            for (List<Object[]> lote : NsysDAO.trozos(cambios)) dao.guardarCostes(lote);
            if (!cambios.isEmpty()) log.info("NSYS: coste guardado en {} móviles", cambios.size());
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron guardar los costes", e);
        }
    }

    /** Tras un Items OK, el cierre de lo del taller (spec 0.9.21 §2.2). La foto ya está guardada y la importación es OK: si el
     *  cierre falla, se avisa en el registro del servidor y se reintenta tras la siguiente. true si el cierre fue bien. */
    private boolean cerrarTaller() {
        try {
            taller.cerrar();
            return true;
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudo cerrar lo del taller tras la importación", e);
            return false;
        }
    }

    /** Tras el cierre, el repaso de las marcas «Hecho» (spec 0.9.22 §3.6) con la hora de exportación del fichero. Solo si el cierre
     *  fue bien: con los cambios sin cerrar haría volver marcas de lo que NSYS ya tiene; la importación siguiente lo repasa. La importación ya
     *  es OK: si el repaso falla, se avisa en el registro del servidor y se reintenta con el siguiente. */
    private void repasarHechos(LocalDateTime exportacion) {
        try {
            porPasar.repasarHechos(exportacion);
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron repasar las marcas «Hecho» tras la importación", e);
        }
    }

    /** Tras el repaso de las marcas, el de los descartes de «No está en NSYS» (spec 0.9.22 pieza 3 §4.4): fuera los de IMEI que
     *  NSYS ya conoce. Con la misma condición (cierre bueno); si falla, se avisa en el registro del servidor y se repite con el
     *  siguiente fichero. */
    private void repasarDescartes() {
        try {
            porPasar.repasarDescartes();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron repasar los descartes tras la importación", e);
        }
    }

    private Traductor traductor() {
        return new Traductor(dao.cargarProductos(), dao.cargarUbicaciones());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /** El motivo de un fallo en pocas palabras: el mensaje de la causa más concreta (sin la consulta SQL que Spring
     *  pone delante) o, si no lo tiene, su clase. */
    static String motivo(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        String texto = raiz.getMessage();
        return AnchosNsys.corta(texto == null || texto.isBlank() ? raiz.getClass().getSimpleName() : texto.strip(), 200);
    }

    static String resumen(ResultadoComparacion r, int repetidas, String modelos) {
        List<String> partes = new ArrayList<>();
        if (repetidas > 0) partes.add(repetidas + " filas repetidas descartadas");
        if (!r.cajasDesconocidas().isEmpty())
            partes.add("Cajas sin traducir: " + String.join(", ",
                    r.cajasDesconocidas().stream().map(c -> c.isEmpty() ? "(sin caja)" : c).toList()));
        if (r.desaparecidos() > 0) partes.add(r.desaparecidos() + " móviles anteriores no venían en el fichero");
        if (modelos != null) partes.add(modelos);
        return partes.isEmpty() ? null : String.join(". ", partes);
    }

    static String resumen(ResultadoMaestro m) {
        List<String> partes = new ArrayList<>();
        if (!m.conflictos().isEmpty())
            partes.add("Sin SKU por nombre repetido con SKU distintos: " + String.join(", ", m.conflictos()));
        if (!m.modelosFueraDeCatalogo().isEmpty())
            partes.add("Modelos fuera del catálogo del ERP: " + String.join(", ", m.modelosFueraDeCatalogo()));
        return partes.isEmpty() ? null : String.join(". ", partes);
    }
}
