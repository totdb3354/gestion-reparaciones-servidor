package com.reparaciones.servidor.pedidos;

/** Los recuentos de una pasada de asociaciones (POST /api/pedidos/asociaciones). Cada par recibido cuenta en uno solo:
 *  {@code confirmadas} (el IMEI ya estaba en una fila del pedido y se confirmo), {@code intercambiadas} (estaba en una fila de otro pedido
 *  sin etiqueta y se intercambio), {@code puestas} (no estaba en ninguna fila y se puso en una fila libre del pedido), {@code yaEstaban} (ya estaba
 *  confirmado: no se toco nada), {@code paraRevisar} (no se toco nada y queda en la lista «para revisar») y {@code descartadas} (IMEI que
 *  no es de 15 cifras, pedido vacio, o un IMEI que vuelve a salir mas abajo en la misma pasada: vale la ultima vez). */
public record RespuestaAsociaciones(int confirmadas, int intercambiadas, int puestas, int yaEstaban, int paraRevisar, int descartadas) {}
