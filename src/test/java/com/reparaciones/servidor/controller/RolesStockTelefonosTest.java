package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final List<String> RUTAS = List.of("/api/stock-telefonos", "/api/stock-telefonos/estado",
            "/api/stock-telefonos/buscar?imei=3500", "/api/stock-telefonos/350000000000001",
            "/api/stock-telefonos/350000000000001/rastro");

    @Test void elTecnicoRecibe403SinLlegarAlDao() throws Exception {
        for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
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
}
