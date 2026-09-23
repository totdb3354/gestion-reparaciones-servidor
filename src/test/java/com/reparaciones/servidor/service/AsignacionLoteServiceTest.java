package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas de negocio del guardado por lotes, con los DAO mockeados (sin transacción real; ver
 *  {@link AsignacionLoteServiceTransaccionTest} para la prueba de frontera transaccional). */
class AsignacionLoteServiceTest {
    private static final String IMEI = "111111111111111";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TelefonoDAO telefonoDao = mock(TelefonoDAO.class);
    private final AsignacionLoteService servicio = new AsignacionLoteService(dao, telefonoDao);

    private static Peticion lote(AsignacionDelLote... a) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, "13pro", 12, false)), List.of(a));
    }

    @Test void primeroLosTelefonosDespuesLasAltas() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt())).thenReturn("A1");
        servicio.guardar(lote(new AsignacionDelLote(IMEI, "R", 3, null, false)), 7, 70);
        InOrder orden = inOrder(telefonoDao, dao);
        orden.verify(telefonoDao).insertar(IMEI, "13pro", 12, false);
        orden.verify(dao).insertarAsignacion(IMEI, 3, null, false, false, 7, 70);
    }

    @Test void cadaCategoriaVaASuAltaConUrgenteFalse() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt())).thenReturn("A1");
        when(dao.insertarAsignacionGlass(any(), anyInt(), any(), anyBoolean(), any(), anyInt())).thenReturn("AG1");
        when(dao.insertarAsignacionPulido(any(), anyInt(), any(), any(), anyInt())).thenReturn("AP1");
        Respuesta r = servicio.guardar(lote(
                new AsignacionDelLote(IMEI, "R", 3, "hola", true),
                new AsignacionDelLote(IMEI, "G", 4, null, true),
                new AsignacionDelLote(IMEI, "P", 5, null, false)), 7, 70);
        verify(dao).insertarAsignacion(IMEI, 3, "hola", false, true, 7, 70);
        verify(dao).insertarAsignacionGlass(IMEI, 4, null, false, 7, 70);
        verify(dao).insertarAsignacionPulido(IMEI, 5, null, 7, 70);
        assertEquals(List.of(new Creada("A1", IMEI, 3, "R"), new Creada("AG1", IMEI, 4, "G"),
                new Creada("AP1", IMEI, 5, "P")), r.creadas());
        assertTrue(r.conflictos().isEmpty());
    }

    @Test void elDuplicadoSeSaltaYSeInformaConElNombre() {
        when(dao.existeAsignacionParaTecnico(IMEI, 3, "R")).thenReturn(true);
        when(dao.getNombreTecnicoById(3)).thenReturn("Técnico A");
        Respuesta r = servicio.guardar(lote(new AsignacionDelLote(IMEI, "R", 3, null, false)), 7, 70);
        verify(dao, never()).insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt());
        assertEquals(List.of(new Conflicto(IMEI, 3, "Técnico A", "R")), r.conflictos());
        assertTrue(r.creadas().isEmpty());
    }

    @Test void comentarioEnBlancoViajaComoNull() {
        when(dao.insertarAsignacionPulido(any(), anyInt(), any(), any(), anyInt())).thenReturn("AP1");
        servicio.guardar(lote(new AsignacionDelLote(IMEI, "P", 5, "   ", false)), 7, 70);
        verify(dao).insertarAsignacionPulido(IMEI, 5, null, 7, 70);
    }

    @Test void unErrorAMitadSePropagaParaQueLaTransaccionHagaRollback() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt()))
                .thenReturn("A1").thenThrow(new IllegalStateException("BD caída"));
        assertThrows(IllegalStateException.class, () -> servicio.guardar(lote(
                new AsignacionDelLote(IMEI, "R", 3, null, false),
                new AsignacionDelLote(IMEI, "R", 4, null, false)), 7, 70));
    }
}
