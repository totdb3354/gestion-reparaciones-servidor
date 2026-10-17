package com.reparaciones.servidor.nsys;

/** Anchos de las columnas de texto del espejo de NSYS. Manda el DDL de sql/migracion-nsys-espejo.sql, y AnchosNsysTest
 *  comprueba que coinciden. Los lectores cortan cada campo a su ancho al leer, en un solo sitio: así las claves y las
 *  comparaciones van con lo mismo que guarda la base de datos, sin eventos fantasma ni «Data too long». */
final class AnchosNsys {

    // Nsys_movil. Los eventos (Nsys_evento: DE, A, A_NSYS) solo llevan estos campos o ubicaciones, y caben.
    static final int IMEI_SN = 40;
    static final int BATCH = 40;
    static final int GOODS = 150;
    static final int GRADE = 20;
    static final int STATUS = 30;
    static final int LOCATION = 80;
    static final int SUPPLIER = 80;

    // Nsys_producto. Nsys_movil copia SKU, MODELO_MAESTRO, CAPACIDAD, COLOR y la calidad (GRADO_DETALLE) con el mismo ancho.
    static final int CLAVE = 160;
    static final int MODELO_MAESTRO = 40;
    static final int CAPACIDAD = 20;
    static final int COLOR = 40;
    static final int CALIDAD_DETALLE = 20;
    static final int SKU = 60;

    // Nsys_importacion
    static final int FICHERO = 120;
    static final int MENSAJE = 500;

    private AnchosNsys() {}

    /** El texto cortado a {@code ancho} caracteres, sin partir un carácter de dos mitades (emoji); null sigue siendo null. */
    static String corta(String texto, int ancho) {
        if (texto == null || texto.length() <= ancho) return texto;
        return texto.substring(0, Character.isHighSurrogate(texto.charAt(ancho - 1)) ? ancho - 1 : ancho);
    }
}
