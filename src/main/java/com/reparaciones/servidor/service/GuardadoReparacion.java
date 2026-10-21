package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerDAO;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Las escrituras del formulario de reparación con el SKU del taller (spec 0.9.21, pieza 2, §2.1 y §2.3): la reparación y lo
 *  que cambia en el móvil, en la misma transacción (las de ReparacionDAO son @Transactional REQUIRED y se unen a esta): si
 *  algo falla, no queda nada. Las usa ReparacionController en «Terminar asignación», «✓ Guardar fila», «Guardar cambios» y
 *  el borrado de una reparación. El «Abrir» automático de glass (EntregaGlassService) sigue yendo al DAO: es una acción
 *  «otro», que no cambia el móvil.
 *  <p>El candado del móvil (TallerDAO.bloquear, un FOR UPDATE sobre Nsys_movil) es el primero de cada transacción, antes de
 *  cualquier lectura normal. La base va en REPEATABLE READ y en InnoDB la primera lectura no bloqueante fija la foto de la
 *  transacción, que un FOR UPDATE posterior no renueva: si otra del mismo móvil (otra reparación, una corrección a mano o el
 *  importador) tenía el candado, esta espera a su commit, pero con la foto ya fijada no vería lo que dejó (por ejemplo, su
 *  VIGENTE nuevo) y quedarían dos VIGENTE del mismo dato. TallerReparaciones vuelve a pedir el candado: en la misma
 *  transacción no cuesta nada. Un IMEI que NSYS no conoce no tiene candado y se guarda igual.
 *  <p>Las cuatro escrituras cogen los candados en el mismo orden: primero el móvil y después Reparacion (las altas, el rango
 *  del día de nextId o la clave ajena ID_REP_ANTERIOR; editar y borrar, la fila de la R…). Con dos órdenes, una edición y un
 *  alta del mismo móvil podían esperarse la una a la otra (InnoDB aborta una con un deadlock: un 500), y una edición que
 *  esperaba al móvil con su R… ya cogida hacía esperar detrás a todas las altas del día. Por eso, al editar o borrar, el IMEI
 *  se lee antes, fuera de la transacción (ReparacionDAO.imeiDe, por otra conexión: sin candados ni foto); después se coge el
 *  móvil, luego la fila de la R… (bloquearReparacion) y se comprueba que sigue siendo de ese móvil. */
@Service
public class GuardadoReparacion {

    private final ReparacionDAO dao;
    private final TallerReparaciones taller;

    public GuardadoReparacion(ReparacionDAO dao, TallerReparaciones taller) {
        this.dao = dao;
        this.taller = taller;
    }

    /** «Terminar asignación» y las filas nuevas de «Guardar cambios»: un guardado, en el orden de sus filas. */
    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec, String idRepAnterior,
                                 String idAsignacion, String categoria, int idUsu) {
        taller.bloquear(imei);
        List<String> creadas = dao.insertarCompleta(filas, imei, idTec, idRepAnterior, idAsignacion, categoria);
        taller.aplicar(imei, guardado(filas, creadas), idUsu);
    }

    /** «✓ Guardar fila» (la web manda una fila con uso; el DAO devuelve su R…). */
    @Transactional
    public String guardarFilaIndividual(List<FilaReparacion> filas, String imei, int idTec, String idRepAnterior,
                                        String idAsignacion, int idUsu) {
        taller.bloquear(imei);
        String idRep = dao.guardarFilaIndividual(filas, imei, idTec, idRepAnterior, idAsignacion);
        List<FilaReparacion> conUso = filas.stream().filter(f -> !f.esSolicitud).toList();
        if (!conUso.isEmpty()) {
            FilaReparacion f = conUso.get(conUso.size() - 1);
            taller.aplicar(imei, List.of(new TallerReparaciones.PiezaGuardada(idRep, f.idCom, f.reutilizado)), idUsu);
        }
        return idRep;
    }

    /** «Guardar cambios» de una reparación: la pieza de antes se lee antes de editar; si cambia, el taller deshace lo suyo y
     *  aplica la nueva. */
    @Transactional
    public void editarReparacion(String idRep, int idComNuevo, boolean esReutilizadoNuevo, String observacionNueva,
                                 int nNuevas, LocalDateTime updatedAt, int idUsu) {
        // El error que da dao.editarReparacion con una R… que no existe: el EmptyResultDataAccessException de su queryForObject
        boolean existe = bloquear(idRep, () -> new EmptyResultDataAccessException("Reparación no encontrada: " + idRep, 1));
        Optional<TallerDAO.PiezaDeReparacion> antes = existe ? taller.pieza(idRep) : Optional.empty();
        dao.editarReparacion(idRep, idComNuevo, esReutilizadoNuevo, observacionNueva, nNuevas, updatedAt);
        antes.ifPresent(a -> taller.editar(a, new TallerReparaciones.PiezaGuardada(idRep, idComNuevo, esReutilizadoNuevo), idUsu));
    }

    /** Borrar una reparación: primero se deshace lo suyo en el móvil. */
    @Transactional
    public void eliminar(String idRep, int idUsu) {
        if (bloquear(idRep, () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep)))
            taller.deshacer(idRep, AvisoTaller.Causa.BORRADA, idUsu);
        dao.eliminar(idRep);
    }

    /** Los candados de una R… al editarla o borrarla, en el orden de las altas: su IMEI leído antes, fuera de la transacción;
     *  el móvil; y la fila de la R…, que tiene que seguir siendo de ese móvil.
     *  @return true si la R… existe y es del móvil bloqueado; false si no existe (con su hueco ya bloqueado, nadie la crea
     *          hasta el commit): sin taller, y el DAO da después el error de siempre
     *  @throws RuntimeException {@code comoSiNoExistiera} si la R… ya no es la que se leyó (se borró y su ID lo cogió otra de
     *          otro móvil, o apareció, entre la lectura y el candado): no se toca nada, porque coger ahora el candado de ese
     *          móvil sería al revés que las altas */
    private boolean bloquear(String idRep, Supplier<RuntimeException> comoSiNoExistiera) {
        Optional<String> imei = dao.imeiDe(idRep);
        imei.ifPresent(taller::bloquear);
        Optional<String> conLaFila = dao.bloquearReparacion(idRep);
        if (conLaFila.isEmpty()) return false;
        if (!conLaFila.equals(imei)) throw comoSiNoExistiera.get();
        return true;
    }

    /** Cada reparación creada con su fila: el DAO crea una por fila con uso real, en su orden. Si no hay tantas como filas
     *  con uso, no se emparejan a ciegas: falla (y la transacción no deja nada). */
    static List<TallerReparaciones.PiezaGuardada> guardado(List<FilaReparacion> filas, List<String> creadas) {
        List<FilaReparacion> conUso = filas.stream().filter(f -> !f.esSolicitud).toList();
        if (conUso.size() != creadas.size())
            throw new IllegalStateException("No cuadran las filas con uso (" + conUso.size() + ") y las reparaciones creadas ("
                    + creadas.size() + ")");
        List<TallerReparaciones.PiezaGuardada> guardado = new ArrayList<>();
        for (int i = 0; i < conUso.size(); i++)
            guardado.add(new TallerReparaciones.PiezaGuardada(creadas.get(i), conUso.get(i).idCom, conUso.get(i).reutilizado));
        return guardado;
    }
}
