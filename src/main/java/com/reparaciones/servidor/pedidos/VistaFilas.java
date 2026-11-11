package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AvisoNsys;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.Importacion;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Lo que enseña «Pedidos» como la hoja (spec «Pedidos como la hoja» §3.2 y §7): las filas con el aviso de su IMEI, calculado al
 *  pedirlas con lo ultimo del espejo de NSYS y del taller; los recomendados de una fila y las fuentes de la pildora. */
@Service
public class VistaFilas {

    /** Tope de resultados del buscador. */
    static final int TOPE_BUSQUEDA = 500;
    static final String MOTIVO_ERROR = "Error al leer la fuente; ver el registro del servidor";
    static final String MOTIVO_RECHAZADA = "La lectura de pedidos no está configurada";
    static final String MOTIVO_NSYS_RECHAZADO = "La última exportación de NSYS no se pudo aceptar";
    static final String MOTIVO_NSYS_ERROR = "No se pudo leer la última exportación de NSYS";
    static final String SHIPPYPRO = "SHIPPYPRO";
    static final String NSYS = "NSYS";
    static final String HISTORICO = "HISTORICO";
    static final String PENDIENTE = "PENDIENTE";
    static final String ENVIADA = "ENVIADA";
    static final String NO_PENDIENTE = "NO_PENDIENTE";
    /** Cuantos dias hacia atras (por FECHA) miran las filas de ShippyPro ya cerradas para saber que IMEI estan ya puestos. */
    static final int DIAS_PUESTOS = 60;

    private final FilasDAO dao;
    private final NsysDAO nsysDao;
    private final TallerNsys taller;
    private final Clock clock;

