package com.fernando.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class BuscadorRegexTest {

    private DocumentoTexto doc;
    private BuscadorRegex buscador;

    @BeforeEach
    void preparar() {
        doc = new DocumentoTexto("El gato negro y el GATO blanco caminan por el jardin.");
        buscador = new BuscadorRegex(doc);
    }

    @Test
    void encuentraCoincidenciasLiterales() {
        List<BuscadorRegex.Coincidencia> resultados = buscador.buscar("gato");
        assertEquals(1, resultados.size());
        assertEquals(3, resultados.get(0).inicio());
        assertEquals("gato", resultados.get(0).texto());
    }

    @Test
    void busquedaInsensibleAMayusculas() {
        List<BuscadorRegex.Coincidencia> resultados = buscador.buscarIgnorandoMayusculas("gato");
        assertEquals(2, resultados.size());
        assertEquals("gato", resultados.get(0).texto());
        assertEquals("GATO", resultados.get(1).texto());
    }

    @Test
    void encuentraPatronesRegex() {
        List<BuscadorRegex.Coincidencia> resultados = buscador.buscar("\\b\\w+o\\b");
        assertEquals(3, resultados.size());
        assertEquals("gato", resultados.get(0).texto());
        assertEquals("negro", resultados.get(1).texto());
        assertEquals("blanco", resultados.get(2).texto());
    }

    @Test
    void devuelveListaVaciaSinCoincidencias() {
        assertTrue(buscador.buscar("perro").isEmpty());
    }

    @Test
    void devuelveListaVaciaConPatronNuloOVacio() {
        assertTrue(buscador.buscar(null).isEmpty());
        assertTrue(buscador.buscar("").isEmpty());
    }

    @Test
    void reemplazaConGrupos() {
        doc.setContenido("2026-10-23 y 2026-01-15");
        int reemplazados = buscador.reemplazarTodo(
                "(\\d{4})-(\\d{2})-(\\d{2})", "$3/$2/$1");

        assertEquals(2, reemplazados);
        assertEquals("23/10/2026 y 15/01/2026", doc.getContenido());
    }

    @Test
    void reemplazarTodoConRegex() {
        int reemplazados = buscador.reemplazarTodo("gato", "perro",
                Pattern.CASE_INSENSITIVE);

        assertEquals(2, reemplazados);
        assertEquals("El perro negro y el perro blanco caminan por el jardin.", doc.getContenido());
    }

    @Test
    void reemplazarNoHaceNadaSinCoincidencias() {
        int reemplazados = buscador.reemplazarTodo("dragon", "unicornio");
        assertEquals(0, reemplazados);
        assertTrue(doc.getContenido().startsWith("El gato"));
    }

    @Test
    void contarCoincidencias() {
        assertEquals(1, buscador.contar("gato"));
        assertEquals(2, buscador.contar("gato", Pattern.CASE_INSENSITIVE));
        assertEquals(0, buscador.contar("perro"));
    }

    @Test
    void patronInvalidoLanzaExcepcion() {
        assertThrows(PatternSyntaxException.class,
                () -> buscador.buscar("[invalido"));
    }

    @Test
    void coincidenciaTieneLongitudCorrecta() {
        List<BuscadorRegex.Coincidencia> resultados = buscador.buscar("negro");
        assertEquals(1, resultados.size());
        assertEquals(5, resultados.get(0).longitud());
        assertEquals(8, resultados.get(0).fin());
    }

    @Test
    void laListaEsInmutable() {
        List<BuscadorRegex.Coincidencia> resultados = buscador.buscar("gato");
        assertThrows(UnsupportedOperationException.class, () -> resultados.add(null));
    }
}
