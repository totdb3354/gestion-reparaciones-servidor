package com.reparaciones.servidor.nsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.ImportadorNsys.*;

/**
 * Carga del histórico (spec pieza 1 §6):
 * - recorre en orden las exportaciones antiguas de Drive, hasta la primera importación normal incluida, y compara cada
 *   una con la anterior;
 * - solo añade eventos: nunca toca la foto ni repite la entrada y la venta del esqueleto;
 * - de noche y un fichero por paso;
 * - se reanuda donde lo dejó y, al terminar, no vuelve a mirar Drive hasta el siguiente arranque.
 */
@Service
public class HistoricoNsys {

    private static final Logger log = LoggerFactory.getLogger(HistoricoNsys.class);
    static final LocalTime EMPIEZA = LocalTime.of(23, 0);
    static final LocalTime ACABA = LocalTime.of(7, 0);

    private final ClienteDrive drive;
    private final NsysDAO dao;
    private final String carpeta;
    private final Clock clock;
    private Map<ClaveMovil, MovilNsys> fotoPrevia;
    private String ficheroPrevio;
    private int filasPrevias;
    private boolean terminado;

    @Autowired
    public HistoricoNsys(ClienteDrive drive, NsysDAO dao, @Value("${nsys.drive.carpeta-items:}") String carpeta) {
        this(drive, dao, carpeta, Clock.system(MADRID));
    }

    HistoricoNsys(ClienteDrive drive, NsysDAO dao, String carpeta, Clock clock) {
        this.drive = drive;
        this.dao = dao;
        this.carpeta = carpeta == null ? "" : carpeta;
        this.clock = clock;
    }

    static boolean deNoche(LocalTime hora) {
        return !hora.isBefore(EMPIEZA) || hora.isBefore(ACABA);
    }

    /** Procesa como mucho un fichero antiguo. */
    public synchronized void paso() {
        if (terminado || carpeta.isBlank() || !deNoche(LocalTime.now(clock))) return;
        try {
            Optional<Importacion> primera = dao.primeraImportacion(ACTUAL, Set.of(OK));
            if (primera.isEmpty()) return;                   // hasta que exista la foto actual
            LocalDateTime limite = primera.get().fechaExportacion();
            List<FicheroItems> candidatos = items(drive, carpeta).stream().filter(f -> !f.fecha().isAfter(limite)).toList();
            Set<String> hechos = dao.ficherosProcesados(HISTORICO);
            Optional<FicheroItems> siguiente = candidatos.stream().filter(f -> !hechos.contains(f.nombre())).findFirst();
            if (siguiente.isEmpty()) {
                terminado = true;
                fotoPrevia = null;                           // ya no hace falta: se libera la foto de memoria
                ficheroPrevio = null;
                log.info("NSYS: histórico cargado");
                return;
            }
            prepararFotoPrevia(candidatos);
            procesar(siguiente.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("NSYS: histórico: {}", e.toString(), e);
        }
    }

    /** La foto del último fichero histórico hecho: la que hay en memoria o, tras un reinicio, la que se vuelve a leer. */
    private void prepararFotoPrevia(List<FicheroItems> candidatos) throws Exception {
        Optional<Importacion> ultimo = dao.ultimaImportacion(HISTORICO, Set.of(OK));
        if (ultimo.isEmpty()) {
            fotoPrevia = null;
            return;
        }
        String nombre = ultimo.get().fichero();
        if (fotoPrevia != null && Objects.equals(ficheroPrevio, nombre)) return;
        FicheroItems f = candidatos.stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElse(null);
        if (f == null) {
            fotoPrevia = null;                               // ya no está en Drive: este fichero hace de punto de partida
            return;
        }
        List<FilaItems> filas = LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id())));
        guardarFoto(f, filas, traductor());
        filasPrevias = filas.size();
        // el freno compara con el fichero anterior, sea OK o RECHAZADO (si no trae filas, sin freno)
        filasPrevias = dao.ultimaImportacion(HISTORICO, Set.of(OK, RECHAZADO)).map(Importacion::filas)
                .filter(n -> n > 0).orElse(filasPrevias);
    }

    private void procesar(FicheroItems f) {
        int id = dao.empezarImportacion(f.nombre(), f.fecha(), HISTORICO, ahora());
        try {
            List<FilaItems> filas = LectorItems.leer(new ByteArrayInputStream(drive.descargar(f.id())));
            Traductor t = traductor();
            if (fotoPrevia == null) {
                dao.terminarImportacion(id, OK, filas.size(), 0, "Punto de partida del histórico", ahora());
                guardarFoto(f, filas, t);
                filasPrevias = filas.size();
                return;
            }
            if (filasPrevias > 0 && filas.size() < filasPrevias * MINIMO_FILAS) {
                dao.terminarImportacion(id, RECHAZADO, filas.size(), 0, String.format(
                        "Trae %d filas frente a las %d del fichero anterior: se trata como incompleto",
                        filas.size(), filasPrevias), ahora());
                if (!filas.isEmpty()) filasPrevias = filas.size();   // la foto no cambia, pero la referencia del freno sí
                return;
            }
            ResultadoComparacion r = Comparador.comparar(fotoPrevia, filas, f.fecha(), Comparador.Modo.HISTORICO, t);
            dao.cerrarHistorico(r.eventos(), id, filas.size(), ahora());   // eventos y OK en una transacción
            guardarFoto(f, filas, t);
            filasPrevias = filas.size();
        } catch (FormatoItemsException e) {
            dao.terminarImportacion(id, RECHAZADO, 0, 0, e.getMessage(), ahora());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarImportacion(id, ERROR, 0, 0, "Interrumpida", ahora());
        } catch (Exception e) {
            log.warn("NSYS: histórico: error con {}", f.nombre(), e);
            dao.terminarImportacion(id, ERROR, 0, 0, e.toString(), ahora());
        }
    }

    private void guardarFoto(FicheroItems f, List<FilaItems> filas, Traductor t) {
        fotoPrevia = Comparador.foto(filas, t, f.fecha());
        ficheroPrevio = f.nombre();
    }

    private Traductor traductor() {
        return new Traductor(dao.cargarProductos(), dao.cargarUbicaciones());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
