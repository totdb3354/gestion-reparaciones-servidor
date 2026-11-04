package com.reparaciones.servidor.pedidos;

import java.util.List;
import java.util.function.Predicate;

/** Cómo se lee una pestaña (spec pieza 2 §4.2-4.3 y ajustes 20-21): las columnas que tiene que tener, las que se leen si están,
 *  la columna clave y cuándo cuenta una fila para saber hasta dónde llegan los datos. */
public record FormatoHoja(List<String> obligatorias, List<String> opcionales, String clave, Predicate<String> cuenta) {}
