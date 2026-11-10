package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo»; {@code nombre}, la columna «Product Name»
 *  tal cual (0.9.22). {@code skuAmazon} es «SKU (Amazon)», que en una fila eSIM trae el SKU sin eSIM y ya no sirve como SKU de canal. {@code skuAmazonEsim}, {@code skuParticulares} y {@code skuBackiphone} (0.9.31, spec pieza 2 §5.4): los SKU
 *  con los que ese producto llega de Amazon (el único SKU de Amazon para el cruce de pedidos: en cada fila del maestro con SKU
 *  de Amazon está rellena), de la web de particulares y de BackiPhone; null si el maestro no los trae. */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                              String nombre, String skuAmazonEsim, String skuParticulares, String skuBackiphone) {

    /** Sin los SKU de los canales: las pruebas que no los necesitan y los conflictos del maestro. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                           String nombre) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, nombre,
                null, null, null);
    }

    /** Sin el nombre del producto: las pruebas que no lo necesitan. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, null);
    }
}
