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
    void enLasWebsEsLaEntradaMasDosOTresDiasLaborablesSegunElEnvio() {
        LocalDateTime lunes = LocalDateTime.of(2026, 11, 16, 23, 30);
        assertThat(FechaSalida.de(pedido("processing", "Rápido (Entrega en 24/48 horas laborables)", lunes), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(FechaSalida.de(pedido("processing", "Envío gratis en 24/72 horas laborables", lunes), Canal.PARTICULARES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(FechaSalida.de(pedido("processing", "Recogida en tienda", lunes), Canal.BACKIPHONE, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19)); // otro texto: 3 dias
        assertThat(FechaSalida.de(pedido("processing", null, lunes), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void enLasWebsSeSaltanLosFestivos() {
        // lunes 7/12 + 2 laborables: martes 8 festivo -> miercoles 9 y jueves 10
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 7, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 10));
    }
}
