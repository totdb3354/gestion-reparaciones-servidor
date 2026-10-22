package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** La capacidad y el color de NSYS en las listas del taller (spec 0.9.21 «Descripción completa donde pone «Modelo»» §2.3), con
 *  los DAO mockeados y sin base de datos. Datos inventados: el iPhone 13 128GB de TallerDePrueba. */
class DescripcionMovilTest {

    private static final String IMEI = "350000000000001";
    private static final String OTRO = "350000000000002";
    private static final String DE_CLIENTE = "350000000000003";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final DescripcionMovil descripcion = new DescripcionMovil(nsys, new TallerNsys(nsys, tallerDao, mock(LogDAO.class)));

    /** Una fila de una lista del taller (asignación o trabajo) de este IMEI, con el modelo del taller «13». */
    private static ReparacionResumen fila(String idRep, String imei) {
        return new ReparacionResumen(idRep, imei, "Técnico 2", null, null, null, null, false, false, null, null, 4, 0, null,
                null, null, 0, false, null, null, "13");
    }

    @Test void poneLaCapacidadYElColorDeNsysYDejaElModeloDelTaller() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        assertSame(filas, descripcion.rellenar(filas));
        assertEquals("128GB", filas.get(0).getCapacidad());
        assertEquals("Azul", filas.get(0).getColor());
        assertEquals("13", filas.get(0).getModelo());
    }

    @SuppressWarnings("unchecked")
    @Test void losImeisRepetidosSeLeenUnaSolaVez() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B"), movil(OTRO, "Negro", "A")));
        // El historial trae una fila por componente: el mismo IMEI sale varias veces.
        List<ReparacionResumen> filas = List.of(fila("R20261022_1", IMEI), fila("R20261022_1", IMEI), fila("R20261022_2", OTRO),
                fila("R20261021_9", IMEI));
        descripcion.rellenar(filas);
        ArgumentCaptor<Collection<String>> imeis = ArgumentCaptor.forClass(Collection.class);
        verify(nsys, times(1)).porImeis(imeis.capture());
        assertEquals(List.of(IMEI, OTRO), List.copyOf(imeis.getValue()));
        verify(tallerDao, times(1)).vigentes();
        assertEquals(List.of("Azul", "Azul", "Negro", "Azul"), filas.stream().map(ReparacionResumen::getColor).toList());
    }

    @Test void masDeQuinientosImeisVanEnTandasYSinNadaEnElEspejoNoSeLeeElTaller() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NsysDAO dao = new NsysDAO(jdbc);
        DescripcionMovil conTandas = new DescripcionMovil(dao, new TallerNsys(dao, tallerDao, mock(LogDAO.class)));
        List<ReparacionResumen> filas = new ArrayList<>();
        for (int n = 0; n < 501; n++) {
            String imei = String.format("3500000000%05d", n);
            filas.add(fila("R20261022_" + n, imei));
            filas.add(fila("R20261022_" + n, imei));
        }
        conTandas.rellenar(filas);
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "501 IMEIs distintos en 1002 filas: 500 + 1, nunca una consulta por fila");
        assertTrue(sql.stream().allMatch(s -> s.contains("FROM Nsys_movil WHERE IMEI_SN IN (")), sql.toString());
        verifyNoInteractions(tallerDao);
        assertNull(filas.get(0).getCapacidad());
    }

    @Test void unImeiFueraDelEspejoSeQuedaSinCapacidadNiColor() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI), fila("A20261022_2", DE_CLIENTE));
        descripcion.rellenar(filas);
        assertEquals("128GB", filas.get(0).getCapacidad());
        assertNull(filas.get(1).getCapacidad());
        assertNull(filas.get(1).getColor());
        assertEquals("13", filas.get(1).getModelo());
    }

    @Test void elColorDelTallerMandaSobreElDeNsysSinLeerElMaestro() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertEquals("Rojo", filas.get(0).getColor());
        assertEquals("128GB", filas.get(0).getCapacidad());
        verify(nsys, never()).cargarProductos();
    }

    @Test void valeElLoteMasRecienteDelImei() {
        // porImeis trae de cada IMEI el lote más reciente primero.
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Verde", "B"), movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertEquals("Verde", filas.get(0).getColor());
    }

    @Test void unNumeroDeSerieEnMinusculasCruza() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil("F2LXABC123", "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", "f2lxabc123"));
        descripcion.rellenar(filas);
        assertEquals("Azul", filas.get(0).getColor());
    }

    @Test void siFallaElEspejoLaListaSaleComoHoy() {
        when(nsys.porImeis(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        assertSame(filas, descripcion.rellenar(filas));
        assertNull(filas.get(0).getCapacidad());
        assertNull(filas.get(0).getColor());
        assertEquals("13", filas.get(0).getModelo());
    }

    @Test void siFallaLoDelTallerNoSeRellenaNada() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertNull(filas.get(0).getCapacidad(), "todo o nada: sin saber lo del taller no se enseña lo de NSYS");
        assertNull(filas.get(0).getColor());
    }

    @Test void unaListaVaciaNoConsultaNada() {
        List<ReparacionResumen> vacia = new ArrayList<>();
        assertSame(vacia, descripcion.rellenar(vacia));
        verifyNoInteractions(nsys, tallerDao);
    }
}
