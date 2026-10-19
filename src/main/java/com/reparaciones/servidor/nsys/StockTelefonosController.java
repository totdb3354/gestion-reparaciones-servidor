package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        // El último cambio de grado de verdad del lote: un cambio de batería (A → A100) no cuenta, o taparía el grado
        // anterior real (C → A → A100 da C).
        String gradoAnterior = dao.eventos(imeiSn).stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && Objects.equals(e.batch(), m.batch()))
                .filter(e -> !Normalizacion.gradoSinBateria(e.de()).equals(Normalizacion.gradoSinBateria(e.a())))
                .reduce((a, b) -> b).map(e -> e.de() == null ? null : Normalizacion.gradoSinBateria(e.de())).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), gradoAnterior, m.sku(),
                ubicacionVisible(m), m.estado().name(), enEmpresa ? dias(m, LocalDate.now(clock)) : null, lotes.size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null);
    }

    @GetMapping("/{imeiSn}/rastro")
    public List<EventoRastro> rastro(@PathVariable String imeiSn) {
        return Rastro.unir(dao.eventos(imeiSn), dao.trabajos(imeiSn));
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
                              @Schema(nullable = true) LocalDate fechaVenta) {}
}
