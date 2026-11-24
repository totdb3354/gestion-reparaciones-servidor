package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FechaSalidaTest {
    private static final Set<LocalDate> FESTIVOS = Set.of(LocalDate.of(2026, 12, 8));
    private static PedidoShippyPro pedido(String estado, String servicio, LocalDateTime fecha) {
        return new PedidoShippyPro("0099-1", "1", fecha, null, "ES", List.of(), estado, servicio);
    }

    @Test
    void enAmazonEsLaFechaDeLatestShip() {
        var p = pedido("Unshipped Latest ship: 2026-11-19", "Standard", LocalDateTime.of(2026, 11, 18, 9, 0));
        assertThat(FechaSalida.de(p, Canal.AMAZON, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(FechaSalida.de(p, Canal.AMAZON_STOCKROOM, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void enAmazonSinLatestShipONoValidaEsNula() {
        assertThat(FechaSalida.de(pedido("processing", null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
        assertThat(FechaSalida.de(pedido(null, null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
        assertThat(FechaSalida.de(pedido("Unshipped Latest ship: 2026-13-40", null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
    }

    @Test
    void enLasWebsEsElLaborableSiguienteAlPrimerLaborableSinMirarElEnvio() {
        LocalDateTime lunesNoche = LocalDateTime.of(2026, 11, 16, 23, 30);
        assertThat(FechaSalida.de(pedido("processing", "Rápido (Entrega en 24/48 horas laborables)", lunesNoche), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(FechaSalida.de(pedido("processing", "Envío gratis en 24/72 horas laborables", lunesNoche), Canal.PARTICULARES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(FechaSalida.de(pedido("processing", null, lunesNoche), Canal.BACKIPHONE, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void enLasWebsElViernesVenceElLunesYElFinDeSemanaElMartes() {
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 20, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 21, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 22, 18, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void enLasWebsSeSaltanLosFestivos() {
        // lunes 7/12 (vispera de festivo): desde lunes 7, el laborable siguiente es el miercoles 9 (martes 8 festivo)
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 7, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 9));
        // entra el martes 8 (festivo): desde miercoles 9, vence el jueves 10
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 8, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void desdeEsElPrimerLaborableEnLasWebsYNuloEnAmazon() {
        assertThat(FechaSalida.desde(Canal.PARTICULARES, LocalDate.of(2026, 11, 21), FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(FechaSalida.desde(Canal.DISTRIBUIDORES, LocalDate.of(2026, 11, 20), FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(FechaSalida.desde(Canal.AMAZON, LocalDate.of(2026, 11, 21), FESTIVOS)).isNull();
        assertThat(FechaSalida.desde(null, LocalDate.of(2026, 11, 21), FESTIVOS)).isNull();
        assertThat(FechaSalida.desde(Canal.PARTICULARES, null, FESTIVOS)).isNull();
    }
}
