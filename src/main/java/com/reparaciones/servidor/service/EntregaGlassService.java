package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import com.reparaciones.servidor.util.CatalogoPiezas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entrega del teléfono a glass con su «Abrir» automático (spec 2026-10-13): sellar la entrega, apuntar la acción «otro»
 * «Abrir» a quien abrió el teléfono y, al deshacer, borrarla. Cada operación va en una transacción: o todo o nada.
 * Las validaciones (tipo, existencia, dueño, firma) las hace el controlador antes de llamar.
 */
@Service
public class EntregaGlassService {

    /** Texto exacto de la acción automática. */
    public static final String TEXTO_ABRIR = "Abrir";

    private final ReparacionDAO dao;

    public EntregaGlassService(ReparacionDAO dao) {
        this.dao = dao;
    }

    /** «Entregar a X»: sella la entrega firmada por {@code idTecEntrega} y apunta el «Abrir» a su reparación normal. */
    @Transactional
    public ResultadoEntregaGlass entregar(String imei, int idTecEntrega, String idAsigNormal) {
        dao.entregarGlass(imei, idTecEntrega);
        return dao.getNormalAbierta(idAsigNormal)
                .map(normal -> registrarAbrir(imei, normal))
                .orElseGet(() -> ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
    }

    /** «Marcar que llegó»: sella la entrega firmada por el de glass y apunta el «Abrir» al dueño de la normal más antigua. */
    @Transactional
    public ResultadoEntregaGlass marcarLlegada(String imei, int idTecGlass) {
        dao.entregarGlass(imei, idTecGlass);
        return dao.getNormalAbiertaMasAntigua(imei)
                .map(normal -> registrarAbrir(imei, normal))
                .orElseGet(() -> ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
    }

    /** «Deshacer entrega» / «Deshacer llegada»: borra el «Abrir» que creó la entrega (si sigue existiendo) y la limpia. */
    @Transactional
    public ResultadoEntregaGlass deshacer(String imei) {
        List<String> abrir = dao.getAbrirDeEntrega(imei);
        for (String idRep : abrir) dao.eliminar(idRep);
        dao.deshacerEntregaGlass(imei);
        return ResultadoEntregaGlass.deshecho(abrir.isEmpty() ? null : abrir.get(0));
    }

    private ResultadoEntregaGlass registrarAbrir(String imei, ReparacionDAO.NormalAbierta normal) {
        if (dao.tieneAperturaManual(imei, normal.idTec(), normal.fechaAsig()))
            return ResultadoEntregaGlass.sinCrear(EstadoAbrir.YA_EXISTIA, normal.nombreTecnico());
        Integer idCom = idComOtro(imei);
        if (idCom == null)
            return ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, normal.nombreTecnico());
        FilaReparacion fila = new FilaReparacion();
        fila.idCom = idCom;
        fila.cantidad = 0;          // neutra de stock, como las acciones del formulario
        fila.reutilizado = false;
        fila.observacion = TEXTO_ABRIR;
        fila.prefijo = "otro";
        String idRep = dao.guardarFilaIndividual(List.of(fila), imei, normal.idTec(), normal.idRepAnterior(), normal.idRep());
        dao.marcarAbrir(imei, idRep);
        return ResultadoEntregaGlass.creado(idRep, normal.nombreTecnico());
    }

    /** Componente «otro» del modelo del teléfono (mismo criterio que el formulario); {@code null} si no hay. */
    private Integer idComOtro(String imei) {
        String modelo = dao.getModeloTelefono(imei);
        if (modelo == null || modelo.isBlank()) return null;
        return dao.getComponentesOtro().stream()
                .filter(c -> modelo.equals(CatalogoPiezas.modelo(c.tipo(), "otro")))
                .map(ReparacionDAO.ComponenteOtro::idCom)
                .findFirst()
                .orElse(null);
    }
}
