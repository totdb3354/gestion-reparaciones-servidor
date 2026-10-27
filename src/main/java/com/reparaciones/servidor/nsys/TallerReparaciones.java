package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** El SKU del taller desde las reparaciones (spec 0.9.21, pieza 2): al guardar una R… con chasis, tapa trasera o batería, el
 *  color y el grado del móvil cambian solos (Taller_cambio, ORIGEN REPARACION); al editarla o borrarla se deshace lo suyo si
 *  sigue mandando; y la banda del formulario hace la misma cuenta sin escribir. Solo las R…: glass (G…) y el resto no
 *  cambian nada. Lo llama GuardadoReparacion dentro de la transacción de la reparación; desde la 0.9.22 pieza 4, también
 *  PorPasarNsys tras cada importación, para la batería recordada de cada móvil (en su propia transacción). */
@Service
public class TallerReparaciones {

    /** Tope de filas de la previsualizacion (la abre cualquier tecnico). */
    static final int MAX_FILAS_PREVIA = 50;

    /** Una fila guardada de una reparación: la R… (null en la previsualización), su componente y si es reutilizada. */
    public record PiezaGuardada(String idRep, int idCom, boolean reutilizada) {}

    private final NsysDAO nsysDao;
    private final TallerDAO tallerDao;
    private final ColorEquivalenciaDAO colorDao;
    private final Clock clock;

    @Autowired
    public TallerReparaciones(NsysDAO nsysDao, TallerDAO tallerDao, ColorEquivalenciaDAO colorDao) {
        this(nsysDao, tallerDao, colorDao, Clock.system(ImportadorNsys.MADRID));
    }

