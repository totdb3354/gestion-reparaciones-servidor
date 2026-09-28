package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import com.reparaciones.servidor.dao.EquivalenciaModeloDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
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

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Toda escritura viva deja una línea en el registro de actividad (spec sp7b §5.7). */
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
class RegistroEscriturasTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ColorEquivalenciaDAO colorEquivalenciaDao;
    @MockBean EquivalenciaModeloDAO equivalenciaModeloDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean ReparacionComponenteDAO reparacionComponenteDao;
    @MockBean LogDAO logDao;

    private String supertecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
    }

    @Test void guardarEquivalenciaDeColorDejaLinea() throws Exception {
        mvc.perform(put("/api/colores/equivalencias").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoExterno\":\"rosa oscuro\",\"colorOficial\":\"ROSA\"}"));
        verify(logDao).insertar(eq(7), eq("GUARDAR_EQUIVALENCIA_COLOR"), contains("rosa oscuro"));
    }

    @Test void guardarEquivalenciaDeModeloDejaLinea() throws Exception {
        mvc.perform(put("/api/modelos/equivalencias").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"textoExterno\":\"iphone 15 pro\",\"modeloInterno\":\"IPHONE_15_PRO\"}"));
        verify(logDao).insertar(eq(7), eq("GUARDAR_EQUIVALENCIA_MODELO"), contains("iphone 15 pro"));
    }

    @Test void crearProveedorDejaLinea() throws Exception {
        mvc.perform(post("/api/proveedores").header("Authorization", supertecnico())
                .header("Idempotency-Key", "clave-crear-proveedor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"proveedor-nuevo\",\"divisa\":\"EUR\",\"tipo\":\"COMPONENTES\"}"));
        verify(logDao).insertar(eq(7), eq("CREAR_PROVEEDOR"), contains("proveedor-nuevo"));
    }

    @Test void activarProveedorDejaLinea() throws Exception {
        mvc.perform(patch("/api/proveedores/4/activo").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":true}"));
        verify(logDao).insertar(eq(7), eq("ALTA_PROVEEDOR"), contains("4"));
    }

    @Test void editarProveedorDejaLinea() throws Exception {
        mvc.perform(put("/api/proveedores/4").header("Authorization", supertecnico())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"proveedor-editado\",\"divisa\":\"EUR\",\"comentario\":\"\"}"));
        verify(logDao).insertar(eq(7), eq("EDITAR_PROVEEDOR"), contains("proveedor-editado"));
    }

    @Test void borrarIncidenciaActivaPorImeiDejaLinea() throws Exception {
        mvc.perform(delete("/api/reparaciones/imei/355400000000111/incidencia-activa")
                .header("Authorization", supertecnico()));
        verify(logDao).insertar(eq(7), eq("BORRAR_INCIDENCIA_ACTIVA"), contains("355400000000111"));
    }

    @Test void limpiarSolicitudDejaLinea() throws Exception {
        mvc.perform(patch("/api/solicitudes/9/limpiar").header("Authorization", supertecnico()));
        verify(logDao).insertar(eq(7), eq("LIMPIAR_SOLICITUD"), contains("9"));
    }

    /** Ruta del menú contextual "Cancelar incidencia": comparte forma con la de borrado de pieza,
     *  retirada en la primera generación de la API, pero esta sigue viva para los dos clientes. */
    @Test void borrarIncidenciaDeReparacionComponenteDejaLinea() throws Exception {
        mvc.perform(delete("/api/reparacion-componentes/A123/incidencia").header("Authorization", supertecnico()));
        verify(logDao).insertar(eq(7), eq("BORRAR_INCIDENCIA"), contains("A123"));
    }
}
