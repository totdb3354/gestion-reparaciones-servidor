package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.dao.MovimientoTelefonoDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.ImportacionRequest;
import com.reparaciones.servidor.model.VerificacionImei;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Importación de lotes en bloque. Re-verifica los conflictos en servidor (otro
 * usuario puede haber importado entre la vista previa y el confirmar): los IMEIs
 * ACTIVOS (estado RECIBIDO/EN_REVISION/BLOQUEADO o con trabajo abierto) se omiten
 * y se devuelven en la respuesta; el resto se inserta o re-entra (ESTADO=RECIBIDO).
 */
@Service
public class LoteImportService {

    private static final java.util.Set<String> ESTADOS_ACTIVOS =
            java.util.Set.of("RECIBIDO", "EN_REVISION", "BLOQUEADO");

    private final LoteDAO loteDao;
    private final TelefonoDAO telefonoDao;
    private final MovimientoTelefonoDAO movimientoDao;
    private final LogDAO logDao;

    public LoteImportService(LoteDAO loteDao, TelefonoDAO telefonoDao,
                             MovimientoTelefonoDAO movimientoDao, LogDAO logDao) {
        this.loteDao = loteDao; this.telefonoDao = telefonoDao;
        this.movimientoDao = movimientoDao; this.logDao = logDao;
    }

    @Transactional
    public ImportacionRequest.Respuesta importar(ImportacionRequest req, int idUsu) {
        List<String> conflictos = new ArrayList<>();
        int lotes = 0, telefonos = 0;
        for (ImportacionRequest.LoteImport lote : req.lotes()) {
            if (lote.telefonos() == null || lote.telefonos().isEmpty()) continue;
            Map<String, VerificacionImei> existentes = telefonoDao.verificar(
                    lote.telefonos().stream().map(ImportacionRequest.TelefonoImport::imei).toList())
                .stream().collect(Collectors.toMap(VerificacionImei::getImei, v -> v));
            int idLote = loteDao.obtenerOCrear(lote.batchNumber(), lote.idProv(), lote.nota());
            lotes++;
            int nLote = 0;
            for (ImportacionRequest.TelefonoImport t : lote.telefonos()) {
                VerificacionImei v = existentes.get(t.imei());
                boolean activo = v != null && (v.getTrabajosAbiertos() > 0
                        || (v.getEstado() != null && ESTADOS_ACTIVOS.contains(v.getEstado())));
                if (activo) { conflictos.add(t.imei()); continue; }
                boolean reentrada = v != null;
                telefonoDao.upsertImportacion(t.imei(), t.modelo(), idLote, t.storageGb(), t.color(),
                        t.gradoProveedor(), t.precioCompra(), t.divisa(), t.precioCompraEur());
                movimientoDao.insertar(t.imei(), null, "ALMACEN", idUsu,
                        reentrada ? "Re-entrada por importación" : "Importación",
                        "LOTE:" + lote.batchNumber());
                nLote++; telefonos++;
            }
            logDao.insertar(idUsu, "IMPORTAR_LOTE",
                    "BATCH: " + lote.batchNumber() + ", TELEFONOS: " + nLote);
        }
        return new ImportacionRequest.Respuesta(lotes, telefonos, conflictos);
    }
}
