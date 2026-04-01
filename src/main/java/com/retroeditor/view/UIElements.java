package com.retroeditor.view;

import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 * Clase contenedora de todos los elementos de la interfaz gráfica
 * que necesitan actualización de idioma u otros textos dinámicos.
 * 
 * Esta clase se utiliza para agrupar las referencias a los controles
 * JavaFX (botones, menús, ítems, etc.) y poder pasarlas fácilmente
 * al controlador, manteniendo el código limpio y siguiendo el patrón MVC.
 */
public class UIElements {

    // ===== Menús =====
    public Menu     menuArchivo;
    public Menu     menuEdicion;
    public Menu     menuCompilacion;

    // ===== Botones principales =====
    public Button   btnCut;
    public Button   btnOpen;
    public Button   btnSave;
    public Button   btnUndo;
    public Button   btnRedo;
    public Button   btnCopy;
    public Button   btnNuevo;
    public Button   btnClose;
    public Button   btnPaste;
    public Button   btnConfig;
    public Button   btnCompilar;
    public Button   btnCancelarCompilacion;
    public Button   btnEjecutar;
    public Button   btnNuevoProyecto;
    public Button   btnAbrirProyecto;

    // ===== Elementos de menú =====
    public MenuItem menuItemFind;
    public MenuItem menuItemNuevo;
    public MenuItem menuItemAbrir;
    public MenuItem menuItemSalir;
    public MenuItem menuItemCerrar;
    public MenuItem menuItemConfig;
    public MenuItem menuItemGuardar;
    public MenuItem menuItemCompilar;
    public MenuItem menuItemEjecutar;
    public MenuItem menuItemFindProject;
    public MenuItem menuItemGuardarComo;

    /**
     * Constructor vacío.
     * 
     * Los campos se asignan desde el controlador o al cargar el FXML.
     */
    public UIElements() { }
}
