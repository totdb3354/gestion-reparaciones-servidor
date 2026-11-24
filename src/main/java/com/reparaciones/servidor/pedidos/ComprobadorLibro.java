package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Si el móvil que el libro de ventas pone en un pedido con etiqueta puede ser el de ese pedido (spec «el libro manda» §2.3): con
 *  «lo que vale» (NSYS + taller) y la misma comparación que el aviso de la celda del IMEI. No vale lo que en la celda cuenta en
 *  «no cuadran» (nivel MAL); eSIM / SIM física, n/a, Ms, B por A o un técnico no lo paran. Solo lee. */
@Component
public class ComprobadorLibro {

    public static final String NO_EN_NSYS = "NO_EN_NSYS";
    public static final String OTRO_MODELO = "OTRO_MODELO";

    /** Por qué no vale: {@code motivo} NO_EN_NSYS u OTRO_MODELO; {@code detalle}, qué es el móvil (null en NO_EN_NSYS). */
    public record Rechazo(String motivo, String detalle) {}

    private final NsysDAO nsysDao;
    private final TallerNsys taller;

    public ComprobadorLibro(NsysDAO nsysDao, TallerNsys taller) {
        this.nsysDao = nsysDao;
        this.taller = taller;
    }

    /** {@code skuPorImei}: IMEI del libro → SKU del pedido en el que lo pone. Devuelve solo los que no valen, por el mismo IMEI.
     *  Una lectura de cada cosa para todos. */
    public Map<String, Rechazo> rechazos(Canal canal, Map<String, String> skuPorImei) {
        if (skuPorImei.isEmpty()) return Map.of();
        Map<String, MovilNsys> moviles = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(skuPorImei.keySet())) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, List<TrabajoAbierto>> trabajos = nsysDao.trabajosAbiertos(skuPorImei.keySet());
        Function<MovilNsys, LoQueVale.Valor> vale = taller.valedor(skuPorImei.keySet());
        AceptaSku acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skuPorImei.values())));
        Map<String, Rechazo> rechazos = new LinkedHashMap<>();
        skuPorImei.forEach((imei, sku) -> {
            String clave = NsysDAO.claveCruce(imei);
            MovilNsys m = moviles.get(clave);
            AvisoImei aviso = AvisoImei.de(acepta.para(canal, sku), canal, m, m == null ? null : vale.apply(m),
                    trabajos.getOrDefault(clave, List.of()), false);
            if (!AvisoImei.MAL.equals(aviso.nivel())) return;
            if (NO_EN_NSYS.equals(aviso.motivo())) rechazos.put(imei, new Rechazo(NO_EN_NSYS, null));
            else rechazos.put(imei, new Rechazo(OTRO_MODELO,
                    "el pedido es " + sku + " y el móvil es " + (aviso.skuMovil() == null ? "—" : aviso.skuMovil())));
        });
        return rechazos;
    }
}
