package com.retroeditor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.retroeditor.model.WorkspaceModel;
import java.io.File;
import java.io.IOException;

/**
 * Servicio para cargar y guardar la configuración del workspace en formato JSON.
 */
public class WorkspaceService {
    private static final String WORKSPACE_FILE = "workspace.json";
    private final ObjectMapper objectMapper;

    public WorkspaceService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Carga el archivo workspace.json desde la raíz del proyecto.
     * @param projectRoot Directorio raíz del proyecto.
     * @return El modelo cargado o un modelo vacío si no existe.
     */
    public WorkspaceModel loadWorkspace(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return new WorkspaceModel();
        }

        File file = new File(projectRoot, WORKSPACE_FILE);
        if (!file.exists()) {
            WorkspaceModel model = new WorkspaceModel();
            model.setProjectName(projectRoot.getName());
            return model;
        }

        try {
            return objectMapper.readValue(file, WorkspaceModel.class);
        } catch (IOException e) {
            System.err.println("Error al cargar workspace.json: " + e.getMessage());
            WorkspaceModel model = new WorkspaceModel();
            model.setProjectName(projectRoot.getName());
            return model;
        }
    }

    /**
     * Guarda el modelo de workspace en la raíz del proyecto.
     * @param projectRoot Directorio raíz del proyecto.
     * @param model Modelo a guardar.
     */
    public void saveWorkspace(File projectRoot, WorkspaceModel model) {
        if (projectRoot == null || !projectRoot.isDirectory() || model == null) {
            return;
        }

        File file = new File(projectRoot, WORKSPACE_FILE);
        try {
            objectMapper.writeValue(file, model);
        } catch (IOException e) {
            System.err.println("Error al guardar workspace.json: " + e.getMessage());
        }
    }
}
