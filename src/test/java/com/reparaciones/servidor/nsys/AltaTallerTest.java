package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Los trabajos de un IMEI que NSYS no conoce (spec 0.9.22 §3.4). Datos inventados. */
class AltaTallerTest {

    private static final LocalDateTime DIA = LocalDateTime.of(2026, 10, 6, 9, 0);

    private static AltaTaller.Trabajo trabajo(String imei, String idRep, int dias, boolean terminado, String tecnico, String cliente) {
        LocalDateTime asignado = DIA.plusDays(dias);
        return new AltaTaller.Trabajo(imei, idRep, asignado, terminado ? asignado.plusHours(2) : null, tecnico, "14", cliente);
    }

    @Test void cuentanLosTrabajosHechosYLasAsignacionesAbiertasYElUltimoEsElMasReciente() {
        List<AltaTaller> altas = AltaTaller.de(List.of(
                trabajo("350000000000088", "A20261006_1", 0, true, "Técnico 2", null),
                trabajo("350000000000088", "R20261006_57", 0, true, "Técnico 2", "AMAZON"),
                trabajo("350000000000088", "A20261010_4", 4, false, "Técnico 1", null)));
        assertEquals(List.of(new AltaTaller("350000000000088", "14", "A20261010_4", true, "Técnico 1", null, 2, DIA,
                DIA.plusDays(4))), altas);
    }

    @Test void unoPorImeiEnElOrdenEnQueLlegan() {
        List<AltaTaller> altas = AltaTaller.de(List.of(
                trabajo("350000000000099", "P20260918_12", 0, true, "Técnico 5", null),
                trabajo("350000000000088", "R20261006_57", 1, true, "Técnico 2", "AMAZON")));
        assertEquals(List.of("350000000000099", "350000000000088"), altas.stream().map(AltaTaller::imei).toList());
        assertEquals("P20260918_12", altas.get(0).ultimo());
        assertFalse(altas.get(0).abierto());
        assertEquals(1, altas.get(0).trabajos());
        assertEquals("AMAZON", altas.get(1).cliente());
    }

    @Test void terminarLaAsignacionNoMueveUltimoEnPorqueSuFilaRNaceConLaHoraDelCierre() {
        AltaTaller.Trabajo a = new AltaTaller.Trabajo("350000000000088", "A20261006_1", DIA, DIA.plusHours(2), "Técnico 2", "14", null);
        AltaTaller.Trabajo r = new AltaTaller.Trabajo("350000000000088", "R20261006_57", DIA.plusHours(2), DIA.plusHours(2), "Técnico 2", "14", null);
        AltaTaller alta = AltaTaller.de(List.of(a, r)).get(0);
        assertEquals("R20261006_57", alta.ultimo());
        assertEquals(DIA, alta.ultimoEn());
    }

    @Test void sinAsignacionesUltimoEnEsLaFechaDeSuUltimaFila() {
        List<AltaTaller> altas = AltaTaller.de(List.of(
                trabajo("350000000000099", "R20261006_57", 0, true, "Técnico 2", null),
                trabajo("350000000000099", "R20261008_3", 2, true, "Técnico 2", null)));
        assertEquals(DIA.plusDays(2), altas.get(0).ultimoEn());
    }

    @Test void sinTrabajosNoHayAltas() {
        assertTrue(AltaTaller.de(List.of()).isEmpty());
    }
}