    @Autowired
    public VistaFilas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller) {
        this(dao, nsysDao, taller, Clock.system(ImportadorNsys.MADRID));
    }

    VistaFilas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller, Clock clock) {
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.taller = taller;
        this.clock = clock;
    }

    /** Sin {@code buscar}: las del periodo (por defecto, el ultimo mes hasta hoy) y las vivas. Con {@code buscar}: las 500 mas
     *  recientes de todo el historico que lo contienen. */
    public ListaFilas filas(LocalDate desde, LocalDate hasta, String buscar) {
        String texto = buscar == null ? "" : buscar.strip();
        if (texto.isEmpty()) {
            LocalDate fin = hasta != null ? hasta : LocalDate.now(clock);
            LocalDate inicio = desde != null ? desde : fin.minusMonths(1);
            return new ListaFilas(vistas(dao.delPeriodo(inicio, fin)), false);
        }
        List<FilaPedido> encontradas = new ArrayList<>(dao.buscar(texto, TOPE_BUSQUEDA));
        boolean hayMas = encontradas.size() > TOPE_BUSQUEDA;
        if (hayMas) encontradas.remove(0); // en orden de hoja: la primera es la mas antigua
        return new ListaFilas(vistas(encontradas), hayMas);
    }

    /** La fila releida con su aviso. */
    public Optional<FilaVista> fila(int id) {
        return dao.porId(id).map(f -> vistas(List.of(f)).get(0));
    }

    /** Los candidatos de una fila; {@code Optional.empty()} si la fila no existe, lista vacia si es del historico o su SKU no lo conoce el maestro. */
    public Optional<List<Recomendador.Candidato>> recomendados(int id) {
        Optional<FilaPedido> encontrada = dao.porId(id);
        if (encontrada.isEmpty()) return Optional.empty();
        FilaPedido f = encontrada.get();
        if (HISTORICO.equals(f.origen()) || f.canal() == null) return Optional.of(List.of());
        AceptaSku acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(Set.of(texto(f.sku())))));
        Aceptacion ac = acepta.para(f.canal(), f.sku());
        if (ac == null) return Optional.of(List.of());
        String propia = f.imei() == null || f.imei().isBlank() ? null : NsysDAO.claveCruce(f.imei());
        Set<String> yaPuestos = new LinkedHashSet<>();
        for (String clave : puestos(dao.shippyProConImeiDesde(desdePuestos())).keySet())
            if (!clave.equals(propia)) yaPuestos.add(clave);
        return Optional.of(Recomendador.de(ac, f.canal(), nsysDao.enEmpresa(), taller.valedorDeTodos(),
                nsysDao.trabajosAbiertos(), yaPuestos));
    }

    private static String texto(String sku) {
        return sku == null ? "" : sku;
    }

    private LocalDate desdePuestos() {
        return LocalDate.now(clock).minusDays(DIAS_PUESTOS);
    }

    /** El estado de una fila (spec §6 bis): HISTORICO, ENVIADA, NO_PENDIENTE o PENDIENTE. */
    static String estado(FilaPedido f) {
        if (HISTORICO.equals(f.origen())) return HISTORICO;
        if (f.enviada()) return ENVIADA;
        return f.viva() ? PENDIENTE : NO_PENDIENTE;
    }

    /** De las filas de ShippyPro con IMEI, las que dejan «ya puesto» su movil: las pendientes y las no pendientes, y las enviadas
     *  mientras NSYS no da el movil por vendido. IMEI (clave de cruce) -> ids de esas filas. */
    private Map<String, Set<Integer>> puestos(List<FilaPedido> filas) {
        Set<String> deEnviadas = new LinkedHashSet<>();
        for (FilaPedido f : filas)
            if (ENVIADA.equals(estado(f)) && f.imei() != null && !f.imei().isBlank()) deEnviadas.add(f.imei());
        Map<String, MovilNsys> moviles = new HashMap<>();
        if (!deEnviadas.isEmpty())
            for (MovilNsys m : nsysDao.porImeis(deEnviadas)) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, Set<Integer>> puestos = new HashMap<>();
        for (FilaPedido f : filas) {
            if (f.imei() == null || f.imei().isBlank()) continue;
            String estado = estado(f);
            if (HISTORICO.equals(estado)) continue;
            String clave = NsysDAO.claveCruce(f.imei());
            if (ENVIADA.equals(estado)) {
                MovilNsys m = moviles.get(clave);
                if (m != null && m.estado() == EstadoMovil.VENDIDO) continue;
            }
            puestos.computeIfAbsent(clave, k -> new LinkedHashSet<>()).add(f.id());
        }
        return puestos;
    }

    private List<FilaVista> vistas(List<FilaPedido> filas) {
        // Una sola carga para todas: solo las pendientes y las no pendientes con IMEI llevan aviso.
        Set<String> imeis = new LinkedHashSet<>();
        Set<String> skus = new LinkedHashSet<>();
        for (FilaPedido f : filas) {
            if (!llevaAviso(f)) continue;
            imeis.add(f.imei());
            if (f.sku() != null) skus.add(f.sku());
        }
        Map<String, MovilNsys> moviles = new HashMap<>();
        Map<String, List<TrabajoAbierto>> trabajos = Map.of();
        Function<MovilNsys, LoQueVale.Valor> vale = m -> null;
        Map<String, Set<Integer>> puestos = Map.of();
        AceptaSku acepta = null;
        if (!imeis.isEmpty()) {
            for (MovilNsys m : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
            trabajos = nsysDao.trabajosAbiertos(imeis);
            vale = taller.valedor(imeis);
            // Sobre todas las filas de ShippyPro con ese IMEI, no solo las de la pagina.
            puestos = puestos(dao.shippyProConImei(imeis, desdePuestos()));
            acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skus)));
        }
        List<FilaVista> vistas = new ArrayList<>();
        for (FilaPedido f : filas) {
            AvisoImei aviso = null;
            if (llevaAviso(f)) {
                String clave = NsysDAO.claveCruce(f.imei());
                MovilNsys m = moviles.get(clave);
                LoQueVale.Valor v = m == null ? null : vale.apply(m);
                Aceptacion ac = f.canal() == null ? null : acepta.para(f.canal(), f.sku());
                // Otra fila (no esta) con el mismo movil puesto.
                boolean enOtraFila = puestos.getOrDefault(clave, Set.of()).stream().anyMatch(id -> id != f.id());
                aviso = AvisoImei.de(ac, f.canal(), m, v, trabajos.getOrDefault(clave, List.of()), enOtraFila);
            }
            String estado = estado(f);
            vistas.add(new FilaVista(f.id(), f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), estado,
                    !HISTORICO.equals(estado), f.envioFecha(), f.transportista(), f.seguimiento(),
                    f.impresaEn(), f.tieneEtiqueta(),
                    aviso == null ? null : aviso.nivel(), aviso == null ? null : aviso.motivo(),
                    aviso == null ? null : aviso.skuMovil(), aviso == null ? null : aviso.caja(), canal(f)));
        }
        return vistas;
    }

    /** El canal de la fila: el guardado en las de ShippyPro; en el historico (sin canal en la base) se deduce de la plataforma. */
    static String canal(FilaPedido f) {
        Canal c = HISTORICO.equals(f.origen()) ? Canal.deHistorico(f.plataforma()) : f.canal();
        return c == null ? null : c.name();
    }

    private static boolean llevaAviso(FilaPedido f) {
        String estado = estado(f);
        return (PENDIENTE.equals(estado) || NO_PENDIENTE.equals(estado)) && f.imei() != null && !f.imei().isBlank();
    }

    /** ShippyPro y NSYS, en este orden. Un ERROR enseña un motivo fijo: su texto puede llevar datos internos. */
    public List<FuenteVista> fuentes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Optional<LecturaGuardada> buena = dao.ultimaLectura(Set.of(LecturaFilas.OK));
        Optional<LecturaGuardada> ultima = dao.ultimaLectura(Set.of(LecturaFilas.OK, LecturaFilas.ERROR, LecturaFilas.RECHAZADA));
        LocalDateTime cuando = buena.map(LecturaGuardada::fin).orElse(null);
        // Motivos fijos y en lenguaje de usuario: el mensaje guardado puede nombrar ajustes o llevar datos internos.
        String motivo = ultima.map(l -> switch (l.resultado()) {
            case LecturaFilas.ERROR -> MOTIVO_ERROR;
            case LecturaFilas.RECHAZADA -> MOTIVO_RECHAZADA;
            default -> null;
        }).orElse(null);
        FuenteVista shippyPro = new FuenteVista(SHIPPYPRO, cuando, ultima.map(LecturaGuardada::resultado).orElse(null),
                buena.map(LecturaGuardada::filas).orElse(0), buena.map(LecturaGuardada::skuDesconocidos).orElse(null), motivo,
                AvisoNsys.nivel(cuando, ahora));
        return List.of(shippyPro, nsys(ahora));
    }

    /** NSYS con la regla de {@code StockTelefonosController.estado()}: la ultima buena y el fallo posterior, si lo hay; aqui con motivos fijos. */
    private FuenteVista nsys(LocalDateTime ahora) {
        Optional<Importacion> ok = nsysDao.ultimaImportacion(ImportadorNsys.ACTUAL, Set.of(ImportadorNsys.OK));
        Optional<Importacion> fallo = nsysDao.ultimaImportacion(ImportadorNsys.ACTUAL,
                Set.of(ImportadorNsys.RECHAZADO, ImportadorNsys.ERROR));
        LocalDateTime ultima = ok.map(Importacion::fechaExportacion).orElse(null);
        boolean falloReciente = fallo.isPresent() && (ultima == null || fallo.get().fechaExportacion().isAfter(ultima));
        String resultado = falloReciente ? fallo.get().resultado() : ok.map(Importacion::resultado).orElse(null);
        String motivo = !falloReciente ? null
                : ImportadorNsys.ERROR.equals(fallo.get().resultado()) ? MOTIVO_NSYS_ERROR : MOTIVO_NSYS_RECHAZADO;
        return new FuenteVista(NSYS, ultima, resultado, ok.map(Importacion::filas).orElse(0), null, motivo,
                AvisoNsys.nivel(ultima, ahora));
    }
}
