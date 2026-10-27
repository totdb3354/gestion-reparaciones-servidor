package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3 y §4): las listas, «Hecho», «Revisado», los descartes de «No está en NSYS» (pieza 3) y las plantillas para subir a
 *  NSYS. Junto a StockTelefonosController, con la misma guarda (SUPERTECNICO y ADMIN). La lista de la 0.9.21
 *  (GET /api/stock-telefonos/por-pasar) sigue allí para la web anterior. */
@RestController
@RequestMapping("/api/stock-telefonos/por-pasar")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class PorPasarController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Cuántos móviles lleva una plantilla (para el aviso de la web). */
    static final String CABECERA_FILAS = "X-Filas";

    private final PorPasarNsys porPasar;

    public PorPasarController(PorPasarNsys porPasar) {
        this.porPasar = porPasar;
    }

    @GetMapping("/tarjetas")
    public TarjetasPorPasar tarjetas() {
        return porPasar.tarjetas();
    }

    /** «Hecho» de una fila o «Marcar todo» de una tarjeta (§3.6). */
    @PutMapping("/hecho")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcar(@RequestBody PeticionHecho peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.marcar(peticion.tarjeta(), peticion.imeis(), principal.getIdUsu());
    }

    /** «deshacer» de una marca. */
    @DeleteMapping("/hecho")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desmarcar(@RequestBody PeticionDeshacer peticion) {
        porPasar.desmarcar(peticion.tarjeta(), peticion.imeiSn());
    }

    /** «Revisado» de un aviso de «Revisar a mano» (§3.5). */
    @PostMapping("/avisos/{idAviso}/revisado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revisado(@PathVariable int idAviso, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.revisado(idAviso, principal.getIdUsu());
    }

    /** «descartar» de una fila de «No está en NSYS» (pieza 3 §4.3), con motivo. */
    @PutMapping("/descarte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@RequestBody PeticionDescarte peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.descartar(peticion.imeiSn(), peticion.motivo(), principal.getIdUsu());
    }

    /** «deshacer» de un descarte («Ver descartados»). */
    @DeleteMapping("/descarte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerDescarte(@RequestBody PeticionDeshacerDescarte peticion, @AuthenticationPrincipal UsuarioPrincipal principal) {
        porPasar.deshacerDescarte(peticion.imeiSn(), principal.getIdUsu());
    }

    @GetMapping(value = "/plantilla-color", produces = XLSX)
    public ResponseEntity<byte[]> plantillaColor() {
        return descarga(porPasar.plantillaColor());
    }

    @GetMapping(value = "/plantilla-altas", produces = XLSX)
    public ResponseEntity<byte[]> plantillaAltas() {
        return descarga(porPasar.plantillaAltas());
    }

    private static ResponseEntity<byte[]> descarga(PorPasarNsys.Plantilla p) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.nombre()).build().toString())
                .header(CABECERA_FILAS, String.valueOf(p.filas()))
                .body(p.contenido());
    }

    /** Esquema PorPasarPeticionHecho: {@code tarjeta} COLOR, GRADO o ALTA. */
    record PeticionHecho(String tarjeta, List<String> imeis) {}

    /** Esquema PorPasarPeticionDeshacer. */
    record PeticionDeshacer(String tarjeta, String imeiSn) {}

    /** Esquema PorPasarPeticionDescarte. */
    record PeticionDescarte(String imeiSn, String motivo) {}

    /** Esquema PorPasarPeticionDeshacerDescarte. */
    record PeticionDeshacerDescarte(String imeiSn) {}
}
