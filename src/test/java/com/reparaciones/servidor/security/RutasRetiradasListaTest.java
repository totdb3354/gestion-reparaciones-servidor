package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las 21 rutas de primera generación, y las vivas que se les parecen (spec sp7b §4.1). */
class RutasRetiradasListaTest {

    private static final List<String> RETIRADAS = List.of(
            "GET /api/componentes",
            "GET /api/componentes/stock-bajo",
            "GET /api/componentes/chasis",
            "GET /api/componentes/evolucion-stock",
            "POST /api/componentes",
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
            "GET /api/telefonos",
            "GET /api/telefonos/355400000000111/exists");

    /** Rutas vivas que comparten prefijo con alguna retirada: el matcher no debe tocarlas. Incluye el ajuste de
     *  stock y el alta/borrado de técnicos: parecían de primera generación pero la suite completa demostró que
     *  siguen vivos (spec sp7b §4.1, paso 7: RolesSolicitudesStockTest y RolesUsuarioTecnicoTest empezaban a
     *  recibir 403 en vez del 200/201/204 que ya probaban con la cadena de seguridad real). */
    private static final List<String> VIVAS = List.of(
            "GET /api/componentes/agrupados",
            "GET /api/componentes/gestionados",
            "PATCH /api/componentes/101/stock",
            "GET /api/compras",
            "GET /api/reparaciones/historial",
            "GET /api/reparaciones/asignaciones",
            "GET /api/reparaciones/asignaciones/R20260928_1",
            "GET /api/reparaciones/estadisticas/puntos",
            "PATCH /api/reparaciones/R20260928_1/por-cerrar",
            "GET /api/tecnicos",
            "GET /api/tecnicos/activos",
            "POST /api/tecnicos",
            "DELETE /api/tecnicos/7",
            "DELETE /api/telefonos/355400000000111",
            "GET /api/telefonos/355400000000111/modelo",
            "POST /api/telefonos");

    @Test void laListaTiene21Entradas() {
        assertEquals(21, RutasRetiradas.LISTA.size());
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
