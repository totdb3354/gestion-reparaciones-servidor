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
import static org.mockito.Mockito.*;

/** Matriz de estados del servidor (spec 4b §4.1): cada transición es un UPDATE condicionado al estado que exige y
 *  responde 409 si no toca ninguna fila; las que suman o restan stock no lo tocan si el estado no cuadra. El lock
 *  de updatedAt se sigue comprobando antes. Un UPDATE sin stub devuelve 0 filas: es el caso "estado incorrecto". */
class CompraComponenteDAOTransicionesTest {

    private static final int ID = 5;
    private static final int ID_COM = 10;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0, 0);

    private static final String SUMAR_STOCK  = "UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?";
    private static final String RESTAR_STOCK = "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?";
    private static final String CONFIRMAR = "UPDATE Compra_componente SET ESTADO='en_camino' WHERE ID_COMPRA=? AND ESTADO='pendiente'";
    private static final String RECIBIDO = "UPDATE Compra_componente SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String PARCIAL = "UPDATE Compra_componente SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String RESTO_COMPLETO = "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?, ESTADO = 'recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String RESTO_PARCIAL = "UPDATE Compra_componente SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ? WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String ALTERADO = "UPDATE Compra_componente SET ESTADO='recibido' WHERE ID_COMPRA=? AND ESTADO='parcial'";
    private static final String CANCELAR = "UPDATE Compra_componente SET ESTADO='cancelado' WHERE ID_COMPRA=? AND ESTADO='en_camino'";
    private static final String DESRECIBIR = "UPDATE Compra_componente SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL WHERE ID_COMPRA=? AND ESTADO='recibido'";
    private static final String EDITAR = "UPDATE Compra_componente SET ID_PROV=?, CANTIDAD=?, ES_URGENTE=?, PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA=? AND (ESTADO IN ('pendiente','en_camino') OR (ESTADO='recibido' AND CANTIDAD=?))";
    private static final String BORRAR = "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente'";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraComponenteDAO dao = new CompraComponenteDAO(jdbc);

    /** La fila que lee getCompraRow: componente, cantidad pedida, recibida y el updatedAt guardado (= AT). */
    private void fila(int cantidad, Integer recibida) {
        when(jdbc.queryForObject(contains("FROM Compra_componente WHERE ID_COMPRA = ?"), any(RowMapper.class), eq(ID)))
                .thenReturn(new CompraComponenteDAO.CompraRow(ID_COM, cantidad, recibida, AT));
    }

    private void stockActual(int stock) {
        when(jdbc.queryForObject(contains("SELECT STOCK FROM Componente"), eq(Integer.class), eq(ID_COM))).thenReturn(stock);
    }

    private static void conflicto(String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    private void sinTocarStock() {
        verify(jdbc, never()).update(eq(SUMAR_STOCK), any(Object[].class));
        verify(jdbc, never()).update(eq(RESTAR_STOCK), any(Object[].class));
    }

    // ── confirmar (pendiente → en_camino) ──
    @Test void confirmarDesdePendientePasaAEnCamino() {
        fila(3, null);
        when(jdbc.update(CONFIRMAR, ID)).thenReturn(1);
        dao.confirmar(ID, AT);
        verify(jdbc).update(CONFIRMAR, ID);
        sinTocarStock();
    }

    @Test void confirmarFueraDePendienteEs409() {
        fila(3, null);
        conflicto("El pedido ya no está pendiente", () -> dao.confirmar(ID, AT));
    }

    // ── confirmar-recibido (en_camino → recibido, + cantidad pedida) ──
    @Test void recibirDesdeEnCaminoSumaLaCantidadPedida() {
        fila(3, null);
        when(jdbc.update(RECIBIDO, ID)).thenReturn(1);
        dao.confirmarRecibido(ID, AT);
        verify(jdbc).update(SUMAR_STOCK, 3, ID_COM);
    }

    @Test void recibirFueraDeEnCaminoEs409SinTocarStock() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarRecibido(ID, AT));
        sinTocarStock();
    }

    // ── confirmar-parcial (en_camino → parcial, + recibida) ──
    @Test void parcialDesdeEnCaminoSumaLoRecibido() {
        fila(5, null);
        when(jdbc.update(PARCIAL, 2, ID)).thenReturn(1);
        dao.confirmarParcial(ID, 2, AT);
        verify(jdbc).update(SUMAR_STOCK, 2, ID_COM);
    }

    @Test void parcialFueraDeEnCaminoEs409SinTocarStock() {
        fila(5, null);
        conflicto("El pedido ya no está en camino", () -> dao.confirmarParcial(ID, 2, AT));
        sinTocarStock();
    }

    // ── recibir-resto (parcial → recibido si completa; si no, sigue parcial; + extra) ──
    @Test void restoQueCompletaPasaARecibidoYSuma() {
        fila(5, 2);
        when(jdbc.update(RESTO_COMPLETO, 3, ID)).thenReturn(1);
        dao.recibirResto(ID, 3, AT);
        verify(jdbc).update(SUMAR_STOCK, 3, ID_COM);
    }

    @Test void restoQueNoCompletaSigueParcialYSuma() {
        fila(5, 2);
        when(jdbc.update(RESTO_PARCIAL, 1, ID)).thenReturn(1);
        dao.recibirResto(ID, 1, AT);
        verify(jdbc).update(SUMAR_STOCK, 1, ID_COM);
    }

    @Test void restoFueraDeParcialEs409SinTocarStock() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.recibirResto(ID, 3, AT));
        sinTocarStock();
    }

    // ── confirmar-alterado (parcial → recibido, sin stock) ──
    @Test void alteradoDesdeParcialPasaARecibido() {
        fila(5, 2);
        when(jdbc.update(ALTERADO, ID)).thenReturn(1);
        dao.confirmarAlterado(ID, AT);
        verify(jdbc).update(ALTERADO, ID);
        sinTocarStock();
    }

    @Test void alteradoFueraDeParcialEs409() {
        fila(5, 2);
        conflicto("El pedido ya no está en recepción parcial", () -> dao.confirmarAlterado(ID, AT));
    }

    // ── cancelar (en_camino → cancelado) ──
    @Test void cancelarDesdeEnCaminoCancela() {
        fila(3, null);
        when(jdbc.update(CANCELAR, ID)).thenReturn(1);
        dao.cancelar(ID, AT);
        verify(jdbc).update(CANCELAR, ID);
        sinTocarStock();
    }

    @Test void cancelarFueraDeEnCaminoEs409() {
        fila(3, null);
        conflicto("El pedido ya no está en camino", () -> dao.cancelar(ID, AT));
    }

    // ── desrecibir (recibido → en_camino, − recibida ?? pedida) ──
    @Test void desrecibirDesdeRecibidoRestaLoRecibido() {
        fila(5, 4);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        stockActual(10);
        dao.desrecibir(ID, AT);
        verify(jdbc).update(RESTAR_STOCK, 4, ID_COM);
    }

    @Test void desrecibirFueraDeRecibidoEs409SinTocarStock() {
        fila(5, null);
        conflicto("El pedido ya no está recibido", () -> dao.desrecibir(ID, AT));
        sinTocarStock();
    }

    /** El 409 de stock se mantiene; el UPDATE del pedido ya hecho lo deshace la transacción (@Transactional). */
    @Test void desrecibirConStockInsuficienteSigueSiendo409SinRestar() {
        fila(5, null);
        when(jdbc.update(DESRECIBIR, ID)).thenReturn(1);
        stockActual(2);
        conflicto("Stock insuficiente para deshacer la recepción (stock actual: 2, a descontar: 5)",
                () -> dao.desrecibir(ID, AT));
        sinTocarStock();
    }

    // ── editar (pendiente, en_camino o recibido con la misma cantidad) ──
    @Test void editarEnUnEstadoEditableEscribe() {
        fila(4, null);
        when(jdbc.update(EDITAR, 2, 4, true, 10.0, "USD", 8.8, ID, 4)).thenReturn(1);
        dao.editar(ID, 2, 4, true, 10.0, "USD", 8.8, AT);
        verify(jdbc).update(EDITAR, 2, 4, true, 10.0, "USD", 8.8, ID, 4);
    }

    @Test void editarEnUnEstadoNoEditableEs409() {
        fila(4, null);
        conflicto("El pedido no se puede editar en su estado actual",
                () -> dao.editar(ID, 2, 4, true, 10.0, "USD", 8.8, AT));
    }

    // ── borrar (pendiente; sin updatedAt, como hoy) ──
    @Test void borrarUnPendienteBorra() {
        when(jdbc.update(BORRAR, ID)).thenReturn(1);
        dao.borrarPendiente(ID);
        verify(jdbc).update(BORRAR, ID);
    }

    @Test void borrarFueraDePendienteEs409() {
        conflicto("El pedido ya no está pendiente (no se puede borrar)", () -> dao.borrarPendiente(ID));
    }

    // ── orden: updatedAt antes que el estado ──
    @Test void unUpdatedAtViejoEs409AntesDeTocarNada() {
        fila(3, null);
        conflicto("Dato modificado por otro usuario", () -> dao.confirmarRecibido(ID, AT.minusSeconds(1)));
        verify(jdbc, never()).update(RECIBIDO, ID);
        sinTocarStock();
    }
}
