package com.fernando.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Buffer de texto con historial de deshacer y rehacer.
 *
 * <p>Toda la logica del editor vive aqui, separada de la interfaz grafica, para
 * que se pueda probar sin abrir ninguna ventana.
 *
 * <h2>Defecto 1: deshacer hasta el estado guardado seguia marcando "modificado"</h2>
 *
 * {@code deshacer()} y {@code rehacer()} ponian {@code modificado = true} sin
 * mirar nada:
 *
 * <pre>
 * guardar          -> modificado = false
 * escribir " mundo" -> modificado = true
 * deshacer          -> contenido = "hola" (identico al del disco)
 *                      modificado = <b>true</b>
 * </pre>
 *
 * <p>El editor preguntaba "¿guardar los cambios?" al cerrar un documento
 * idéntico al que hay en el disco. Es un defecto que entrena al usuario a decir
 * que si por costumbre, y el dia que la pregunta importa la contesta sin leerla.
 *
 * <p>Ahora se lleva un <b>numero de version</b>: cada cambio lo sube, cada
 * deshacer lo baja, cada rehacer lo vuelve a subir, y guardar anota en que
 * version quedo el disco. {@code estaModificado()} es una comparacion de dos
 * enteros — sin recorrer el texto.
 *
 * <p>Con un detalle: si se escribe algo nuevo estando por debajo de la version
 * guardada, esa version <b>deja de ser alcanzable</b> — la rama que llevaba a
 * ella se borro con el historial de rehacer — y el documento queda modificado
 * para siempre, que es lo correcto.
 *
 * <h2>Defecto 2: el historial guardaba el documento entero en cada cambio</h2>
 *
 * <pre>{@code
 * paraDeshacer.push(contenido);   // una copia completa por pulsacion
 * }</pre>
 *
 * <p>Con el limite de {@value #LIMITE_HISTORIAL} pasos, escribir cien caracteres
 * en un documento de 5 MB dejaba <b>500 MB</b> retenidos. El coste era
 * proporcional al tamaño del documento cuando deberia serlo al tamaño del
 * cambio. Ver {@link Cambio}.
 */
public class DocumentoTexto {

    /** Cuantos pasos de deshacer se conservan. */
    public static final int LIMITE_HISTORIAL = 100;

    /** Version que significa "el estado del disco ya no se puede alcanzar". */
    private static final int SIN_VERSION_GUARDADA = Integer.MIN_VALUE;

    private String contenido;
    private final Deque<Cambio> paraDeshacer = new ArrayDeque<>();
    private final Deque<Cambio> paraRehacer = new ArrayDeque<>();

    private int version;
    private int versionGuardada;

    /** Crea un documento vacio. */
    public DocumentoTexto() {
        this("");
    }

    /**
     * @param contenidoInicial texto de partida; {@code null} se trata como vacio
     */
    public DocumentoTexto(String contenidoInicial) {
        this.contenido = Objects.requireNonNullElse(contenidoInicial, "");
        this.versionGuardada = 0;
    }

    /** @return el texto completo */
    public String getContenido() {
        return contenido;
    }

    // ---------------------------------------------------------------
    //  Edicion
    // ---------------------------------------------------------------

    /**
     * Reemplaza todo el contenido. El historial guarda solo la parte que cambio:
     * escribir una letra al final de un documento largo produce un cambio de una
     * letra.
     *
     * @param nuevo texto nuevo; {@code null} se trata como vacio
     */
    public void setContenido(String nuevo) {
        aplicar(Cambio.entre(contenido, Objects.requireNonNullElse(nuevo, "")));
    }

    /**
     * @param posicion donde insertar, de 0 a la longitud
     * @param texto    texto a insertar
     * @throws IndexOutOfBoundsException si la posicion no existe
     * @throws IllegalArgumentException  si el texto es nulo
     */
    public void insertar(int posicion, String texto) {
        exigirPosicionValida(posicion);
        if (texto == null) {
            throw new IllegalArgumentException("El texto no puede ser nulo");
        }
        if (!texto.isEmpty()) {
            aplicar(new Cambio(posicion, "", texto));
        }
    }

    /**
     * @param texto texto a agregar al final
     */
    public void anexar(String texto) {
        insertar(contenido.length(), texto);
    }

    /**
     * @param desde primera posicion a borrar, incluida
     * @param hasta ultima posicion a borrar, excluida
     * @throws IndexOutOfBoundsException si alguna posicion no existe
     * @throws IllegalArgumentException  si el rango esta invertido
     */
    public void borrar(int desde, int hasta) {
        exigirPosicionValida(desde);
        exigirPosicionValida(hasta);
        if (desde > hasta) {
            throw new IllegalArgumentException(
                    "'desde' (" + desde + ") no puede ser mayor que 'hasta' (" + hasta + ")");
        }
        if (desde != hasta) {
            aplicar(new Cambio(desde, contenido.substring(desde, hasta), ""));
        }
    }

    /**
     * Reemplaza todas las coincidencias.
     *
     * @param buscado   texto a buscar; nulo o vacio no hace nada
     * @param reemplazo texto nuevo; {@code null} equivale a borrar
     * @return cuantas coincidencias se cambiaron
     */
    public int reemplazarTodo(String buscado, String reemplazo) {
        if (buscado == null || buscado.isEmpty()) {
            return 0;
        }
        int coincidencias = buscarTodas(buscado).size();
        if (coincidencias > 0) {
            setContenido(contenido.replace(buscado, Objects.requireNonNullElse(reemplazo, "")));
        }
        return coincidencias;
    }

    // ---------------------------------------------------------------
    //  Deshacer y rehacer
    // ---------------------------------------------------------------

    /**
     * @return {@code true} si habia algo que deshacer
     */
    public boolean deshacer() {
        if (paraDeshacer.isEmpty()) {
            return false;
        }
        Cambio cambio = paraDeshacer.pop();
        contenido = cambio.revertirEn(contenido);
        paraRehacer.push(cambio);
        version--;
        return true;
    }

    /**
     * @return {@code true} si habia algo que rehacer
     */
    public boolean rehacer() {
        if (paraRehacer.isEmpty()) {
            return false;
        }
        Cambio cambio = paraRehacer.pop();
        contenido = cambio.aplicarA(contenido);
        paraDeshacer.push(cambio);
        version++;
        return true;
    }

    /** @return {@code true} si queda algo por deshacer */
    public boolean sePuedeDeshacer() {
        return !paraDeshacer.isEmpty();
    }

    /** @return {@code true} si queda algo por rehacer */
    public boolean sePuedeRehacer() {
        return !paraRehacer.isEmpty();
    }

    /**
     * @return cuantos caracteres ocupa el historial. Es lo que antes era el
     *         tamaño del documento multiplicado por el numero de pasos.
     */
    public int pesoDelHistorial() {
        return paraDeshacer.stream().mapToInt(Cambio::peso).sum()
                + paraRehacer.stream().mapToInt(Cambio::peso).sum();
    }

    // ---------------------------------------------------------------
    //  Estado guardado
    // ---------------------------------------------------------------

    /**
     * @return {@code true} si el contenido difiere del que se guardo por ultima
     *         vez. Deshacer hasta el estado guardado devuelve {@code false}.
     */
    public boolean estaModificado() {
        return version != versionGuardada;
    }

    /** Anota que el contenido actual es el que hay en el disco. */
    public void marcarGuardado() {
        versionGuardada = version;
    }

    // ---------------------------------------------------------------
    //  Busqueda
    // ---------------------------------------------------------------

    /**
     * @param texto         texto a buscar
     * @param desdePosicion desde donde buscar
     * @return la posicion de la primera coincidencia, o -1
     */
    public int buscar(String texto, int desdePosicion) {
        if (texto == null || texto.isEmpty()) {
            return -1;
        }
        if (desdePosicion < 0 || desdePosicion > contenido.length()) {
            return -1;
        }
        return contenido.indexOf(texto, desdePosicion);
    }

    /**
     * @param texto texto a buscar
     * @return las posiciones de todas las coincidencias, sin solaparse
     */
    public List<Integer> buscarTodas(String texto) {
        List<Integer> posiciones = new ArrayList<>();
        if (texto == null || texto.isEmpty()) {
            return posiciones;
        }
        int indice = contenido.indexOf(texto);
        while (indice >= 0) {
            posiciones.add(indice);
            indice = contenido.indexOf(texto, indice + texto.length());
        }
        return posiciones;
    }

    // ---------------------------------------------------------------
    //  Estadisticas
    // ---------------------------------------------------------------

    /** @return cuantos caracteres tiene, espacios incluidos */
    public int contarCaracteres() {
        return contenido.length();
    }

    /** @return cuantos caracteres tiene sin contar espacios ni saltos */
    public int contarCaracteresSinEspacios() {
        return (int) contenido.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    /** @return cuantas palabras hay, separadas por cualquier espacio en blanco */
    public int contarPalabras() {
        if (contenido.isBlank()) {
            return 0;
        }
        return contenido.trim().split("\\s+").length;
    }

    /**
     * @return cuantas lineas hay. Un documento vacio tiene <b>cero</b> lineas, no
     *         una: es una decision, y la alternativa — contar siempre los saltos
     *         mas uno — es igual de defendible mientras este documentada.
     */
    public int contarLineas() {
        if (contenido.isEmpty()) {
            return 0;
        }
        return contenido.split("\n", -1).length;
    }

    // ---------------------------------------------------------------
    //  Interno
    // ---------------------------------------------------------------

    /**
     * Aplica un cambio y lo anota en el historial.
     *
     * <p>Si se edita estando por debajo de la version guardada, esa version deja
     * de ser alcanzable: la rama de rehacer que llevaba a ella se borra, y el
     * documento queda modificado hasta que se vuelva a guardar.
     */
    private void aplicar(Cambio cambio) {
        if (cambio == null) {
            return;   // no cambio nada
        }
        contenido = cambio.aplicarA(contenido);

        paraDeshacer.push(cambio);
        if (paraDeshacer.size() > LIMITE_HISTORIAL) {
            paraDeshacer.removeLast();
        }
        if (!paraRehacer.isEmpty() && versionGuardada > version) {
            versionGuardada = SIN_VERSION_GUARDADA;
        }
        paraRehacer.clear();
        version++;
    }

    private void exigirPosicionValida(int posicion) {
        if (posicion < 0 || posicion > contenido.length()) {
            throw new IndexOutOfBoundsException(
                    "Posicion " + posicion + " fuera del documento (0.." + contenido.length() + ")");
        }
    }
}
