package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** El pedido abierto de cada IMEI, para la línea de las listas del taller (spec pieza 2 §9.3). Con la lectura apagada no pregunta
 *  nada (los tests, y la vuelta atrás). */
@Service
public class UnidadesDeImei {

    public record PedidoDeImei(String numero, String canal, String sku, boolean repetido) {}

    private final PedidosDAO dao;
    private final boolean activa;

    @Autowired
    public UnidadesDeImei(PedidosDAO dao, @Value("${pedidos.lectura.activa:false}") boolean activa) {
        this.dao = dao;
        this.activa = activa;
    }

    /** Por {@link NsysDAO#claveCruce}: el pedido más antiguo de cada IMEI (la consulta ya viene por fecha) y si está en más de uno. */
    public Map<String, PedidoDeImei> abiertasPorImei(Collection<String> imeis) {
        if (!activa || imeis == null || imeis.isEmpty()) return Map.of();
        Map<String, PedidoDeImei> porImei = new HashMap<>();
        for (PedidosDAO.UnidadDeImei u : dao.unidadesAbiertasDeImeis(imeis)) {
            String clave = NsysDAO.claveCruce(u.imei());
            PedidoDeImei previo = porImei.get(clave);
            if (previo == null) porImei.put(clave, new PedidoDeImei(u.numero(), u.canal(), u.sku(), false));
            else porImei.put(clave, new PedidoDeImei(previo.numero(), previo.canal(), previo.sku(), true));
        }
        return porImei;
    }
}
