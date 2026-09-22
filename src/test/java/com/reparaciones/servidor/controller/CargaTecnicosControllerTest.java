package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaTecnicos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/reparaciones/carga-tecnicos (spec 3a §5): la carga diaria por técnico que antes
 *  calculaba el cliente JavaFX, con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class CargaTecnicosControllerTest {

    private static final String IMEI_1 = "000000000000001";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean TecnicoDAO tecnicoDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    /** Asignación abierta con los campos que usa el cálculo (mismo helper que CargaTecnicosTest). */
    private ReparacionResumen asig(String idRep, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, IMEI_1, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null, null, 0, false, null, null, null);
        r.setCliente(cliente);
        return r;
    }

    private void mockearTecnicosActivos() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(
                new Tecnico(1, "T1", true, false, false),
                new Tecnico(2, "T2", true, false, false)));
    }

    /**
     * Fija el contrato central de la spec (pedidos y total viajan juntos, con contenidos
     * distintos): T1 tiene dos asignaciones abiertas normales, una con cliente y otra sin
     * cliente; solo la primera cuenta en "pedidos", las dos en "total". T2 no tiene ninguna.
     *
     * Los recuentos del desglose (pendiente.normales) no dependen de la jornada del día —
     * CargaTecnicos.calcularDia los acumula antes de escalarlos por el factor de horas—, así
     * que 1 y 2 son deterministas cualquier día de la semana, a diferencia de comparar
     * directamente pctPendiente (que en fin de semana da 0 en los dos alcances por igual).
     * sinJornada sí depende del día real, así que se calcula aquí con el mismo helper que usa
     * el controlador en vez de asumir un día de la semana concreto.
     *
     * "hecho" lleva además una asignación cerrada hoy de un tipo distinto (glass) al de las
     * abiertas (normales): así el mapeo posicional de dto() en ReparacionController (normales,
     * chasis, porCerrar, glass, enEsperaPieza) se afirma en positivo y una permuta entre esos
     * campos se detecta. pctHecho/pctPendiente de T1 se comparan contra el resultado de llamar
     * a CargaTecnicos.calcularDia() con los mismos datos, en vez de fijar un valor o comparar con
     * cero: así la aserción es determinista cualquier día de la semana (entre semana, hecho y
     * pendiente de T1 salen de tipos distintos y por tanto de valores distintos si se permutan;
     * en fin de semana el factor de jornada es 0 y ambos dan legítimamente 0, indistinguibles
     * entre sí, pero eso no hace la aserción intermitente: sigue comparando contra el mismo
     * cálculo de producción que el controlador usa esa misma petición).
     */
    @Test void supertecnicoRecibeLosDosAlcances() throws Exception {
        mockearTecnicosActivos();
        List<ReparacionResumen> abiertas = List.of(
                asig("A_1", 1, "CLI"),
                asig("A_2", 1, null));
        List<ReparacionResumen> cerradasHoy = List.of(asig("AG_1", 1, "CLI"));
        when(dao.getAsignaciones(null)).thenReturn(abiertas);
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(cerradasHoy);
        var dia = CargaTecnicos.diaDeHoy();
        boolean sinJornadaHoy = CargaTecnicos.JORNADA_HORAS.getOrDefault(dia, 0) == 0;
        CargaTecnicos.DiaTecnico esperadoTotalT1   = CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, false).get(1);
        CargaTecnicos.DiaTecnico esperadoPedidosT1 = CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, true).get(1);

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos.length()").value(2))
                .andExpect(jsonPath("$.total.length()").value(2))
                // T1 (fila 0): la asignación sin cliente solo cuenta en "total".
                .andExpect(jsonPath("$.pedidos[0].idTec").value(1))
                .andExpect(jsonPath("$.pedidos[0].nombre").value("T1"))
                .andExpect(jsonPath("$.pedidos[0].pendiente.normales").value(1))
                .andExpect(jsonPath("$.total[0].pendiente.normales").value(2))
                // T1 "hecho" hoy: una glass (distinta del tipo de las abiertas), en positivo.
                .andExpect(jsonPath("$.pedidos[0].hecho.glass").value(1))
                .andExpect(jsonPath("$.total[0].hecho.glass").value(1))
                .andExpect(jsonPath("$.pedidos[0].hecho.normales").value(0))
                .andExpect(jsonPath("$.pedidos[0].hecho.chasis").value(0))
                // pctHecho/pctPendiente de T1, contra el cálculo de producción (ver javadoc).
                .andExpect(jsonPath("$.total[0].pctPendiente", closeTo(esperadoTotalT1.pctPendiente(), 1e-9)))
                .andExpect(jsonPath("$.total[0].pctHecho", closeTo(esperadoTotalT1.pctHecho(), 1e-9)))
                .andExpect(jsonPath("$.pedidos[0].pctPendiente", closeTo(esperadoPedidosT1.pctPendiente(), 1e-9)))
                .andExpect(jsonPath("$.pedidos[0].pctHecho", closeTo(esperadoPedidosT1.pctHecho(), 1e-9)))
                // T2 (fila 1): sin asignaciones, desglose a cero en los dos alcances.
                .andExpect(jsonPath("$.pedidos[1].idTec").value(2))
                .andExpect(jsonPath("$.pedidos[1].nombre").value("T2"))
                .andExpect(jsonPath("$.pedidos[1].sinJornada").value(sinJornadaHoy))
                .andExpect(jsonPath("$.total[1].sinJornada").value(sinJornadaHoy))
                .andExpect(jsonPath("$.pedidos[1].pendiente.normales").value(0))
                .andExpect(jsonPath("$.pedidos[1].pendiente.chasis").value(0))
                .andExpect(jsonPath("$.pedidos[1].pendiente.porCerrar").value(0))
                .andExpect(jsonPath("$.pedidos[1].pendiente.glass").value(0))
                .andExpect(jsonPath("$.pedidos[1].pendiente.enEsperaPieza").value(0))
                .andExpect(jsonPath("$.pedidos[1].hecho.normales").value(0))
                .andExpect(jsonPath("$.total[1].hecho.normales").value(0));
    }

    /**
     * Las glass abiertas cuentan en el tramo PENDIENTE, como en el JavaFX (la lista que
     * PendientesSuperTecnicoController pasa a calcularDia es reparaciones + glass + pulido). Aquí el
     * técnico 1 solo tiene una glass abierta —getAsignaciones() devuelve vacío—, así que con la versión
     * anterior, que alimentaba el cálculo solo con dao.getAsignaciones(null), su pendiente salía a cero
     * mientras el tramo "hecho" sí contaba glass (getAsignacionesCompletadasHoy une A% y AG%).
     *
     * Se mockea también getAsignacionesPulido() con una fila AP… para fijar que el pulido viaja en la
     * lista pero NO computa (decisión A5): pendiente.normales/chasis/porCerrar siguen a cero.
     *
     * DETERMINISMO (ningún test puede depender del día en que se ejecute):
     * - pendiente.glass es un RECUENTO del desglose, y calcularDia lo acumula ANTES de escalarlo por el
     *   factor de jornada: vale 1 cualquier día de la semana, fin de semana incluido.
     * - pctPendiente no se compara contra un literal, sino contra CargaTecnicos.calcularDia() invocado
     *   aquí con el mismo día que resuelve el controlador (en fin de semana el factor es 0 y los dos dan
     *   0 legítimamente, sin volver intermitente la aserción).
     * - "entre semana es mayor que cero" se afirma sobre el cálculo puro con un día FIJO (lunes), que no
     *   depende del reloj: es la propiedad que se quiere fijar, sin sacarla del día real.
     */
    @Test void laCargaPendienteCuentaLasGlassAbiertas() throws Exception {
        mockearTecnicosActivos();
        List<ReparacionResumen> glass  = List.of(asig("AG_9", 1, "CLI"));
        List<ReparacionResumen> pulido = List.of(asig("AP_9", 1, "CLI"));
        when(dao.getAsignaciones(null)).thenReturn(List.of());
        when(dao.getAsignacionesGlass(null)).thenReturn(glass);
        when(dao.getAsignacionesPulido(null)).thenReturn(pulido);
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(List.of());
        List<ReparacionResumen> abiertas = new ArrayList<>(glass);
        abiertas.addAll(pulido);
        var dia = CargaTecnicos.diaDeHoy();
        CargaTecnicos.DiaTecnico esperadoTotalT1   = CargaTecnicos.calcularDia(abiertas, List.of(), dia, false).get(1);
        CargaTecnicos.DiaTecnico esperadoPedidosT1 = CargaTecnicos.calcularDia(abiertas, List.of(), dia, true).get(1);
        // Entre semana la glass abierta consume jornada: se afirma sobre el cálculo puro con día fijo.
        assertTrue(CargaTecnicos.calcularDia(abiertas, List.of(), DayOfWeek.MONDAY, true).get(1).pctPendiente() > 0,
                "entre semana una glass abierta tiene que dar pctPendiente > 0");

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(status().isOk())
                // T1: la glass abierta cuenta como pendiente, y el pulido que viaja con ella no computa.
                .andExpect(jsonPath("$.pedidos[0].idTec").value(1))
                .andExpect(jsonPath("$.pedidos[0].pendiente.glass").value(1))
                .andExpect(jsonPath("$.total[0].pendiente.glass").value(1))
                .andExpect(jsonPath("$.pedidos[0].pendiente.normales").value(0))
                .andExpect(jsonPath("$.pedidos[0].pendiente.chasis").value(0))
                .andExpect(jsonPath("$.pedidos[0].pendiente.porCerrar").value(0))
                .andExpect(jsonPath("$.pedidos[0].hecho.glass").value(0))
                .andExpect(jsonPath("$.pedidos[0].pctPendiente", closeTo(esperadoPedidosT1.pctPendiente(), 1e-9)))
                .andExpect(jsonPath("$.total[0].pctPendiente", closeTo(esperadoTotalT1.pctPendiente(), 1e-9)))
                // T2 no tiene nada: el reparto por técnico no se mezcla.
                .andExpect(jsonPath("$.pedidos[1].idTec").value(2))
                .andExpect(jsonPath("$.pedidos[1].pendiente.glass").value(0));
    }

    @Test void adminTambienPuedeConsultarla() throws Exception {
        mockearTecnicosActivos();
        when(dao.getAsignaciones(null)).thenReturn(List.of());
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenReturn(List.of());

        int status = mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", admin()))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    @Test void tecnicoRecibe403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", tecnico()))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test void siFallanLasCompletadasHoyDegradaASoloPendiente() throws Exception {
        mockearTecnicosActivos();
        when(dao.getAsignaciones(null)).thenReturn(List.of(asig("A_1", 1, "CLI")));
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenThrow(new RuntimeException("BD caída"));

        mvc.perform(get("/api/reparaciones/carga-tecnicos").header("Authorization", supertecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos[*].pctHecho", everyItem(is(0.0))))
                .andExpect(jsonPath("$.total[*].pctHecho", everyItem(is(0.0))));
    }
}
