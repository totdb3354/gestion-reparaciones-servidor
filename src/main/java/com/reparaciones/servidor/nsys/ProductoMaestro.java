package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo»; {@code nombre}, la columna «Product Name»
 *  tal cual (0.9.22: el nombre del producto sin el grado, del que sale el nombre en inglés de cada color; null si el maestro no
 *  la trae). */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                              String nombre) {

    /** Sin el nombre del producto: las pruebas que no lo necesitan. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, null);
    }
}
