package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Las reglas de las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3): las listas a partir de los móviles con algo del
 *  taller vigente (TallerNsys#movilesPorPasar), los IMEI que NSYS no conoce y los avisos por revisar; la marca «Hecho» de cada fila
 *  y su repaso tras cada importación (§3.6, con los ajustes 1 y 2 del plan). Es puro. */
final class TarjetasNsys {

    private TarjetasNsys() {}

    /** La marca «Hecho» que vale para una fila; NINGUNA si no hay. */
    record Marca(String por, LocalDateTime en, LocalDateTime volvio) {
        static final Marca NINGUNA = new Marca(null, null, null);
    }

    /** Lo que hace el repaso de las marcas tras una importación OK. */
    record Repaso(List<HechoTaller> borrar, List<HechoTaller> vuelven) {}

    /** La clave de una marca: la tarjeta y el IMEI por {@link NsysDAO#claveCruce}. */
    static String clave(TarjetaHecho tarjeta, String imeiSn) {
        return tarjeta.name() + "|" + NsysDAO.claveCruce(imeiSn);
    }

    static Map<String, HechoTaller> porClave(List<HechoTaller> marcas) {
        Map<String, HechoTaller> mapa = new HashMap<>();
        for (HechoTaller h : marcas) mapa.put(clave(h.tarjeta(), h.imeiSn()), h);
        return mapa;
    }

    /** La marca de una fila que sale desde {@code desde}: la del IMEI y la tarjeta, si no es anterior a la fila (una anterior era
     *  de un cambio de antes del mismo móvil y no vale). */
    static Marca marca(Map<String, HechoTaller> marcas, TarjetaHecho tarjeta, String imeiSn, LocalDateTime desde) {
        HechoTaller h = marcas.get(clave(tarjeta, imeiSn));
        if (h == null || (desde != null && h.fecha().isBefore(desde))) return Marca.NINGUNA;
        return new Marca(h.usuario(), h.fecha(), h.volvio());
    }

    /** Una fila marcada que vale y no ha vuelto: no cuenta ni va en las plantillas ni en «Copiar lista». */
    static boolean marcada(LocalDateTime hechoEn, LocalDateTime hechoVolvio) {
        return hechoEn != null && hechoVolvio == null;
    }

    /** El cambio vigente de {@code dato} de un móvil (el de mayor id si hubiera dos, como LoQueVale). */
    private static Optional<CambioTaller> cambio(TallerNsys.MovilPorPasar p, DatoTaller dato) {
        return p.cambios().stream().filter(c -> c.dato() == dato).max(Comparator.comparingInt(CambioTaller::id));
    }

    private static List<PorQueTaller> porQue(TallerNsys.MovilPorPasar p, DatoTaller dato) {
        return p.cambios().stream().filter(c -> c.dato() == dato).map(TallerNsys::porQue).toList();
    }

    static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        return color(moviles, marcas, Map.of());
    }

    /** «Cambio de color» (§3.2): los móviles con un cambio de color vigente. Si el color que vale es el de NSYS (aún sin cerrar),
     *  el producto es el de ahora. {@code vueltas}: la vuelta de cada móvil por clave de cruce (pieza 4 §6); sin ella, en la
     *  empresa. */
    static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas,
                                     Map<String, VueltaNsys> vueltas) {
        List<FilaColorNsys> filas = new ArrayList<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            Optional<CambioTaller> c = cambio(p, DatoTaller.COLOR);
            if (c.isEmpty()) continue;
            MovilNsys m = p.movil();
            ProductoNsys.Poner poner = p.valor().colorNsys() == null ? new ProductoNsys.Poner(m.goods(), false) : p.poner();
            String deNsys = GrafiasNsys.colorDelGoods(m.goods());
            String colorA = p.valor().colorNsys() == null ? (deNsys != null ? deNsys : m.color())
                    : poner == null ? p.valor().color() : GrafiasNsys.colorDelGoods(poner.producto());
            LocalDateTime desde = c.get().fecha();
            Marca h = marca(marcas, TarjetaHecho.COLOR, m.imeiSn(), desde);
            VueltaNsys v = vueltas.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            filas.add(new FilaColorNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), deNsys != null ? deNsys : m.color(),
                    colorA, m.goods(), m.grade(), poner == null ? null : poner.producto(), poner != null && poner.compruebalo(),
                    cambio(p, DatoTaller.GRADO).isPresent(), porQue(p, DatoTaller.COLOR), desde, h.por(), h.en(), h.volvio(),
                    v.vendidoEn(), v.bajaEn(), v.desde()));
        }
        filas.sort(Comparator.comparing(FilaColorNsys::desde).thenComparing(FilaColorNsys::imeiSn));
        return filas;
    }

    static List<FilaGradoNsys> grado(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        return grado(moviles, marcas, Map.of());
    }

    /** «Cambio de grado» (§3.3): los móviles con un cambio de grado vigente, también los que cambian de color; con su vuelta
     *  (pieza 4 §6). */
    static List<FilaGradoNsys> grado(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas,
                                     Map<String, VueltaNsys> vueltas) {
        List<FilaGradoNsys> filas = new ArrayList<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            Optional<CambioTaller> c = cambio(p, DatoTaller.GRADO);
            if (c.isEmpty()) continue;
            MovilNsys m = p.movil();
            LocalDateTime desde = c.get().fecha();
            Marca h = marca(marcas, TarjetaHecho.GRADO, m.imeiSn(), desde);
            VueltaNsys v = vueltas.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            filas.add(new FilaGradoNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.grade(), p.valor().grade(),
                    m.goods(), cambio(p, DatoTaller.COLOR).isPresent(), porQue(p, DatoTaller.GRADO), desde, h.por(), h.en(),
                    h.volvio(), v.vendidoEn(), v.bajaEn(), v.desde()));
        }
        filas.sort(Comparator.comparing(FilaGradoNsys::desde).thenComparing(FilaGradoNsys::imeiSn));
        return filas;
    }

    /** Los descartes por {@link NsysDAO#claveCruce} de su IMEI. */
    static Map<String, DescarteTaller> descartesPorClave(List<DescarteTaller> descartes) {
        Map<String, DescarteTaller> mapa = new HashMap<>();
        for (DescarteTaller d : descartes) mapa.put(NsysDAO.claveCruce(d.imeiSn()), d);
        return mapa;
    }

    /** El descarte de una fila de «No está en NSYS», valga o no; null si no tiene. */
    private static DescarteTaller descarte(AltaTaller a, Map<String, DescarteTaller> descartes) {
        return descartes.get(NsysDAO.claveCruce(a.imei()));
    }

    /** Descartada (spec pieza 3 §4.1): con un descarte no anterior a su última asignación; sin fecha de asignación, vale. */
    static boolean descartada(AltaTaller a, Map<String, DescarteTaller> descartes) {
        DescarteTaller d = descarte(a, descartes);
        return d != null && (a.ultimoEn() == null || !d.fecha().isBefore(a.ultimoEn()));
    }

    /** «No está en NSYS» (§3.4), sin las descartadas; la que tiene un descarte superado por un trabajo nuevo lleva quién, cuándo y
     *  por qué la descartaron (pieza 3 §5.4). */
    static List<FilaAltaNsys> altas(List<AltaTaller> altas, Map<String, HechoTaller> marcas, Map<String, DescarteTaller> descartes) {
        List<FilaAltaNsys> filas = new ArrayList<>();
        for (AltaTaller a : altas) {
            if (descartada(a, descartes)) continue;
            Marca h = marca(marcas, TarjetaHecho.ALTA, a.imei(), a.desde());
            DescarteTaller antes = descarte(a, descartes);
            filas.add(new FilaAltaNsys(a.imei(), a.modelo(), a.ultimo(), Rastro.categoria(a.ultimo()), a.abierto(), a.tecnico(),
                    a.cliente(), a.trabajos(), a.desde(), h.por(), h.en(), h.volvio(),
                    antes == null ? null : antes.usuario(), antes == null ? null : antes.fecha(),
                    antes == null ? null : antes.motivo()));
        }
        filas.sort(Comparator.comparing(FilaAltaNsys::desde, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FilaAltaNsys::imeiSn));
        return filas;
    }

    /** «Ver descartados» (pieza 3 §4.2): las filas descartadas, del descarte más reciente al más antiguo. */
    static List<FilaDescartadaNsys> descartadas(List<AltaTaller> altas, Map<String, DescarteTaller> descartes) {
        List<FilaDescartadaNsys> filas = new ArrayList<>();
        for (AltaTaller a : altas) {
            if (!descartada(a, descartes)) continue;
            DescarteTaller d = descarte(a, descartes);
            filas.add(new FilaDescartadaNsys(a.imei(), a.modelo(), a.ultimo(), Rastro.categoria(a.ultimo()), d.usuario(),
                    d.fecha(), d.motivo()));
        }
        filas.sort(Comparator.comparing(FilaDescartadaNsys::descartadoEn, Comparator.reverseOrder())
                .thenComparing(FilaDescartadaNsys::imeiSn));
        return filas;
    }

    /** «Revisar a mano» (§3.5), en el orden de los avisos, con el modelo de la fila más reciente del espejo de su IMEI
     *  ({@code espejo}, por {@link NsysDAO#claveCruce}) y, desde la pieza 4, su vuelta ({@code vueltas}; sin ella, en la empresa).
     *  Qué avisos salen lo decide quien llama (PorPasarNsys). */
    static List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Map<String, MovilNsys> espejo, Map<String, VueltaNsys> vueltas) {
        return avisos.stream().filter(a -> AvisoTaller.POR_REVISAR.contains(a.tipo())).map(a -> {
            String clave = NsysDAO.claveCruce(a.imeiSn());
            MovilNsys m = espejo.get(clave);
            VueltaNsys v = vueltas.getOrDefault(clave, VueltaNsys.EN_EMPRESA);
            return new FilaRevisarNsys(a.id(), a.imeiSn(), m == null ? null : m.modelo(), m == null ? null : m.modeloMaestro(),
                    m == null ? null : m.capacidad(), a.tipo().name(), a.causa() == null ? null : a.causa().name(),
                    a.dato().name(), a.de(), a.a(), a.motivo(), a.idRep(), a.usuario(), a.fecha(), v.vendidoEn(), v.bajaEn(),
                    v.desde());
        }).toList();
    }

    static List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Map<String, MovilNsys> espejo) {
        return revisar(avisos, espejo, Map.of());
    }

    /** El primer momento que cuenta para «Sin grado en NSYS» (pieza 4 §4.1): la 0.9.21 en producción, hora de Madrid. */
    static final LocalDateTime DESDE_SIN_GRADO = LocalDateTime.of(2026, 10, 22, 12, 49);

    private static final Comparator<NsysDAO.TrabajoFecha> POR_ASIGNACION = Comparator.comparing(NsysDAO.TrabajoFecha::asignado,
            Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(NsysDAO.TrabajoFecha::idRep);

    /** «Sin grado en NSYS» (pieza 4 §4.1): por cada IMEI con trabajos desde {@link #DESDE_SIN_GRADO} ({@code trabajos}, por clave de
     *  cruce), su fila más reciente del espejo ({@code espejo}), lo que vale de su grado ({@code vigentes}), si sale ({@code
     *  vueltas}: en la empresa o que ha vuelto; si no está, no sale) y su batería recordada ({@code baterias}), todo por clave de
     *  cruce. Sale si lo que vale de su grado es «como n/a». Del trabajo más antiguo al más reciente, y por IMEI. */
    static List<FilaSinGradoNsys> sinGrado(Map<String, List<NsysDAO.TrabajoFecha>> trabajos, Map<String, MovilNsys> espejo,
                                           Map<String, List<CambioTaller>> vigentes, Map<String, VueltaNsys> vueltas,
                                           Map<String, List<BateriaRecordada>> baterias) {
        List<FilaSinGradoNsys> filas = new ArrayList<>();
        trabajos.forEach((clave, delImei) -> {
            MovilNsys m = espejo.get(clave);
            VueltaNsys v = vueltas.get(clave);
            if (m == null || v == null || delImei.isEmpty()) return;
            String grado = LoQueVale.valor(vigentes.getOrDefault(clave, List.of()), DatoTaller.GRADO, m);
            if (GradoTaller.grupo(grado) != GradoTaller.Grupo.COMO_NA) return;
            NsysDAO.TrabajoFecha ultimo = delImei.stream().max(POR_ASIGNACION).orElseThrow();
            Optional<BateriaRecordada> b = BateriaRecordada.ultima(baterias.getOrDefault(clave, List.of()));
            filas.add(new FilaSinGradoNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.grade(), ultimo.idRep(),
                    ultimo.asignado(), Rastro.categoria(ultimo.idRep()), b.map(BateriaRecordada::idRep).orElse(null),
                    b.map(BateriaRecordada::nueva).orElse(null), b.map(BateriaRecordada::fecha).orElse(null), v.vendidoEn(),
                    v.bajaEn(), v.desde()));
        });
        filas.sort(Comparator.comparing(FilaSinGradoNsys::ultimoTrabajoEn, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FilaSinGradoNsys::imeiSn));
        return filas;
    }

    /** Las filas de Color, Grado y No está en NSYS que hay ahora, por clave de marca, con su «desde» (para el repaso). */
    static Map<String, LocalDateTime> filas(List<TallerNsys.MovilPorPasar> moviles, List<AltaTaller> altas) {
        Map<String, LocalDateTime> filas = new HashMap<>();
        for (TallerNsys.MovilPorPasar p : moviles) {
            cambio(p, DatoTaller.COLOR).ifPresent(c -> filas.put(clave(TarjetaHecho.COLOR, p.movil().imeiSn()), c.fecha()));
            cambio(p, DatoTaller.GRADO).ifPresent(c -> filas.put(clave(TarjetaHecho.GRADO, p.movil().imeiSn()), c.fecha()));
        }
        for (AltaTaller a : altas) filas.put(clave(TarjetaHecho.ALTA, a.imei()), a.desde());
        return filas;
    }

    /** El repaso con la hora de exportación del fichero (§3.6): una marca cuya fila ya no está, o anterior a su fila, se borra;
     *  una que sigue pendiente y es anterior al fichero «vuelve»; una posterior al fichero (un fichero de antes de marcar no dice
     *  nada) o ya vuelta se queda como está. */
    static Repaso repasar(List<HechoTaller> marcas, Map<String, LocalDateTime> filas, LocalDateTime exportacion) {
        List<HechoTaller> borrar = new ArrayList<>();
        List<HechoTaller> vuelven = new ArrayList<>();
        for (HechoTaller h : marcas) {
            String clave = clave(h.tarjeta(), h.imeiSn());
            LocalDateTime desde = filas.get(clave);
            if (!filas.containsKey(clave) || (desde != null && h.fecha().isBefore(desde))) borrar.add(h);
            else if (h.volvio() == null && h.fecha().isBefore(exportacion)) vuelven.add(h);
        }
        return new Repaso(borrar, vuelven);
    }
}
