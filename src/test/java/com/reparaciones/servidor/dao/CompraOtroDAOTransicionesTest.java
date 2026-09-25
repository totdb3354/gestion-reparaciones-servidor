package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** La misma matriz que CompraComponenteDAOTransicionesTest para "otros pedidos" (spec 4b §4.1): sin stock, pero con
 *  los mismos estados exigidos y los mismos 409. */
class CompraOtroDAOTransicionesTest {

    private static final int ID = 5;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0, 0);

    private static final String CONFIRMAR = "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'";
    private static final String RECIBIDO = "UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String PARCIAL = "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String RESTO_COMPLETO = "UPDATE Compra_otro SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
    private static final String ALTERADO = "UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=? AND ESTADO='parcial'";
    private static final String CANCELAR = "UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=? AND ESTADO='en_camino'";
    private static final String DESRECIBIR = "UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA_OTRO=? AND ESTADO='recibido'";
    private static final String EDITAR = "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))";
    private static final String BORRAR = "DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraOtroDAO dao = new CompraOtroDAO(jdbc);

    private void fila(int cantidad, Integer recibida) {
        when(jdbc.queryForObject(contains("FROM Compra_otro WHERE ID_COMPRA_OTRO = ?"), any(RowMapper.class), eq(ID)))
                .thenReturn(new CompraOtroDAO.Row(cantidad, recibida, AT));
    }

    private static void conflicto(String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    @Test void confirmarDesdePendientePasaAEnCamino() {
        fila(3, null);
        when(jdbc.update(CONFIRMAR, ID)).thenReturn(1);
        dao.confirmar(ID, AT);
        verify(jdbc).update(CONFIRMAR, ID);
    }

    @Test void confirmarFueraDePendienteEs409() {
        fila(3, null);
        conflicto("El pedido ya no está pendiente", () -> dao.confirmar(ID, AT));
    }

    @Test void recibirDesdeEnCaminoPasaARecibidoSinStock() {
        fila(3, null);
        when(jdbc.update(RECIBIDO, ID)).thenReturn(1);
        dao.confirmarRecibido(ID, AT);
        verify(jdbc).update(RECIBIDO, ID);
        verify(jdbc, never()).update(startsWith("UPDATE Componente"), any(Object[].class));
    }

    @Test void recibirFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarRecibido(ID, AT));
    }

    @Test void parcialDesdeEnCaminoGuardaLoRecibido() {
        fila(5, null);
        when(jdbc.update(PARCIAL, 2, ID)).thenReturn(1);
        dao.confirmarParcial(ID, 2, AT);
        verify(jdbc).update(PARCIAL, 2, ID);
    }

    @Test void parcialFueraDeEnCaminoEs409() {
        fila(5, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarParcial(ID, 2, AT));
    }

    @Test void restoQueCompletaPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(RESTO_COMPLETO, 3, ID)).thenReturn(1);
        dao.recibirResto(ID, 3, AT);
        verify(jdbc).update(RESTO_COMPLETO, 3, ID);
    }

    @Test void restoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.recibirResto(ID, 3, AT));
    }

    @Test void alteradoDesdeParcialPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(ALTERADO, ID)).thenReturn(1);
        dao.confirmarAlterado(ID, AT);
        verify(jdbc).update(ALTERADO, ID);
    }

    @Test void alteradoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.confirmarAlterado(ID, AT));
    }

    @Test void cancelarDesdeEnCaminoCancela() {
        fila(3, null);
        when(jdbc.update(CANCELAR, ID)).thenReturn(1);
        dao.cancelar(ID, AT);
        verify(jdbc).update(CANCELAR, ID);
    }

    @Test void cancelarFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.cancelar(ID, AT));
    }

    @Test void desrecibirDesdeRecibidoVuelveAEnCamino() {
        fila(5, 5);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        dao.desrecibir(ID, AT);
        verify(jdbc).update(DESRECIBIR, ID);
    }

    @Test void desrecibirFueraDeRecibidoEs409() {
        fila(5, null);
        conflicto("El pedido ya no está recibido", () -> dao.desrecibir(ID, AT));
    }

    @Test void editarEnUnEstadoEditableEscribe() {
        fila(4, null);
        when(jdbc.update(EDITAR, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, ID, 4)).thenReturn(1);
        dao.editar(ID, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, AT);
        verify(jdbc).update(EDITAR, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, ID, 4);
    }

    @Test void editarEnUnEstadoNoEditableEs409() {
        fila(4, null);
        conflicto("El pedido no se puede editar en su estado actual",
                () -> dao.editar(ID, 2, "Cinta de embalar", 4, false, 1.5, "EUR", 1.5, AT));
    }

    @Test void borrarUnPendienteBorra() {
        when(jdbc.update(BORRAR, ID)).thenReturn(1);
        dao.borrarPendiente(ID);
        verify(jdbc).update(BORRAR, ID);
    }

    @Test void borrarFueraDePendienteEs409() {
        conflicto("El pedido ya no está pendiente (no se puede borrar)", () -> dao.borrarPendiente(ID));
    }

    @Test void unUpdatedAtViejoEs409AntesDeTocarNada() {
        fila(3, null);
        conflicto("Dato modificado por otro usuario", () -> dao.cancelar(ID, AT.minusSeconds(1)));
        verify(jdbc, never()).update(CANCELAR, ID);
    }
}