    TallerReparaciones(NsysDAO nsysDao, TallerDAO tallerDao, ColorEquivalenciaDAO colorDao, Clock clock) {
        this.nsysDao = nsysDao;
        this.tallerDao = tallerDao;
        this.colorDao = colorDao;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- al guardar (spec §2.1)

    /** El candado del móvil ({@link TallerDAO#bloquear}). GuardadoReparacion lo pide lo primero de su transacción, antes de
     *  cualquier lectura normal (por qué, allí); aplicar y deshacer lo vuelven a pedir, y repetirlo no cuesta nada. false si
     *  NSYS no conoce el IMEI. */
    public boolean bloquear(String imei) {
        return tallerDao.bloquear(imei);
    }

    /** Un guardado: las R… de una misma llamada («Terminar asignación», «✓ Guardar fila» o una fila editada), en el orden de
     *  sus filas salvo el chasis, que va primero (0.9.21.1, ver EfectoTaller). Cada R… que cambia algo crea sus propios
     *  cambios y sus avisos. Un IMEI que NSYS no conoce no cambia nada. Un chasis que da letra a un móvil sin grado
     *  aplica su batería recordada (0.9.22 pieza 4) y la da por usada. */
    public void aplicar(String imei, List<PiezaGuardada> guardado, int idUsu) {
        List<PiezaTaller> piezas = piezas(guardado.stream().filter(p -> esReparacion(p.idRep())).toList());
        if (piezas.isEmpty() || !tallerDao.bloquear(imei)) return;
        Optional<MovilNsys> movil = masReciente(imei);
        if (movil.isEmpty()) return;
        MovilNsys m = movil.get();
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        List<BateriaRecordada> recordadas = tallerDao.bateriasRecordadas(List.of(m.imeiSn()));
        EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m), piezas, colorDeChasis(m),
                BateriaRecordada.ultima(recordadas).orElse(null));
        LocalDateTime ahora = ahora();
        for (EfectoTaller.Paso p : r.pasos()) {
            PiezaTaller pieza = p.pieza();
            if (!p.nuevos().isEmpty()) tallerDao.guardar(m.imeiSn(), p.nuevos(), pieza.idRep(), idUsu, pieza.texto(), ahora);
            if (p.colorSinDeducir())
                tallerDao.avisar(new AvisoTaller.Nuevo(m.imeiSn(), pieza.idRep(), AvisoTaller.Tipo.SIN_COLOR, null,
                        DatoTaller.COLOR, pieza.sku(), null, pieza.texto()), idUsu, ahora);
            if (p.gradoSinCalcular() != null)
                tallerDao.avisar(new AvisoTaller.Nuevo(m.imeiSn(), pieza.idRep(), AvisoTaller.Tipo.SIN_GRADO, null,
                        DatoTaller.GRADO, p.gradoSinCalcular(), null, pieza.texto()), idUsu, ahora);
        }
        // 0.9.22 pieza 4 (§5.2): si un chasis aplicó la batería recordada, esa y las anteriores del móvil quedan usadas.
        if (r.recordadaUsada())
            tallerDao.usarBaterias(recordadas.stream().map(BateriaRecordada::idAviso).toList(), r.gradoDespues(), ahora);
    }

    /** Lo que hizo la batería recordada de un móvil tras la importación: cuántos avisos dio por usados y si creó un cambio de grado
     *  (para la línea del registro del servidor). */
    public record BateriaUsada(int usadas, boolean cambio) {
        static final BateriaUsada NADA = new BateriaUsada(0, false);
    }

    /** Tras cada importación ACTUAL que acaba en OK (0.9.22 pieza 4 §5.2), las baterías recordadas de un móvil ({@code m}: su fila
     *  del espejo), en su transacción y con el candado del móvil tomado lo primero: lo vigente y las baterías de ese IMEI se releen
     *  dentro (lo que vio la lectura de todos pudo cambiar con un «Corregir…» o un chasis a la vez, y con «Corregir…» manda lo
     *  escrito). Si el grado que vale tiene letra, el cambio de grado que haga falta a nombre de la R… de la más reciente (ORIGEN
     *  REPARACION, motivo el de la pieza) y todas por usadas con el grado que queda; con un ignorado, solo por usadas; si sigue
     *  «como n/a» o ya no queda ninguna, nada. READ COMMITTED como «Revisado» y «Corregir…». Lo llama PorPasarNsys desde fuera
     *  (pasa por el proxy). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BateriaUsada usarBateriaRecordada(MovilNsys m) {
        if (!tallerDao.bloquear(m.imeiSn())) return BateriaUsada.NADA;
        List<BateriaRecordada> recordadas = tallerDao.bateriasRecordadas(List.of(m.imeiSn()));
        if (recordadas.isEmpty()) return BateriaUsada.NADA;
        String grado = LoQueVale.valor(tallerDao.vigentes(List.of(m.imeiSn())), DatoTaller.GRADO, m);
        GradoTaller.Grupo grupo = GradoTaller.grupo(grado);
        if (grupo == GradoTaller.Grupo.COMO_NA) return BateriaUsada.NADA;
        LocalDateTime ahora = ahora();
        String queda = grado;
        boolean cambio = false;
        if (grupo == GradoTaller.Grupo.CON_FORMA) {
            BateriaRecordada b = BateriaRecordada.ultima(recordadas).orElseThrow();
            Optional<TallerDAO.Nuevo> n = GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, b.nueva())
                    .flatMap(nuevo -> EfectoTaller.cambio(DatoTaller.GRADO, nuevo, grado, m));
            if (n.isPresent()) {
                tallerDao.guardar(m.imeiSn(), List.of(n.get()), b.idRep(), b.idUsu(), b.motivo(), ahora);
                queda = n.get().valor();
                cambio = true;
            }
        }
        int usadas = tallerDao.usarBaterias(recordadas.stream().map(BateriaRecordada::idAviso).toList(), queda, ahora);
        return new BateriaUsada(usadas, cambio);
    }

    // ---------------------------------------------------------------- editar y borrar (spec §2.3)

    /** La pieza que tiene ahora una R… (las demás reparaciones no cuentan). */
    public Optional<TallerDAO.PiezaDeReparacion> pieza(String idRep) {
        return esReparacion(idRep) ? tallerDao.piezaDeReparacion(idRep) : Optional.empty();
    }

    /** Tras «Guardar cambios» de una R…: si cambia la pieza (otro SKU, o nueva ↔ reutilizada), se deshace lo suyo y se
     *  aplica la nueva; si solo cambia la observación o la cantidad, nada (Ajuste 10). */
    public void editar(TallerDAO.PiezaDeReparacion antes, PiezaGuardada nueva, int idUsu) {
        if (mismaPieza(antes, nueva.idCom(), nueva.reutilizada())) return;
        deshacer(nueva.idRep(), AvisoTaller.Causa.EDITADA, idUsu);
        aplicar(antes.imei(), List.of(nueva), idUsu);
    }

    /** Al borrar una R… o al cambiar su pieza: lo suyo que sigue mandando se deshace (vuelve lo de antes o lo de NSYS) y lo
     *  que ya no manda solo se avisa. Todo queda en el rastro. Si entre el plan y el UPDATE el cambio dejó de ser vigente
     *  (el DAO devuelve 0), no se reactiva nada y solo se avisa. */
    public void deshacer(String idRep, AvisoTaller.Causa causa, int idUsu) {
        if (!esReparacion(idRep)) return;
        List<CambioTaller> suyos = tallerDao.deReparacion(idRep);
        if (suyos.isEmpty()) return;
        String imei = suyos.get(0).imeiSn();
        tallerDao.bloquear(imei);
        PlanDeshacer.Plan plan = planDeshacer(tallerDao.deImei(imei), idRep);
        if (plan.vacio()) return;
        Optional<MovilNsys> m = masReciente(imei);
        LocalDateTime ahora = ahora();
        for (PlanDeshacer.Deshecho d : plan.deshechos()) {
            CambioTaller c = d.cambio();
            if (tallerDao.deshacer(c.id(), ahora) != 1) {
                avisarSinDeshacer(imei, idRep, causa, c, idUsu, ahora);
                continue;
            }
            // 0.9.22 pieza 4 (D1): las baterias que se usaron con este grado (mismo instante) vuelven a pendientes.
            if (c.dato() == DatoTaller.GRADO) tallerDao.reabrirBaterias(imei, c.fecha());
            if (d.vuelve() != null) tallerDao.reactivar(d.vuelve().id());
            String vuelveA = d.vuelve() != null ? d.vuelve().valor() : m.map(c.dato()::deNsys).orElse(c.valorNsys());
            tallerDao.avisar(new AvisoTaller.Nuevo(imei, idRep, AvisoTaller.Tipo.DESHECHO, causa, c.dato(), c.valor(),
                    vuelveA, c.motivo()), idUsu, ahora);
        }
        for (CambioTaller c : plan.sinDeshacer()) avisarSinDeshacer(imei, idRep, causa, c, idUsu, ahora);
    }

    private void avisarSinDeshacer(String imei, String idRep, AvisoTaller.Causa causa, CambioTaller c, int idUsu,
                                   LocalDateTime ahora) {
        tallerDao.avisar(new AvisoTaller.Nuevo(imei, idRep, AvisoTaller.Tipo.SIN_DESHACER, causa, c.dato(), c.valor(), null,
                c.motivo()), idUsu, ahora);
    }

    // ---------------------------------------------------------------- la banda del formulario (spec §2.4)

    /** Lo que haría guardar estas filas, sin escribir nada: primero la R… que se edita (si su pieza cambia, se deshace lo
     *  suyo y se aplica la nueva, como en «Guardar cambios»; el formulario edita una a la vez) y después las filas nuevas,
     *  juntas, como un guardado. */
    public PreviaTaller previsualizar(PeticionPreviaTaller peticion) {
        if (peticion != null && peticion.filas() != null && peticion.filas().size() > MAX_FILAS_PREVIA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Demasiadas filas para previsualizar (maximo " + MAX_FILAS_PREVIA + ")");
        }
        String imei = peticion == null || peticion.imei() == null ? "" : peticion.imei().strip();
        Optional<MovilNsys> movil = imei.isEmpty() ? Optional.empty() : masReciente(imei);
        if (movil.isEmpty()) return PreviaTaller.FUERA_DE_NSYS;
        MovilNsys m = movil.get();
        List<FilaPreviaTaller> filas = peticion.filas() == null ? List.of()
                : peticion.filas().stream().filter(Objects::nonNull).toList();
        List<CambioTaller> todos = tallerDao.deImei(m.imeiSn());
        List<CambioTaller> vigentes = todos.stream().filter(c -> c.estado() == CambioTaller.Estado.VIGENTE).toList();
        ValoresTaller antes = new ValoresTaller(LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m));
        Function<String, Optional<String>> colorDe = colorDeChasis(m);
        BateriaRecordada recordada = BateriaRecordada.ultima(tallerDao.bateriasRecordadas(List.of(m.imeiSn()))).orElse(null);
        String color = antes.color();
        String grado = antes.grado();
        boolean sinColor = false;
        boolean sinGrado = false;
        Optional<FilaPreviaTaller> editada = filas.stream().filter(f -> f.idRep() != null).findFirst();
        Optional<TallerDAO.PiezaDeReparacion> actual = editada.flatMap(f -> pieza(f.idRep()));
        if (editada.isPresent() && actual.isPresent()
                && !mismaPieza(actual.get(), editada.get().idCom(), editada.get().reutilizada())) {
            FilaPreviaTaller f = editada.get();
            List<CambioTaller> trasDeshacer = planDeshacer(todos, f.idRep()).vigentes(vigentes);
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(trasDeshacer, DatoTaller.COLOR, m),
                    LoQueVale.valor(trasDeshacer, DatoTaller.GRADO, m),
                    piezas(List.of(new PiezaGuardada(f.idRep(), f.idCom(), f.reutilizada()))), colorDe, recordada);
            if (r.recordadaUsada()) recordada = null;
            color = r.colorDespues();
            grado = r.gradoDespues();
            sinColor = r.colorSinDeducir();
            sinGrado = r.gradoSinCalcular();
        }
        List<PiezaTaller> nuevas = piezas(filas.stream().filter(f -> f.idRep() == null)
                .map(f -> new PiezaGuardada(null, f.idCom(), f.reutilizada())).toList());
        if (!nuevas.isEmpty()) {
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, color, grado, nuevas, colorDe, recordada);
            color = r.colorDespues();
            grado = r.gradoDespues();
            sinColor |= r.colorSinDeducir();
            sinGrado |= r.gradoSinCalcular();
        }
        return new PreviaTaller(true, antes, new ValoresTaller(color, grado), sinColor, sinGrado);
    }

    // ---------------------------------------------------------------- utilidades

    /** La misma pieza: el mismo componente y la misma elección (nueva o reutilizada). */
    static boolean mismaPieza(TallerDAO.PiezaDeReparacion antes, int idCom, boolean reutilizada) {
        return antes.idCom() == idCom && antes.reutilizada() == reutilizada;
    }

    private static boolean esReparacion(String idRep) {
        return idRep != null && idRep.startsWith("R");
    }

    /** Las filas que cuentan, con su SKU, en su orden. */
    private List<PiezaTaller> piezas(List<PiezaGuardada> filas) {
        if (filas.isEmpty()) return List.of();
        Map<Integer, String> tipos = tallerDao.tiposDeComponentes(filas.stream().map(PiezaGuardada::idCom).toList());
        return filas.stream().flatMap(f -> {
            String sku = tipos.get(f.idCom());
            return PiezaTaller.Tipo.deSku(sku).map(t -> new PiezaTaller(f.idRep(), sku.strip(), t, !f.reutilizada())).stream();
        }).toList();
    }

    /** El plan de deshacer de una R…, sabiendo qué otras reparaciones con cambios siguen existiendo (Ajuste 3). */
    private PlanDeshacer.Plan planDeshacer(List<CambioTaller> delImei, String idRep) {
        Set<String> otras = new LinkedHashSet<>();
        for (CambioTaller c : delImei) if (c.idRep() != null && !c.idRep().equals(idRep)) otras.add(c.idRep());
        Set<String> vivas = otras.isEmpty() ? Set.of() : tallerDao.reparacionesQueExisten(otras);
        return PlanDeshacer.de(delImei, idRep, vivas::contains);
    }

    /** La fila más reciente del IMEI en el espejo (vale esa si el IMEI está en varios lotes). */
    private Optional<MovilNsys> masReciente(String imei) {
        return nsysDao.porImei(imei).stream().findFirst();
    }

    /** SKU → color del maestro, con el maestro (y el nombre en inglés de cada color, 0.9.22), el Goods más usado de cada color y
     *  Color_equivalencia leídos solo si hace falta (una batería no los necesita). */
    private Function<String, Optional<String>> colorDeChasis(MovilNsys m) {
        Supplier<ColorChasis> chasis = TallerNsys.perezoso(() -> {
            Maestro maestro = new Maestro(nsysDao.cargarProductos());
            List<String> colores = maestro.colores(m);
            Map<String, String> nombres = new HashMap<>();
            for (String c : colores) {
                String nombre = maestro.nombreColor(m, c);
                if (nombre != null) nombres.put(c, nombre);
            }
            return ColorChasis.de(colores, nsysDao.goodsMasUsados(m), nombres, m.capacidad(), alias());
        });
        return sku -> chasis.get().deducir(sku);
    }

    private Map<String, String> alias() {
        Map<String, String> alias = new HashMap<>();
        for (Map<String, String> fila : colorDao.getAll()) alias.put(fila.get("textoExterno"), fila.get("colorOficial"));
        return alias;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
