package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La batería recordada que vale de un IMEI (spec 0.9.22 pieza 4 §5.1): la más reciente, por fecha y luego por id. */
class BateriaRecordadaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 26, 17, 28);

    private static BateriaRecordada b(int id, LocalDateTime fecha, boolean nueva) {
        return new BateriaRecordada(id, "350000000000001", "R20261026_" + id, nueva, "batería bati13 nueva", 8, fecha);
    }

    @Test void vaLaMasRecienteYAIgualHoraLaDeMayorId() {
        assertEquals(Optional.of(b(3, HORA, false)), BateriaRecordada.ultima(List.of(b(1, HORA.minusDays(1), true), b(3, HORA, false),
                b(2, HORA, true))));
        assertEquals(Optional.empty(), BateriaRecordada.ultima(List.of()));
    }
}
