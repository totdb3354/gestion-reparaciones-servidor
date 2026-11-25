package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.Normalizacion;
import com.reparaciones.servidor.nsys.UbicacionNsys;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** La regla de la lista de devoluciones del bot (spec 2026-11-24-bot-devoluciones §5, con «reparado después» y «venta anterior
 *  devuelta» del 25/11: una fila cerrada con el IMEI cuenta salvo que NSYS registre una vuelta posterior a su cierre). Pura:
 *  recibe lo que se sabe de un IMEI y dice si se devuelve, a dónde, o por qué no se devuelve solo. */
public final class ReglaDevoluciones {

    public static final String CAJA_PEDIDOS = "Pedidos realizados";
    /** Las cajas de revisar del bot (LOCATIONS_NO_TOCAR de stock/nsys_bot_completo.py): nunca son destino. */
    public static final Set<String> CAJAS_REVISAR = Set.of("Pendiente revisar si hay error de inventario",
            "MV Pendiente revisar si hay error de inventario");
    /** La entrada en «Pedidos realizados» que ve el espejo cae, como mucho, a esta distancia del descuento del bot. */
    public static final Duration MARGEN_ENTRADA = Duration.ofMinutes(30);
    public static final int ERRORES_SEGUIDOS = 3;

    public static final String DESCONTADO = "DESCONTADO";
    public static final String DEVUELTO = "DEVUELTO";
    public static final String OK = "OK";
    public static final String NO_APARECE = "NO_APARECE";
    public static final String ERROR = "ERROR";
    public static final Set<String> ACCIONES = Set.of(DESCONTADO, DEVUELTO);
    public static final Set<String> RESULTADOS = Set.of(OK, NO_APARECE, ERROR);
    /** El unico motivo de hoy; la reserva de IMEI añadira RESERVA_LIBERADA. */
    public static final String IMEI_CORREGIDO = "IMEI_CORREGIDO";

    /** Una fila de Bot_movimiento (hora de Madrid). */
    public record Paso(String accion, String resultado, String origen, LocalDateTime fecha) {}
    /** El ultimo apunte en que el IMEI salio de una fila: la fila, cuando (Madrid) y quien («Lucía», «el libro de ventas»…). */
    public record Salida(int idFila, LocalDateTime fecha, String quien) {}
    /** Un trabajo del taller del IMEI, con su asignacion en hora de Madrid. */
    public record Trabajo(String idRep, LocalDateTime asignado) {}
    /** Lo que se sabe de un IMEI candidato: sus pasos del bot (por fecha), su ultima salida de una fila, cuando se puso en filas,
     *  el espejo (estado y caja del lote que esta de pedido, si lo hay; grado de NSYS), su ultima entrada en «Pedidos realizados»
     *  segun Nsys_evento y sus trabajos del taller. Ademas, los cierres de las filas cerradas que tienen el IMEI y las fechas en que
     *  NSYS registra que el movil volvio a la empresa (venta deshecha, reentrada, salida de «Pedidos realizados» o entrada sin
     *  localizacion): una fila cerrada solo deja de contar si hay una vuelta posterior a su cierre. */
    public record Datos(String imei, List<Paso> pasos, Salida salida, List<LocalDateTime> puestos, String estadoEspejo,
                        String cajaEspejo, String grado, LocalDateTime entradaPedidos, List<Trabajo> trabajos,
                        List<LocalDateTime> cierresFilas, List<LocalDateTime> vueltas) {}

    public enum Tipo { DEVOLVER, SIN_DESTINO, NADA }

    /** DEVOLVER con su destino; SIN_DESTINO con el motivo (va al historial de la fila de la salida); NADA. */
    public record Veredicto(Tipo tipo, String destino, String motivo, Salida salida) {
        public static final Veredicto NADA = new Veredicto(Tipo.NADA, null, null, null);
    }

    private ReglaDevoluciones() {}

    public static Veredicto evaluar(Datos d, Map<String, UbicacionNsys> ubicaciones) {
        Paso descuento = d.pasos().stream()
                .filter(p -> DESCONTADO.equals(p.accion()) && OK.equals(p.resultado()) && p.fecha() != null)
                .max(Comparator.comparing(Paso::fecha)).orElse(null);
        if (descuento == null) return Veredicto.NADA;
        LocalDateTime t = descuento.fecha();
        // Una fila cerrada con el IMEI sigue contando (vender gana a devolver) salvo que NSYS registre una vuelta posterior a su cierre.
        for (LocalDateTime cierre : d.cierresFilas())
            if (d.vueltas().stream().noneMatch(v -> v.isAfter(cierre))) return Veredicto.NADA;
        Salida salida = d.salida();
        if (salida == null || !salida.fecha().isAfter(t)) return Veredicto.NADA;
        if (d.puestos().stream().noneMatch(p -> !p.isAfter(t))) return Veredicto.NADA;
        if (!EstadoMovil.DE_PEDIDO.name().equals(d.estadoEspejo()) || !CAJA_PEDIDOS.equals(d.cajaEspejo())) return Veredicto.NADA;
        LocalDateTime entrada = d.entradaPedidos();
        if (entrada == null || entrada.isBefore(t.minus(MARGEN_ENTRADA)) || entrada.isAfter(t.plus(MARGEN_ENTRADA))) return Veredicto.NADA;
        List<Paso> devueltos = d.pasos().stream()
                .filter(p -> DEVUELTO.equals(p.accion()) && p.fecha() != null && !p.fecha().isBefore(t)).toList();
        if (devueltos.stream().anyMatch(p -> OK.equals(p.resultado()) || NO_APARECE.equals(p.resultado()))) return Veredicto.NADA;
        if (erroresSeguidosAlFinal(devueltos) >= ERRORES_SEGUIDOS) return Veredicto.NADA;

        List<String> reparaciones = d.trabajos().stream()
                .filter(tr -> tr.asignado() != null && tr.asignado().isAfter(t)).map(Trabajo::idRep).toList();
        if (!reparaciones.isEmpty()) return sinDestino("reparado después (" + String.join(", ", reparaciones) + ")", salida);
        String caja = descuento.origen() == null ? "" : descuento.origen().strip();
        if (caja.isEmpty()) return sinDestino("sin caja", salida);
        if (!vendible(caja, ubicaciones.get(caja))) return sinDestino(caja + " no es vendible", salida);
        String grado = Normalizacion.gradoSinBateria(d.grado()).strip().toUpperCase(Locale.ROOT);
        if (EstadoMovil.NO_VENDIBLES.contains(grado)) return sinDestino("grado " + grado + " no es vendible", salida);
        return new Veredicto(Tipo.DEVOLVER, caja, null, salida);
    }

    /** Cuantos ERROR seguidos hay al final de la lista (en su orden). */
    public static int erroresSeguidosAlFinal(List<Paso> devueltos) {
        int n = 0;
        for (int i = devueltos.size() - 1; i >= 0 && ERROR.equals(devueltos.get(i).resultado()); i--) n++;
        return n;
    }

    /** Una caja de NSYS a la que se puede devolver: conocida por el ERP, cuenta como stock, no es «Pedidos realizados» ni de revisar. */
    static boolean vendible(String caja, UbicacionNsys ubicacion) {
        return !CAJAS_REVISAR.contains(caja) && ubicacion != null && EstadoMovil.de("Available", ubicacion) == EstadoMovil.STOCK;
    }

    private static Veredicto sinDestino(String motivo, Salida salida) {
        return new Veredicto(Tipo.SIN_DESTINO, null, motivo, salida);
    }
}
