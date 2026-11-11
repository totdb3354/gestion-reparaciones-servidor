package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.FilaPedido;
import com.reparaciones.servidor.pedidos.FilaVista;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.pedidos.ListaFilas;
import com.reparaciones.servidor.pedidos.VistaFilas;
import com.reparaciones.servidor.pedidos.Canal;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Pedidos» como la hoja: SUPERTECNICO y ADMIN; el TECNICO recibe 403 sin llegar a la vista ni al DAO. */
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
class RolesPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean VistaFilas vista;
    @MockBean FilasDAO dao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    /** Las cuatro rutas, cada una con su peticion para un token dado. */
    private static final List<java.util.function.Function<String, MockHttpServletRequestBuilder>> RUTAS = List.of(
            t -> get("/api/pedidos/filas").header("Authorization", t),
            t -> get("/api/pedidos/filas/5/recomendados").header("Authorization", t),
            t -> put("/api/pedidos/filas/5/imei").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"imei\":\"350000000000001\"}"),
            t -> get("/api/pedidos/fuentes").header("Authorization", t));

    @Test void elTecnicoRecibe403SinLlegarALaVistaNiAlDao() throws Exception {
        for (var ruta : RUTAS) mvc.perform(ruta.apply(tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(vista, dao);
    }

    @Test void supertecnicoYAdminLasUsan() throws Exception {
        when(vista.filas(any(), any(), any())).thenReturn(new ListaFilas(List.of(), false));
        when(vista.recomendados(anyInt())).thenReturn(Optional.of(List.of()));
        when(vista.fuentes()).thenReturn(List.of());
        when(dao.porId(anyInt())).thenReturn(Optional.of(new FilaPedido(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null,
                "402-0000000-0000005", LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, null)));
        when(dao.ponerImei(anyInt(), any(), any(), any())).thenReturn(FilasDAO.Resultado.PUESTO);
        when(vista.fila(anyInt())).thenReturn(Optional.of(new FilaVista(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA",
                "350000000000001", "402-0000000-0000005", LocalDate.of(2026, 11, 4), "PENDIENTE", true, null, null, null, null, null, null, null)));
        for (String token : List.of(supertecnico(), admin()))
            for (var ruta : RUTAS) mvc.perform(ruta.apply(token)).andExpect(status().isOk());
    }
}
