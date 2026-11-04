package com.reparaciones.servidor.pedidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** El canal de un pedido (spec pieza 2 §4.1). El Simulador no llega de ShippyPro. */
public enum Canal {
    AMAZON("Amazon"), AMAZON_STOCKROOM("Amazon Stockroom"), DISTRIBUIDORES("Distribuidores"), PARTICULARES("Particulares"),
    BACKIPHONE("BackiPhone"), SIMULADOR("Simulador");

    private static final Logger log = LoggerFactory.getLogger(Canal.class);
    private final String etiqueta;

    Canal(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean esAmazon() {
        return this == AMAZON || this == AMAZON_STOCKROOM;
    }

    public boolean esWeb() {
        return this == DISTRIBUIDORES || this == PARTICULARES || this == BACKIPHONE;
    }

    /** «100:AMAZON, 200:AMAZON_STOCKROOM» → marketplace de ShippyPro → canal (ajuste pedidos.shippypro.canales). Un trozo mal
     *  escrito, un canal desconocido o el SIMULADOR se saltan con un aviso en el log. */
    public static Map<String, Canal> deAjuste(String texto) {
        Map<String, Canal> canales = new HashMap<>();
        if (texto == null || texto.isBlank()) return canales;
        for (String trozo : texto.split(",")) {
            String[] partes = trozo.strip().split(":");
            if (partes.length != 2 || partes[0].isBlank()) {
                if (!trozo.isBlank()) log.warn("Pedidos: «{}» en pedidos.shippypro.canales no es «marketplace:CANAL»", trozo.strip());
                continue;
            }
            try {
                Canal c = Canal.valueOf(partes[1].strip().toUpperCase(Locale.ROOT));
                if (c == SIMULADOR) throw new IllegalArgumentException();
                canales.put(partes[0].strip(), c);
            } catch (IllegalArgumentException e) {
                log.warn("Pedidos: «{}» en pedidos.shippypro.canales no es un canal de ShippyPro", partes[1].strip());
            }
        }
        return canales;
    }
}
