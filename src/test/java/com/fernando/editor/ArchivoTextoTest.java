package com.fernando.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link ArchivoTexto}.
 *
 * <p>El bloque "guardado atomico" es el que importa: ahi vivia un defecto que
 * convertia el guardado — la operacion que el usuario ejecuta precisamente para
 * no perder su trabajo — en el momento mas peligroso del programa.
 */
class ArchivoTextoTest {

    private Path carpeta;

    @BeforeEach
    void prepararCarpeta() throws IOException {
        carpeta = Files.createTempDirectory("editor");
    }

    @Test
    void guardarYAbrirConservaElContenido() throws IOException {
        Path archivo = Files.createTempFile("editor", ".txt");
        try {
            DocumentoTexto original = new DocumentoTexto("Primera linea\nSegunda linea");
            ArchivoTexto.guardar(original, archivo);

            DocumentoTexto recuperado = ArchivoTexto.abrir(archivo);
            assertEquals("Primera linea\nSegunda linea", recuperado.getContenido());
            assertEquals(2, recuperado.contarLineas());
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    @Test
    void guardaEnUtf8YConservaLosAcentos() throws IOException {
        Path archivo = Files.createTempFile("editor", ".txt");
        try {
            ArchivoTexto.guardar(new DocumentoTexto("Canción con ñ y tildes: áéíóú"), archivo);

            assertEquals("Canción con ñ y tildes: áéíóú",
                    Files.readString(archivo, StandardCharsets.UTF_8));
            assertEquals("Canción con ñ y tildes: áéíóú", ArchivoTexto.abrir(archivo).getContenido());
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    @Test
    void guardarMarcaElDocumentoComoNoModificado() throws IOException {
        Path archivo = Files.createTempFile("editor", ".txt");
        try {
            DocumentoTexto documento = new DocumentoTexto();
            documento.anexar("algo");
            assertTrue(documento.estaModificado());

            ArchivoTexto.guardar(documento, archivo);
            assertFalse(documento.estaModificado());
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    @Test
    void abrirUnArchivoInexistenteLanzaIOException() {
        assertThrows(IOException.class, () -> ArchivoTexto.abrir(Path.of("no-existe-jamas-12345.txt")));
    }

    @Test
    void abrirUnaCarpetaLanzaIOException() throws IOException {
        Path carpeta = Files.createTempDirectory("editor");
        try {
            assertThrows(IOException.class, () -> ArchivoTexto.abrir(carpeta));
        } finally {
            Files.deleteIfExists(carpeta);
        }
    }

    @Test
    void guardarCreaLasCarpetasQueFalten() throws IOException {
        Path base = Files.createTempDirectory("editor");
        Path destino = base.resolve("sub/carpeta/archivo.txt");
        try {
            ArchivoTexto.guardar(new DocumentoTexto("contenido"), destino);
            assertTrue(Files.exists(destino));
            assertEquals("contenido", Files.readString(destino));
        } finally {
            Files.deleteIfExists(destino);
            Files.deleteIfExists(base.resolve("sub/carpeta"));
            Files.deleteIfExists(base.resolve("sub"));
            Files.deleteIfExists(base);
        }
    }

    // ---------------------------------------------------------------
    //  Guardado atomico: donde estaba el defecto
    // ---------------------------------------------------------------

    /**
     * LA PRUEBA QUE DOCUMENTA EL DEFECTO CORREGIDO.
     *
     * <p>{@code Files.writeString} sin opciones abre con
     * {@code CREATE + TRUNCATE_EXISTING + WRITE}: el truncado ocurre <b>antes</b>
     * de escribir el primer byte. Entre ese momento y el final de la escritura,
     * el archivo del disco esta vacio o a medias, y si el proceso muere ahi no
     * queda ni lo nuevo ni lo viejo.
     *
     * <p>Ahora se escribe en un temporal y se renombra encima. El renombrado
     * dentro del mismo sistema de archivos es atomico: el nombre apunta al
     * archivo viejo o al nuevo, nunca a medias.
     *
     * <p>Esta prueba comprueba lo observable — que el guardado deja el contenido
     * correcto y <b>no deja ningun temporal tirado</b>. La ventana de perdida en
     * si no se puede provocar desde una prueba: hace falta matar el proceso en el
     * instante justo. Es un defecto razonado, no medido, y conviene decirlo.
     */
    @Test
    void guardarNoDejaArchivosTemporales() throws IOException {
        Path archivo = carpeta.resolve("documento.txt");
        Files.writeString(archivo, "CONTENIDO ORIGINAL");

        ArchivoTexto.guardar(new DocumentoTexto("contenido nuevo"), archivo);

        assertEquals("contenido nuevo", Files.readString(archivo));
        try (var listado = Files.list(carpeta)) {
            List<String> nombres = listado.map(p -> p.getFileName().toString()).sorted()
                    .collect(java.util.stream.Collectors.toUnmodifiableList());
            assertEquals(List.of("documento.txt"), nombres,
                    "el temporal tiene que haber desaparecido");
        }
    }

    /**
     * Si el guardado falla, el archivo que ya estaba <b>sigue intacto</b>. Se
     * provoca poniendo un directorio donde iria el temporal: la escritura falla y
     * el original no se toca.
     */
    @Test
    void siElGuardadoFallaElArchivoAnteriorSobrevive() throws IOException {
        Path archivo = carpeta.resolve("importante.txt");
        Files.writeString(archivo, "CONTENIDO ORIGINAL");

        // El temporal se llama "importante.txt.tmp"; si ahi hay una carpeta,
        // escribirlo falla.
        Files.createDirectory(carpeta.resolve("importante.txt.tmp"));

        assertThrows(IOException.class,
                () -> ArchivoTexto.guardar(new DocumentoTexto("lo nuevo"), archivo));

        assertEquals("CONTENIDO ORIGINAL", Files.readString(archivo),
                "el original tiene que seguir ahi entero");
    }

    /** Y deja el documento marcado como guardado. */
    @Test
    void guardarMarcaElDocumentoComoGuardado() throws IOException {
        DocumentoTexto documento = new DocumentoTexto("hola");
        documento.anexar(" mundo");
        assertTrue(documento.estaModificado());

        ArchivoTexto.guardar(documento, carpeta.resolve("nota.txt"));

        assertFalse(documento.estaModificado());
    }

    @Test
    void lasRutasNulasDanIoExceptionYnoNullPointer() {
        assertThrows(IOException.class, () -> ArchivoTexto.abrir(null));
        assertThrows(IOException.class, () -> ArchivoTexto.guardar(new DocumentoTexto("x"), null));
        assertThrows(IOException.class, () -> ArchivoTexto.guardar(null, carpeta.resolve("x.txt")));
    }
}
