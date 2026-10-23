package com.fernando.editor;

import java.util.Objects;

/**
 * Un cambio puntual en el texto: en la posicion {@code posicion} se quito
 * {@code borrado} y se puso {@code insertado}.
 *
 * <h2>Por que el historial guarda esto y no el documento entero</h2>
 *
 * La version anterior apilaba una copia completa del contenido en cada cambio:
 *
 * <pre>{@code
 * private void cambiarA(String nuevo) {
 *     paraDeshacer.push(contenido);   // <- el documento entero
 *     ...
 * }
 * }</pre>
 *
 * <p>Con un limite de 100 pasos, escribir 100 caracteres en un documento de 5 MB
 * dejaba <b>500 MB</b> retenidos en el historial. Y no es un caso extremo: 5 MB
 * son unas 1500 paginas de texto, y 100 pulsaciones son media frase.
 *
 * <p>El coste es proporcional al <b>tamaño del documento</b> cuando deberia
 * serlo al <b>tamaño del cambio</b>. Escribir una letra no tiene por que costar
 * lo que mide el archivo.
 *
 * <p>Guardando el delta, esa misma frase ocupa unos cientos de bytes. Es como
 * funciona cualquier editor real, y de paso hace que deshacer y rehacer sean
 * operaciones locales en vez de reemplazos completos.
 *
 * @param posicion  donde ocurre el cambio
 * @param borrado   lo que habia ahi antes; cadena vacia si solo se inserto
 * @param insertado lo que hay ahora; cadena vacia si solo se borro
 */
final class Cambio {

    private final int posicion;
    private final String borrado;
    private final String insertado;

    Cambio(int posicion, String borrado, String insertado) {
        if (posicion < 0) {
            throw new IllegalArgumentException("La posicion no puede ser negativa: " + posicion);
        }
        if (borrado == null || insertado == null) {
            throw new IllegalArgumentException("Los textos del cambio no pueden ser nulos");
        }
        this.posicion = posicion;
        this.borrado = borrado;
        this.insertado = insertado;
    }

    int posicion() {
        return posicion;
    }

    String borrado() {
        return borrado;
    }

    String insertado() {
        return insertado;
    }

    /**
     * Calcula el cambio minimo entre dos versiones del texto.
     *
     * <p>Recorta el prefijo y el sufijo comunes, que es lo que hace que escribir
     * una letra al final de un documento largo produzca un cambio de una letra y
     * no de todo el documento.
     *
     * @param viejo texto anterior
     * @param nuevo texto nuevo
     * @return el cambio, o {@code null} si son iguales
     */
    static Cambio entre(String viejo, String nuevo) {
        if (viejo.equals(nuevo)) {
            return null;
        }
        int minimo = Math.min(viejo.length(), nuevo.length());

        int prefijo = 0;
        while (prefijo < minimo && viejo.charAt(prefijo) == nuevo.charAt(prefijo)) {
            prefijo++;
        }
        int sufijo = 0;
        while (sufijo < minimo - prefijo
                && viejo.charAt(viejo.length() - 1 - sufijo) == nuevo.charAt(nuevo.length() - 1 - sufijo)) {
            sufijo++;
        }

        return new Cambio(prefijo,
                viejo.substring(prefijo, viejo.length() - sufijo),
                nuevo.substring(prefijo, nuevo.length() - sufijo));
    }

    /**
     * @param texto texto al que aplicar el cambio
     * @return el texto con el cambio hecho
     */
    String aplicarA(String texto) {
        return texto.substring(0, posicion) + insertado + texto.substring(posicion + borrado.length());
    }

    /**
     * @param texto texto al que revertir el cambio
     * @return el texto como estaba antes
     */
    String revertirEn(String texto) {
        return texto.substring(0, posicion) + borrado + texto.substring(posicion + insertado.length());
    }

    /** @return cuantos caracteres ocupa este cambio en el historial */
    int peso() {
        return borrado.length() + insertado.length();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cambio)) {
            return false;
        }
        Cambio cambio = (Cambio) o;
        return posicion == cambio.posicion
                && Objects.equals(borrado, cambio.borrado)
                && Objects.equals(insertado, cambio.insertado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(posicion, borrado, insertado);
    }

    @Override
    public String toString() {
        return "Cambio[posicion=" + posicion + ", borrado=" + borrado + ", insertado=" + insertado + "]";
    }
}
