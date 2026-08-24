
package com.retroeditor.controller.projectExplorer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.retroeditor.controller.MainController;
import com.retroeditor.model.ProjectExplorerModel;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;


public class ProjectExplorerController {
    
    ProjectExplorerModel projectExplorerModel = new ProjectExplorerModel();

    // Subclase para asociar File a cada TreeItem
    private static class FileTreeItem extends javafx.scene.control.TreeItem<String> {

        private final File file;

        public FileTreeItem(File file) {
            super(file != null ? file.getName() : "");
            this.file = file;
        }

        public File getFile() { return file; }
    }


    @FXML
    private TreeView<String> treeView;
    private File             rootDirectory;
    private MainController   mainController;

    /**
     * Establece el controlador principal para permitir la comunicación.
     * @param mainController Controlador principal.
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Establece el directorio raíz para el explorador de proyectos.
     * @param dir Directorio raíz.
     */
    public void setRootDirectory(File dir) {
        this.rootDirectory = dir;
        FileTreeItem rootItem = createNode(dir);

        if (rootItem != null) {
            treeView.setRoot(rootItem);
            rootItem.setExpanded(true);
        } else {
            treeView.setRoot(null);
        }

        treeView.setShowRoot(true);

        treeView.setCellFactory(param -> new TreeCell<String>() {

            private final ContextMenu cellMenu = new ContextMenu();
            private final MenuItem newFileItem = new MenuItem("Nuevo archivo");
            private final MenuItem newFolderItem = new MenuItem("Nueva carpeta");
            private final MenuItem renameItem = new MenuItem("Renombrar");
            private final MenuItem deleteItem = new MenuItem("Eliminar");
            private final MenuItem openInSplitItem = new MenuItem("Abrir en vista dividida");
            private final MenuItem runRomItem = new MenuItem("Ejecutar ROM en emulador");
            private final MenuItem openInExplorerItem = new MenuItem("Abrir directorio en Windows");

            {
                cellMenu.getItems().addAll(newFileItem, newFolderItem, renameItem, deleteItem, openInSplitItem, runRomItem, openInExplorerItem);

                openInSplitItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    File file = getFileFromTreeItem(selected);
                    if (file != null && file.isFile() && mainController != null) {
                        mainController.openFileInSecondaryTab(file);
                    }
                });

                newFileItem.setOnAction(e -> {

                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    File         parent0  = getFileFromTreeItem(selected);

                    final File parent = (parent0 == null) ? rootDirectory : parent0;

                    if (parent != null && parent.isDirectory()) {
                        TextInputDialog dialog = new TextInputDialog("nuevo.c");
                        dialog.setTitle("Nuevo archivo");
                        dialog.setHeaderText("Crear nuevo archivo en " + parent.getName());
                        dialog.setContentText("Nombre del archivo:");

                        dialog.showAndWait().ifPresent(rawName -> {
                            String name = rawName != null ? rawName.trim() : "";

                            if (name.isEmpty()) {
                                showError("Nombre inválido.");
                                return;
                            }

                            if (name.contains(java.io.File.separator) || name.contains("/")) {
                                showError("El nombre no puede contener separadores de ruta.");
                                return;
                            }

                            System.out.println("[ProjectExplorer] Creating file '" + name + "' in " + parent.getAbsolutePath());
                            
                            try {
                                java.io.File created = new File(parent, name);
                                projectExplorerModel.createFile(parent, name);
                                refreshTree();

                                javafx.application.Platform.runLater(() -> {
                                    // seleccionar el nuevo archivo en el árbol
                                    expandAndSelect(created);

                                    // abrir el archivo en el editor
                                    if (mainController != null) {
                                        mainController.openFileFromExplorer(created);
                                    }

                                    javafx.scene.control.Alert ok = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                                    
                                    ok.setTitle      ("Archivo creado");
                                    ok.setHeaderText (null);
                                    ok.setContentText("Archivo creado: " + name);
                                    ok.showAndWait   ();
                                });

                            } catch (IOException ex) {
                                ex.printStackTrace();
                                showError("Error al crear archivo: " + ex.getMessage());
                            }
                        });
                    }
                });

                // Acción para crear nueva carpeta
                newFolderItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    File         parent0  = getFileFromTreeItem(selected);

                    final File parent = (parent0 == null) ? rootDirectory : parent0;
                    
                    if (parent != null && parent.isDirectory()) {
                        TextInputDialog dialog = new TextInputDialog("nueva_carpeta");

                        dialog.setTitle("Nueva carpeta");
                        dialog.setHeaderText("Crear nueva carpeta en " + parent.getName());
                        dialog.setContentText("Nombre de la carpeta:");

                        dialog.showAndWait().ifPresent(rawName -> {
                            String name = rawName != null ? rawName.trim() : "";

                            if (name.isEmpty()) {
                                showError("Nombre inválido.");
                                return;
                            }

                            if (name.contains(java.io.File.separator) || name.contains("/")) {
                                showError("El nombre no puede contener separadores de ruta.");
                                return;
                            }

                            System.out.println("[ProjectExplorer] Creating directory '" + name + "' in " + parent.getAbsolutePath());
                            
                            try {
                                java.io.File createdDir = new File(parent, name);
                                projectExplorerModel.createDirectory(parent, name);
                                refreshTree();
                                javafx.application.Platform.runLater(() -> {
                                    
                                    // expandir y seleccionar la nueva carpeta en el árbol
                                    expandAndSelect(createdDir);
                                    
                                    // Asegurar que la carpeta creada esté expandida para que el usuario pueda crear archivos dentro
                                    FileTreeItem found = findTreeItem((FileTreeItem) treeView.getRoot(), createdDir);
                                    
                                    if (found != null) found.setExpanded(true);

                                    javafx.scene.control.Alert ok = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                                    
                                    ok.setTitle      ("Carpeta creada");
                                    ok.setHeaderText (null);
                                    ok.setContentText("Carpeta creada: " + name);
                                    ok.showAndWait   ();
                                });

                            } catch (IOException ex) {
                                ex.printStackTrace();
                                showError("Error al crear carpeta: " + ex.getMessage());
                            }
                        });
                    }
                });

                openInExplorerItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    File target = getFileFromTreeItem(selected);
                    openDirectoryInWindowsExplorer(target);
                });

                runRomItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    File target = getFileFromTreeItem(selected);

                    if (target == null || !target.isFile()) {
                        showError("Selecciona un archivo ROM para ejecutar.");
                        return;
                    }

                    if (mainController == null) {
                        showError("No hay controlador principal disponible para ejecutar la ROM.");
                        return;
                    }

                    mainController.runRomFromProjectExplorer(target);
                });

                renameItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    renameTreeItem(selected);
                });

                deleteItem.setOnAction(e -> {
                    FileTreeItem selected = (FileTreeItem) getTreeItem();
                    deleteTreeItem(selected);
                });

                // Manejo de clic derecho para seleccionar el ítem
                this.setOnMousePressed(evt -> {

                    if (evt.isSecondaryButtonDown()) {

                        FileTreeItem ti = (FileTreeItem) getTreeItem();

                        if (ti != null) {
                            treeView.getSelectionModel().select(ti);
                            System.out.println("[ProjectExplorer] Right-click on: " + ti.getFile());
                        } else {
                            System.out.println("[ProjectExplorer] Right-click on cell with no TreeItem");
                        }
                    }
                });
                
                // Mostrar el menú contextual en la ubicación del puntero para garantizar la visibilidad
                this.setOnContextMenuRequested(evt -> {
                    
                    try {
                        FileTreeItem ti = (FileTreeItem) getTreeItem();
                        System.out.println("[ProjectExplorer] Context menu requested for: " + (ti != null ? ti.getFile() : "null"));
                        cellMenu.show(this, evt.getScreenX(), evt.getScreenY());
                        evt.consume();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }

            /**
             * Actualiza el contenido de la celda.
             * @param String  item
             * @param boolean empty
             */
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    FileTreeItem treeItem = (FileTreeItem) getTreeItem();
                    File file = null;

                    if (treeItem != null) file = treeItem.getFile();

                    if (file == null && treeItem != null && treeItem.getParent() == null) file = rootDirectory;

                    javafx.scene.control.Label iconLabel;

                    if (file != null && file.isDirectory()) {
                        iconLabel = new javafx.scene.control.Label("\uD83D\uDCC1"); 
                    } else {
                        iconLabel = new javafx.scene.control.Label("\uD83D\uDCC4");
                    }
                    
                    javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, iconLabel, new javafx.scene.control.Label(item));
                    setGraphic(hbox);
                    setText(null);
                    boolean canRunRom = file != null
                        && file.isFile()
                        && mainController != null
                        && mainController.isSupportedRomFile(file);
                    boolean canDelete = file != null
                        && !(rootDirectory != null && file.getAbsolutePath().equals(rootDirectory.getAbsolutePath()));
                    boolean canRename = canDelete;
                    runRomItem.setDisable(!canRunRom);
                    renameItem.setDisable(!canRename);
                    deleteItem.setDisable(!canDelete);
                    // Asignar el menú contextual a la celda para que la acción use el TreeItem de esta celda
                    setContextMenu(cellMenu);
                }
            }
        });

        // Permitir abrir archivo con Enter
        treeView.setOnKeyPressed((KeyEvent event) -> {

            if (event.getCode() == KeyCode.ENTER) {
                FileTreeItem selected = (FileTreeItem) treeView.getSelectionModel().getSelectedItem();

                if (selected != null) {
                    File file = getFileFromTreeItem(selected);

                    if (file != null && file.isFile() && mainController != null) {
                        mainController.openFileFromExplorer(file);
                    }
                }
                event.consume();
                return;
            }

            if (event.getCode() == KeyCode.DELETE) {
                FileTreeItem selected = (FileTreeItem) treeView.getSelectionModel().getSelectedItem();

                deleteTreeItem(selected);

                event.consume();
            }
        });

        // Doble click para abrir archivo
        treeView.setOnMouseClicked((MouseEvent event) -> {

            if (event.getClickCount() == 2) {
                FileTreeItem selected = (FileTreeItem) treeView.getSelectionModel().getSelectedItem();

                if (selected != null) {
                    File file = getFileFromTreeItem(selected);

                    if (file != null && file.isFile() && mainController != null) {
                        if (mainController.isSupportedRomFile(file)) {
                            mainController.runRomFromProjectExplorer(file);
                        } else {
                            mainController.openFileFromExplorer(file);
                        }
                    }
                }
            }
        });
    }

    // Recursivo: crea el árbol de archivos y carpetas
    private FileTreeItem createNode(File file) {

        if (file == null) return null;

        FileTreeItem node = new FileTreeItem(file);

        if (file.isDirectory()) {
            // añadir un nodo hijo ficticio para mostrar el icono de expansión
            node.getChildren().add(new javafx.scene.control.TreeItem<>(""));

            // cuando se expanda, cargar los hijos reales
            node.addEventHandler(javafx.scene.control.TreeItem.branchExpandedEvent(), ev -> {
                javafx.scene.control.TreeItem<?> sourceItem = (javafx.scene.control.TreeItem<?>) ev.getSource();

                if (!(sourceItem instanceof FileTreeItem)) return;

                FileTreeItem source = (FileTreeItem) sourceItem;

                // Si solo tiene el nodo ficticio, cargar los hijos reales
                if (source.getChildren().size() == 1 && !(source.getChildren().get(0) instanceof FileTreeItem)) {
                    source.getChildren().clear();
                    File f = source.getFile();

                    if (f != null && f.isDirectory()) {
                        File[] files = f.listFiles();

                        if (files != null) {
                            java.util.Arrays.sort(files, (a, b) -> {

                                if (a.isDirectory() && !b.isDirectory()) return -1;

                                if (!a.isDirectory() && b.isDirectory()) return 1;

                                return a.getName().compareToIgnoreCase(b.getName());
                            });
                            for (File child : files) {
                                FileTreeItem childNode = createNode(child);

                                if (childNode != null) source.getChildren().add(childNode);
                            }
                        }
                    }
                }
            });
        }
        return node;
    }

    /**
     * Refresca el árbol de archivos.
     */
    public void refreshTree() {
        if (rootDirectory != null) setRootDirectory(rootDirectory);
    }

    @FXML
    private void onRefreshTree() {
        refreshTree();
    }

    /**
     * Encuentra un FileTreeItem buscando por la ruta absoluta del archivo.
     * @param root   Nodo raíz donde iniciar la búsqueda.
     * @param target Archivo a buscar.
     * @return FileTreeItem correspondiente al archivo, o null si no se encuentra.
     */
    private FileTreeItem findTreeItem(FileTreeItem root, File target) {
        if (root == null || target == null) return null;

        File f = root.getFile();

        if (f != null && f.getAbsolutePath().equals(target.getAbsolutePath())) return root;

        for (javafx.scene.control.TreeItem<String> child : root.getChildren()) {
            if (child instanceof FileTreeItem) {
                FileTreeItem found = findTreeItem((FileTreeItem) child, target);

                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Expande y selecciona un archivo o carpeta en el árbol.
     * @param File target Archivo o carpeta a seleccionar.
     */
    private void expandAndSelect(File target) {

        if (target == null || treeView == null) return;

        FileTreeItem root = (FileTreeItem) treeView.getRoot();

        if (root == null) return;

        FileTreeItem found = findTreeItem(root, target);

        if (found == null) return;

        // expande los padres
        javafx.scene.control.TreeItem<String> parent = found.getParent();

        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
        
        // selecciona y desplaza
        treeView.getSelectionModel().select(found);
        treeView.scrollTo(treeView.getSelectionModel().getSelectedIndex());
    }

    /**
     * Extrae el File asociado a un FileTreeItem.
     * @param FileTreeItem item
     * @return File
     */
    private File getFileFromTreeItem(FileTreeItem item) {

        if (item == null) return null;

        // Prefierir el File almacenado en el propio FileTreeItem
        File f = item.getFile();

        if (f != null) return f;

        // Si este es el nodo raíz (sin padre), usar rootDirectory como respaldo
        if (item.getParent() == null) return rootDirectory;

        return null;
    }

    /**
     * Muestra un mensaje de error.
     * @param String msg
     */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private boolean confirmDelete(File target) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        String type = target.isDirectory() ? "carpeta" : "archivo";

        alert.setTitle("Confirmar borrado");
        alert.setHeaderText("Se va a borrar el " + type + ": " + target.getName());
        alert.setContentText("Esta acción no se puede deshacer. ¿Quieres continuar?");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void deleteTreeItem(FileTreeItem selected) {
        if (selected == null) return;

        File target = getFileFromTreeItem(selected);
        if (target == null) return;

        if (rootDirectory != null && target.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            showError("No se puede borrar la carpeta raíz del proyecto.");
            return;
        }

        if (!confirmDelete(target)) return;

        try {
            boolean targetWasDirectory = target.isDirectory();
            projectExplorerModel.deleteFileOrDirectory(target);
            if (mainController != null) {
                mainController.closeTabsForDeletedTarget(target, targetWasDirectory);
            }
            refreshTree();
        } catch (IOException ex) {
            showError("Error al borrar: " + ex.getMessage());
        }
    }

    public void renameSelectedTreeItem() {
        FileTreeItem selected = (FileTreeItem) treeView.getSelectionModel().getSelectedItem();
        renameTreeItem(selected);
    }

    private void renameTreeItem(FileTreeItem selected) {
        if (selected == null) {
            showError("Selecciona un archivo o carpeta para renombrar.");
            return;
        }

        File target = getFileFromTreeItem(selected);
        if (target == null) {
            showError("No se pudo resolver el elemento seleccionado.");
            return;
        }

        if (rootDirectory != null && target.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            showError("No se puede renombrar la carpeta raíz del proyecto.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(target.getName());
        dialog.setTitle("Renombrar");
        dialog.setHeaderText("Renombrar " + (target.isDirectory() ? "carpeta" : "archivo") + ": " + target.getName());
        dialog.setContentText("Nuevo nombre:");

        dialog.showAndWait().ifPresent(rawName -> {
            String newName = rawName != null ? rawName.trim() : "";

            if (newName.isEmpty()) {
                showError("Nombre inválido.");
                return;
            }

            if (newName.contains(java.io.File.separator) || newName.contains("/")) {
                showError("El nombre no puede contener separadores de ruta.");
                return;
            }

            if (newName.equals(target.getName())) return;

            File renamedTarget = new File(target.getParentFile(), newName);

            try {
                projectExplorerModel.renameFileOrDirectory(target, newName);
                if (mainController != null) {
                    mainController.onProjectExplorerTargetRenamed(target, renamedTarget, target.isDirectory());
                }
                refreshTree();
                javafx.application.Platform.runLater(() -> expandAndSelect(renamedTarget));
            } catch (IOException ex) {
                showError("Error al renombrar: " + ex.getMessage());
            }
        });
    }

    private void openDirectoryInWindowsExplorer(File selectedPath) {
        File directoryToOpen = resolveDirectoryToOpen(selectedPath);

        if (directoryToOpen == null) {
            showError("No se pudo resolver el directorio para abrir.");
            return;
        }

        if (!directoryToOpen.exists() || !directoryToOpen.isDirectory()) {
            showError("El directorio no existe o no es válido: " + directoryToOpen.getAbsolutePath());
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directoryToOpen);
                return;
            }

            // Fallback específico para Windows si Desktop no está disponible.
            new ProcessBuilder("explorer.exe", directoryToOpen.getAbsolutePath()).start();
        } catch (IOException | SecurityException ex) {
            showError("No se pudo abrir el directorio: " + ex.getMessage());
        }
    }

    private File resolveDirectoryToOpen(File selectedPath) {
        File target = selectedPath != null ? selectedPath : rootDirectory;

        if (target == null) return null;
        if (target.isDirectory()) return target;

        File parent = target.getParentFile();
        return parent != null ? parent : rootDirectory;
    }
}




