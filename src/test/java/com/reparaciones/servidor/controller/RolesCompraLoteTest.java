package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.LoteCompras;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CompraLoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los lotes de pedidos (spec 4b §4.4): solo SUPERTECNICO, clave obligatoria y wiring del controlador y el servicio
 *  nuevos con la cadena de seguridad real. */
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
class RolesCompraLoteTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean CompraLoteService servicio;
    @MockBean ComponenteDAO componenteDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final String CUERPO_COMPRAS = """
            {"lineas":[{"idCom":1,"idProv":2,"cantidad":1,"esUrgente":false,"precioUnidad":0.0}],
             "solicitudes":{"urgentes":[],"preventivas":[]}}""";

    private ResultActions lote(String ruta, String cuerpo, String token, String clave) throws Exception {
        var peticion = post(ruta).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (clave != null) peticion = peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion);
    }

    private void catalogo() {
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
    }

    @Test void comprasTecnicoYAdminReciben403() throws Exception {
        lote("/api/compras/lote", CUERPO_COMPRAS, tecnico(), "c1").andExpect(status().isForbidden());
        lote("/api/compras/lote", CUERPO_COMPRAS, admin(), "c2").andExpect(status().isForbidden());
    }

    @Test void comprasSupertecnicoGuardaYDevuelveLosIds() throws Exception {
        catalogo();
        when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41)));
        lote("/api/compras/lote", CUERPO_COMPRAS, supertecnico(), "c3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idsCreados[0]").value(41));
    }

    @Test void comprasSinClaveEs400() throws Exception {
        catalogo();
        lote("/api/compras/lote", CUERPO_COMPRAS, supertecnico(), null).andExpect(status().isBadRequest());
    }
}
