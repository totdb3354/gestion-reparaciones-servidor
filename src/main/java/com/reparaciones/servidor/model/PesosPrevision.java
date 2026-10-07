package com.reparaciones.servidor.model;

/** Pesos en % de la previsión de pedidos: días 1-30, 31-60 y 61-90 (spec 0.9.5 §4.2). */
public record PesosPrevision(int peso1, int peso2, int peso3) {}
