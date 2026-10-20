package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compara una exportación de NSYS con lo que ya había y saca la foto nueva y los eventos del rastro (spec pieza 1 §5.4
 *  y §6). Es puro: no toca la base de datos. */
public final class Comparador {

    /**
     * Modo de la comparación:
     * - PRIMERA_CARGA: no hay nada previo. Sale el esqueleto, con la entrada y la venta en las fechas de NSYS (solo el día).
     * - NORMAL: cada exportación nueva contra la foto guardada.
     * - HISTORICO: exportaciones antiguas, una contra la anterior. Solo cambios; no toca la foto.
     */
    public enum Modo { PRIMERA_CARGA, NORMAL, HISTORICO }

    static final String RELEASED = "Released";

    private Comparador() {}

    public static ResultadoComparacion comparar(Map<ClaveMovil, MovilNsys> anteriores, List<FilaItems> filas,
                                                LocalDateTime fecha, Modo modo, Traductor t) {
        List<MovilNsys> aGuardar = new ArrayList<>();
        List<EventoNsys> eventos = new ArrayList<>();
        Set<String> desconocidas = new TreeSet<>();
        Set<ClaveMovil> vistas = new HashSet<>();
        Map<String, List<FilaItems>> lotesPorImei = modo == Modo.PRIMERA_CARGA ? porImei(filas) : Map.of();
        Set<String> imeisPrevios = new HashSet<>();
        if (modo == Modo.NORMAL) for (ClaveMovil k : anteriores.keySet()) imeisPrevios.add(k.imeiSn());

        for (FilaItems f : filas) {
            if (!vistas.add(f.clave())) continue;               // fila repetida en el fichero: cuenta la primera
            UbicacionNsys caja = t.caja(f.location());
            if (caja == null) desconocidas.add(Normalizacion.caja(f.location()));
            MovilNsys anterior = anteriores.get(f.clave());
            if (anterior == null) {
                if (modo == Modo.HISTORICO) continue;           // el histórico no da altas: las pone el esqueleto
                MovilNsys nuevo = construir(f, t, caja, null, fecha);
                aGuardar.add(nuevo);
                if (modo == Modo.PRIMERA_CARGA) {
                    esqueleto(f, lotesPorImei.get(f.imeiSn()), fecha, eventos);
                } else {
                    eventos.add(evento(f, fecha, false, TipoEvento.ENTRADA, null, nuevo.ubicacion(), nuevo.location()));
                    if (imeisPrevios.contains(f.imeiSn()))
                        eventos.add(evento(f, fecha, false, TipoEvento.REENTRADA, null, f.batch(), null));
                }
                continue;
            }
            MovilNsys nuevo = construir(f, t, caja, anterior, fecha);
            cambios(anterior, nuevo, fecha, eventos);
            if (modo != Modo.HISTORICO && !anterior.mismoContenido(nuevo))
                aGuardar.add(nuevo.conFechas(anterior.vistoPrimero(), fecha));
        }
        int desaparecidos = 0;
        for (ClaveMovil k : anteriores.keySet()) if (!vistas.contains(k)) desaparecidos++;
        return new ResultadoComparacion(aGuardar, eventos, desaparecidos, desconocidas);
    }

    /** Cuántas filas repiten IMEI/SN y lote de otra anterior del mismo fichero. {@link #comparar} se queda con la
     *  primera y descarta estas. Una exportación sana no trae ninguna. */
    public static int repetidas(List<FilaItems> filas) {
        Set<ClaveMovil> vistas = new HashSet<>();
        int repetidas = 0;
        for (FilaItems f : filas) if (!vistas.add(f.clave())) repetidas++;
        return repetidas;
    }

    /** La foto de una exportación sin nada previo. La usan el histórico (exportación contra exportación) y los tests. */
    public static Map<ClaveMovil, MovilNsys> foto(List<FilaItems> filas, Traductor t, LocalDateTime fecha) {
        Map<ClaveMovil, MovilNsys> foto = new HashMap<>();
        for (FilaItems f : filas) foto.putIfAbsent(f.clave(), construir(f, t, t.caja(f.location()), null, fecha));
        return foto;
    }

    static MovilNsys construir(FilaItems f, Traductor t, UbicacionNsys caja, MovilNsys anterior, LocalDateTime fecha) {
        ProductoMaestro p = t.producto(f.goods(), f.grade());
        // NSYS vacía la caja al cerrar el móvil (Released, Removed…): sin caja, un vendido o una baja no está «En
        // revisión»; se queda sin ubicación y con la última (spec 0.9.18.2 §2).
        String ubicacion = caja == null || cerradoSinCaja(f.status(), f.location()) ? null : caja.ubicacion();
        String ultima = ubicacion != null ? ubicacion
                : anterior == null ? null
                : anterior.ubicacion() != null ? anterior.ubicacion() : anterior.ubicacionUltima();
        return new MovilNsys(f.imeiSn(), f.batch(), f.goods(), f.grade(), f.status(), Normalizacion.caja(f.location()),
                f.supplier(), f.receivingDate(), f.soldDate(),
                p == null ? null : p.skuWeb(), p == null ? null : p.modelo(), p == null ? null : p.modeloMaestro(),
                p == null ? null : p.capacidad(), p == null ? null : p.color(), p == null ? null : p.calidadDetalle(),
                p != null ? p.esEsim() : Normalizacion.esEsim(f.goods()),
                ubicacion, ultima, EstadoMovil.de(f.status(), caja),
                anterior == null ? fecha : anterior.vistoPrimero(), anterior == null ? fecha : anterior.cambiado());
    }

