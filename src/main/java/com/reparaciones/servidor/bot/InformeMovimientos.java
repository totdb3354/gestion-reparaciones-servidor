package com.reparaciones.servidor.bot;

/** Respuesta del POST: cuantos movimientos se guardaron y cuantos ya estaban (reenvio). */
public record InformeMovimientos(int guardados, int repetidos) {}
