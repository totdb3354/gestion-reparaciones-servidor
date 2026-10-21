package com.reparaciones.servidor.nsys;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El aviso del SKU del taller en el formulario de reparación (spec 0.9.21, pieza 2, §2.4). Es el formulario del técnico:
 *  técnico, supertécnico y admin; no devuelve precios ni stock. Va aparte de ReparacionController para no tocar sus
 *  guardas (Ajuste 7). Es una lectura: sin registro de actividad ni clave de idempotencia. */
@RestController
@RequestMapping("/api/reparaciones/sku-taller")
@PreAuthorize("hasAnyRole('TECNICO', 'SUPERTECNICO', 'ADMIN')")
public class SkuTallerController {

    private final TallerReparaciones taller;

    public SkuTallerController(TallerReparaciones taller) {
        this.taller = taller;
    }

    /** Lo que el guardado de estas filas cambiará en el móvil, con la misma cuenta que al guardar. */
    @PostMapping("/previsualizar")
    public PreviaTaller previsualizar(@RequestBody PeticionPreviaTaller peticion) {
        return taller.previsualizar(peticion);
    }
}
