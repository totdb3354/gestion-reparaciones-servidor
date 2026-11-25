package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.Datos;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Salida;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Trabajo;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Veredicto;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import com.reparaciones.servidor.pedidos.CambioImei;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** La lista de devoluciones del bot y lo que el bot informa (spec 2026-11-24-bot-devoluciones §5 y §6). */
@Service
public class Devoluciones {

    private static final Logger log = LoggerFactory.getLogger(Devoluciones.class);

    /** Solo se miran los descuentos de los ultimos 90 dias. */
    static final int DIAS_DESCONTADO = 90;
    static final int TOPE_CANDIDATOS = 1000;
    /** Tope defensivo de IMEI en la lista. */
    static final int TOPE_LISTA = 500;
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final BotDAO dao;
    private final NsysDAO nsys;
    private final Clock clock;

    @Autowired
    public Devoluciones(BotDAO dao, NsysDAO nsys) {
        this(dao, nsys, Clock.system(ImportadorNsys.MADRID));
    }

    Devoluciones(BotDAO dao, NsysDAO nsys, Clock clock) {
        this.dao = dao;
        this.nsys = nsys;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListaDevoluciones lista() {
        return lista(TOPE_LISTA);
    }

    /** La lista con un tope dado; los SIN_DESTINO nuevos se apuntan en la fila de la salida (una vez por IMEI y fila). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListaDevoluciones lista(int tope) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<String> imeis = dao.candidatos(ahora.minusDays(DIAS_DESCONTADO), TOPE_CANDIDATOS);
        if (imeis.size() == TOPE_CANDIDATOS)
            log.warn("La lista de devoluciones llego al tope de {} candidatos: puede haber IMEI sin mirar", TOPE_CANDIDATOS);
        if (imeis.isEmpty()) return new ListaDevoluciones(List.of());
        Map<String, List<ReglaDevoluciones.Paso>> pasos = dao.pasos(imeis);
        List<BotDAO.Apunte> apuntes = dao.apuntes(imeis);
        Map<String, LocalDateTime> entradas = dao.entradasEnPedidos(imeis);
        Map<String, List<ReglaDevoluciones.FilaConImei>> filas = dao.filasConImei(imeis);
        Map<String, List<LocalDateTime>> vueltas = dao.vueltas(imeis);
        Map<String, MovilNsys> espejo = new HashMap<>();
        for (MovilNsys m : nsys.porImeis(imeis))
            espejo.merge(m.imeiSn(), m, (viejo, nuevo) -> viejo.estado() != EstadoMovil.DE_PEDIDO && nuevo.estado() == EstadoMovil.DE_PEDIDO ? nuevo : viejo);
        Map<String, UbicacionNsys> ubicaciones = new HashMap<>();
        for (UbicacionNsys u : nsys.cargarUbicaciones()) ubicaciones.put(u.location(), u);
        Map<String, List<Trabajo>> trabajos = new HashMap<>();
        for (NsysDAO.TrabajoFecha t : nsys.trabajosDe(imeis))
            trabajos.computeIfAbsent(t.imei(), k -> new ArrayList<>()).add(new Trabajo(t.idRep(), t.asignado()));

        Map<String, List<String>> porDestino = new TreeMap<>();
        for (String imei : imeis) {
            MovilNsys m = espejo.get(imei);
            Datos d = new Datos(imei, pasos.getOrDefault(imei, List.of()), salida(apuntes, imei), puestos(apuntes, imei),
                    m == null || m.estado() == null ? null : m.estado().name(), m == null ? null : m.location(),
                    m == null ? null : m.grade(), entradas.get(imei), trabajos.getOrDefault(imei, List.of()),
                    filas.getOrDefault(imei, List.of()), vueltas.getOrDefault(imei, List.of()));
            Veredicto v = ReglaDevoluciones.evaluar(d, ubicaciones);
            if (v.tipo() == ReglaDevoluciones.Tipo.DEVOLVER) porDestino.computeIfAbsent(v.destino(), k -> new ArrayList<>()).add(imei);
            else if (v.tipo() == ReglaDevoluciones.Tipo.SIN_DESTINO && !yaApuntado(apuntes, imei, v.salida().idFila()))
                dao.apuntar(v.salida().idFila(), ahora, imei, CambioImei.SIN_DESTINO, v.motivo());
        }
        List<ListaDevoluciones.Grupo> grupos = new ArrayList<>();
        int quedan = tope;
        for (Map.Entry<String, List<String>> e : porDestino.entrySet()) {
            if (quedan <= 0) break;
            List<ListaDevoluciones.ImeiADevolver> suyos = e.getValue().stream().sorted().limit(quedan)
                    .map(i -> new ListaDevoluciones.ImeiADevolver(i, ReglaDevoluciones.IMEI_CORREGIDO)).toList();
            grupos.add(new ListaDevoluciones.Grupo(e.getKey(), suyos));
            quedan -= suyos.size();
        }
        return new ListaDevoluciones(grupos);
    }

    /** Guarda lo que informa el bot (los repetidos no entran) y apunta en la fila de la salida cada devolucion nueva. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InformeMovimientos registrar(MovimientosBot cuerpo) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        int guardados = 0, repetidos = 0;
        for (MovimientoBot m : cuerpo.movimientos()) {
            if (dao.guardar(cuerpo.run(), m, ahora) == 0) {
                repetidos++;
                continue;
            }
            guardados++;
            if (ReglaDevoluciones.DEVUELTO.equals(m.accion())) apuntarDevolucion(m, ahora);
        }
        return new InformeMovimientos(guardados, repetidos);
    }

    private void apuntarDevolucion(MovimientoBot m, LocalDateTime ahora) {
        Optional<Salida> salida = dao.ultimaSalida(m.imei());
        if (salida.isEmpty()) return;
        int fila = salida.get().idFila();
        switch (m.resultado()) {
            case ReglaDevoluciones.OK -> dao.apuntar(fila, ahora, m.imei(), CambioImei.DEVUELTO, "de Pedidos realizados a " + m.destino()
                    + (ReglaDevoluciones.SALIDA_CANCELADO.equals(salida.get().motivo()) ? "; pedido cancelado por " : "; IMEI cambiado por ")
                    + salida.get().quien() + " el " + DIA_HORA.format(salida.get().fecha()));
            case ReglaDevoluciones.NO_APARECE -> dao.apuntar(fila, ahora, m.imei(), CambioImei.NO_DEVUELTO,
                    "no aparece en NSYS en Pedidos realizados");
            case ReglaDevoluciones.ERROR -> {
                if (esElTercerError(dao.ultimosDevueltos(m.imei())))
                    dao.apuntar(fila, ahora, m.imei(), CambioImei.NO_DEVUELTO, ReglaDevoluciones.ERRORES_SEGUIDOS + " errores seguidos");
            }
            default -> { }
        }
    }

    /** Los resultados, el mas reciente primero: los tres primeros son ERROR y el cuarto (si hay) no. */
    static boolean esElTercerError(List<String> ultimos) {
        int n = ReglaDevoluciones.ERRORES_SEGUIDOS;
        if (ultimos.size() < n) return false;
        for (int i = 0; i < n; i++) if (!ReglaDevoluciones.ERROR.equals(ultimos.get(i))) return false;
        return ultimos.size() == n || !ReglaDevoluciones.ERROR.equals(ultimos.get(n));
    }

    private static Salida salida(List<BotDAO.Apunte> apuntes, String imei) {
        BotDAO.Apunte ultima = null;
        for (BotDAO.Apunte a : apuntes) if (CambioImei.IMEI.equals(a.tipo()) && imei.equals(a.imeiAnterior())) ultima = a;
        return ultima == null ? null : new Salida(ultima.idFila(), ultima.fecha(), ultima.quien(), ReglaDevoluciones.SALIDA_IMEI);
    }

    private static List<LocalDateTime> puestos(List<BotDAO.Apunte> apuntes, String imei) {
        return apuntes.stream().filter(a -> CambioImei.IMEI.equals(a.tipo()) && imei.equals(a.imeiNuevo())).map(BotDAO.Apunte::fecha).toList();
    }

    private static boolean yaApuntado(List<BotDAO.Apunte> apuntes, String imei, int idFila) {
        return apuntes.stream().anyMatch(a -> CambioImei.SIN_DESTINO.equals(a.tipo()) && a.idFila() == idFila && imei.equals(a.imeiAnterior()));
    }
}
