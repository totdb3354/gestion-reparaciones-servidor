package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BloqueCompraService con las DAO mockeadas y el DestinoBloque real (0.9.9, spec «Pedidos por bloques» §5). */
class BloqueCompraServiceTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    final BloqueCompraDAO bloqueDao = mock(BloqueCompraDAO.class);
    final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    final BloqueCompraService servicio = new BloqueCompraService(bloqueDao, compraDao, new DestinoBloque(bloqueDao));

    static BloqueCompraDAO.Bloque bloque(int id, int idProv) {
        return new BloqueCompraDAO.Bloque(id, idProv, AT, null, false, AT);
    }

    /** Línea 5 del proveedor 2 en el bloque `idBloque`, con UPDATED_AT = AT. */
    static CompraComponente linea(String estado, Integer idBloque) {
        return new CompraComponente(5, 1, "lcd-x-negro", 2, "Proveedor A", 3, null, false, AT, null, 10.0, "EUR", 10.0,
                estado, AT).conBloque(idBloque, AT, null, AT, false);
    }

    static void falla(HttpStatus estado, String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(estado, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    // ── alta suelta ──
    @Test void altaSueltaVaAlAbiertoDelProveedor() {
        when(bloqueDao.abiertoMasReciente(2)).thenReturn(Optional.of(41));
        assertEquals(new DestinoBloque.Destino(41, false), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 41);
    }

    @Test void altaSueltaSinAbiertoCreaUnBloque() {
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 90);
    }
}
