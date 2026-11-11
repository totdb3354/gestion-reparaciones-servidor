package com.reparaciones.servidor.pedidos;

import java.util.List;

/** Qué columnas se leen de una pestaña, por el nombre de su cabecera (fila 1): las que tiene que haber y las que se leen si están. */
public record FormatoHoja(List<String> obligatorias, List<String> opcionales) {}
