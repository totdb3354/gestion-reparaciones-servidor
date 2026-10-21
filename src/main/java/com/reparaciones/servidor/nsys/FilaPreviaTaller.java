package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una fila del formulario para la previsualización (spec 0.9.21 pieza 2 §2.4, Ajuste 6): su componente y si es reutilizada.
 *  {@code idRep}: null en una fila nueva; el de la R… que se edita en «Guardar cambios» (se deshace lo suyo y se aplica la
 *  nueva, como al guardar). */
public record FilaPreviaTaller(@Schema(nullable = true) String idRep, int idCom, boolean reutilizada) {}
