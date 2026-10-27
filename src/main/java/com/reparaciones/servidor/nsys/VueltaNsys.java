package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Si un móvil sale en «Revisar a mano», Cambio de color y Cambio de grado (spec 0.9.22 pieza 4 §6): uno que NSYS tiene en la
 *  empresa, tal cual; uno vendido o de baja, solo si ha vuelto. D es el día de la venta ({@code soldDate}, sin hora) o de la baja
 *  (el día de {@code cambiado}, la última vez que NSYS cambió su fila), y el mismo día no cuenta. Cuenta toda asignación
 *  (A…/AG…/AP…, como {@code AltaTaller.Trabajo.esAsignacion}) con día posterior a D, y toda R/G/P con día posterior a D salvo la que
 *  es de una asignación que ya estaba abierta el día D (asignada hasta D y sin terminar o terminada después de D): se hizo mientras
 *  seguía abierta o al cerrarla, porque las R/G/P se insertan antes del UPDATE que pone FECHA_FIN. Es la misma idea que 3823dab
 *  ({@code AltaTaller.ultimoEn}: la última asignación, no la última fila). Límite conocido: filas añadidas a una asignación ya
 *  cerrada mucho después siguen contando. {@code vendidoEn} o {@code bajaEn}: D; {@code desde}: la menor asignación de las que
 *  cuentan (hora de Madrid). Los tres null: en la empresa. Es puro. */
public record VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde) {

    /** Un móvil en la empresa: sale tal cual. */
    public static final VueltaNsys EN_EMPRESA = new VueltaNsys(null, null, null);

    /** La vuelta de un móvil con los trabajos suyos (hora de Madrid, en cualquier orden); vacío si no sale. */
    public static Optional<VueltaNsys> de(MovilNsys m, Collection<NsysDAO.TrabajoFecha> trabajos) {
        if (m.estado().enEmpresa()) return Optional.of(EN_EMPRESA);
        boolean vendido = m.estado() == EstadoMovil.VENDIDO;
        LocalDate dia = vendido ? m.soldDate() : m.cambiado() == null ? null : m.cambiado().toLocalDate();
        if (dia == null) return Optional.empty();
        List<NsysDAO.TrabajoFecha> abiertas = trabajos.stream().filter(t -> t.esAsignacion() && t.asignado() != null
                && !t.asignado().toLocalDate().isAfter(dia)
                && (t.terminado() == null || t.terminado().toLocalDate().isAfter(dia))).toList();
        return trabajos.stream().filter(t -> t.asignado() != null && t.asignado().toLocalDate().isAfter(dia))
                .filter(t -> t.esAsignacion() || abiertas.stream().noneMatch(a -> esDe(t, a)))
                .map(NsysDAO.TrabajoFecha::asignado).min(Comparator.naturalOrder())
                .map(desde -> new VueltaNsys(vendido ? dia : null, vendido ? null : dia, desde));
    }

    /** Si una R/G/P se hizo con la asignación aún abierta o al cerrarla. */
    private static boolean esDe(NsysDAO.TrabajoFecha rgp, NsysDAO.TrabajoFecha asignacion) {
        return asignacion.terminado() == null || !rgp.asignado().isAfter(asignacion.terminado());
    }

    /** Los trabajos, por {@link NsysDAO#claveCruce} del IMEI, en el orden en que llegan. */
    public static Map<String, List<NsysDAO.TrabajoFecha>> trabajosPorImei(Collection<NsysDAO.TrabajoFecha> trabajos) {
        Map<String, List<NsysDAO.TrabajoFecha>> mapa = new LinkedHashMap<>();
        for (NsysDAO.TrabajoFecha t : trabajos)
            mapa.computeIfAbsent(NsysDAO.claveCruce(t.imei()), k -> new ArrayList<>()).add(t);
        return mapa;
    }
}
