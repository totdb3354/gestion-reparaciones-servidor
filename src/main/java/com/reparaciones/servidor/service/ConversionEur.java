package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.TipoCambioDAO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Importe en euros de un precio en su divisa (spec 4b §4.3, P3). La tasa de TipoCambioDAO es la de Frankfurter con
 *  from=EUR: unidades de la divisa por 1 EUR, así que se divide. Lo usan los POST/PUT sueltos de compras y de otros y
 *  los dos lotes; el precioEur que manda el cliente se ignora (el JavaFX 0.16.x multiplicaba). */
@Service
public class ConversionEur {

    private final TipoCambioDAO tipoCambio;

    public ConversionEur(TipoCambioDAO tipoCambio) {
        this.tipoCambio = tipoCambio;
    }

    /** EUR (o sin divisa) → el propio precio, sin consultar la tasa. Otra divisa → precio / tasa a 2 decimales
     *  HALF_UP. Si no hay tasa, propaga el 503 de TipoCambioDAO. */
    public double aEuros(double precioUnidad, String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (d.isEmpty() || "EUR".equals(d)) return precioUnidad;
        double tasa = tipoCambio.getTasa(d);
        return BigDecimal.valueOf(precioUnidad)
                .divide(BigDecimal.valueOf(tasa), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
