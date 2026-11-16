package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La base de la asociacion de IMEI y pedido en Amazon: la migracion (la que se aplica en la VM), crear_bd.sql y la migracion de la tabla de
 *  pedidos dicen lo mismo. Se comparan con los espacios juntados y sin «IF NOT EXISTS». */
class DdlAsociacionTest {

    private static final String MIGRACION = "migracion-pedidos-asociacion.sql";

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String junto(String texto) {
        return texto.replace("IF NOT EXISTS ", "").replaceAll("\\s+", " ").strip();
    }

    private static String crear(String sql, String tabla) {
        String junto = junto(sql);
        int desde = junto.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla);
        return junto.substring(desde, junto.indexOf(");", desde) + 2);
    }

    /** La definicion de una columna de Pedido_fila, sin los comentarios del fichero: del nombre al final de su COMMENT. */
    private static String columna(String sql, String nombre) {
        String junto = junto(sql.replaceAll("(?m)^--.*$", ""));
        int desde = junto.indexOf(" " + nombre + " ");
        assertTrue(desde >= 0, nombre);
        int comentario = junto.indexOf("COMMENT '", desde) + "COMMENT '".length();
        return junto.substring(desde, junto.indexOf("'", comentario) + 1).strip();
    }

    @Test void lasDosTablasSonLasMismasEnLosTresFicheros() throws Exception {
        for (String tabla : new String[]{"Pedido_asociacion_revisar", "Pedido_asociacion_pasada"}) {
            String migracion = crear(fichero(MIGRACION), tabla);
            assertEquals(migracion, crear(fichero("crear_bd.sql"), tabla));
            assertEquals(migracion, crear(fichero("migracion-pedidos-tabla.sql"), tabla));
        }
        String revisar = crear(fichero(MIGRACION), "Pedido_asociacion_revisar");
        for (String trozo : new String[]{"ID INT NOT NULL AUTO_INCREMENT", "CANAL VARCHAR(30) NOT NULL", "PEDIDO VARCHAR(60) NOT NULL",
                "IMEI VARCHAR(20) NOT NULL", "MOTIVO VARCHAR(30) NOT NULL", "VISTO_EN DATETIME NOT NULL", "PRIMARY KEY (ID)",
                "KEY idx_asociacion_revisar_canal (CANAL)"})
            assertTrue(revisar.contains(trozo), trozo);
        String pasada = crear(fichero(MIGRACION), "Pedido_asociacion_pasada");
        for (String trozo : new String[]{"CANAL VARCHAR(30) NOT NULL", "CUANDO DATETIME NOT NULL", "CONFIRMADAS INT NOT NULL",
                "INTERCAMBIADAS INT NOT NULL", "PUESTAS INT NOT NULL", "YA_ESTABAN INT NOT NULL", "PARA_REVISAR INT NOT NULL",
                "DESCARTADAS INT NOT NULL", "LIBRO_MODIFICADO DATETIME NULL", "ERROR VARCHAR(500) NULL", "ERROR_EN DATETIME NULL",
                "PRIMARY KEY (CANAL)"})
            assertTrue(pasada.contains(trozo), trozo);
    }

    /** Las tres columnas del lector de los libros: en la migracion van en el CREATE (base sin la tabla) y en un ALTER idempotente (base con
     *  la version anterior de la tabla), con la misma definicion. */
    @Test void lasColumnasDelLectorVanEnElCreateYEnElAlterIgual() throws Exception {
        String sql = fichero(MIGRACION).replaceAll("(?m)^--.*$", "").replaceAll("\\s+", " ");
        String pasada = crear(fichero(MIGRACION), "Pedido_asociacion_pasada");
        int alter = sql.indexOf("ALTER TABLE Pedido_asociacion_pasada ");
        assertTrue(alter > sql.indexOf("CREATE TABLE IF NOT EXISTS Pedido_asociacion_pasada ("), "el ALTER va despues del CREATE");
        String trozoAlter = sql.substring(alter, sql.indexOf("SELECT", alter));   // los comentarios llevan «;»
        for (String nombre : new String[]{"LIBRO_MODIFICADO", "ERROR", "ERROR_EN"}) {
            int desde = pasada.indexOf(" " + nombre + " ");
            String definicion = pasada.substring(desde + 1, pasada.indexOf("'", pasada.indexOf("COMMENT '", desde) + 9) + 1);
            assertTrue(trozoAlter.contains("ADD COLUMN IF NOT EXISTS " + definicion), definicion);
        }
        assertTrue(fichero(MIGRACION).contains("ADD COLUMN IF NOT EXISTS LIBRO_MODIFICADO"));
        assertTrue(fichero(MIGRACION).contains("AS NUEVAS;") && fichero(MIGRACION).contains("NUEVAS debe dar 7"));
    }

    @Test void lasDosColumnasDePedidoFilaSonLasMismasEnLosTresFicheros() throws Exception {
        for (String nombre : new String[]{"ASOCIADA_EN", "ASOCIADA_ORIGEN"}) {
            String migracion = columna(fichero(MIGRACION), nombre);
            assertEquals(migracion, columna(fichero("crear_bd.sql"), nombre));
            assertEquals(migracion, columna(fichero("migracion-pedidos-tabla.sql"), nombre));
        }
        assertTrue(columna(fichero(MIGRACION), "ASOCIADA_EN").startsWith("ASOCIADA_EN DATETIME NULL"));
        assertTrue(columna(fichero(MIGRACION), "ASOCIADA_ORIGEN").startsWith("ASOCIADA_ORIGEN VARCHAR(20) NULL"));
    }

    @Test void laMigracionEsAditivaConVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("USE gestion_reparaciones;"));
        assertTrue(sql.contains("AS YA_ESTAN"), "vista previa");
        assertTrue(sql.contains("AS NUEVAS"), "comprobacion final");
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS ASOCIADA_EN") && sql.contains("ADD COLUMN IF NOT EXISTS ASOCIADA_ORIGEN"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS Pedido_asociacion_revisar")
                && sql.contains("CREATE TABLE IF NOT EXISTS Pedido_asociacion_pasada"));
        String sinComentarios = sql.replaceAll("(?m)^--.*$", "");
        for (String peligro : new String[]{"DROP ", "DELETE ", "UPDATE ", "TRUNCATE ", "MODIFY ", "CHANGE "})
            assertFalse(sinComentarios.contains(peligro), peligro);
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Pedido_asociacion_revisar;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Pedido_asociacion_pasada;"));
    }
}
