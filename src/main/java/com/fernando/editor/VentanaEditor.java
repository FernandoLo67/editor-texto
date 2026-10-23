package com.fernando.editor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Interfaz grafica del editor, hecha con Swing, que es parte del JDK.
 *
 * <p>Toda la logica real vive en {@link DocumentoTexto}; esta clase solo la
 * conecta con la pantalla. Esa separacion es lo que permite que el proyecto
 * tenga 40 pruebas sin abrir una sola ventana — un editor probado a traves de su
 * interfaz grafica seria lento, fragil y dificil de ejecutar en un servidor sin
 * pantalla.
 *
 * <p>Los campos van {@code transient} y hay un {@code serialVersionUID} porque
 * {@link JFrame} es serializable: se hereda esa obligacion sin quererla, y es
 * uno de los precios de extender una clase de Swing en vez de componerla.
 */
public class VentanaEditor extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient JTextArea areaTexto = new JTextArea();
    private final transient JLabel barraEstado = new JLabel(" ");
    private transient DocumentoTexto documento = new DocumentoTexto();
    private transient Path archivoActual;

    public VentanaEditor() {
        super("Editor de Texto");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        areaTexto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        areaTexto.setLineWrap(true);
        areaTexto.setWrapStyleWord(true);

        add(new JScrollPane(areaTexto), BorderLayout.CENTER);
        add(barraEstado, BorderLayout.SOUTH);
        setJMenuBar(construirMenu());
        actualizarEstado();
    }

    private JMenuBar construirMenu() {
        JMenuBar barra = new JMenuBar();

        JMenu archivo = new JMenu("Archivo");
        archivo.add(item("Nuevo", KeyEvent.VK_N, e -> nuevo()));
        archivo.add(item("Abrir...", KeyEvent.VK_O, e -> abrir()));
        archivo.add(item("Guardar", KeyEvent.VK_S, e -> guardar()));
        archivo.addSeparator();
        archivo.add(item("Salir", KeyEvent.VK_Q, e -> dispose()));

        JMenu edicion = new JMenu("Edicion");
        edicion.add(item("Deshacer", KeyEvent.VK_Z, e -> {
            sincronizarDesdeArea();
            if (documento.deshacer()) {
                areaTexto.setText(documento.getContenido());
            }
            actualizarEstado();
        }));
        edicion.add(item("Rehacer", KeyEvent.VK_Y, e -> {
            sincronizarDesdeArea();
            if (documento.rehacer()) {
                areaTexto.setText(documento.getContenido());
            }
            actualizarEstado();
        }));
        edicion.addSeparator();
        edicion.add(item("Buscar y reemplazar...", KeyEvent.VK_H, e -> buscarYReemplazar()));

        barra.add(archivo);
        barra.add(edicion);
        return barra;
    }

    private JMenuItem item(String texto, int tecla, java.awt.event.ActionListener accion) {
        JMenuItem menuItem = new JMenuItem(texto);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(tecla, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        menuItem.addActionListener(accion);
        return menuItem;
    }

    private void nuevo() {
        documento = new DocumentoTexto();
        archivoActual = null;
        areaTexto.setText("");
        actualizarEstado();
    }

    private void abrir() {
        JFileChooser selector = new JFileChooser();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            archivoActual = selector.getSelectedFile().toPath();
            documento = ArchivoTexto.abrir(archivoActual);
            areaTexto.setText(documento.getContenido());
            actualizarEstado();
        } catch (IOException e) {
            mostrarError("No se pudo abrir el archivo: " + e.getMessage());
        }
    }

    private void guardar() {
        if (archivoActual == null) {
            JFileChooser selector = new JFileChooser();
            if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            archivoActual = selector.getSelectedFile().toPath();
        }
        try {
            sincronizarDesdeArea();
            ArchivoTexto.guardar(documento, archivoActual);
            actualizarEstado();
        } catch (IOException e) {
            mostrarError("No se pudo guardar: " + e.getMessage());
        }
    }

    private void buscarYReemplazar() {
        JTextField campoBuscar = new JTextField();
        JTextField campoReemplazo = new JTextField();
        Object[] formulario = {"Buscar:", campoBuscar, "Reemplazar por:", campoReemplazo};

        if (JOptionPane.showConfirmDialog(this, formulario, "Buscar y reemplazar",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        sincronizarDesdeArea();
        int cambios = documento.reemplazarTodo(campoBuscar.getText(), campoReemplazo.getText());
        areaTexto.setText(documento.getContenido());
        actualizarEstado();
        JOptionPane.showMessageDialog(this, cambios + " coincidencia(s) reemplazada(s).");
    }

    /** Copia al documento lo que el usuario escribio en el area de texto. */
    private void sincronizarDesdeArea() {
        documento.setContenido(areaTexto.getText());
    }

    private void actualizarEstado() {
        DocumentoTexto vista = new DocumentoTexto(areaTexto.getText());
        barraEstado.setText(String.format("  %s | %d palabras | %d caracteres | %d lineas%s",
                archivoActual == null ? "(sin guardar)" : archivoActual.getFileName(),
                vista.contarPalabras(), vista.contarCaracteres(), vista.contarLineas(),
                documento.estaModificado() ? " | modificado" : ""));
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
