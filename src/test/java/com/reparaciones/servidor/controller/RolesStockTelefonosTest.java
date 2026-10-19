package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.ResumenHistorico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stock de teléfonos (0.9.17, spec pieza 1 §3): SUPERTECNICO y ADMIN; el TECNICO recibe 403 sin llegar al DAO. Cadena de
 *  seguridad real y DAO mockeado (sin base de datos). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesStockTelefonosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean NsysDAO dao;
    @MockBean TelefonoDAO telefonoDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final List<String> RUTAS = List.of("/api/stock-telefonos", "/api/stock-telefonos/estado",
            "/api/stock-telefonos/buscar?imei=3500", "/api/stock-telefonos/350000000000001",
            "/api/stock-telefonos/350000000000001/rastro", "/api/stock-telefonos/importaciones");

    @Test void elTecnicoRecibe403SinLlegarAlDao() throws Exception {
        for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        LocalDateTime t = LocalDateTime.of(2026, 10, 1, 10, 0);
        when(dao.porImei("350000000000001")).thenReturn(List.of(new MovilNsys("350000000000001", "BT-9001",
                "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A", null, null, null, null, null,
                null, null, null, false, "Listos", "Listos", EstadoMovil.STOCK, t, t)));
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", supertecnico())).andExpect(status().isOk());
            mvc.perform(get(ruta).header("Authorization", admin())).andExpect(status().isOk());
        }
    }

    @Test void sinTokenRecibe403SinLlegarAlDao() throws Exception {
        // Sin cabecera Authorization este servidor responde 403 (ver OpenApiContractTest).
        for (String ruta : RUTAS) mvc.perform(get(ruta)).andExpect(status().isForbidden());
        verifyNoInteractions(dao);
    }

    @Test void conTokenInvalidoRecibe401SinLlegarAlDao() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", "Bearer no-es-un-jwt")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(dao);
    }

    @Test void elTecnicoSigueLeyendoElModeloDeUnImeiQueAhoraSaleDeNsys() throws Exception {
        // GET /api/telefonos/{imei}/modelo: misma ruta, respuesta y permisos que con IMEIcheck (constraints, reglas de rol).
        when(telefonoDao.getModelo("350000000000001")).thenReturn(null);
        when(dao.modeloDe("350000000000001")).thenReturn("13pro");
        mvc.perform(get("/api/telefonos/350000000000001/modelo").header("Authorization", tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("13pro"));
    }

    private static MovilNsys movil(EstadoMovil estado, LocalDate venta) {
        LocalDateTime t = LocalDateTime.of(2026, 10, 1, 10, 0);
        return new MovilNsys("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released", "x", "Proveedor A",
                LocalDate.of(2023, 4, 1), venta, null, null, null, null, null, null, false, null, null, estado, t, t);
    }

    @Test void fichaDeUnVendidoTieneFechaDeVentaYNoTieneDias() throws Exception {
        when(dao.porImei("350000000000002")).thenReturn(List.of(movil(EstadoMovil.VENDIDO, LocalDate.of(2023, 5, 2))));
        mvc.perform(get("/api/stock-telefonos/350000000000002").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VENDIDO"))
                .andExpect(jsonPath("$.fechaVenta").value("2023-05-02"))
                .andExpect(jsonPath("$.dias").value(nullValue()));
    }

    @Test void fichaDeUnaBajaNoTieneDias() throws Exception {
        when(dao.porImei("350000000000002")).thenReturn(List.of(movil(EstadoMovil.BAJA, null)));
        mvc.perform(get("/api/stock-telefonos/350000000000002").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BAJA"))
                .andExpect(jsonPath("$.dias").value(nullValue()))
                .andExpect(jsonPath("$.fechaVenta").value(nullValue()));
    }

    /** 0.9.18 (spec §6.2): «Pegar varios IMEIs» con la misma guarda que el resto del stock de teléfonos. */
    @Test void comprobarUnaListaSoloParaSupertecnicoYAdmin() throws Exception {
        String cuerpo = "{\"imeis\":[\"350000000000001\"]}";
        mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dao);
        for (String token : List.of(supertecnico(), admin())) {
            mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].imei").value("350000000000001"))
                    .andExpect(jsonPath("$[0].encontrado").value(false));
        }
    }

    @Test void comprobarMasDeQuinientosEs400() throws Exception {
        String imeis = IntStream.range(0, 501).mapToObj(n -> "\"" + String.format("3500000000%05d", n) + "\"")
                .collect(Collectors.joining(","));
        mvc.perform(post("/api/stock-telefonos/comprobar").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imeis\":[" + imeis + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void laListaDeImportacionesPideVeintePorDefecto() throws Exception {
        when(dao.resumenHistorico()).thenReturn(new ResumenHistorico(0, 0, null));
        mvc.perform(get("/api/stock-telefonos/importaciones").header("Authorization", admin())).andExpect(status().isOk());
        verify(dao).importaciones("ACTUAL", 20);
    }
}
