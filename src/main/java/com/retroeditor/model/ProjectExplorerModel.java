package com.retroeditor.model;

/** 
 * Modelo para el explorador de proyectos 
*/
public class ProjectExplorerModel {


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

    /**
     * Borra un archivo o carpeta. Si es carpeta, el borrado es recursivo.
     * @param target Archivo o carpeta a borrar.
     * @return true si el borrado se completó.
     * @throws java.io.IOException si no se puede borrar.
     */
    public boolean deleteFileOrDirectory(java.io.File target) throws java.io.IOException {
        if (target == null) throw new java.io.IOException("Elemento inválido");
        if (!target.exists()) throw new java.io.IOException("El elemento no existe: " + target.getName());

        if (target.isDirectory()) {
            java.nio.file.Files.walkFileTree(target.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    java.nio.file.Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, java.io.IOException exc) throws java.io.IOException {
                    if (exc != null) throw exc;
                    java.nio.file.Files.delete(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            return true;
        }

        if (!target.delete()) {
            throw new java.io.IOException("No se pudo borrar: " + target.getName());
        }

        return true;
    }

}
