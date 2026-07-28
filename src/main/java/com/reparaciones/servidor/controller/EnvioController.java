package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EnvioDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** F2c: remesas de salida. Solo teléfonos OK entran; resultado por IMEI. */
@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final EnvioDAO envioDao;
    private final LogDAO logDao;

    public EnvioController(EnvioDAO envioDao, LogDAO logDao) {
        this.envioDao = envioDao;
        this.logDao = logDao;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public EnvioDAO.ResultadoLote enviar(@RequestBody EnvioRequest req,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean sinCliente = req.idCli() == null;
        boolean sinTexto = req.destinoTexto() == null || req.destinoTexto().isBlank();
        if (sinCliente && sinTexto)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El envío necesita un destino (cliente o texto)");
        EnvioDAO.ResultadoLote r = envioDao.enviarLote(req.idCli(), req.destinoTexto(), req.referencia(), req.imeis(), principal.getIdUsu());
        if (r.idEnvio() != null) {
            long enviados = r.items().stream().filter(i -> "ENVIADO".equals(i.resultado())).count();
            logDao.insertar(principal.getIdUsu(), "ENVIAR_TELEFONOS",
                    "ID_ENVIO: " + r.idEnvio() + ", DESTINO: " + (req.idCli() != null ? "CLI " + req.idCli() : req.destinoTexto())
                    + (req.referencia() != null && !req.referencia().isBlank() ? ", REF: " + req.referencia() : "")
                    + ", ENVIADOS: " + enviados + "/" + r.items().size());
        }
        return r;
    }

    private record EnvioRequest(Integer idCli, String destinoTexto, String referencia, List<String> imeis) {}
}
