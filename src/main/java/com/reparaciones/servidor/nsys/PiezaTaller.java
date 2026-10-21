package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Locale;
import java.util.Optional;

/** Una pieza de una reparación que cambia el móvil (spec 0.9.21 «SKU del taller», pieza 2, §2.1): chasis, tapa trasera o
 *  batería, por el prefijo de su SKU (Componente.TIPO). {@code idRep}: la R… (null en la previsualización); {@code nueva}:
 *  gasta del stock (ES_REUTILIZADO falso). */
public record PiezaTaller(String idRep, String sku, Tipo tipo, boolean nueva) {

    public enum Tipo {
        CHASIS("cha", "chasis", true), TAPA("tapa", "tapa", false), BATERIA("bat", "batería", false);

        private final String prefijo;
        private final String nombre;
        private final boolean masculino;

        Tipo(String prefijo, String nombre, boolean masculino) {
            this.prefijo = prefijo;
            this.nombre = nombre;
            this.masculino = masculino;
        }

        /** Chasis y tapa cambian el color; la batería, solo el grado. */
        public boolean conColor() {
            return this != BATERIA;
        }

        /** El tipo de un SKU por su prefijo (lo que va antes de la primera «i»: chai13red → cha); vacío si no cuenta. */
        public static Optional<Tipo> deSku(String sku) {
            if (sku == null || sku.isBlank()) return Optional.empty();
            String prefijo = CatalogoPiezas.prefijo(sku.strip().toLowerCase(Locale.ROOT));
            for (Tipo t : values()) if (t.prefijo.equals(prefijo)) return Optional.of(t);
            return Optional.empty();
        }
    }

    /** «chasis chai13red nuevo», «batería bati13 reutilizada»: el MOTIVO de sus cambios y lo que dice el rastro. */
    public String texto() {
        String estado = nueva ? (tipo.masculino ? "nuevo" : "nueva") : (tipo.masculino ? "reutilizado" : "reutilizada");
        return tipo.nombre + " " + sku + " " + estado;
    }
}
