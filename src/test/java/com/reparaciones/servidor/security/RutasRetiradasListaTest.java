package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las 24 rutas de primera generación, y las vivas que se les parecen (spec sp7b §4.1). */
class RutasRetiradasListaTest {

    private static final List<String> RETIRADAS = List.of(
            "GET /api/componentes",
            "GET /api/componentes/stock-bajo",
            "GET /api/componentes/chasis",
            "GET /api/componentes/evolucion-stock",
            "POST /api/componentes",
            "PATCH /api/componentes/101/stock",
            "DELETE /api/componentes/101",
            "GET /api/compras/en-camino",
            "GET /api/reparacion-componentes/R20260928_1",
            "POST /api/reparacion-componentes",
            "DELETE /api/reparacion-componentes/R20260928_1/101",
            "PATCH /api/reparacion-componentes/R20260928_1/incidencia",
            "GET /api/reparaciones",
            "GET /api/reparaciones/imei/355400000000111/count",
            "GET /api/reparaciones/historial/imei/355400000000111",
            "GET /api/reparaciones/asignaciones/imei/355400000000111",
            "GET /api/reparaciones/imei/355400000000111/tecnicos-asignados",
            "GET /api/reparaciones/estadisticas",
            "POST /api/reparaciones",
            "PATCH /api/reparaciones/R20260928_1/completar",
            "POST /api/tecnicos",
            "DELETE /api/tecnicos/7",
            "GET /api/telefonos",
            "GET /api/telefonos/355400000000111/exists");

    /** Rutas vivas que comparten prefijo con alguna retirada: el matcher no debe tocarlas. */
    private static final List<String> VIVAS = List.of(
            "GET /api/componentes/agrupados",
            "GET /api/componentes/gestionados",
            "GET /api/compras",
            "GET /api/reparaciones/historial",
            "GET /api/reparaciones/asignaciones",
            "GET /api/reparaciones/asignaciones/R20260928_1",
            "GET /api/reparaciones/estadisticas/puntos",
            "PATCH /api/reparaciones/R20260928_1/por-cerrar",
            "GET /api/tecnicos",
            "GET /api/tecnicos/activos",
            "DELETE /api/telefonos/355400000000111",
            "GET /api/telefonos/355400000000111/modelo",
            "POST /api/telefonos",
            "PATCH /api/componentes/101/stock-minimo",
            "DELETE /api/reparacion-componentes/R20260928_1/incidencia");

    @Test void laListaTiene24Entradas() {
        assertEquals(24, RutasRetiradas.LISTA.size());
    }

    @Test void todasLasRetiradasCoinciden() {
        for (String caso : RETIRADAS) {
            String[] p = caso.split(" ", 2);
            assertTrue(RutasRetiradas.coincide(p[0], p[1]), "debería estar retirada: " + caso);
        }
    }

    @Test void ningunaRutaVivaCoincide() {
        for (String caso : VIVAS) {
            String[] p = caso.split(" ", 2);
            assertTrue(!RutasRetiradas.coincide(p[0], p[1]), "NO debería estar retirada: " + caso);
        }
    }

    @Test void noHayEntradasDuplicadas() {
        Set<String> vistas = new HashSet<>();
        for (RutasRetiradas.Ruta r : RutasRetiradas.LISTA) {
            assertTrue(vistas.add(r.metodo() + " " + r.patron()), "duplicada: " + r);
        }
    }
}
