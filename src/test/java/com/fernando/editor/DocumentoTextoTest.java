package com.fernando.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentoTextoTest {

    private DocumentoTexto documento;

    @BeforeEach
    void prepararDocumento() {
        documento = new DocumentoTexto("Hola mundo");
    }

    @Test
    void insertarEnMedioDelTexto() {
        documento.insertar(4, " cruel");
        assertEquals("Hola cruel mundo", documento.getContenido());
    }

    @Test
    void insertarAlPrincipioYAlFinal() {
        documento.insertar(0, ">> ");
        documento.anexar(" <<");
        assertEquals(">> Hola mundo <<", documento.getContenido());
    }

    @Test
    void insertarFueraDeRangoLanzaExcepcion() {
        assertThrows(IndexOutOfBoundsException.class, () -> documento.insertar(99, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> documento.insertar(-1, "x"));
    }

    @Test
    void borrarQuitaElRangoIndicado() {
        documento.borrar(4, 10);
        assertEquals("Hola", documento.getContenido());
    }

    @Test
    void borrarConRangoInvertidoEsRechazado() {
        assertThrows(IllegalArgumentException.class, () -> documento.borrar(8, 2));
    }

    @Test
    void buscarDevuelveLaPosicionDeLaCoincidencia() {
        assertEquals(5, documento.buscar("mundo", 0));
        assertEquals(-1, documento.buscar("Java", 0));
    }

    @Test
    void buscarDesdeUnaPosicionIgnoraLasAnteriores() {
        documento.setContenido("uno dos uno dos");
        assertEquals(0, documento.buscar("uno", 0));
        assertEquals(8, documento.buscar("uno", 1));
    }

    @Test
    void buscarTodasEncuentraLasCoincidenciasSinSolaparse() {
        documento.setContenido("aaaa");
        assertIterableEquals(java.util.List.of(0, 2), documento.buscarTodas("aa"));
    }

    @Test
    void buscarTextoVacioNoDevuelveNada() {
        assertEquals(-1, documento.buscar("", 0));
        assertTrue(documento.buscarTodas("").isEmpty());
    }

    @Test
    void reemplazarTodoDevuelveLaCantidadDeCambios() {
        documento.setContenido("gato perro gato");
        assertEquals(2, documento.reemplazarTodo("gato", "pez"));
        assertEquals("pez perro pez", documento.getContenido());
    }

    @Test
    void reemplazarAlgoQueNoExisteNoCambiaNada() {
        assertEquals(0, documento.reemplazarTodo("Java", "Kotlin"));
        assertEquals("Hola mundo", documento.getContenido());
    }

    @Test
    void reemplazarPorCadenaVaciaBorraLasCoincidencias() {
        documento.setContenido("a-b-c");
        assertEquals(2, documento.reemplazarTodo("-", ""));
        assertEquals("abc", documento.getContenido());
    }

    @Test
    void deshacerVuelveAlEstadoAnterior() {
        documento.anexar("!!!");
        assertTrue(documento.deshacer());
        assertEquals("Hola mundo", documento.getContenido());
    }

    @Test
    void deshacerVariosPasosSeguidos() {
        documento.anexar(" uno");
        documento.anexar(" dos");
        documento.deshacer();
        documento.deshacer();
        assertEquals("Hola mundo", documento.getContenido());
    }

    @Test
    void rehacerRestauraLoDeshecho() {
        documento.anexar("!!!");
        documento.deshacer();
        assertTrue(documento.rehacer());
        assertEquals("Hola mundo!!!", documento.getContenido());
    }

    @Test
    void unCambioNuevoInvalidaLaPilaDeRehacer() {
        documento.anexar(" uno");
        documento.deshacer();
        documento.anexar(" dos");

        assertFalse(documento.sePuedeRehacer());
        assertEquals("Hola mundo dos", documento.getContenido());
    }

    @Test
    void deshacerEnUnDocumentoSinCambiosDevuelveFalso() {
        assertFalse(new DocumentoTexto("x").deshacer());
        assertFalse(new DocumentoTexto("x").rehacer());
    }

    @Test
    void escribirElMismoContenidoNoGeneraUnPasoDeHistorial() {
        documento.setContenido("Hola mundo");
        assertFalse(documento.sePuedeDeshacer());
    }

    @Test
    void unDocumentoNuevoNoEstaModificado() {
        assertFalse(documento.estaModificado());
        documento.anexar("x");
        assertTrue(documento.estaModificado());
        documento.marcarGuardado();
        assertFalse(documento.estaModificado());
    }

    @Test
    void cuentaPalabrasIgnorandoEspaciosDeMas() {
        documento.setContenido("  uno   dos  tres  ");
        assertEquals(3, documento.contarPalabras());
    }

    @Test
    void documentoVacioTieneCeroPalabrasYCeroLineas() {
        DocumentoTexto vacio = new DocumentoTexto("");
        assertEquals(0, vacio.contarPalabras());
        assertEquals(0, vacio.contarLineas());
        assertEquals(0, vacio.contarCaracteres());
    }

    @Test
    void cuentaLineasIncluyendoLaUltimaVacia() {
        documento.setContenido("uno\ndos\n");
        assertEquals(3, documento.contarLineas());
    }

    @Test
    void cuentaCaracteresConYSinEspacios() {
        documento.setContenido("a b c");
        assertEquals(5, documento.contarCaracteres());
        assertEquals(3, documento.contarCaracteresSinEspacios());
    }

    // ---------------------------------------------------------------
    //  Estado guardado: donde estaba el defecto
    // ---------------------------------------------------------------

    /**
     * LA PRUEBA QUE DOCUMENTA EL DEFECTO CORREGIDO.
     *
     * <p>{@code deshacer()} ponia {@code modificado = true} sin mirar nada, asi
     * que deshacer hasta el estado guardado dejaba el documento marcado como
     * modificado. El editor preguntaba "¿guardar los cambios?" sobre un
     * contenido identico al del disco.
     *
     * <p>Es un defecto que entrena al usuario a decir que si por costumbre, y el
     * dia que la pregunta importa la contesta sin leerla.
     */
    @Test
    void deshacerHastaElEstadoGuardadoDejaElDocumentoSinCambios() {
        DocumentoTexto documento = new DocumentoTexto("hola");
        documento.marcarGuardado();

        documento.anexar(" mundo");
        assertTrue(documento.estaModificado());

        documento.deshacer();

        assertEquals("hola", documento.getContenido());
        assertFalse(documento.estaModificado(), "el contenido es identico al del disco");
    }

    /** Y rehacer vuelve a marcarlo. */
    @Test
    void rehacerVuelveAMarcarloComoModificado() {
        DocumentoTexto documento = new DocumentoTexto("hola");
        documento.marcarGuardado();
        documento.anexar(" mundo");
        documento.deshacer();

        documento.rehacer();

        assertEquals("hola mundo", documento.getContenido());
        assertTrue(documento.estaModificado());
    }

    /** Varios pasos: subir y bajar por el historial cruza el punto de guardado. */
    @Test
    void elPuntoDeGuardadoSeReconoceAcualquierProfundidad() {
        DocumentoTexto documento = new DocumentoTexto("");
        documento.anexar("a");
        documento.anexar("b");
        documento.marcarGuardado();            // guardado en "ab"
        documento.anexar("c");
        documento.anexar("d");

        assertTrue(documento.estaModificado());
        documento.deshacer();
        assertTrue(documento.estaModificado(), "en 'abc' sigue habiendo cambios");
        documento.deshacer();
        assertEquals("ab", documento.getContenido());
        assertFalse(documento.estaModificado(), "de vuelta en el estado guardado");
        documento.deshacer();
        assertTrue(documento.estaModificado(), "y por debajo tambien hay diferencia");
    }

    /**
     * Si se escribe algo nuevo estando por debajo de la version guardada, esa
     * version deja de ser alcanzable: la rama de rehacer que llevaba a ella se
     * borro. El documento queda modificado hasta que se vuelva a guardar.
     */
    @Test
    void editarDespuesDeDeshacerHaceInalcanzableElEstadoGuardado() {
        DocumentoTexto documento = new DocumentoTexto("");
        documento.anexar("a");
        documento.anexar("b");
        documento.marcarGuardado();            // guardado en "ab"

        documento.deshacer();                  // "a"
        documento.anexar("z");                 // "az": la rama hacia "ab" se perdio

        assertEquals("az", documento.getContenido());
        assertTrue(documento.estaModificado());

        documento.deshacer();                  // "a"
        assertTrue(documento.estaModificado(), "y ninguna vuelta atras lo limpia");
        documento.deshacer();                  // ""
        assertTrue(documento.estaModificado());
    }

    @Test
    void unDocumentoRecienCreadoNoEstaModificado() {
        assertFalse(new DocumentoTexto().estaModificado());
        assertFalse(new DocumentoTexto("hola").estaModificado());
    }

    /** Un cambio que no cambia nada no ensucia el documento ni el historial. */
    @Test
    void escribirLoMismoNoCuentaComoCambio() {
        DocumentoTexto documento = new DocumentoTexto("hola");
        documento.marcarGuardado();

        documento.setContenido("hola");
        documento.insertar(2, "");
        documento.borrar(1, 1);

        assertFalse(documento.estaModificado());
        assertFalse(documento.sePuedeDeshacer());
    }

    // ---------------------------------------------------------------
    //  El historial guarda deltas, no copias
    // ---------------------------------------------------------------

    /**
     * EL SEGUNDO DEFECTO.
     *
     * <p>El historial apilaba una copia completa del contenido en cada cambio.
     * Con el limite de 100 pasos, escribir cien caracteres en un documento de
     * 5 MB dejaba 500 MB retenidos: el coste era proporcional al tamaño del
     * <b>documento</b> cuando deberia serlo al del <b>cambio</b>.
     */
    @Test
    void elHistorialPesaLoQueMidenLosCambiosYnoElDocumento() {
        String largo = "x".repeat(1_000_000);
        DocumentoTexto documento = new DocumentoTexto(largo);

        for (int i = 0; i < DocumentoTexto.LIMITE_HISTORIAL; i++) {
            documento.anexar("a");
        }

        assertEquals(100, documento.pesoDelHistorial(),
                "cien pulsaciones son cien caracteres, no cien megabytes");
        assertTrue(documento.pesoDelHistorial() < largo.length() / 1000);
    }

    /** {@code setContenido} tambien guarda solo la parte que cambio. */
    @Test
    void reemplazarTodoElTextoGuardaSoloLaDiferencia() {
        DocumentoTexto documento = new DocumentoTexto("el mismo principio y el mismo final");

        documento.setContenido("el mismo XXX y el mismo final");

        assertEquals("principio".length() + "XXX".length(), documento.pesoDelHistorial(),
                "solo la parte de en medio");
    }

    /** Y deshacer reconstruye exactamente el texto anterior. */
    @Test
    void deshacerReconstruyeElTextoExacto() {
        String[] pasos = {"", "hola", "hola mundo", "adios mundo", "adios", "otra cosa distinta"};
        DocumentoTexto documento = new DocumentoTexto(pasos[0]);

        for (int i = 1; i < pasos.length; i++) {
            documento.setContenido(pasos[i]);
        }
        for (int i = pasos.length - 1; i > 0; i--) {
            assertEquals(pasos[i], documento.getContenido());
            assertTrue(documento.deshacer());
        }
        assertEquals(pasos[0], documento.getContenido());
        assertFalse(documento.deshacer());
    }

    /** Y rehacer vuelve a subir por el mismo camino. */
    @Test
    void rehacerVuelveASubirPorElMismoCamino() {
        String[] pasos = {"", "a", "ab", "abc", "abcd"};
        DocumentoTexto documento = new DocumentoTexto(pasos[0]);
        for (int i = 1; i < pasos.length; i++) {
            documento.setContenido(pasos[i]);
        }
        while (documento.deshacer()) {
            // hasta el principio
        }
        for (int i = 1; i < pasos.length; i++) {
            assertTrue(documento.rehacer());
            assertEquals(pasos[i], documento.getContenido());
        }
        assertFalse(documento.rehacer());
    }

    /**
     * El limite del historial corta los pasos mas viejos, no los mas nuevos.
     * Deshacer todo no devuelve al original — devuelve tan atras como el limite
     * permita — y eso es lo correcto: el limite existe justamente para no
     * guardarlo todo.
     */
    @Test
    void elLimiteDelHistorialDescartaLosPasosMasViejos() {
        DocumentoTexto documento = new DocumentoTexto("");
        for (int i = 0; i < 150; i++) {
            documento.anexar("x");
        }

        int pasos = 0;
        while (documento.deshacer()) {
            pasos++;
        }

        assertEquals(DocumentoTexto.LIMITE_HISTORIAL, pasos);
        assertEquals("x".repeat(50), documento.getContenido(),
                "quedan los 50 primeros, que ya no se pueden deshacer");
    }

    /** Un cambio nuevo borra la rama de rehacer. */
    @Test
    void escribirDespuesDeDeshacerBorraLaRamaDeRehacer() {
        DocumentoTexto documento = new DocumentoTexto("");
        documento.anexar("a");
        documento.anexar("b");
        documento.deshacer();

        assertTrue(documento.sePuedeRehacer());
        documento.anexar("z");
        assertFalse(documento.sePuedeRehacer());
    }

    // ---------------------------------------------------------------
    //  El calculo de la diferencia
    // ---------------------------------------------------------------

    @Test
    void laDiferenciaRecortaElPrefijoYelSufijoComunes() {
        Cambio cambio = Cambio.entre("hola mundo cruel", "hola planeta cruel");

        assertEquals(5, cambio.posicion());
        assertEquals("mundo", cambio.borrado());
        assertEquals("planeta", cambio.insertado());
    }

    @Test
    void laDiferenciaEntreTextosIgualesEsNula() {
        assertNull(Cambio.entre("igual", "igual"));
        assertNull(Cambio.entre("", ""));
    }

    /**
     * Ida y vuelta sobre todos los pares de un conjunto de textos con prefijos y
     * sufijos comunes, cadenas vacias y repeticiones — que es donde el recorte de
     * prefijo y sufijo se puede pasar de largo y solaparse.
     */
    @Test
    void aplicarYrevertirUnCambioSiempreVuelveAlOrigen() {
        String[] textos = {"", "a", "aa", "aaa", "ab", "aba", "abab", "hola", "hola mundo",
                "mundo hola", "xhola", "holax", "xholax", "\n", "a\nb"};

        for (String viejo : textos) {
            for (String nuevo : textos) {
                Cambio cambio = Cambio.entre(viejo, nuevo);
                if (cambio == null) {
                    assertEquals(viejo, nuevo);
                    continue;
                }
                assertEquals(nuevo, cambio.aplicarA(viejo),
                        "aplicar: '" + viejo + "' -> '" + nuevo + "'");
                assertEquals(viejo, cambio.revertirEn(nuevo),
                        "revertir: '" + nuevo + "' -> '" + viejo + "'");
            }
        }
    }
}
