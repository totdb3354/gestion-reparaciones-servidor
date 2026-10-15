package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.DestinoPedido;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.reparaciones.servidor.controller.ValidacionPedidos.*;

/** Alta por lotes del formulario "Nuevo pedido" (spec 4b §4.4, P5): una petición, una transacción y una clave de
 *  idempotencia por guardado. Los POST sueltos de CompraController y CompraOtroController siguen para el JavaFX.
 *  Desde la 0.9.15 (spec «Añadir otro» §4.2) `POST /api/compras/lote` es «Nuevo pedido» con piezas y otros (`otros`),
 *  y cada otro va al bloque de su proveedor con las piezas. `POST /api/compras-otros/lote` sigue para la web anterior
 *  durante los despliegues (cada otro al bloque abierto por defecto de su proveedor). */
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
    private final RegistroBloques registroBloques;

    public CompraLoteController(CompraLoteService servicio, ComponenteDAO componenteDao, ProveedorDAO proveedorDao,
                                ReparacionComponenteDAO reparacionComponenteDao, SolicitudStockDAO solicitudStockDao,
                                ConversionEur conversion, LogDAO logDao, RegistroIdempotencia idempotencia,
                                RegistroBloques registroBloques) {
        this.servicio = servicio;
        this.componenteDao = componenteDao;
        this.proveedorDao = proveedorDao;
        this.reparacionComponenteDao = reparacionComponenteDao;
        this.solicitudStockDao = solicitudStockDao;
        this.conversion = conversion;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
        this.registroBloques = registroBloques;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras/lote")
    public LoteCompras.Respuesta guardarLoteCompras(@RequestBody LoteCompras.Peticion req,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal,
                                                    @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                    String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        List<LoteComprasOtros.Linea> pedidosOtros = req.otros() == null ? List.of() : req.otros();
        // 0.9.15: primero todas las piezas («Línea n: …», como siempre) y después los otros («Otro n: …»). Sin piezas
        // vale si hay otros.
        List<LineaValidada> lineas = validarLineasCompra(req.lineas(), pedidosOtros.isEmpty());
        List<OtroValidado> otros = validarOtros(pedidosOtros, ValidacionPedidos::enOtro);
        List<Integer> urgentes = lista(req.solicitudes() == null ? null : req.solicitudes().urgentes());
        List<Integer> preventivas = lista(req.solicitudes() == null ? null : req.solicitudes().preventivas());
        validarSolicitudes(lineas, urgentes, preventivas);
        Map<Integer, DestinoPedido> destinos = destinos(req.destinos());
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_COMPRAS, claveIdempotencia, req,
                // La tasa (Frankfurter: lenta y externa) se resuelve aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardarCompras(resolverCompras(lineas), urgentes, preventivas, destinos, resolverOtros(otros)),
                r -> {
                    for (CompraLoteService.BloqueCreado b : r.bloquesCreados()) registroBloques.creado(idUsu, b.idBloque(), b.idProv());
                    registrarLogsCompras(lineas, urgentes, preventivas, idUsu);
                    registrarLogsOtros(otros, r.bloquePorProveedor(), idUsu);
                }).respuesta();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/compras-otros/lote")
    public LoteCompras.Respuesta guardarLoteOtros(@RequestBody LoteComprasOtros.Peticion req,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal,
                                                  @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                                  String claveIdempotencia) {
        exigirClave(claveIdempotencia);
        if (req.lineas() == null || req.lineas().isEmpty()) throw regla(MSG_SIN_LINEAS);
        List<OtroValidado> lineas = validarOtros(req.lineas(), ValidacionPedidos::enLinea);
        int idUsu = principal.getIdUsu();
        // 0.9.15: cada otro va al bloque abierto más reciente de su proveedor o a uno nuevo (la web 0.9.14 usa este lote).
        return idempotencia.ejecutar(idUsu, OP_OTROS, claveIdempotencia, req,
                () -> servicio.guardarOtros(resolverOtros(lineas)),
                r -> {
                    for (CompraLoteService.BloqueCreado b : r.bloquesCreados()) registroBloques.creado(idUsu, b.idBloque(), b.idProv());
                    registrarLogsOtros(lineas, r.bloquePorProveedor(), idUsu);
                }).respuesta();
    }

    // ── validación ───────────────────────────────────────────────────────────

    static void exigirClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AsignacionController.MSG_SIN_CLAVE);
        }
    }

    private record LineaValidada(LoteCompras.Linea linea, ComponenteDAO.Basico componente, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: componente, proveedor, desactivados, cantidad, precio. Sin piezas es 422
     *  salvo que el lote traiga otros (0.9.15): `exigirAlguna` = no trae otros. */
    private List<LineaValidada> validarLineasCompra(List<LoteCompras.Linea> lineas, boolean exigirAlguna) {
        List<LoteCompras.Linea> lista = lineas == null ? List.of() : lineas;
        if (lista.isEmpty() && exigirAlguna) throw regla(MSG_SIN_LINEAS);
        List<LineaValidada> validadas = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            int n = i + 1;
            LoteCompras.Linea l = lista.get(i);
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

    /** `concepto` es el de la línea recortado (0.9.15): el que se mide, se guarda y se registra. */
    private record OtroValidado(LoteComprasOtros.Linea linea, String concepto, Proveedor proveedor) {}

    /** Por línea y en el orden de la spec: concepto, concepto de más de 255 (0.9.15), proveedor, proveedor
     *  desactivado, cantidad, precio. `error` da el «Línea n: …» del lote de otros o el «Otro n: …» de «Nuevo pedido». */
    private List<OtroValidado> validarOtros(List<LoteComprasOtros.Linea> lineas,
                                            BiFunction<Integer, String, ResponseStatusException> error) {
        List<OtroValidado> validadas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            int n = i + 1;
            LoteComprasOtros.Linea l = lineas.get(i);
            if (l == null || l.concepto() == null || l.concepto().isBlank()) throw error.apply(n, L_CONCEPTO);
            String concepto = l.concepto().trim();
            if (concepto.length() > CONCEPTO_MAX) throw error.apply(n, L_CONCEPTO_LARGO);
            Proveedor p = proveedor(l.idProv());
            if (p == null) throw error.apply(n, L_PROVEEDOR);
            if (!p.isActivo()) throw error.apply(n, L_PROVEEDOR_OFF);
            if (l.cantidad() <= 0) throw error.apply(n, L_CANTIDAD);
            if (!Double.isFinite(l.precioUnidad()) || l.precioUnidad() < 0) throw error.apply(n, L_PRECIO);
            validadas.add(new OtroValidado(l, concepto, p));
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

    /** Destino por proveedor (0.9.9): estar en la lista = elegido por la web; idBloque nulo = bloque nuevo. Con un
     *  proveedor repetido vale el último. Vale para sus piezas y sus otros (0.9.15). */
    static Map<Integer, DestinoPedido> destinos(List<LoteCompras.Destino> lista) {
        Map<Integer, DestinoPedido> m = new HashMap<>();
        if (lista != null) {
            for (LoteCompras.Destino d : lista) {
                if (d != null) m.put(d.idProv(), new DestinoPedido(d.idBloque()));
            }
        }
        return m;
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
            resueltas.add(new CompraLoteService.LineaOtro(v.proveedor().getIdProv(), v.concepto(), l.cantidad(),
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

    /** El mismo texto que POST /api/compras-otros, con el bloque al que fue (0.9.15). */
    private void registrarLogsOtros(List<OtroValidado> lineas, Map<Integer, Integer> bloquePorProveedor, int idUsu) {
        for (OtroValidado v : lineas) {
            logDao.insertar(idUsu, "CREAR_PEDIDO_OTRO", "CONCEPTO: " + v.concepto()
                    + ", PROVEEDOR: " + v.proveedor().getNombre() + ", CANT: " + v.linea().cantidad()
                    + ", BLOQUE: " + bloquePorProveedor.get(v.proveedor().getIdProv()));
        }
    }
}
