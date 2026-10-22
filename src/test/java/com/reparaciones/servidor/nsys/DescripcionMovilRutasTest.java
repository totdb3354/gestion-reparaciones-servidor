package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.TallerDePrueba.movil;
import static com.reparaciones.servidor.nsys.TallerDePrueba.vigente;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las seis listas del taller llevan la capacidad y el color de NSYS (spec 0.9.21 «Descripción completa donde pone «Modelo»»
 *  §2.3), también para el técnico: cadena de seguridad real, DescripcionMovil y TallerNsys de verdad y los DAO mockeados (sin
 *  base de datos). */
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
class DescripcionMovilRutasTest {

    private static final String IMEI = "350000000000001";
    private static final String DE_CLIENTE = "350000000000003";
    private static final List<String> RUTAS = List.of("/api/reparaciones/asignaciones", "/api/reparaciones/historial",
            "/api/glass/asignaciones", "/api/glass/historial", "/api/pulidos/asignaciones", "/api/pulidos/historial");

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean NsysDAO nsys;
    @MockBean TallerDAO tallerDao;

    private String tecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "usuario_prueba", "", "TECNICO", 4));
    }

    /** Dos filas del técnico 4 («Técnico 2»): una de un móvil de NSYS y otra de un cliente externo. Nuevas en cada llamada. */
    private static List<ReparacionResumen> lista() {
        return List.of(fila(IMEI), fila(DE_CLIENTE));
    }

    private static ReparacionResumen fila(String imei) {
        return new ReparacionResumen("A20261022_1", imei, "Técnico 2", null, null, null, null, false, false, null, null, 4, 0,
                null, null, null, 0, false, null, null, "13");
    }

    @BeforeEach void espejoYTaller() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(dao.getAsignaciones(4)).thenAnswer(i -> lista());
        when(dao.getHistorial(4)).thenAnswer(i -> lista());
        when(dao.getAsignacionesGlass(4)).thenAnswer(i -> lista());
        when(dao.getHistorialGlass(4)).thenAnswer(i -> lista());
        when(dao.getAsignacionesPulido(4)).thenAnswer(i -> lista());
        when(dao.getHistorialPulido(4)).thenAnswer(i -> lista());
    }

    @Test void elTecnicoRecibeLaCapacidadYElColorQueValeEnLasSeisListas() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", tecnico()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].modelo").value("13"))
                    .andExpect(jsonPath("$[0].capacidad").value("128GB"))
                    .andExpect(jsonPath("$[0].color").value("Rojo"))
                    .andExpect(jsonPath("$[1].capacidad").value(nullValue()))
                    .andExpect(jsonPath("$[1].color").value(nullValue()));
        }
        verify(nsys, times(6)).porImeis(any());
        verify(tallerDao, times(6)).vigentes();
    }

    @Test void siElEspejoFallaLaListaSaleIgualSinCapacidadNiColor() throws Exception {
        when(nsys.porImeis(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idRep").value("A20261022_1"))
                .andExpect(jsonPath("$[0].modelo").value("13"))
                .andExpect(jsonPath("$[0].capacidad").value(nullValue()))
                .andExpect(jsonPath("$[0].color").value(nullValue()));
    }
}
