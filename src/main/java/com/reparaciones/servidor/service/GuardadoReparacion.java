package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerDAO;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Las escrituras del formulario de reparación con el SKU del taller (spec 0.9.21, pieza 2, §2.1 y §2.3): la reparación y lo
 *  que cambia en el móvil, en la misma transacción (las de ReparacionDAO son @Transactional REQUIRED y se unen a esta): si
 *  algo falla, no queda nada. Las usa ReparacionController en «Terminar asignación», «✓ Guardar fila», «Guardar cambios» y
 *  el borrado de una reparación. El «Abrir» automático de glass (EntregaGlassService) sigue yendo al DAO: es una acción
 *  «otro», que no cambia el móvil.
 *  <p>El candado del móvil (TallerDAO.bloquear, un FOR UPDATE sobre Nsys_movil) es lo primero de cada transacción, antes de
 *  cualquier lectura normal. La base va en REPEATABLE READ y en InnoDB la primera lectura no bloqueante fija la foto de la
 *  transacción, que un FOR UPDATE posterior no renueva: si otra del mismo móvil (otra reparación, una corrección a mano o el
 *  importador) tenía el candado, esta espera a su commit, pero con la foto ya fijada no vería lo que dejó (por ejemplo, su
 *  VIGENTE nuevo) y quedarían dos VIGENTE del mismo dato. Al editar o borrar, el IMEI sale antes de la propia R… con una
 *  lectura bloqueante, que no fija la foto. TallerReparaciones vuelve a pedir el candado: en la misma transacción no cuesta
 *  nada. Un IMEI que NSYS no conoce no tiene candado y se guarda igual. */
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
        bloquearMovilDe(idRep);
        Optional<TallerDAO.PiezaDeReparacion> antes = taller.pieza(idRep);
        dao.editarReparacion(idRep, idComNuevo, esReutilizadoNuevo, observacionNueva, nNuevas, updatedAt);
        antes.ifPresent(a -> taller.editar(a, new TallerReparaciones.PiezaGuardada(idRep, idComNuevo, esReutilizadoNuevo), idUsu));
    }

    /** Borrar una reparación: primero se deshace lo suyo en el móvil. */
    @Transactional
    public void eliminar(String idRep, int idUsu) {
        bloquearMovilDe(idRep);
        taller.deshacer(idRep, AvisoTaller.Causa.BORRADA, idUsu);
        dao.eliminar(idRep);
    }

    /** El candado del móvil de una R… que ya existe: su IMEI con la fila bloqueada y después el móvil. Si no existe, nada:
     *  el DAO da después el error de siempre. */
    private void bloquearMovilDe(String idRep) {
        dao.bloquearReparacion(idRep).ifPresent(taller::bloquear);
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
