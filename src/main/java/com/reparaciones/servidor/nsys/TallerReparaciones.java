package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** El SKU del taller desde las reparaciones (spec 0.9.21, pieza 2): al guardar una R… con chasis, tapa trasera o batería, el
 *  color y el grado del móvil cambian solos (Taller_cambio, ORIGEN REPARACION); al editarla o borrarla se deshace lo suyo si
 *  sigue mandando; y la banda del formulario hace la misma cuenta sin escribir. Solo las R…: glass (G…) y el resto no
 *  cambian nada. Lo llama GuardadoReparacion dentro de la transacción de la reparación. */
@Service
public class TallerReparaciones {

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

    /** Un guardado: las R… de una misma llamada («Terminar asignación», «✓ Guardar fila» o una fila editada), en el orden de
     *  sus filas. Cada R… que cambia algo crea sus propios cambios y sus avisos. Un IMEI que NSYS no conoce no cambia nada. */
    public void aplicar(String imei, List<PiezaGuardada> guardado, int idUsu) {
        List<PiezaTaller> piezas = piezas(guardado.stream().filter(p -> esReparacion(p.idRep())).toList());
        if (piezas.isEmpty() || !tallerDao.bloquear(imei)) return;
        Optional<MovilNsys> movil = masReciente(imei);
        if (movil.isEmpty()) return;
        MovilNsys m = movil.get();
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m), piezas, colorDeChasis(m));
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
        String imei = peticion == null || peticion.imei() == null ? "" : peticion.imei().strip();
        Optional<MovilNsys> movil = imei.isEmpty() ? Optional.empty() : masReciente(imei);
        if (movil.isEmpty()) return PreviaTaller.FUERA_DE_NSYS;
        MovilNsys m = movil.get();
        List<FilaPreviaTaller> filas = peticion.filas() == null ? List.of() : peticion.filas();
        List<CambioTaller> todos = tallerDao.deImei(m.imeiSn());
        List<CambioTaller> vigentes = todos.stream().filter(c -> c.estado() == CambioTaller.Estado.VIGENTE).toList();
        ValoresTaller antes = new ValoresTaller(LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m));
        Function<String, Optional<String>> colorDe = colorDeChasis(m);
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
                    piezas(List.of(new PiezaGuardada(f.idRep(), f.idCom(), f.reutilizada()))), colorDe);
            color = r.colorDespues();
            grado = r.gradoDespues();
            sinColor = r.colorSinDeducir();
            sinGrado = r.gradoSinCalcular();
        }
        List<PiezaTaller> nuevas = piezas(filas.stream().filter(f -> f.idRep() == null)
                .map(f -> new PiezaGuardada(null, f.idCom(), f.reutilizada())).toList());
        if (!nuevas.isEmpty()) {
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, color, grado, nuevas, colorDe);
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

    /** SKU → color del maestro, con el maestro, el Goods más usado de cada color y Color_equivalencia leídos solo si hace
     *  falta (una batería no los necesita). */
    private Function<String, Optional<String>> colorDeChasis(MovilNsys m) {
        Supplier<ColorChasis> chasis = TallerNsys.perezoso(() -> ColorChasis.de(new Maestro(nsysDao.cargarProductos()).colores(m),
                nsysDao.goodsMasUsados(m), m.capacidad(), alias()));
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
