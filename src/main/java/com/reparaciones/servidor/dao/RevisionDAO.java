package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import com.reparaciones.servidor.model.RevisionFuncional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Revisiones de teléfono (F2b): 1:N con Telefono, una fila por pasada;
 * la vigente = MAX(ID_REVISION) por IMEI. Parte guardada ≡ su *_FECHA IS NOT NULL.
 */
@Repository
public class RevisionDAO {

    private final JdbcTemplate jdbc;

    public RevisionDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** ID de la revisión vigente, o null si el teléfono nunca pasó por revisión. */
    private Integer idVigente(String imei) {
        return jdbc.queryForObject("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?", Integer.class, imei);
    }

    /** Guarda ediciones de partes solo con el teléfono EN_REVISION (la ficha es solo-lectura fuera). */
    private int vigenteEditable(String imei) {
        Integer id = idVigente(imei);
        if (id == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono no tiene revisión abierta");
        String estado = jdbc.queryForObject("SELECT ESTADO FROM Telefono WHERE IMEI = ?", String.class, imei);
        if (!"EN_REVISION".equals(estado))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono no está en revisión");
        return id;
    }

    @Transactional
    public void guardarEstetica(String imei, String grado, String pant, int idUsu) {
        int id = vigenteEditable(imei);
        jdbc.update("UPDATE Revision SET EST_GRADO = ?, EST_PANT = ?, EST_ID_USU = ?, EST_FECHA = NOW() WHERE ID_REVISION = ?",
                grado, pant, idUsu, id);
        // Espejo: el inventario sigue enseñando la verdad vigente en Telefono.GRADO_PROPIO
        jdbc.update("UPDATE Telefono SET GRADO_PROPIO = ? WHERE IMEI = ?", grado, imei);
    }

    @Transactional
    public void guardarFuncional(String imei, RevisionFuncional f, int idUsu) {
        int id = vigenteEditable(imei);
        jdbc.update("UPDATE Revision SET FUN_BATERIA_PCT = ?, FUN_PANT_TACTIL = ?, FUN_PANT_QUEMADA = ?," +
                " FUN_PANT_MAL = ?, FUN_CAM_MANCHA = ?, FUN_CAM_LENTE = ?, FUN_ALT_SUP = ?, FUN_ALT_INF = ?," +
                " FUN_MIC = ?, FUN_FACE_ID = ?, FUN_MS = ?, FUN_MS_TEXTO = ?, FUN_BLOQUEO_OP = ?," +
                " FUN_OBSERVACION = ?, FUN_ID_USU = ?, FUN_FECHA = NOW() WHERE ID_REVISION = ?",
                f.bateriaPct(), f.pantTactil(), f.pantQuemada(), f.pantMal(), f.camMancha(), f.camLente(),
                f.altSup(), f.altInf(), f.mic(), f.faceId(), f.ms(), f.msTexto(), f.bloqueoOp(),
                f.observacion(), idUsu, id);
    }

    /** Revisión vigente con nombres de usuario por parte, o null si nunca hubo. */
    public Revision getVigente(String imei) {
        List<Revision> filas = jdbc.query(
                "SELECT r.*, ue.NOMBRE_USUARIO AS EST_USUARIO, uf.NOMBRE_USUARIO AS FUN_USUARIO" +
                " FROM Revision r" +
                " LEFT JOIN Usuario ue ON ue.ID_USU = r.EST_ID_USU" +
                " LEFT JOIN Usuario uf ON uf.ID_USU = r.FUN_ID_USU" +
                " WHERE r.IMEI = ? ORDER BY r.ID_REVISION DESC LIMIT 1",
                (rs, row) -> {
                    Revision r = new Revision();
                    r.setIdRevision(rs.getInt("ID_REVISION"));
                    r.setImei(rs.getString("IMEI"));
                    r.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
                    r.setEstGrado(rs.getString("EST_GRADO"));
                    r.setEstPant(rs.getString("EST_PANT"));
                    r.setEstIdUsu((Integer) rs.getObject("EST_ID_USU"));
                    r.setEstUsuario(rs.getString("EST_USUARIO"));
                    r.setEstFecha(rs.getTimestamp("EST_FECHA") == null ? null : rs.getTimestamp("EST_FECHA").toLocalDateTime());
                    r.setFunBateriaPct((Integer) rs.getObject("FUN_BATERIA_PCT"));
                    r.setFunPantTactil(rs.getBoolean("FUN_PANT_TACTIL"));
                    r.setFunPantQuemada(rs.getBoolean("FUN_PANT_QUEMADA"));
                    r.setFunPantMal(rs.getBoolean("FUN_PANT_MAL"));
                    r.setFunCamMancha(rs.getBoolean("FUN_CAM_MANCHA"));
                    r.setFunCamLente(rs.getBoolean("FUN_CAM_LENTE"));
                    r.setFunAltSup(rs.getBoolean("FUN_ALT_SUP"));
                    r.setFunAltInf(rs.getBoolean("FUN_ALT_INF"));
                    r.setFunMic(rs.getBoolean("FUN_MIC"));
                    r.setFunFaceId(rs.getBoolean("FUN_FACE_ID"));
                    r.setFunMs(rs.getBoolean("FUN_MS"));
                    r.setFunMsTexto(rs.getString("FUN_MS_TEXTO"));
                    r.setFunBloqueoOp(rs.getBoolean("FUN_BLOQUEO_OP"));
                    r.setFunObservacion(rs.getString("FUN_OBSERVACION"));
                    r.setFunIdUsu((Integer) rs.getObject("FUN_ID_USU"));
                    r.setFunUsuario(rs.getString("FUN_USUARIO"));
                    r.setFunFecha(rs.getTimestamp("FUN_FECHA") == null ? null : rs.getTimestamp("FUN_FECHA").toLocalDateTime());
                    return r;
                }, imei);
        return filas.isEmpty() ? null : filas.get(0);
    }
}
