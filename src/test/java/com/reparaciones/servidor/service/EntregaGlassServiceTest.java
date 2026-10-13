package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Abrir» automático al entregar a glass (spec 2026-10-13 §2 y §4.1). */
class EntregaGlassServiceTest {

    private static final String IMEI = "351111112222333";
    private static final LocalDateTime ASIG = LocalDateTime.of(2026, 10, 13, 8, 40);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final EntregaGlassService servicio = new EntregaGlassService(dao);
    private final ReparacionDAO.NormalAbierta normal =
            new ReparacionDAO.NormalAbierta("A20261013_1", 7, "Técnico L", ASIG, null);

    private void conPiezaOtro() {
        when(dao.getModeloTelefono(IMEI)).thenReturn("13pro");
        when(dao.getComponentesOtro()).thenReturn(List.of(
                new ReparacionDAO.ComponenteOtro(500, "otroi13"),
                new ReparacionDAO.ComponenteOtro(501, "otroi13pro")));
    }

    @SuppressWarnings("unchecked")
    @Test void entregarSellaYApuntaElAbrirAQuienEntrega() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"))).thenReturn("R20261013_101");

        assertEquals(ResultadoEntregaGlass.creado("R20261013_101", "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));

        verify(dao).entregarGlass(IMEI, 7);
        ArgumentCaptor<List<FilaReparacion>> filas = ArgumentCaptor.forClass(List.class);
        verify(dao).guardarFilaIndividual(filas.capture(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"));
        assertEquals(1, filas.getValue().size());
        FilaReparacion f = filas.getValue().get(0);
        assertEquals(501, f.idCom);            // la pieza «otro» del 13 Pro, no la del 13
        assertEquals(0, f.cantidad);           // neutra de stock
        assertFalse(f.reutilizado);
        assertFalse(f.esSolicitud);
        assertEquals("Abrir", f.observacion);
        assertEquals("otro", f.prefijo);
        verify(dao).marcarAbrir(IMEI, "R20261013_101");
    }

    @Test void conIncidenciaElAbrirLlevaElIdRepAnteriorDeLaAsignacion() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(
                new ReparacionDAO.NormalAbierta("A20261013_1", 7, "Técnico L", ASIG, "R20261001_4")));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), eq("R20261001_4"), eq("A20261013_1"))).thenReturn("R20261013_102");
        assertEquals(EstadoAbrir.CREADO, servicio.entregar(IMEI, 7, "A20261013_1").abrir());
    }

    @Test void conAperturaManualPreviaNoDuplica() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        when(dao.tieneAperturaManual(IMEI, 7, ASIG)).thenReturn(true);
        conPiezaOtro();
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.YA_EXISTIA, "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));
        verify(dao).entregarGlass(IMEI, 7);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
        verify(dao, never()).marcarAbrir(anyString(), anyString());
    }

    @Test void sinModeloOSinPiezaOtroDelModeloEntregaIgualSinAbrir() {
        when(dao.getNormalAbierta("A20261013_1")).thenReturn(Optional.of(normal));
        when(dao.getModeloTelefono(IMEI)).thenReturn(null);
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, "Técnico L"), servicio.entregar(IMEI, 7, "A20261013_1"));
        when(dao.getModeloTelefono(IMEI)).thenReturn("7");
        when(dao.getComponentesOtro()).thenReturn(List.of(new ReparacionDAO.ComponenteOtro(500, "otroi13")));
        assertEquals(EstadoAbrir.SIN_PIEZA_OTRO, servicio.entregar(IMEI, 7, "A20261013_1").abrir());
        verify(dao, times(2)).entregarGlass(IMEI, 7);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
    }

    @Test void llegadaFirmaElDeGlassYApuntaElAbrirAlDuenoDeLaNormalMasAntigua() {
        when(dao.getNormalAbiertaMasAntigua(IMEI)).thenReturn(Optional.of(normal));
        conPiezaOtro();
        when(dao.guardarFilaIndividual(anyList(), eq(IMEI), eq(7), isNull(), eq("A20261013_1"))).thenReturn("R20261013_103");
        assertEquals(ResultadoEntregaGlass.creado("R20261013_103", "Técnico L"), servicio.marcarLlegada(IMEI, 9));
        verify(dao).entregarGlass(IMEI, 9);
        verify(dao).marcarAbrir(IMEI, "R20261013_103");
    }

    @Test void llegadaSinNormalAbiertaNoApuntaANadie() {
        when(dao.getNormalAbiertaMasAntigua(IMEI)).thenReturn(Optional.empty());
        assertEquals(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null), servicio.marcarLlegada(IMEI, 9));
        verify(dao).entregarGlass(IMEI, 9);
        verify(dao, never()).guardarFilaIndividual(anyList(), anyString(), anyInt(), any(), anyString());
    }

    @Test void deshacerBorraElAbrirDeLaEntregaAntesDeLimpiarla() {
        when(dao.getAbrirDeEntrega(IMEI)).thenReturn(List.of("R20261013_101"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20261013_101"), servicio.deshacer(IMEI));
        InOrder orden = inOrder(dao);
        orden.verify(dao).eliminar("R20261013_101");
        orden.verify(dao).deshacerEntregaGlass(IMEI);
    }

    @Test void deshacerSinAbrirSoloLimpiaLaEntrega() {
        when(dao.getAbrirDeEntrega(IMEI)).thenReturn(List.of());
        assertEquals(ResultadoEntregaGlass.deshecho(null), servicio.deshacer(IMEI));
        verify(dao, never()).eliminar(anyString());
        verify(dao).deshacerEntregaGlass(IMEI);
    }

    @Test void lasTresOperacionesSonTransaccionales() {
        for (String nombre : List.of("entregar", "marcarLlegada", "deshacer")) {
            Method m = Arrays.stream(EntregaGlassService.class.getMethods())
                    .filter(x -> x.getName().equals(nombre)).findFirst().orElseThrow();
            assertNotNull(m.getAnnotation(Transactional.class), nombre);
        }
    }
}
