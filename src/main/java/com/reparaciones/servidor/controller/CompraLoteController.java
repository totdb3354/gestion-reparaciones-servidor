package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.LoteComprasOtros;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import com.reparaciones.servidor.service.ConversionEur;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.reparaciones.servidor.controller.ValidacionPedidos.*;

/** Alta por lotes de los formularios "Nuevo pedido" y "Nuevo otro pedido" (spec 4b §4.4, P5): una petición, una
 *  transacción y una clave de idempotencia por guardado. Los POST sueltos de CompraController y CompraOtroController
 *  siguen para el JavaFX. Controlador propio para no tocar los constructores de los otros dos. */
@RestController
@RequestMapping("/api")
public class CompraLoteController {

    static final String OP_COMPRAS = "compras-lote";
    static final String OP_OTROS   = "compras-otros-lote";

    private final CompraLoteService servicio;
    private final ComponenteDAO componenteDao;
    private final ProveedorDAO proveedorDao;
    private final ReparacionComponenteDAO reparacionComponenteDao;
    private final SolicitudStockDAO solicitudStockDao;
    private final ConversionEur conversion;
    private final LogDAO logDao;
    private final RegistroIdempotencia idempotencia;

    public CompraLoteController(CompraLoteService servicio, ComponenteDAO componenteDao, ProveedorDAO proveedorDao,
                                ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao,
                                ConversionEur conversion, LogDAO logDao, RegistroIdempotencia idempotencia) {
        this.servicio = servicio;
        this.componenteDao = componenteDao;
        this.proveedorDao = proveedorDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
        this.conversion = conversion;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras/lote")
    public LoteCompras.Respuesta guardarLoteCompras(@RequestBody LoteCompras.Peticion req,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal,
                                                    @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                    String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        List<LineaValidada> lineas = validarLineasCompra(req.lineas());
        List<Integer> urgentes = lista(req.solicitudes() == null ? null : req.solicitudes().urgentes());
        List<Integer> preventivas = lista(req.solicitudes() == null ? null : req.solicitudes().preventivas());
        validarSolicitudes(lineas, urgentes, preventivas);
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_COMPRAS, claveIdempotencia, req,
                // La tasa (Frankfurter: lenta y externa) se resuelve aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardarCompras(resolverCompras(lineas), urgentes, preventivas),
                r -> registrarLogsCompras(lineas, urgentes, preventivas, idUsu));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras-otros/lote")
    public LoteCompras.Respuesta guardarLoteOtros(@RequestBody LoteComprasOtros.Peticion req,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal,
                                                  @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                  String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        List<OtroValidado> lineas = validarLineasOtro(req.lineas());
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_OTROS, claveIdempotencia, req,
                () -> servicio.guardarOtros(resolverOtros(lineas)),
                r -> registrarLogsOtros(lineas, idUsu));
    }

    // ── validación ───────────────────────────────────────────────────────────

