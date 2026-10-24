package com.fernando.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Busqueda y reemplazo con expresiones regulares sobre un DocumentoTexto.
 *
 * <p>Separado de DocumentoTexto para no mezclar la logica de edicion con la de
 * regex: el documento sabe insertar y borrar, el buscador sabe encontrar
 * patrones. Cada uno es mas facil de probar por separado.
 *
 * <p>Las coincidencias se devuelven como objetos {@link Coincidencia} inmutables
 * con posicion, longitud y el texto encontrado, para que el consumidor no
 * tenga que recalcular nada.
 */
public final class BuscadorRegex {

    private final DocumentoTexto documento;

    public BuscadorRegex(DocumentoTexto documento) {
        this.documento = Objects.requireNonNull(documento);
    }

    /**
     * Busca todas las coincidencias de un patron regex.
     *
     * @param patron regex a buscar
     * @param flags  flags de Pattern (CASE_INSENSITIVE, MULTILINE, etc.)
     * @return lista de coincidencias en orden de aparicion
     * @throws PatternSyntaxException si el patron es invalido
     */
    public List<Coincidencia> buscar(String patron, int flags) {
        if (patron == null || patron.isEmpty()) {
            return Collections.emptyList();
        }
        Pattern regex = Pattern.compile(patron, flags);
        Matcher matcher = regex.matcher(documento.getContenido());
        List<Coincidencia> resultados = new ArrayList<>();
        while (matcher.find()) {
            resultados.add(new Coincidencia(matcher.start(), matcher.end(), matcher.group()));
        }
        return Collections.unmodifiableList(resultados);
    }

    public List<Coincidencia> buscar(String patron) {
        return buscar(patron, 0);
    }

    /**
     * Busca sin distincion de mayusculas.
     */
    public List<Coincidencia> buscarIgnorandoMayusculas(String patron) {
        return buscar(patron, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Reemplaza todas las coincidencias del patron. Cada reemplazo puede usar
     * referencias a grupos ($1, $2, etc.).
     *
     * @param patron   regex a buscar
     * @param reemplazo texto de reemplazo, con soporte de referencias a grupos
     * @param flags    flags de Pattern
     * @return cuantas coincidencias se reemplazaron
     */
    public int reemplazarTodo(String patron, String reemplazo, int flags) {
        if (patron == null || patron.isEmpty()) {
            return 0;
        }
        Pattern regex = Pattern.compile(patron, flags);
        Matcher matcher = regex.matcher(documento.getContenido());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count > 0) {
            matcher.reset();
            documento.setContenido(matcher.replaceAll(reemplazo));
        }
        return count;
    }

    public int reemplazarTodo(String patron, String reemplazo) {
        return reemplazarTodo(patron, reemplazo, 0);
    }

    /**
     * Cuenta las coincidencias sin construir la lista.
     */
    public int contar(String patron, int flags) {
        if (patron == null || patron.isEmpty()) {
            return 0;
        }
        Matcher matcher = Pattern.compile(patron, flags).matcher(documento.getContenido());
        int total = 0;
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    public int contar(String patron) {
        return contar(patron, 0);
    }

    /**
     * Una coincidencia encontrada en el texto.
     */
    public static final class Coincidencia {

        private final int inicio;
        private final int fin;
        private final String texto;

        public Coincidencia(int inicio, int fin, String texto) {
            this.inicio = inicio;
            this.fin = fin;
            this.texto = texto;
        }

        public int inicio() { return inicio; }
        public int fin() { return fin; }
        public int longitud() { return fin - inicio; }
        public String texto() { return texto; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Coincidencia)) return false;
            Coincidencia c = (Coincidencia) o;
            return inicio == c.inicio && fin == c.fin && Objects.equals(texto, c.texto);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inicio, fin, texto);
        }

        @Override
        public String toString() {
            return "Coincidencia[" + inicio + ".." + fin + " \"" + texto + "\"]";
        }
    }
}
