package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** La descripción completa del móvil en las listas del taller (spec 0.9.21 «Descripción completa donde pone «Modelo»» §2.3):
 *  a cada fila le pone la capacidad del espejo de NSYS y el color que vale (lo del taller vigente o, si no hay, el de NSYS), de
 *  la fila más reciente de su IMEI. El nombre del modelo sigue saliendo de Telefono.MODELO. Un IMEI que no está en el espejo
 *  (un cliente externo) se queda sin los dos. El cruce va en Java por {@link NsysDAO#claveCruce}, nunca con un JOIN (las tablas
 *  tienen intercalaciones distintas). Nunca rompe una lista: si el espejo o lo del taller fallan, las filas salen como antes y
 *  se apunta en el log. */
@Service
public class DescripcionMovil {

    private static final Logger log = LoggerFactory.getLogger(DescripcionMovil.class);

    private final NsysDAO nsysDao;
    private final TallerNsys taller;

    public DescripcionMovil(NsysDAO nsysDao, TallerNsys taller) {
        this.nsysDao = nsysDao;
        this.taller = taller;
    }

    /** Lo de NSYS que lleva una fila. */
    private record DelEspejo(String capacidad, String color) {}

    /** Rellena la capacidad y el color de estas filas (las cambia en el sitio) y devuelve la misma lista. Una lectura del espejo
     *  por IMEI distinto, en tandas de {@value NsysDAO#TANDA_IN} ({@link NsysDAO#porImeis}), y una de lo del taller (todos los
     *  vigentes); nunca una consulta por fila. Todo o nada: si falla una lectura, ninguna fila cambia. */
    public List<ReparacionResumen> rellenar(List<ReparacionResumen> filas) {
        if (filas == null || filas.isEmpty()) return filas;
        Map<String, DelEspejo> moviles;
        try {
            moviles = leer(filas);
        } catch (RuntimeException e) {
            log.warn("Listas del taller sin capacidad ni color ({} filas): no se pudo leer el espejo de NSYS o lo del taller: {}",
                    filas.size(), e.toString());
            return filas;
        }
        for (ReparacionResumen r : filas) {
            DelEspejo m = moviles.get(NsysDAO.claveCruce(r.getImei()));
            if (m == null) continue;
            r.setCapacidad(m.capacidad());
            r.setColor(m.color());
        }
        return filas;
    }

    private Map<String, DelEspejo> leer(List<ReparacionResumen> filas) {
        Set<String> imeis = new LinkedHashSet<>();
        for (ReparacionResumen r : filas) if (r.getImei() != null && !r.getImei().isBlank()) imeis.add(r.getImei());
        if (imeis.isEmpty()) return Map.of();
        // porImeis trae de cada IMEI el lote más reciente primero: se queda ese.
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        if (masReciente.isEmpty()) return Map.of();
        Function<MovilNsys, String> color = taller.colorDeTodos();
        Map<String, DelEspejo> moviles = new HashMap<>();
        masReciente.forEach((clave, m) -> moviles.put(clave, new DelEspejo(m.capacidad(), color.apply(m))));
        return moviles;
    }
}
