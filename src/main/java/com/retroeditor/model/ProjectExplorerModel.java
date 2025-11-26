package com.retroeditor.model;

/** 
 * Modelo para el explorador de proyectos 
*/
public class ProjectExplorerModel {

    public void showError(String msg) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    /**
     * Crea un nuevo archivo dentro de parent con el nombre dado.
     * @param parent Directorio padre donde crear el archivo.
     * @param name Nombre del nuevo archivo.
     * @return true si el archivo se creó correctamente.
     * @throws java.io.IOException con un mensaje amigable en caso de fallo.
     */
    public boolean createFile(java.io.File parent, String name) throws java.io.IOException {
        if (parent == null) throw new java.io.IOException("Directorio padre inválido");
        
        if (!parent.exists() || !parent.isDirectory()) throw new java.io.IOException("El directorio destino no existe o no es una carpeta");
        
        java.io.File newFile = new java.io.File(parent, name);
        
        if (newFile.exists()) throw new java.io.IOException("El archivo ya existe: " + name);
        
        boolean created = newFile.createNewFile();
        
        if (!created) throw new java.io.IOException("No se pudo crear el archivo: " + name);
        
        return true;
    }

    /**
     * Crea un nuevo directorio dentro de parent con el nombre dado.
     * @param parent Directorio padre donde crear la carpeta.
     * @param name Nombre de la nueva carpeta.
     * @return true si la carpeta se creó correctamente.
     * @throws java.io.IOException con un mensaje amigable en caso de fallo.
     */
    public boolean createDirectory(java.io.File parent, String name) throws java.io.IOException {
        if (parent == null) throw new java.io.IOException("Directorio padre inválido");
        
        if (!parent.exists() || !parent.isDirectory()) throw new java.io.IOException("El directorio destino no existe o no es una carpeta");
        
        java.io.File newDir = new java.io.File(parent, name);
        
        if (newDir.exists()) throw new java.io.IOException("La carpeta ya existe: " + name);
        
        boolean created = newDir.mkdir();
        
        if (!created) throw new java.io.IOException("No se pudo crear la carpeta: " + name);
        
        return true;
    }

}
