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
    private final MovimientoDAO movimientoDao;

    public RevisionDAO(JdbcTemplate jdbc, MovimientoDAO movimientoDao) {
        this.jdbc = jdbc;
        this.movimientoDao = movimientoDao;
    }

    /** Resultado del escaneo "a revisar" (tabla de reglas spec F2b §4). */
    public enum ResultadoARevisar { PASADO, PASADO_ESTABA_OK, YA_ESTABA, EN_REPARACION, BLOQUEADO, FUERA, HISTORICO, NO_EXISTE }

    /**
     * Procesa UN IMEI del escaneo masivo: clasifica según su estado y, si procede,
     * lo pasa a EN_REVISION creando la fila de revisión de la pasada nueva.
     * Transaccional por IMEI: un fallo no tumba el resto del lote escaneado.
     */
    @Transactional
    public ResultadoARevisar pasarARevisar(String imei, int idUsu) {
        List<Object[]> filas = jdbc.query(
                "SELECT ESTADO, ID_CLI FROM Telefono WHERE IMEI = ?",
                (rs, row) -> new Object[]{ rs.getString("ESTADO"), (Integer) rs.getObject("ID_CLI") },
                imei);
        if (filas.isEmpty()) return ResultadoARevisar.NO_EXISTE;
        Object[] fila = filas.get(0);
        String estado = (String) fila[0];
        Integer idCli = (Integer) fila[1];
        if (estado == null) return ResultadoARevisar.HISTORICO;   // fuera del ciclo (decisión 15)
        Integer abiertos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
        if (abiertos != null && abiertos > 0) return ResultadoARevisar.EN_REPARACION;
        return switch (estado) {
            case "EN_REVISION" -> ResultadoARevisar.YA_ESTABA;
            case "BLOQUEADO"   -> ResultadoARevisar.BLOQUEADO;
            case "ENVIADO", "DESGUACE" -> ResultadoARevisar.FUERA;
            case "RECIBIDO", "OK" -> {
                jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ?", imei);
                jdbc.update("INSERT INTO Revision (IMEI, FECHA_CREACION) VALUES (?, NOW())", imei);
                movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe(estado, idCli), "PARA_REVISAR", idUsu, null, null);
                yield "OK".equals(estado) ? ResultadoARevisar.PASADO_ESTABA_OK : ResultadoARevisar.PASADO;
            }
            default -> ResultadoARevisar.FUERA;
        };
    }

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

    /** Bloqueo automático al guardar la funcional con "bloqueo operador". @return true si cambió el estado. */
    public boolean bloquearPorRevision(String imei, int idUsu) {
        boolean cambio = jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", imei) > 0;
        if (cambio) {
            movimientoDao.registrar(imei, "PARA_REVISAR", "BLOQUEO", idUsu,
                    "Bloqueo de operador detectado en revisión", null);
        }
        return cambio;
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

    /** OK humano: exige revisión vigente completa, batería ≥ 85 y sin trabajos abiertos (veto duro en servidor). */
    @Transactional
    public void marcarOk(String imei, int idUsu) {
        Revision v = getVigente(imei);
        if (v == null || v.getEstFecha() == null || v.getFunFecha() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revisión incompleta: faltan partes por guardar");
        if (v.getFunBateriaPct() == null || v.getFunBateriaPct() < 85)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batería < 85: reparación obligatoria antes del OK");
        if (v.isFunBloqueoOp())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bloqueo de operador marcado en la revisión");
        Integer abiertos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
        if (abiertos != null && abiertos > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tiene trabajos abiertos");
        transicion(imei, "UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'");
        Integer idCli = primeraFila(jdbc.query("SELECT ID_CLI FROM Telefono WHERE IMEI = ?",
                (rs, row) -> (Integer) rs.getObject("ID_CLI"), imei));
        movimientoDao.registrar(imei, "PARA_REVISAR", MovimientoDAO.ubicacionDe("OK", idCli), idUsu, null, null);
    }

    public void bloquear(String imei, int idUsu, String motivo) {
        transicion(imei, "UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'");
        movimientoDao.registrar(imei, "PARA_REVISAR", "BLOQUEO", idUsu, motivo, null);
    }

    /** Desbloquear devuelve a EN_REVISION; la derivación decide el resto (§2.1 spec canónica). */
    public void desbloquear(String imei, int idUsu) {
        transicion(imei, "UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'");
        movimientoDao.registrar(imei, "BLOQUEO", "PARA_REVISAR", idUsu, null, null);
    }

    public void desguace(String imei, int idUsu, String motivo) {
        String estadoPrevio = primeraFila(jdbc.query("SELECT ESTADO FROM Telefono WHERE IMEI = ?",
                (rs, row) -> rs.getString("ESTADO"), imei));
        transicion(imei, "UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')");
        movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe(estadoPrevio, null), "DESGUACE", idUsu, motivo, null);
    }

    private void transicion(String imei, String sql) {
        if (jdbc.update(sql, imei) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El estado del teléfono cambió: recarga y reintenta");
    }

    /** Primera fila de una consulta o {@code null} si no hay filas; a diferencia de
     *  {@code Stream.findFirst()}, tolera que la propia fila encontrada sea {@code null}
     *  (p.ej. ID_CLI sin asignar) sin lanzar NullPointerException (Optional no admite null). */
    private static <T> T primeraFila(List<T> filas) {
        return filas.isEmpty() ? null : filas.get(0);
    }
}