    private static void cambios(MovilNsys antes, MovilNsys ahora, LocalDateTime fecha, List<EventoNsys> eventos) {
        FilaItems f = ahora.comoFila();
        // Al cerrar el móvil NSYS vacía la caja: no es un movimiento (spec 0.9.18.2 §2).
        if (!Objects.equals(antes.location(), ahora.location()) && !cerradoSinCaja(ahora.status(), ahora.location()))
            eventos.add(evento(f, fecha, false, TipoEvento.UBICACION, desde(antes), ahora.ubicacion(), ahora.location()));
        if (!Objects.equals(antes.grade(), ahora.grade()))
            eventos.add(evento(f, fecha, false, TipoEvento.GRADO, antes.grade(), ahora.grade(), null));
        if (!Objects.equals(antes.goods(), ahora.goods()))
            eventos.add(evento(f, fecha, false, TipoEvento.PRODUCTO, antes.goods(), ahora.goods(), null));
        if (!Objects.equals(antes.status(), ahora.status())) {
            TipoEvento tipo = RELEASED.equals(ahora.status()) ? TipoEvento.VENTA
                    : RELEASED.equals(antes.status()) && EstadoMovil.VIVOS.contains(ahora.status()) ? TipoEvento.VENTA_DESHECHA
                    : TipoEvento.ESTADO;
            eventos.add(evento(f, fecha, false, tipo, antes.status(), ahora.status(), null));
        }
    }

    /** Un móvil cerrado (Status que no es vivo) con la caja vacía: NSYS se la vacía al venderlo o darlo de baja (spec
     *  0.9.18.2 §2). Un móvil vivo sin caja, en cambio, está «En revisión». */
    static boolean cerradoSinCaja(String status, String location) {
        return !EstadoMovil.vivo(status) && Normalizacion.caja(location).isEmpty();
    }

    /** De dónde sale el móvil en un cambio de caja: su ubicación del ERP o, si no tiene, la caja de NSYS. Un móvil
     *  cerrado sin caja no sale de ninguna: sin «Desde», así su vuelta no cuenta como revisión (spec 0.9.18.2 §2). */
    private static String desde(MovilNsys antes) {
        if (antes.ubicacion() != null) return antes.ubicacion();
        return cerradoSinCaja(antes.status(), antes.location()) ? null : antes.location();
    }

    /** Primera carga: la entrada (o reentrada, si el IMEI tiene un lote anterior) y la venta, con las fechas de NSYS. */
    private static void esqueleto(FilaItems f, List<FilaItems> lotes, LocalDateTime fecha, List<EventoNsys> eventos) {
        boolean primera = lotes == null || lotes.stream().noneMatch(o -> entroAntes(o, f));
        LocalDateTime entrada = f.receivingDate() != null ? f.receivingDate().atStartOfDay() : fecha;
        boolean soloDia = f.receivingDate() != null;
        eventos.add(primera ? evento(f, entrada, soloDia, TipoEvento.ENTRADA, null, null, null)
                            : evento(f, entrada, soloDia, TipoEvento.REENTRADA, null, f.batch(), null));
        if (f.soldDate() != null)
            eventos.add(evento(f, f.soldDate().atStartOfDay(), true, TipoEvento.VENTA, null, null, null));
    }

    /** ¿Entró {@code o} antes que {@code f}? Por fecha de recepción y, si empatan o falta alguna, por lote. */
    private static boolean entroAntes(FilaItems o, FilaItems f) {
        if (o.batch().equals(f.batch())) return false;
        LocalDate a = o.receivingDate();
        LocalDate b = f.receivingDate();
        if (a != null && b != null && !a.equals(b)) return a.isBefore(b);
        return o.batch().compareTo(f.batch()) < 0;
    }

    private static Map<String, List<FilaItems>> porImei(List<FilaItems> filas) {
        Map<String, List<FilaItems>> mapa = new HashMap<>();
        for (FilaItems f : filas) mapa.computeIfAbsent(f.imeiSn(), k -> new ArrayList<>()).add(f);
        return mapa;
    }

    private static EventoNsys evento(FilaItems f, LocalDateTime fecha, boolean soloDia, TipoEvento tipo,
                                     String de, String a, String aNsys) {
        return new EventoNsys(f.imeiSn(), f.batch(), fecha, soloDia, tipo, de, a, aNsys);
    }
}
