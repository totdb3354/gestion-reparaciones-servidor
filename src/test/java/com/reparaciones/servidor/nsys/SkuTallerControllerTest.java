package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SkuTallerControllerTest {

    @Test void laPreviaSaleDelServicio() {
        TallerReparaciones taller = mock(TallerReparaciones.class);
        PeticionPreviaTaller peticion = new PeticionPreviaTaller("350000000000001", List.of(new FilaPreviaTaller(null, 131, false)));
        PreviaTaller previa = new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Rojo", "A"), false, false);
        when(taller.previsualizar(peticion)).thenReturn(previa);
        assertEquals(previa, new SkuTallerController(taller).previsualizar(peticion));
    }
}
