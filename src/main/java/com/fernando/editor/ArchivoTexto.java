package com.fernando.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lectura y escritura de archivos de texto en UTF-8.
 *
 * <h2>El defecto: guardar no era atomico</h2>
 *
 * <pre>{@code
 * Files.writeString(archivo, documento.getContenido(), UTF_8);
 * }</pre>
 *
 * <p>Sin opciones, {@code writeString} abre el archivo con
 * {@code CREATE + TRUNCATE_EXISTING + WRITE}. El <b>truncado ocurre antes de
 * escribir el primer byte</b>. Entre ese momento y el final de la escritura, el
 * archivo del disco esta vacio o a medias.
 *
 * <p>Si el proceso muere ahi — se corta la luz, se acaba el disco, el usuario
 * mata la aplicacion — no queda ni el contenido nuevo ni el viejo. Se pierde el
 * documento entero por guardar.
 *
 * <p>Y no hace falta un fallo dramatico: en un editor de texto, "guardar" es la
 * operacion que el usuario ejecuta precisamente para <b>no</b> perder su
 * trabajo. Que sea el momento mas peligroso del programa es lo contrario de lo
 * que promete.
 *
 * <h2>La correccion: escribir aparte y renombrar</h2>
 *
 * <ol>
 *   <li>Se escribe todo en un archivo temporal, en la misma carpeta.</li>
 *   <li>Se renombra encima del original con {@link StandardCopyOption#ATOMIC_MOVE}.</li>
 * </ol>
 *
 * <p>Un renombrado dentro del mismo sistema de archivos es una operacion
 * atomica: o el nombre apunta al archivo viejo o apunta al nuevo, nunca a medias.
 * Cualquier fallo antes del renombrado deja el original intacto y solo ensucia un
 * temporal.
 *
 * <p>El temporal va en la <b>misma carpeta</b> a proposito, no en la del sistema:
 * {@code ATOMIC_MOVE} solo funciona dentro del mismo sistema de archivos, y
 * {@code /tmp} suele estar en otro.
 *
 * <p>Si el sistema de archivos no admite el movimiento atomico — algunos de red
 * no — se cae a un reemplazo normal, que sigue siendo mejor que truncar: la
 * ventana de peligro pasa de "toda la escritura" a "el instante del reemplazo".
 */
public final class ArchivoTexto {

    private static final String SUFIJO_TEMPORAL = ".tmp";

    private ArchivoTexto() {
    }

    /**
     * @param archivo archivo a leer
     * @return un documento con ese contenido, marcado como guardado
     * @throws IOException si no existe, es una carpeta, o falla la lectura
     */
    public static DocumentoTexto abrir(Path archivo) throws IOException {
        if (archivo == null) {
            throw new IOException("La ruta no puede ser nula");
        }
        if (!Files.exists(archivo)) {
            throw new IOException("El archivo no existe: " + archivo);
        }
        if (Files.isDirectory(archivo)) {
            throw new IOException("La ruta es una carpeta, no un archivo: " + archivo);
        }
        DocumentoTexto documento = new DocumentoTexto(Files.readString(archivo, StandardCharsets.UTF_8));
        documento.marcarGuardado();
        return documento;
    }

    /**
     * Guarda el documento sin arriesgar el archivo que ya estaba.
     *
     * @param documento documento a guardar
     * @param archivo   destino
     * @throws IOException si falla la escritura o el renombrado
     */
    public static void guardar(DocumentoTexto documento, Path archivo) throws IOException {
        if (documento == null || archivo == null) {
            throw new IOException("El documento y la ruta son obligatorios");
        }
        Path destino = archivo.toAbsolutePath();
        Path carpeta = destino.getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }

        Path temporal = destino.resolveSibling(destino.getFileName() + SUFIJO_TEMPORAL);
        try {
            Files.writeString(temporal, documento.getContenido(), StandardCharsets.UTF_8);
            mover(temporal, destino);
        } finally {
            // Si algo fallo, el temporal no se queda tirado. Y si el movimiento
            // salio bien, ya no existe y esto no hace nada.
            Files.deleteIfExists(temporal);
        }
        documento.marcarGuardado();
    }

    private static void mover(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
