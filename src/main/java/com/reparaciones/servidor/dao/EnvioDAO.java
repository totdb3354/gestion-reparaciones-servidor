package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Remesas de salida (F2c). enviarLote = UNA transacción para toda la remesa
 * (decisión de plan nº1): resultados por IMEI sin abortar por rechazados; el
 * Envio se crea perezosamente al primer éxito — nunca queda vacío (lección F2a).
 */
@Repository
public class EnvioDAO {

    public record ItemEnvio(String imei, String resultado, String estado) {}
    public record ResultadoLote(Integer idEnvio, List<ItemEnvio> items) {}

    private final JdbcTemplate jdbc;
    private final MovimientoDAO movimientoDao;

    public EnvioDAO(JdbcTemplate jdbc, MovimientoDAO movimientoDao) {
        this.jdbc = jdbc;
        this.movimientoDao = movimientoDao;
    }

    @Transactional
    public ResultadoLote enviarLote(Integer idCli, String destinoTexto, String referencia,
                                    List<String> imeis, int idUsu) {
        Integer idEnvio = null;
        List<ItemEnvio> items = new ArrayList<>();
        for (String imei : new java.util.LinkedHashSet<>(imeis)) {
            List<Object[]> fila = jdbc.query("SELECT ESTADO, ID_CLI FROM Telefono WHERE IMEI = ?",
                    (rs, row) -> new Object[]{ rs.getString("ESTADO"), (Integer) rs.getObject("ID_CLI") }, imei);
            if (fila.isEmpty()) { items.add(new ItemEnvio(imei, "NO_EXISTE", null)); continue; }
            String estado = (String) fila.get(0)[0];
            Integer idCliTel = (Integer) fila.get(0)[1];
            if (estado == null) { items.add(new ItemEnvio(imei, "HISTORICO", null)); continue; }
            int flip = jdbc.update("UPDATE Telefono SET ESTADO = 'ENVIADO', ES_DEVOLUCION = 0 WHERE IMEI = ? AND ESTADO = 'OK'", imei);
            if (flip == 0) { items.add(new ItemEnvio(imei, "NO_OK", estado)); continue; }
            if (idEnvio == null) {
                jdbc.update("INSERT INTO Envio (FECHA, ID_CLI, DESTINO_TEXTO, REFERENCIA, ID_USU) VALUES (NOW(), ?, ?, ?, ?)",
                        idCli, destinoTexto, referencia, idUsu);
                idEnvio = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
            }
            jdbc.update("INSERT INTO Envio_Telefono (ID_ENVIO, IMEI) VALUES (?, ?)", idEnvio, imei);
            movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe("OK", idCliTel), "ENVIADO", idUsu, null, "ENVIO " + idEnvio);
            items.add(new ItemEnvio(imei, "ENVIADO", null));
        }
        return new ResultadoLote(idEnvio, items);
    }
}
