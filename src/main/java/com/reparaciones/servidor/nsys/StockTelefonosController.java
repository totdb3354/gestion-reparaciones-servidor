package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.ImportadorNsys.*;

/** Stock de teléfonos y rastro (spec pieza 1 §8): solo lectura, del espejo de NSYS. SUPERTECNICO y ADMIN; no hay
 *  jerarquía de roles, así que van los dos nombrados. */
@RestController
@RequestMapping("/api/stock-telefonos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class StockTelefonosController {

    static final String MOTIVO_ERROR = "Error al leer de Drive o al guardar; ver el registro del servidor";

    /** Como mucho los IMEIs de una vez en «Pegar varios IMEIs» (spec 0.9.18 §4.9). */
    static final int MAX_COMPROBAR = 500;

    /** Tope de filas de la lista de importaciones (spec 0.9.18 §6.5); por defecto, 20. */
    static final int LIMITE_IMPORTACIONES = 50;
    private static final String EN_CURSO = "EN_CURSO";

    private final NsysDAO dao;
    private final Clock clock;

    @Autowired
    public StockTelefonosController(NsysDAO dao) {
        this(dao, Clock.system(MADRID));
    }

    StockTelefonosController(NsysDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Los móviles que están en la empresa (en stock, de pedido o que no cuentan), cada uno con sus trabajos abiertos del
     *  taller (0.9.18). La web filtra en local y calcula «Quién lo tiene». */
    @GetMapping
    public List<MovilStock> stock() {
        LocalDate hoy = LocalDate.now(clock);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos();
        return dao.enEmpresa().stream().map(m -> new MovilStock(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(),
                m.capacidad(), m.color(), Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()),
                m.esEsim(), m.sku(), ubicacionVisible(m), m.estado().name(), dias(m, hoy),
                abiertos.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()))).toList();
    }

    @GetMapping("/estado")
    public EstadoImportacion estado() {
        Optional<Importacion> ok = dao.ultimaImportacion(ACTUAL, Set.of(OK));
        Optional<Importacion> fallo = dao.ultimaImportacion(ACTUAL, Set.of(RECHAZADO, ERROR));
        LocalDateTime ultima = ok.map(Importacion::fechaExportacion).orElse(null);
        boolean falloReciente = fallo.isPresent() && (ultima == null || fallo.get().fechaExportacion().isAfter(ultima));
        return new EstadoImportacion(ultima, AvisoNsys.nivel(ultima, LocalDateTime.now(clock)),
                falloReciente ? fallo.get().fechaExportacion() : null, falloReciente ? motivo(fallo.get()) : null);
    }

    /** Los rechazos los explica un texto nuestro; un ERROR guarda e.toString(), que puede llevar ids de ficheros de
     *  Drive o el correo de la cuenta de servicio, y no sale de la API. */
    private static String motivo(Importacion fallo) {
        return ERROR.equals(fallo.resultado()) ? MOTIVO_ERROR : fallo.mensaje();
    }

    /** Hasta 10 móviles de cualquier estado cuyo IMEI/SN contiene el texto (mínimo 4 caracteres). */
    @GetMapping("/buscar")
    public List<MovilBuscado> buscar(@RequestParam String imei) {
        if (imei == null || imei.strip().length() < 4) return List.of();
        return dao.buscar(imei.strip(), 10).stream().map(m -> new MovilBuscado(m.imeiSn(), m.modelo(), m.modeloMaestro(),
                m.capacidad(), m.color(), m.estado().name(), ubicacionVisible(m),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null)).toList();
    }

    @GetMapping("/{imeiSn}")
    public FichaMovil ficha(@PathVariable String imeiSn) {
        List<MovilNsys> lotes = dao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        MovilNsys m = lotes.get(0);
        List<EventoNsys> eventos = dao.eventos(imeiSn);
        // El último cambio de grado de verdad del lote: un cambio de batería (A → A100) no cuenta, o taparía el grado
        // anterior real (C → A → A100 da C).
        String gradoAnterior = eventos.stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && Objects.equals(e.batch(), m.batch()))
                .filter(e -> !Normalizacion.gradoSinBateria(e.de()).equals(Normalizacion.gradoSinBateria(e.a())))
                .reduce((a, b) -> b).map(e -> e.de() == null ? null : Normalizacion.gradoSinBateria(e.de())).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        // 0.9.18 (spec §5.2): la última revisión del lote y, si está en «En revisión», desde cuándo.
        Revision.Ultima revisado = Revision.ultima(eventos, m.batch());
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), gradoAnterior, m.sku(),
                ubicacionVisible(m), m.estado().name(), enEmpresa ? dias(m, LocalDate.now(clock)) : null, lotes.size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                revisado == null ? null : revisado.fecha(), revisado != null && revisado.devolucion(),
                Revision.pendienteDesde(m, eventos));
    }

    @GetMapping("/{imeiSn}/rastro")
    public List<EventoRastro> rastro(@PathVariable String imeiSn) {
        return Rastro.unir(dao.eventos(imeiSn), dao.trabajos(imeiSn));
    }

    /** «Pegar varios IMEIs» (spec 0.9.18 §4.9 y §6.2): una entrada por IMEI pedido, en el mismo orden. Busca en todo el
     *  espejo, también los vendidos; de cada IMEI, el lote más reciente, con sus trabajos abiertos. La web manda los IMEIs
     *  limpios y sin repetir; más de {@value #MAX_COMPROBAR}, o alguno nulo o en blanco, es un 400. */
    @PostMapping("/comprobar")
    public List<MovilComprobado> comprobar(@RequestBody PeticionComprobar peticion) {
        List<String> imeis = peticion == null || peticion.imeis() == null ? List.of() : peticion.imeis();
        if (imeis.size() > MAX_COMPROBAR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Como mucho " + MAX_COMPROBAR + " IMEIs por vez");
        if (imeis.stream().anyMatch(i -> i == null || i.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista lleva IMEIs vacíos");
        if (imeis.isEmpty()) return List.of();
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : dao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos(imeis);
        return imeis.stream().map(imei -> {
            MovilNsys m = masReciente.get(NsysDAO.claveCruce(imei));
            if (m == null) return new MovilComprobado(imei, false, null, null, null, null, null, false, null, null, null, List.of());
            return new MovilComprobado(imei, true, m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                    Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), ubicacionVisible(m),
                    m.estado().name(), m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                    abiertos.getOrDefault(NsysDAO.claveCruce(imei), List.of()));
        }).toList();
    }

    /** Las últimas importaciones del Items actual, de la más reciente a la más antigua, y el progreso del histórico (spec
     *  0.9.18 §4.11 y §6.5): para ver desde la web que llega un Items cada media hora. Los huecos los calcula la web. */
    @GetMapping("/importaciones")
    public ListaImportaciones importaciones(@RequestParam(defaultValue = "20") int limite) {
        int n = Math.max(1, Math.min(LIMITE_IMPORTACIONES, limite));
        List<FilaImportacion> filas = dao.importaciones(ACTUAL, n).stream().map(StockTelefonosController::fila).toList();
        ResumenHistorico h = dao.resumenHistorico();
        LocalDateTime hasta = dao.primeraImportacion(ACTUAL, Set.of(OK)).map(Importacion::fechaExportacion).orElse(null);
        return new ListaImportaciones(filas, new ProgresoHistorico(h.total(), h.hechos(), h.ultimo(), hasta));
    }

    /** El motivo: el texto nuestro de OK y RECHAZADO; el genérico de un ERROR, cuyo MENSAJE es e.toString() y puede llevar
     *  ids de Drive o el correo de la cuenta de servicio (como en /estado); nada mientras está en curso. */
    private static FilaImportacion fila(RegistroImportacion r) {
        boolean enCurso = EN_CURSO.equals(r.resultado());
        Long segundos = enCurso || r.empezada() == null || r.terminada() == null ? null
                : Duration.between(r.empezada(), r.terminada()).toSeconds();
        String motivo = enCurso ? null : ERROR.equals(r.resultado()) ? MOTIVO_ERROR : r.mensaje();
        return new FilaImportacion(r.fichero(), r.fechaExportacion(), r.resultado(), r.filas(), r.cambios(), segundos, motivo);
    }

    /** Un móvil de pedido sigue «en su caja»: la última ubicación conocida antes de «Pedidos realizados». */
    private static String ubicacionVisible(MovilNsys m) {
        return m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
    }

    private static Integer dias(MovilNsys m, LocalDate hoy) {
        LocalDate desde = m.receivingDate() != null ? m.receivingDate()
                : m.vistoPrimero() != null ? m.vistoPrimero().toLocalDate() : null;
        return desde == null ? null : (int) ChronoUnit.DAYS.between(desde, hoy);
    }

    private record MovilStock(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva, boolean esEsim,
                              @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, List<TrabajoAbierto> trabajos) {}

    private record EstadoImportacion(@Schema(nullable = true) LocalDateTime ultimaExportacion, String nivel,
                                     @Schema(nullable = true) LocalDateTime ultimoFallo,
                                     @Schema(nullable = true) String motivoFallo) {}

    private record MovilBuscado(String imeiSn, @Schema(nullable = true) String modelo,
                                @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                @Schema(nullable = true) String color, String estado,
                                @Schema(nullable = true) String ubicacion, @Schema(nullable = true) LocalDate fechaVenta) {}

    private record FichaMovil(String imeiSn, String lote, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              @Schema(nullable = true) String color, String grado, boolean bateriaNueva,
                              @Schema(nullable = true) String gradoAnterior, @Schema(nullable = true) String sku,
                              @Schema(nullable = true) String ubicacion, String estado,
                              @Schema(nullable = true) Integer dias, int entradas,
                              @Schema(nullable = true) LocalDate fechaVenta,
                              @Schema(nullable = true) LocalDateTime revisadoEl, boolean revisadoDevolucion,
                              @Schema(nullable = true) LocalDate pendienteRevisionDesde) {}

    /** Del paquete (no privado) para que los tests lo construyan; el esquema se llama StockTelefonosPeticionComprobar. */
    record PeticionComprobar(List<String> imeis) {}

    private record ListaImportaciones(List<FilaImportacion> importaciones, ProgresoHistorico historico) {}

    private record FilaImportacion(String fichero, LocalDateTime fechaExportacion, String resultado, int filas, int cambios,
                                   @Schema(nullable = true) Long segundos, @Schema(nullable = true) String motivo) {}

    /** {@code total} = 0: no hay ninguna importación HISTORICO y la web no pinta la línea. {@code hasta}: el Items de la
     *  primera importación ACTUAL OK, donde termina el histórico. */
    private record ProgresoHistorico(int total, int hechos, @Schema(nullable = true) LocalDateTime ultimo,
                                     @Schema(nullable = true) LocalDateTime hasta) {}

    private record MovilComprobado(String imei, boolean encontrado, @Schema(nullable = true) String modelo,
                                   @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                   @Schema(nullable = true) String color, @Schema(nullable = true) String grado,
                                   boolean bateriaNueva, @Schema(nullable = true) String ubicacion,
                                   @Schema(nullable = true) String estado, @Schema(nullable = true) LocalDate fechaVenta,
                                   List<TrabajoAbierto> trabajos) {}
}
