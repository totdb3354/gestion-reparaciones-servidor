package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AvisoNsys;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

/** Lo que enseña «Pedidos» (spec pieza 2 §8): los pedidos abiertos con el estado de cada unidad, calculado al pedirlo con lo
 *  último del espejo de NSYS y del taller, y las fuentes para la píldora. */
@Service
public class VistaPedidos {

    static final String MOTIVO_ERROR = "Error al leer la fuente; ver el registro del servidor";

    private final PedidosDAO dao;
    private final NsysDAO nsysDao;
    private final TallerNsys taller;
    private final int diasSimulador;
    private final Clock clock;

    @Autowired
    public VistaPedidos(PedidosDAO dao, NsysDAO nsysDao, TallerNsys taller, @Value("${pedidos.simulador.dias:14}") int diasSimulador) {
        this(dao, nsysDao, taller, diasSimulador, Clock.system(ImportadorNsys.MADRID));
    }

    VistaPedidos(PedidosDAO dao, NsysDAO nsysDao, TallerNsys taller, int diasSimulador, Clock clock) {
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.taller = taller;
        this.diasSimulador = diasSimulador;
        this.clock = clock;
    }

    public List<PedidoVista> pedidos(boolean viejos) {
        LocalDate limite = LocalDate.now(clock).minusDays(diasSimulador);
        List<PedidoGuardado> pedidos = dao.abiertos().stream()
                .filter(p -> viejos || !esViejo(p, limite)).toList();
        if (pedidos.isEmpty()) return List.of();
        Set<Integer> ids = new LinkedHashSet<>(pedidos.stream().map(PedidoGuardado::id).toList());
        Map<Integer, List<UnidadGuardada>> porPedido = new HashMap<>();
        // Solo las de los pedidos que se enseñan: un IMEI que solo se repite en un pedido viejo escondido no es «repetido».
        for (UnidadGuardada u : dao.unidades(ids))
            if (ids.contains(u.idPedido())) porPedido.computeIfAbsent(u.idPedido(), k -> new ArrayList<>()).add(u);
        Set<String> imeis = new LinkedHashSet<>();
        Map<String, Integer> veces = new HashMap<>();
        porPedido.values().forEach(l -> l.forEach(u -> {
            if (u.imei() == null) return;
            imeis.add(u.imei());
            veces.merge(NsysDAO.claveCruce(u.imei()), 1, Integer::sum);
        }));
        Map<String, MovilNsys> moviles = new HashMap<>();
        Map<String, List<TrabajoAbierto>> trabajos = Map.of();
        Function<MovilNsys, LoQueVale.Valor> vale = m -> null;
        if (!imeis.isEmpty()) {
            for (MovilNsys m : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
            trabajos = nsysDao.trabajosAbiertos(imeis);
            vale = taller.valedor(imeis);
        }
        Set<String> skus = new LinkedHashSet<>();
        porPedido.values().forEach(l -> l.forEach(u -> skus.add(u.sku())));
        AceptaSku acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skus)));

        List<PedidoVista> vistas = new ArrayList<>();
        for (PedidoGuardado p : pedidos) {
            List<UnidadVista> unidades = new ArrayList<>();
            for (UnidadGuardada u : porPedido.getOrDefault(p.id(), List.of()))
                unidades.add(unidad(u, p.canal(), acepta, moviles, trabajos, vale, veces));
            if (!unidades.isEmpty())
                vistas.add(new PedidoVista(p.id(), p.canal().name(), p.numero(), p.cliente(), p.pais(), p.fecha(), p.fuente().name(),
                        esViejo(p, limite), unidades));
        }
        return vistas;
    }

    private static UnidadVista unidad(UnidadGuardada u, Canal canal, AceptaSku acepta, Map<String, MovilNsys> moviles,
                                      Map<String, List<TrabajoAbierto>> trabajos, Function<MovilNsys, LoQueVale.Valor> vale,
                                      Map<String, Integer> veces) {
        Aceptacion ac = acepta.para(canal, u.sku());
        Atributos pide = ac == null ? Atributos.NINGUNO : ac.pide();
        if (u.imei() == null)
            return new UnidadVista(u.id(), u.posicion(), u.sku(), pide, null, null, null, u.sinPedidoConcreto(), u.otroOrderId(),
                    false, "SIN_MOVIL", null, MovilUnidad.NINGUNO);
        String clave = NsysDAO.claveCruce(u.imei());
        MovilNsys m = moviles.get(clave);
        List<TrabajoAbierto> t = trabajos.getOrDefault(clave, List.of());
        LoQueVale.Valor v = m == null ? null : vale.apply(m);
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, t, canal);
        MovilUnidad movil = m == null ? MovilUnidad.NINGUNO : new MovilUnidad(true, m.modelo(), m.modeloMaestro(), m.capacidad(),
                v.color(), v.grado(), v.bateriaNueva(), m.esEsim(), v.sku(),
                m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion(), m.estado().name(), t);
        return new UnidadVista(u.id(), u.posicion(), u.sku(), pide, u.imei(), u.imeiFuente(), u.imeiTecleadoEn(),
                u.sinPedidoConcreto(), u.otroOrderId(), veces.getOrDefault(clave, 0) > 1, r.estado(), r.motivo(), movil);
    }

    private static boolean esViejo(PedidoGuardado p, LocalDate limite) {
        return p.fuente() == FuentePedidos.SIMULADOR && p.fecha().toLocalDate().isBefore(limite);
    }

    public List<FuentePedidosVista> fuentes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<FuentePedidosVista> fuentes = new ArrayList<>();
        for (FuentePedidos f : FuentePedidos.values()) {
            Optional<LecturaGuardada> buena = dao.ultimaLectura(f, Set.of(LecturaPedidos.OK));
            Optional<LecturaGuardada> ultima = dao.ultimaLectura(f, Set.of(LecturaPedidos.OK, LecturaPedidos.ERROR,
                    LecturaPedidos.RECHAZADA, LecturaPedidos.INCOMPLETA));
            LocalDateTime cuando = buena.map(LecturaGuardada::fin).orElse(null);
            String resultado = ultima.map(LecturaGuardada::resultado).orElse(null);
            String motivo = ultima.map(l -> LecturaPedidos.ERROR.equals(l.resultado()) ? MOTIVO_ERROR : l.mensaje()).orElse(null);
            fuentes.add(new FuentePedidosVista(f.name(), cuando, ultima.map(LecturaGuardada::fin).orElse(null), resultado,
                    buena.map(LecturaGuardada::filas).orElse(0), buena.map(LecturaGuardada::skuDesconocidos).orElse(null), motivo,
                    AvisoNsys.nivel(cuando, ahora)));
        }
        return fuentes;
    }
}
