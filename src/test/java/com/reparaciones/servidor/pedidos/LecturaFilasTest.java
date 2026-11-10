package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LecturaFilasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 10, 0);
    private final ClienteShippyPro shippy = mock(ClienteShippyPro.class);
    private final FilasDAO dao = mock(FilasDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final Clock reloj = Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID);

    private LecturaFilas lectura(String canales) {
        return new LecturaFilas(shippy, dao, nsys, canales, reloj);
    }

    private static PedidoShippyPro pedido(String numero) {
        return new PedidoShippyPro(numero, "100", AHORA.minusDays(1), null, "ES",
                List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)));
    }

    private static FilaPedido viva(int id, String pedido) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 9), "AMZ", "i13128AzulA", null, pedido, LocalDate.of(2026, 11, 9),
                "SHIPPYPRO", Canal.AMAZON, 1, null);
    }

    @Test void unaPasadaBuenaInsertaCierraYApuntaOk() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(pedido("402-0000000-0000011")), List.of()));
        when(dao.vivasDeShippyPro()).thenReturn(List.of(viva(9, "402-0000000-0000099")));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(AceptaSkuTest.producto("Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128AzulAP", "bk-azul-a")));

        lectura("100:AMAZON").pasada();

        verify(dao).insertar(argThat(l -> l.size() == 1 && l.get(0).pedido().equals("402-0000000-0000011")
                && l.get(0).fecha().equals(LocalDate.of(2026, 11, 10))));
        verify(dao).cerrar(List.of(9), AHORA);
        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.OK), eq(1), eq(Set.of()), isNull(), eq(AHORA));
        verify(dao).podarLecturas(AHORA.minusDays(30));
    }

    @Test void sinElAjusteDeCanalesEsRechazadaYNoCierraNada() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);

        lectura("").pasada();

        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.RECHAZADA), eq(0), eq(Set.of()),
                eq("Falta el ajuste pedidos.shippypro.canales"), eq(AHORA));
        verify(dao, never()).cerrar(any(), any());
        verify(dao, never()).insertar(any());
        verifyNoInteractions(shippy);
    }

    @Test void siShippyProFallaEsErrorSinUrlYNoCierraNada() throws Exception {
        when(dao.empezarLectura(any())).thenReturn(5);
        when(shippy.pendientes()).thenThrow(new IOException("fallo en https://ejemplo.invalid/?clave=secreta"));

        lectura("100:AMAZON").pasada();

        verify(dao).terminarLectura(eq(5), eq(LecturaFilas.ERROR), eq(0), eq(Set.of()),
                argThat(m -> m.contains("IOException") && !m.contains("secreta") && !m.contains("http")), eq(AHORA));
        verify(dao, never()).cerrar(any(), any());
        verify(dao, never()).vivasDeShippyPro();
    }
}
