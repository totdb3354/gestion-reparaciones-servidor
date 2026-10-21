package com.reparaciones.servidor.nsys;

/** Lo que el guardado cambiará en el móvil (spec 0.9.21 pieza 2 §2.4, Ajuste 6): lo que vale ahora y lo que valdrá (iguales
 *  si nada cambia) y si el color o el grado no se pueden calcular. {@code enNsys} false: NSYS no conoce el IMEI (la web deja
 *  de preguntar). */
public record PreviaTaller(boolean enNsys, ValoresTaller antes, ValoresTaller despues, boolean colorSinDeducir,
                           boolean gradoSinCalcular) {

    public static final PreviaTaller FUERA_DE_NSYS = new PreviaTaller(false, ValoresTaller.VACIO, ValoresTaller.VACIO, false,
            false);
}
