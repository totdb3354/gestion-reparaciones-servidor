package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.Normalizacion;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** La pasada de reservas de IMEI (spec reserva §5), al final de cada vuelta de {@link LecturaFilas}: 1) resuelve las reservas de
 *  las filas que ya tienen IMEI puesto; 2) suelta las que ya no valen; 3) reparte ({@link Reparto}) un telefono a las filas que
 *  esperan y apunta «sin stock» a las que se quedan sin. Motivos para soltar (DETALLE de RESERVA_QUITADA): {@link #CANCELADA},
 *  {@link #MARCADA}, {@link #YA_NO_ESTA}, {@link #CON_ETIQUETA}, {@link #ENVIADA_SIN_IMEI} (la fila se envio sin IMEI: logistica
 *  seguramente mando ese movil sin teclearlo, asi que las devoluciones NO deben devolverlo a stock), {@link #NO_ENCONTRADO},
 *  {@link #PUESTO_EN_OTRA} y {@link #NO_DISPONIBLE}. Las devoluciones del bot ({@code bot.Devoluciones}) siguen el criterio de la
 *  0.9.47: devuelven CANCELADA, MARCADA y NO_DISPONIBLE (y RESERVA_SUSTITUIDA), dejan YA_NO_ESTA «sin destino» (una persona
 *  comprueba si salio) y no tocan el resto. Cada escritura es una transaccion por fila con la fila bloqueada
 *  ({@link FilasDAO}), para no pisar a una persona que ponga el IMEI a la vez. */
@Service
public class Reservas {

    private static final Logger log = LoggerFactory.getLogger(Reservas.class);

    /** Motivos de RESERVA_QUITADA (DETALLE). «Cancelar móvil» (marca CANCELADO) sin etiqueta impresa. */
    public static final String CANCELADA = "CANCELADA";
    /** Otra marca (ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR) sin etiqueta impresa: la fila espera, pero no a ese telefono. */
    public static final String MARCADA = "MARCADA";
    /** «Ya no está en ShippyPro»: la fila se cerro sin envio (y sin «Cancelar móvil»). Puede que el movil saliera igualmente. */
    public static final String YA_NO_ESTA = "YA_NO_ESTA";
    /** Con marca y con la etiqueta ya impresa: el movil reservado pudo salir con ella; no se devuelve a stock. */
    public static final String CON_ETIQUETA = "CON_ETIQUETA";
    /** La fila se cerro con envio y sin IMEI: seguramente salio el movil reservado sin teclearlo; no se devuelve a stock. */
    public static final String ENVIADA_SIN_IMEI = "ENVIADA_SIN_IMEI";
    /** El movil esta en una de las cajas de revisar ({@link #CAJAS_REVISAR}): una persona no lo encuentra. */
    public static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    /** El movil ya no esta en la empresa o su estado no es STOCK ni «de pedido» (vendido, de baja, caja que no cuenta). */
    public static final String NO_DISPONIBLE = "NO_DISPONIBLE";
    /** El IMEI reservado se tecleo en otra fila. */
    public static final String PUESTO_EN_OTRA = "PUESTO_EN_OTRA";
    /** Las cajas de NSYS donde una persona deja un movil que no encuentra. */
    static final Set<String> CAJAS_REVISAR = Set.of("Pendiente revisar si hay error de inventario",
            "MV Pendiente revisar si hay error de inventario");

    /** Lo que hizo una pasada: filas reservadas, reservas soltadas y filas que esperan sin telefono que cumpla los criterios. */
    public record Resumen(int reservadas, int soltadas, int sinStock) {}

    private final FilasDAO dao;
    private final NsysDAO nsysDao;
    private final TallerNsys taller;

    public Reservas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller) {
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.taller = taller;
    }

    public Resumen pasada(LocalDateTime ahora) {
        List<FilaPedido> filas = dao.conReservaOEsperando();
        if (filas.isEmpty()) return new Resumen(0, 0, 0);

        // 1. El IMEI ya esta puesto en una fila con reserva: confirmada o sustituida (sin usuario).
        List<FilaPedido> conReserva = new ArrayList<>();
        List<FilaPedido> sinReserva = new ArrayList<>();
        int resueltas = 0;
        // El IMEI puesto en las resueltas queda ocupado aunque la lectura de «ya puestos» no lo traiga.
        Set<String> resueltos = new HashSet<>();
        for (FilaPedido f : filas) {
            if (f.imeiReservado() == null) sinReserva.add(f);
            else if (conImei(f)) {
                dao.resolverReservaPuesta(f.id(), null, ahora);
                resueltos.add(NsysDAO.claveCruce(f.imei()));
                resueltas++;
            } else conReserva.add(f);
        }

        // «Ya puestos»: el mismo criterio que VistaFilas (filas de ShippyPro con IMEI de los ultimos DIAS_PUESTOS dias).
        Map<String, Set<Integer>> puestos = VistaFilas.puestos(
                dao.shippyProConImeiDesde(ahora.toLocalDate().minusDays(VistaFilas.DIAS_PUESTOS)), nsysDao);
        Function<MovilNsys, LoQueVale.Valor> vale = null;

        // 2. Soltar las reservas que ya no valen.
        int soltadas = 0;
        Set<String> retenidos = new HashSet<>();
        List<FilaPedido> esperando = new ArrayList<>();
        if (!conReserva.isEmpty()) {
            Map<String, MovilNsys> moviles = new HashMap<>();
            Set<String> imeis = new LinkedHashSet<>();
            for (FilaPedido f : conReserva) imeis.add(f.imeiReservado());
            // De cada IMEI, el lote mas reciente (porImeis los da primero).
            for (MovilNsys m : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
            vale = taller.valedorDeTodos();
            for (FilaPedido f : conReserva) {
                String clave = NsysDAO.claveCruce(f.imeiReservado());
                String motivo = motivoParaSoltar(f, moviles.get(clave), vale, puestos.containsKey(clave));
                if (motivo == null) {
                    retenidos.add(clave);
                    continue;
                }
                dao.soltarReserva(f.id(), f.imeiReservado(), motivo, ahora);
                soltadas++;
                // Seguramente salio sin teclearlo: no se reserva a otro pedido en esta pasada.
                if (ENVIADA_SIN_IMEI.equals(motivo)) retenidos.add(clave);
                if (espera(f)) esperando.add(f);
            }
        }
        for (FilaPedido f : sinReserva) if (espera(f)) esperando.add(f);

        // 3. Reservar las que esperan (con canal y SKU que el maestro conoce).
        int reservadas = 0;
        int sinStock = 0;
        if (!esperando.isEmpty()) {
            Set<String> skus = new LinkedHashSet<>();
            for (FilaPedido f : esperando) if (f.sku() != null) skus.add(f.sku());
            AceptaSku acepta = new AceptaSku(skus.isEmpty() ? List.of() : nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skus)));
            Map<Integer, FilaPedido> porId = new HashMap<>();
            List<Reparto.Fila> paraReparto = new ArrayList<>();
            for (FilaPedido f : esperando) {
                Aceptacion ac = acepta.para(f.canal(), f.sku());
                if (ac == null) continue;
                porId.put(f.id(), f);
                LocalDate fechaPedido = f.fechaCompra() != null ? f.fechaCompra() : f.fecha();
                paraReparto.add(new Reparto.Fila(f.id(), f.canal(), f.pais(), fechaPedido, ac));
            }
            if (!paraReparto.isEmpty()) {
                Set<String> ocupados = new HashSet<>(puestos.keySet());
                ocupados.addAll(retenidos);
                ocupados.addAll(resueltos);
                List<MovilNsys> stock = new ArrayList<>();
                for (MovilNsys m : nsysDao.enEmpresa())
                    if (m.imeiSn() != null && m.imeiSn().strip().length() <= FilasDAO.ANCHO_RESERVADO) stock.add(m);
                if (vale == null) vale = taller.valedorDeTodos();
                for (Reparto.Resultado r : Reparto.de(paraReparto, stock, vale, nsysDao.trabajosAbiertos(), ocupados)) {
                    if (r.imei() != null) {
                        if (dao.reservar(r.idFila(), r.imei(), r.porque(), ahora)) reservadas++;
                        continue;
                    }
                    sinStock++;
                    if (porId.get(r.idFila()).sinStockEn() == null) dao.apuntarSinStock(r.idFila(), ahora);
                }
            }
        }

        Resumen resumen = new Resumen(reservadas, soltadas, sinStock);
        // Cada 5 minutos: a INFO solo si hizo algo.
        String texto = "Pedidos: reservas de IMEI: {} reservadas, {} soltadas, {} sin stock, {} resueltas al poner el IMEI";
        if (reservadas > 0 || soltadas > 0 || resueltas > 0) log.info(texto, reservadas, soltadas, sinStock, resueltas);
        else log.debug(texto, reservadas, soltadas, sinStock, resueltas);
        return resumen;
    }

    /** El motivo para soltar la reserva de una fila sin IMEI, o null si sigue valiendo. {@code movil}: el lote mas reciente del IMEI
     *  reservado (null si no esta en el espejo); {@code puesto}: el IMEI esta puesto en otra fila. */
    static String motivoParaSoltar(FilaPedido f, MovilNsys movil, Function<MovilNsys, LoQueVale.Valor> vale, boolean puesto) {
        if (f.cerradaEn() != null && f.envioFecha() != null) return ENVIADA_SIN_IMEI;
        // Como la regla de la 0.9.47: «Cancelar móvil» antes que «ya no está»; con etiqueta impresa, el movil pudo salir.
        if (Marca.CANCELADO.name().equals(f.marca())) return f.impresaEn() != null ? CON_ETIQUETA : CANCELADA;
        if (f.cerradaEn() != null) return YA_NO_ESTA;
        if (f.marca() != null) return f.impresaEn() != null ? CON_ETIQUETA : MARCADA;
        if (movil == null) return NO_DISPONIBLE;
        if (CAJAS_REVISAR.contains(Normalizacion.caja(movil.location()))) return NO_ENCONTRADO;
        EstadoMovil estado = movil.estado() == null ? null : movil.estado().conGrado(grado(movil, vale));
        if (estado != EstadoMovil.STOCK && estado != EstadoMovil.DE_PEDIDO) return NO_DISPONIBLE;
        if (puesto) return PUESTO_EN_OTRA;
        return null;
    }

    private static String grado(MovilNsys m, Function<MovilNsys, LoQueVale.Valor> vale) {
        LoQueVale.Valor v = vale.apply(m);
        return v == null ? null : v.grado();
    }

    private static boolean conImei(FilaPedido f) {
        return f.imei() != null && !f.imei().isBlank();
    }

    /** Puede reservar (spec §2): de ShippyPro, viva, sin marca, sin IMEI y con canal. */
    private static boolean espera(FilaPedido f) {
        return "SHIPPYPRO".equals(f.origen()) && f.viva() && f.marca() == null && !conImei(f) && f.canal() != null;
    }
}
