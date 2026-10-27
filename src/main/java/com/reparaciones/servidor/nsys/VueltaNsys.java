package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Si un móvil sale en «Revisar a mano», Cambio de color y Cambio de grado (spec 0.9.22 pieza 4 §6): uno que NSYS tiene en la
 *  empresa, tal cual; uno vendido o de baja, solo si ha vuelto: el taller tiene algún trabajo suyo asignado un día posterior a la
 *  venta ({@code soldDate}, que va sin hora: el mismo día no cuenta) o a la baja (el día de {@code cambiado}, la última vez que
 *  NSYS cambió su fila). {@code vendidoEn} o {@code bajaEn}: ese día; {@code desde}: la asignación del primer trabajo posterior
 *  (hora de Madrid). Los tres null: en la empresa. Es puro. */
public record VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde) {

    /** Un móvil en la empresa: sale tal cual. */
    public static final VueltaNsys EN_EMPRESA = new VueltaNsys(null, null, null);

    /** La vuelta de un móvil con las asignaciones de sus trabajos (hora de Madrid, en cualquier orden); vacío si no sale. */
    public static Optional<VueltaNsys> de(MovilNsys m, Collection<LocalDateTime> asignaciones) {
        if (m.estado().enEmpresa()) return Optional.of(EN_EMPRESA);
        boolean vendido = m.estado() == EstadoMovil.VENDIDO;
        LocalDate dia = vendido ? m.soldDate() : m.cambiado() == null ? null : m.cambiado().toLocalDate();
        if (dia == null) return Optional.empty();
        return asignaciones.stream().filter(Objects::nonNull).filter(a -> a.toLocalDate().isAfter(dia))
                .min(Comparator.naturalOrder())
                .map(desde -> new VueltaNsys(vendido ? dia : null, vendido ? null : dia, desde));
    }

    /** Las asignaciones de unos trabajos, por {@link NsysDAO#claveCruce} del IMEI, en el orden en que llegan. */
    public static Map<String, List<LocalDateTime>> asignacionesPorImei(Collection<NsysDAO.TrabajoFecha> trabajos) {
        Map<String, List<LocalDateTime>> mapa = new LinkedHashMap<>();
        for (NsysDAO.TrabajoFecha t : trabajos)
            mapa.computeIfAbsent(NsysDAO.claveCruce(t.imei()), k -> new ArrayList<>()).add(t.asignado());
        return mapa;
    }
}
