package com.reparaciones.servidor.pedidos;

import java.util.List;

/** Las filas de la tabla, en orden de hoja (la mas antigua primero). hayMas: la busqueda tenia mas resultados que el tope. */
public record ListaFilas(List<FilaVista> filas, boolean hayMas) {}
