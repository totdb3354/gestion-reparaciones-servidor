package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.security.ClaveVendidosFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Los IMEI vendidos para el bot de stock (spec «lista de IMEI vendidos para el bot»). No es una ruta de usuario: solo entra la clave
 *  de maquina que comprueba {@link ClaveVendidosFilter}; ningun rol de usuario la abre. Solo lectura. */
@RestController
@RequestMapping(ClaveVendidosFilter.RUTA)
@PreAuthorize("hasRole('" + ClaveVendidosFilter.ROL + "')")
public class VendidosController {

    static final int DIAS_MIN = 1;
    static final int DIAS_MAX = 31;
    static final int TOPE = 5000;
    static final String MSG_DIAS = "dias tiene que estar entre 1 y 31";
    static final String MSG_CANAL = "canal no es uno de los conocidos";

    private final FilasDAO dao;
    private final Clock clock;

    @Autowired
    public VendidosController(FilasDAO dao) {
        this(dao, Clock.system(ImportadorNsys.MADRID));
    }

    VendidosController(FilasDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** Los vendidos cuyo ultimo cambio de IMEI cae en hoy o en los {@code dias - 1} dias de Madrid anteriores. */
    @GetMapping
    public List<Vendido> vendidos(@RequestParam(defaultValue = "7") int dias, @RequestParam(required = false) String canal) {
        if (dias < DIAS_MIN || dias > DIAS_MAX) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_DIAS);
        Canal filtro = null;
        if (canal != null && !canal.isEmpty()) {
            try {
                filtro = Canal.valueOf(canal);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CANAL);
            }
        }
        LocalDateTime desde = LocalDate.now(clock).minusDays(dias - 1L).atStartOfDay();
        return dao.vendidosDesde(desde, filtro, TOPE);
    }
}
