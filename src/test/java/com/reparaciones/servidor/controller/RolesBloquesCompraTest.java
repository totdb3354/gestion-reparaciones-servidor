package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
import com.reparaciones.servidor.model.TipoLinea;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las nueve escrituras de bloques (0.9.9; mover un otro, 0.9.15): solo SUPERTECNICO, con la cadena de seguridad real y el servicio mockeado. */
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
class RolesBloquesCompraTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean BloqueCompraService servicio;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final String AT = "\"updatedAt\":\"2026-10-09T10:00:00\"";
    private static final String LINEAS = "{\"lineas\":[{\"idCompra\":5," + AT + "}]}";

    private record Op(String metodo, String ruta, String cuerpo) {}

    private static final List<Op> ESCRITURAS = List.of(
            new Op("PATCH", "/api/compras/5/bloque", "{\"idBloque\":40," + AT + "}"),
            new Op("POST", "/api/bloques-compra/41/confirmar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/recibir", LINEAS),
            new Op("POST", "/api/bloques-compra/41/cancelar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/borrar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/separar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/juntar", "{\"destino\":40,\"lineas\":[{\"idCompra\":5," + AT + "}]}"),
            new Op("PATCH", "/api/bloques-compra/41/nota", "{\"nota\":\"Ped. 1\"," + AT + "}"),
            new Op("PATCH", "/api/compras-otros/88/bloque", "{\"idBloque\":40," + AT + "}"));

    private ResultActions llamar(Op op, String token) throws Exception {
        return mvc.perform(request(HttpMethod.valueOf(op.metodo()), op.ruta()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(op.cuerpo()));
    }

    @Test void tecnicoYAdminReciben403EnTodas() throws Exception {
        for (Op op : ESCRITURAS) {
            llamar(op, tecnico()).andExpect(status().isForbidden());
            llamar(op, admin()).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test void elSupertecnicoLlegaAlServicioEnTodas() throws Exception {
        when(servicio.mover(anyInt(), any(), any())).thenReturn(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false));
        when(servicio.moverOtro(anyInt(), any(), any())).thenReturn(new BloqueCompraService.Movimiento(88, "pendiente", 41, 40, false, 2, false, TipoLinea.OTRO));
        when(servicio.aplicar(anyInt(), any(), anyList())).thenReturn(new BloqueCompraService.Aplicada(41, List.of()));
        when(servicio.separar(anyInt(), anyList())).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(5)));
        when(servicio.juntar(anyInt(), anyInt(), anyList())).thenReturn(new BloqueCompraService.Union(41, 40, List.of(5)));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
        for (Op op : ESCRITURAS) llamar(op, supertecnico()).andExpect(status().isOk());
        verify(servicio).separar(41, List.of(new LineaVista(5, LocalDateTime.of(2026, 10, 9, 10, 0), null)));
        verify(servicio).moverOtro(88, new DestinoPedido(40), LocalDateTime.of(2026, 10, 9, 10, 0));
    }

    @Test void separarDevuelveElNumeroDelBloqueNuevo() throws Exception {
        when(servicio.separar(anyInt(), anyList())).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(5)));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
        llamar(ESCRITURAS.get(5), supertecnico()).andExpect(status().isOk()).andExpect(jsonPath("$.idBloque").value(90));
    }

    /** 0.9.15: el tipo de cada línea viaja en el JSON; sin él llega nulo (el servicio lo trata como pieza). */
    @Test void elTipoDeCadaLineaLlegaAlServicio() throws Exception {
        when(servicio.aplicar(anyInt(), any(), anyList())).thenReturn(new BloqueCompraService.Aplicada(41, List.of()));
        llamar(new Op("POST", "/api/bloques-compra/41/confirmar", "{\"lineas\":[{\"idCompra\":5," + AT + ",\"tipo\":\"PIEZA\"},"
                + "{\"idCompra\":88," + AT + ",\"tipo\":\"OTRO\"},{\"idCompra\":6," + AT + "}]}"), supertecnico())
                .andExpect(status().isOk());
        LocalDateTime at = LocalDateTime.of(2026, 10, 9, 10, 0);
        verify(servicio).aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of(
                new LineaVista(5, at, TipoLinea.PIEZA), new LineaVista(88, at, TipoLinea.OTRO), new LineaVista(6, at, null)));
    }

    @Test void unTipoDesconocidoEs400SinLlegarAlServicio() throws Exception {
        llamar(new Op("POST", "/api/bloques-compra/41/confirmar",
                "{\"lineas\":[{\"idCompra\":5," + AT + ",\"tipo\":\"CAJA\"}]}"), supertecnico())
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
}
