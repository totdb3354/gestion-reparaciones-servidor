package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.util.PrevisionPedido;
import com.reparaciones.servidor.util.PrevisionPedido.Resultado;
import com.reparaciones.servidor.util.PrevisionPedido.Tramos;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Rellena la previsión de pedidos del listado de Stock (spec 0.9.5 §3.3). Stock, mínimo, en camino y pendiente ya vienen
 *  resueltos al master del grupo compartido; el consumo se busca también por master. */
@Service
public class PrevisionPedidoService {

    private final ComponenteDAO componenteDao;
    private final ParametroDAO parametroDao;

    public PrevisionPedidoService(ComponenteDAO componenteDao, ParametroDAO parametroDao) {
        this.componenteDao = componenteDao;
        this.parametroDao = parametroDao;
    }

    public void rellenar(List<Componente> componentes, LocalDate hoy) {
        PrevisionPedido.Pesos pesos = parametroDao.getPesosPrevision();
        Map<Integer, Tramos> porMaster = PrevisionPedido.agrupar(
                componenteDao.getConsumoDiario(hoy.minusDays(PrevisionPedido.DIAS_VENTANA), hoy), hoy);
        for (Componente c : componentes) {
            if (!c.isActivo()) continue;
            int master = c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom();
            int pendiente = c.getPendiente() != null ? c.getPendiente() : 0;
            Resultado r = PrevisionPedido.calcular(porMaster.getOrDefault(master, Tramos.CERO), pesos,
                    c.getStockMinimo(), c.getStock(), c.getEnCamino() + pendiente);
            c.setPrevision(r.consumoDiario(), r.pedir60());
        }
    }
}