    static void exigirClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AsignacionController.MSG_SIN_CLAVE);
        }
    }

    private record LineaValidada(LoteCompras.Linea linea, ComponenteDAO.Basico componente, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: componente, proveedor, desactivados, cantidad, precio. */
    private List<LineaValidada> validarLineasCompra(List<LoteCompras.Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) throw regla(MSG_SIN_LINEAS);
        List<LineaValidada> validadas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int n = i + 1;
            LoteCompras.Linea l = lineas.get(i);
            ComponenteDAO.Basico c = l == null || l.idCom() == null ? null : componenteDao.getBasico(l.idCom()).orElse(null);
            if (c == null) throw enLinea(n, L_COMPONENTE);
            Proveedor p = proveedor(l.idProv());
            if (p == null) throw enLinea(n, L_PROVEEDOR);
            if (!c.activo()) throw enLinea(n, L_COMPONENTE_OFF);
            if (!p.isActivo()) throw enLinea(n, L_PROVEEDOR_OFF);
            if (l.cantidad() <= 0) throw enLinea(n, L_CANTIDAD);
            if (!Double.isFinite(l.precioUnidad()) || l.precioUnidad() < 0) throw enLinea(n, L_PRECIO);
            validadas.add(new LineaValidada(l, c, p));
        }
        return validadas;
    }

    private Proveedor proveedor(Integer idProv) {
        return idProv == null ? null : proveedorDao.getById(idProv).orElse(null);
    }

    private record OtroValidado(LoteComprasOtros.Linea linea, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: concepto, proveedor, proveedor desactivado, cantidad, precio. */
    private List<OtroValidado> validarLineasOtro(List<LoteComprasOtros.Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) throw regla(MSG_SIN_LINEAS);
        List<OtroValidado> validadas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int n = i + 1;
            LoteComprasOtros.Linea l = lineas.get(i);
            if (l == null || l.concepto() == null || l.concepto().isBlank()) throw enLinea(n, L_CONCEPTO);
            Proveedor p = proveedor(l.idProv());
            if (p == null) throw enLinea(n, L_PROVEEDOR);
            if (!p.isActivo()) throw enLinea(n, L_PROVEEDOR_OFF);
            if (l.cantidad() <= 0) throw enLinea(n, L_CANTIDAD);
            if (!Double.isFinite(l.precioUnidad()) || l.precioUnidad() < 0) throw enLinea(n, L_PRECIO);
            validadas.add(new OtroValidado(l, p));
        }
        return validadas;
    }

    /** Cada solicitud tiene que pedir el componente (resuelto al master) de alguna línea; si no existe, tampoco casa. */
    private void validarSolicitudes(List<LineaValidada> lineas, List<Integer> urgentes, List<Integer> preventivas) {
        Set<Integer> masters = new HashSet<>();
        for (LineaValidada v : lineas) masters.add(v.componente().idMaster());
        for (Integer idRc : urgentes) {
            Integer idCom = idRc == null ? null : reparacionComponenteDao.getIdComDeSolicitud(idRc);
            if (!masters.contains(masterDe(idCom))) throw regla(MSG_SOLICITUD_SIN_LINEA);
        }
        for (Integer idSol : preventivas) {
            Integer idCom = idSol == null ? null : solicitudStockDao.getIdCom(idSol);
            if (!masters.contains(masterDe(idCom))) throw regla(MSG_SOLICITUD_SIN_LINEA);
        }
    }

    private Integer masterDe(Integer idCom) {
        if (idCom == null) return null;
        return componenteDao.getBasico(idCom).map(ComponenteDAO.Basico::idMaster).orElse(null);
    }

    private static List<Integer> lista(List<Integer> ids) {
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    // ── resolución (fuera de la transacción) ─────────────────────────────────

    /** Divisa del proveedor (nula o en blanco = EUR, el DEFAULT de la columna) y EUR con ConversionEur. */
    private List<CompraLoteService.LineaCompra> resolverCompras(List<LineaValidada> lineas) {
        List<CompraLoteService.LineaCompra> resueltas = new ArrayList<>();
        for (LineaValidada v : lineas) {
            String divisa = divisaDe(v.proveedor());
            LoteCompras.Linea l = v.linea();
            resueltas.add(new CompraLoteService.LineaCompra(v.componente().idCom(), v.proveedor().getIdProv(),
                    l.cantidad(), l.esUrgente(), l.precioUnidad(), divisa, conversion.aEuros(l.precioUnidad(), divisa)));
        }
        return resueltas;
    }

    static String divisaDe(Proveedor p) {
        String d = p.getDivisa();
        return d == null || d.isBlank() ? "EUR" : d.trim().toUpperCase();
    }

    private List<CompraLoteService.LineaOtro> resolverOtros(List<OtroValidado> lineas) {
        List<CompraLoteService.LineaOtro> resueltas = new ArrayList<>();
        for (OtroValidado v : lineas) {
            String divisa = divisaDe(v.proveedor());
            LoteComprasOtros.Linea l = v.linea();
            resueltas.add(new CompraLoteService.LineaOtro(v.proveedor().getIdProv(), l.concepto(), l.cantidad(),
                    l.esUrgente(), l.precioUnidad(), divisa, conversion.aEuros(l.precioUnidad(), divisa)));
        }
        return resueltas;
    }

    // ── logs (trasEscribir: una sola vez, tras la transacción) ───────────────

    /** Los mismos textos que POST /api/compras y los PATCH de estado de las dos solicitudes. */
    private void registrarLogsCompras(List<LineaValidada> lineas, List<Integer> urgentes, List<Integer> preventivas,
                                      int idUsu) {
        for (LineaValidada v : lineas) {
            logDao.insertar(idUsu, "CREAR_PEDIDO", "COMPONENTE: " + v.componente().tipo()
                    + ", PROVEEDOR: " + v.proveedor().getNombre() + ", CANT: " + v.linea().cantidad());
        }
        for (Integer idRc : urgentes) {
            logDao.insertar(idUsu, "GESTIONAR_SOLICITUD", "ID_RC: " + idRc + ", ESTADO: GESTIONADA");
        }
        for (Integer idSol : preventivas) {
            logDao.insertar(idUsu, "GESTIONAR_SOLICITUD_STOCK", "ID_SOL: " + idSol + ", ESTADO: GESTIONADA");
        }
    }

    /** El mismo texto que POST /api/compras-otros. */
    private void registrarLogsOtros(List<OtroValidado> lineas, int idUsu) {
        for (OtroValidado v : lineas) {
            logDao.insertar(idUsu, "CREAR_PEDIDO_OTRO", "CONCEPTO: " + v.linea().concepto()
                    + ", PROVEEDOR: " + v.proveedor().getNombre() + ", CANT: " + v.linea().cantidad());
        }
    }
}
