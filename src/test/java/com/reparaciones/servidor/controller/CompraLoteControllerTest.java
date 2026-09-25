package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.dao.TipoCambioDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import com.reparaciones.servidor.service.ConversionEur;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST /api/compras/lote (spec 4b §4.4, P5): clave obligatoria, 422 por línea en el orden de la spec, solicitudes
 *  casadas por master, divisa del proveedor y EUR resueltos antes del servicio, reintento idempotente y logs. */
class CompraLoteControllerTest {

    private static final String CLAVE = "clave-1";

    private final CompraLoteService servicio = mock(CompraLoteService.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);
    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final CompraLoteController ctl = new CompraLoteController(servicio, componenteDao, proveedorDao,
            reparacionComponenteDao, solicitudStockDao, new ConversionEur(tipoCambio), logDao, new RegistroIdempotencia());
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraLoteControllerTest() {
        // Componentes: 1 master activo, 4 slave de 1, 5 desactivado
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(componenteDao.getBasico(4)).thenReturn(Optional.of(new ComponenteDAO.Basico(4, 1, "lcd-y-negro", true)));
        when(componenteDao.getBasico(5)).thenReturn(Optional.of(new ComponenteDAO.Basico(5, 5, "bat-x", false)));
        // Proveedores: 2 en EUR, 3 en USD, 6 desactivado
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(3)).thenReturn(Optional.of(new Proveedor(3, "Proveedor B", true, "USD", null, "COMPONENTES")));
        when(proveedorDao.getById(6)).thenReturn(Optional.of(new Proveedor(6, "ACME", false, "EUR", null, "COMPONENTES")));
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41)));
    }

    private static LoteCompras.Linea linea(Integer idCom, Integer idProv, int cantidad, double precio) {
        return new LoteCompras.Linea(idCom, idProv, cantidad, false, precio);
    }

    private static LoteCompras.Peticion lote(LoteCompras.Linea... lineas) {
        return new LoteCompras.Peticion(Arrays.asList(lineas), null);
    }

    private static LoteCompras.Peticion conSolicitudes(List<Integer> urgentes, List<Integer> preventivas,
                                                       LoteCompras.Linea... lineas) {
        return new LoteCompras.Peticion(Arrays.asList(lineas), new LoteCompras.Solicitudes(urgentes, preventivas));
    }

    private String falla422(LoteCompras.Peticion peticion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteCompras(peticion, super7, CLAVE));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaGuardadoNiRegistrado() {
        verifyNoInteractions(servicio, logDao);
    }

    @Test void sinClaveEs400() {
        for (String clave : new String[]{null, "", "  "}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, clave));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertEquals("Falta la clave de idempotencia", e.getReason());
        }
        nadaGuardadoNiRegistrado();
    }

    @Test void sinLineasEs422() {
        assertEquals("Añade al menos una línea.", falla422(lote()));
        assertEquals("Añade al menos una línea.", falla422(new LoteCompras.Peticion(null, null)));
        nadaGuardadoNiRegistrado();
    }

    @Test void lineaSinComponenteOConUnoInexistenteEs422ConSuNumero() {
        assertEquals("Línea 2: selecciona un componente.", falla422(lote(linea(1, 2, 1, 0.0), linea(null, 2, 1, 0.0))));
        assertEquals("Línea 2: selecciona un componente.", falla422(lote(linea(1, 2, 1, 0.0), linea(99, 2, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void lineaSinProveedorOConUnoInexistenteEs422() {
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(1, null, 1, 0.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(1, 99, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    /** Orden de la spec: componente, proveedor, componente desactivado, proveedor desactivado, cantidad, precio. */
    @Test void losErroresDeUnaLineaSalenEnElOrdenDeLaSpec() {
        assertEquals("Línea 1: selecciona un componente.", falla422(lote(linea(null, null, 0, -1.0))));
        assertEquals("Línea 1: selecciona un proveedor.", falla422(lote(linea(5, null, 0, -1.0))));
        assertEquals("Línea 1: el componente está desactivado.", falla422(lote(linea(5, 6, 0, -1.0))));
        assertEquals("Línea 1: el proveedor está desactivado.", falla422(lote(linea(1, 6, 0, -1.0))));
        assertEquals("Línea 1: la cantidad debe ser mayor que 0.", falla422(lote(linea(1, 2, 0, -1.0))));
        assertEquals("Línea 1: el precio no puede ser negativo.", falla422(lote(linea(1, 2, 1, -1.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void unaSolicitudSinLineaEs422() {
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(5);     // bat-x: ninguna línea lo pide
        when(solicitudStockDao.getIdCom(21)).thenReturn(5);
        when(reparacionComponenteDao.getIdComDeSolicitud(12)).thenReturn(null);  // no existe
        String msg = "La solicitud no corresponde a ninguna línea del pedido.";
        assertEquals(msg, falla422(conSolicitudes(List.of(11), List.of(), linea(1, 2, 1, 0.0))));
        assertEquals(msg, falla422(conSolicitudes(List.of(), List.of(21), linea(1, 2, 1, 0.0))));
        assertEquals(msg, falla422(conSolicitudes(List.of(12), List.of(), linea(1, 2, 1, 0.0))));
        nadaGuardadoNiRegistrado();
    }

    @Test void laSolicitudDeUnSlaveCasaConLaLineaDelMasterYSePasaAlServicio() {
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(4);    // slave de 1
        when(solicitudStockDao.getIdCom(21)).thenReturn(1);
        ctl.guardarLoteCompras(conSolicitudes(List.of(11), List.of(21), linea(1, 2, 2, 0.0)), super7, CLAVE);
        verify(servicio).guardarCompras(anyList(), eq(List.of(11)), eq(List.of(21)));
    }

    @Test void laDivisaEsLaDelProveedorYElEurSeCalculaAntesDelServicio() {
        ctl.guardarLoteCompras(lote(linea(1, 3, 2, 10.0), linea(4, 2, 1, 5.5)), super7, CLAVE);
        verify(servicio).guardarCompras(eq(List.of(
                new CompraLoteService.LineaCompra(1, 3, 2, false, 10.0, "USD", 8.8),
                new CompraLoteService.LineaCompra(4, 2, 1, false, 5.5, "EUR", 5.5))), eq(List.of()), eq(List.of()));
    }

    @Test void laMismaClaveYLaMismaPeticionDevuelvenLaRespuestaGuardadaSinInsertar() {
        LoteCompras.Respuesta primera = ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, CLAVE);
        LoteCompras.Respuesta segunda = ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, CLAVE);
        assertSame(primera, segunda);
        assertEquals(List.of(41), segunda.idsCreados());
        verify(servicio, times(1)).guardarCompras(anyList(), anyList(), anyList());
        verify(logDao, times(1)).insertar(eq(7), eq("CREAR_PEDIDO"), anyString());
    }

    @Test void registraUnLogPorLineaYOtroPorSolicitudUnaSolaVez() {
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41, 42)));
        when(reparacionComponenteDao.getIdComDeSolicitud(11)).thenReturn(1);
        when(solicitudStockDao.getIdCom(21)).thenReturn(4);
        ctl.guardarLoteCompras(conSolicitudes(List.of(11), List.of(21),
                linea(1, 2, 3, 0.0), linea(4, 3, 1, 10.0)), super7, CLAVE);
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 3");
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-y-negro, PROVEEDOR: Proveedor B, CANT: 1");
        verify(logDao).insertar(7, "GESTIONAR_SOLICITUD", "ID_RC: 11, ESTADO: GESTIONADA");
        verify(logDao).insertar(7, "GESTIONAR_SOLICITUD_STOCK", "ID_SOL: 21, ESTADO: GESTIONADA");
        verifyNoMoreInteractions(logDao);
    }

    @Test void sinTasaEs503SinLlamarAlServicioYLaClaveSePuedeReintentar() {
        when(tipoCambio.getTasa("USD"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."))
                .thenReturn(1.1367);
        LoteCompras.Peticion peticion = lote(linea(1, 3, 1, 10.0));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarLoteCompras(peticion, super7, CLAVE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        verifyNoInteractions(servicio, logDao);
        // La escritura no llegó a hacerse: RegistroIdempotencia liberó la clave y el reintento sí guarda
        ctl.guardarLoteCompras(peticion, super7, CLAVE);
        verify(servicio).guardarCompras(anyList(), anyList(), anyList());
    }
}
