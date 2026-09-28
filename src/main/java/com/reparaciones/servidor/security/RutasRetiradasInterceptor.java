package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.LogDAO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rechaza con 403 las rutas de {@link RutasRetiradas} y anota el intento en el registro de actividad, con quién,
 * qué pidió y desde dónde. Corre después de la cadena de seguridad, así que el usuario del token ya está en el
 * contexto. Si el registro falla, el rechazo se mantiene: la traza va al log de la aplicación.
 */
@Component
public class RutasRetiradasInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RutasRetiradasInterceptor.class);

    private final LogDAO logDao;

    public RutasRetiradasInterceptor(LogDAO logDao) {
        this.logDao = logDao;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String metodo = request.getMethod();
        String uri    = request.getRequestURI();
        if (!RutasRetiradas.coincide(metodo, uri)) return true;
        anotar(metodo, uri, origen(request));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, RutasRetiradas.MSG_RETIRADA);
    }

    /** La dirección que ve la aplicación: la del proxy si la pone, y si no la del socket. */
    private static String origen(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Real-IP");
        if (reenviada != null && !reenviada.isBlank()) return reenviada;
        String cadena = request.getHeader("X-Forwarded-For");
        if (cadena != null && !cadena.isBlank()) return cadena.split(",")[0].trim();
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private void anotar(String metodo, String uri, String origen) {
        Integer idUsu = idUsuarioDelContexto();
        String detalle = metodo + " " + uri + (origen.isBlank() ? "" : ", ORIGEN: " + origen);
        if (idUsu == null) {
            log.warn("Intento a ruta retirada sin usuario en el contexto: {}", detalle);
            return;
        }
        try {
            logDao.insertar(idUsu, RutasRetiradas.ACCION_LOG, detalle);
        } catch (RuntimeException e) {
            log.warn("No se pudo anotar el intento a ruta retirada ({}): {}", detalle, e.toString());
        }
    }

    private static Integer idUsuarioDelContexto() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal p)) return null;
        return p.getIdUsu();
    }
}
