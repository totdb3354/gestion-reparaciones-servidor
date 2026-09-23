package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** La transacción del guardado por lotes (spec 3b §4.2, D1): upsert de teléfonos y altas juntos; una asignación
 *  que ya existe (técnico + IMEI + categoría) se salta y se informa; cualquier excepción deshace el lote entero.
 *  Las altas de ReparacionDAO son @Transactional REQUIRED y se unen a esta. Urgente siempre false al crear. */
@Service
public class AsignacionLoteService {

    private final ReparacionDAO dao;
    private final TelefonoDAO telefonoDao;

    public AsignacionLoteService(ReparacionDAO dao, TelefonoDAO telefonoDao) {
        this.dao = dao;
        this.telefonoDao = telefonoDao;
    }

    @Transactional
    public Respuesta guardar(Peticion p, Integer idTecAsigna, int idUsu) {
        for (TelefonoDelLote t : p.telefonos())
            telefonoDao.insertar(t.imei(), t.modelo(), t.idCli(), t.clienteExplicito());
        List<Creada> creadas = new ArrayList<>();
        List<Conflicto> conflictos = new ArrayList<>();
        for (AsignacionDelLote a : p.asignaciones()) {
            if (dao.existeAsignacionParaTecnico(a.imei(), a.idTec(), a.categoria())) {
                conflictos.add(new Conflicto(a.imei(), a.idTec(), dao.getNombreTecnicoById(a.idTec()), a.categoria()));
                continue;
            }
            String comentario = (a.comentario() == null || a.comentario().isBlank()) ? null : a.comentario();
            String idRep = switch (a.categoria()) {
                case "G" -> dao.insertarAsignacionGlass(a.imei(), a.idTec(), comentario, false, idTecAsigna, idUsu);
                case "P" -> dao.insertarAsignacionPulido(a.imei(), a.idTec(), comentario, idTecAsigna, idUsu);
                default  -> dao.insertarAsignacion(a.imei(), a.idTec(), comentario, false, a.esChasis(), idTecAsigna, idUsu);
            };
            creadas.add(new Creada(idRep, a.imei(), a.idTec(), a.categoria()));
        }
        return new Respuesta(creadas, conflictos);
    }
}
