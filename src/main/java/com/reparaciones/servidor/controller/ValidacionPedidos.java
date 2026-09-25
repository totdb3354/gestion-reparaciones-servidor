package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** Reglas de alta, edición y recepción de pedidos que hasta el 4b solo aplicaba el cliente JavaFX (spec 4b §4.2).
 *  Mismos textos que el cliente cuando existen. Un 422 se lanza siempre antes de escribir y de registrar log.
 *  La usan CompraController, CompraOtroController y CompraLoteController. */
final class ValidacionPedidos {

    private ValidacionPedidos() {}

    /** Las dos divisas del combo del cliente (mismo criterio que ProveedorController, sub-proyecto 4a). */
    static final Set<String> DIVISAS = Set.of("EUR", "USD");

    static final String MSG_CANTIDAD            = "Cantidad no válida (debe ser > 0).";
    static final String MSG_PRECIO              = "Precio no válido.";
    static final String MSG_CONCEPTO            = "El concepto no puede estar vacío.";
    static final String MSG_DIVISA              = "Divisa no válida (EUR o USD).";
    static final String MSG_COMPONENTE_INACTIVO = "El componente no está activo.";
    static final String MSG_PROVEEDOR_INACTIVO  = "El proveedor no está activo.";
    static final String MSG_CANTIDAD_RECIBIDO   = "No se puede cambiar la cantidad de un pedido recibido.";
    static final String MSG_RESTO_CERO          = "La cantidad debe ser mayor que 0.";

    // ── Lotes (spec 4b §4.2 y §4.4): los mismos avisos que el formulario del cliente, ahora en el servidor ──
    static final String MSG_SIN_LINEAS          = "Añade al menos una línea.";
    static final String MSG_SOLICITUD_SIN_LINEA = "La solicitud no corresponde a ninguna línea del pedido.";
    static final String L_COMPONENTE            = "selecciona un componente.";
    static final String L_CONCEPTO              = "el concepto no puede estar vacío.";
    static final String L_PROVEEDOR             = "selecciona un proveedor.";
    static final String L_COMPONENTE_OFF        = "el componente está desactivado.";
    static final String L_PROVEEDOR_OFF         = "el proveedor está desactivado.";
    static final String L_CANTIDAD              = "la cantidad debe ser mayor que 0.";
    static final String L_PRECIO                = "el precio no puede ser negativo.";

    static void cantidadPositiva(int cantidad) {
        if (cantidad <= 0) throw regla(MSG_CANTIDAD);
    }

    /** Rechaza también NaN e infinito (Minor 8 de la revisión final: {@code 1e400} lo parsea Jackson como Infinity). */
    static void precioNoNegativo(double precio) {
        if (!Double.isFinite(precio) || precio < 0) throw regla(MSG_PRECIO);
    }

    static void conceptoInformado(String concepto) {
        if (concepto == null || concepto.isBlank()) throw regla(MSG_CONCEPTO);
    }

    /** Devuelve la divisa normalizada (recortada y en mayúsculas), que es la que se guarda. */
    static String divisaValida(String divisa) {
        String d = divisa == null ? "" : divisa.trim().toUpperCase();
        if (!DIVISAS.contains(d)) throw regla(MSG_DIVISA);
        return d;
    }

    static ComponenteDAO.Basico componenteActivo(ComponenteDAO dao, int idCom) {
        ComponenteDAO.Basico c = dao.getBasico(idCom).orElse(null);
        if (c == null || !c.activo()) throw regla(MSG_COMPONENTE_INACTIVO);
        return c;
    }

    static Proveedor proveedorActivo(ProveedorDAO dao, int idProv) {
        Proveedor p = dao.getById(idProv).orElse(null);
        if (p == null || !p.isActivo()) throw regla(MSG_PROVEEDOR_INACTIVO);
        return p;
    }

    /** P2: en un pedido recibido la cantidad pedida no se toca (el stock ya se sumó con ella). */
    static void cantidadEditable(String estado, int cantidadGuardada, int cantidadNueva) {
        if ("recibido".equals(estado) && cantidadNueva != cantidadGuardada) throw regla(MSG_CANTIDAD_RECIBIDO);
    }

    static void rangoParcial(int cantidadRecibida, int cantidad) {
        if (cantidadRecibida <= 0 || cantidadRecibida >= cantidad) {
            throw regla("La cantidad debe ser mayor que 0 y menor que " + cantidad + ".");
        }
    }

    static void rangoResto(int cantidadExtra, Integer recibida, int cantidad) {
        if (cantidadExtra <= 0) throw regla(MSG_RESTO_CERO);
        int restante = cantidad - (recibida == null ? 0 : recibida);
        if (cantidadExtra > restante) {
            throw regla("No puedes recibir más de lo pedido. Faltan " + restante + " unidad(es).");
        }
    }

    /** "Línea {n}: {texto}" con n 1-based, como el aviso del formulario. */
    static ResponseStatusException enLinea(int numero, String texto) {
        return regla("Línea " + numero + ": " + texto);
    }

    static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
