package com.retroeditor.controller.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.emulator.EmulatorLauncher;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

public class BuildController {

    FXUtils            fxUtils            = new FXUtils();
    TerminalController terminalController = new TerminalController();

    /**
     * Compila el archivo actualmente abierto en la pestaña seleccionada.
     * @param event
     * @param consoleOutputArea
     * @param tabPane
     * @param tabFileMap
     * @param configModel
     */
    public void onCompilar(ActionEvent event, 
                          TextArea consoleOutputArea,
                          TabPane tabPane,
                          Map<Tab, File> tabFileMap,
                          ConfigModel configModel) {

        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) {
            terminalController.appendConsoleOutput("No hay archivo abierto para compilar.",consoleOutputArea);
            return;
        }

        File file = tabFileMap.get(tab);

        if (file == null) {
            terminalController.appendConsoleOutput("No se puede compilar: el archivo no está guardado.",consoleOutputArea);
            return;
        }

        String   selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
        String   filePath         = file.getAbsolutePath();
        String   outputName       = filePath.substring(0, filePath.lastIndexOf('.'));
        String[] cmd;

        if ("Spectrum".equalsIgnoreCase(selectedCompiler)) {
            String spectrumBin = configModel.getConfigProperty("spectrum_bin", "");

            if (spectrumBin == null || spectrumBin.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);

                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se ha configurado la ruta de Spectrum");
                alert.setContentText("Por favor, configura la ruta del bin de Spectrum en las opciones de configuración.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se ha configurado la ruta del bin de Spectrum.",consoleOutputArea);

                return;
            }

            String compiler = spectrumBin + "/zcc";

            cmd = new String[]{compiler, "-o", outputName + ".tap", filePath};

        } else {
            String gbdkBin = configModel.getConfigProperty("gbdk_bin", "");

            if (gbdkBin == null || gbdkBin.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se ha configurado la ruta de GBDK");
                alert.setContentText("Por favor, configura la ruta del bin de GBDK en las opciones de configuración.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se ha configurado la ruta del bin de GBDK.",consoleOutputArea);

                return;
            }
            
            String compiler = gbdkBin + "/lcc";
            cmd = new String[]{compiler, "-o", outputName + ".gb", filePath};
        }

        terminalController.appendConsoleOutput("Compilando con: " + String.join(" ", cmd),consoleOutputArea);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);

            pb.redirectErrorStream(true);

            Process        proc   = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String         line;

            while ((line = reader.readLine()) != null) {
                terminalController.appendConsoleOutput(line, consoleOutputArea);
            }

            int exitCode = proc.waitFor();
            terminalController.appendConsoleOutput("Compilación finalizada. Código de salida: " + exitCode, consoleOutputArea);

        } catch (Exception e) {
            terminalController.appendConsoleOutput("Error al ejecutar el compilador: " + e.getMessage(), consoleOutputArea);
        }
    }

    /**
     * Ejecuta el archivo compilado en el emulador correspondiente.
     * @param event
     * @param consoleOutputArea
     * @param tabPane
     * @param tabFileMap
     * @param configModel
     */
    public  void onEjecutar( ActionEvent event,
                             TextArea consoleOutputArea,
                             TabPane tabPane,
                             Map<Tab, File> tabFileMap,
                             ConfigModel configModel) {

        terminalController.appendConsoleOutput("Compilando y ejecutando...", consoleOutputArea);

        // Detectar compilador seleccionado
        String selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");

        if (selectedCompiler.equals("GBDK")) {
            // Buscar el archivo .gb generado (asumimos mismo nombre que el archivo abierto)
            File file = getCurrentFile(tabPane, tabFileMap);

            if (file == null) {
                terminalController.appendConsoleOutput("No hay archivo abierto para ejecutar.", consoleOutputArea);
                return;
            }

            String filePath     = file.getAbsolutePath();
            String outputName   = filePath.substring(0, filePath.lastIndexOf('.')) + ".gb";
            File romFile        = new File(outputName);
            
            if (!romFile.exists()) {
                terminalController.appendConsoleOutput("No se encontró el archivo ROM: " + outputName, consoleOutputArea);
                return;
            }

            // Ruta al coffee-gb.jar
            String coffeeGbPath = "libs/coffee-gb-1.6.0.jar";
            File    coffeeGbJar = new File(coffeeGbPath);

            if (!coffeeGbJar.exists()) {
                terminalController.appendConsoleOutput("No se encontró coffee-gb.jar en " + coffeeGbPath, consoleOutputArea);
                return;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", coffeeGbJar.getAbsolutePath(), romFile.getAbsolutePath()
                );
                pb.start();
                terminalController.appendConsoleOutput("Lanzando emulador Coffee GB con: " + romFile.getName(), consoleOutputArea);
            } catch (Exception e) {
                terminalController.appendConsoleOutput("Error al lanzar Coffee GB: " + e.getMessage(), consoleOutputArea);
            }

        } else if (selectedCompiler.equalsIgnoreCase("Spectrum")) {
            // Para Spectrum, buscar el .tap generado (o similar) y lanzar JSpeccy
            File file = getCurrentFile(tabPane, tabFileMap);

            if (file == null) {
                terminalController.appendConsoleOutput("No hay archivo abierto para ejecutar.", consoleOutputArea);
                return;
            }

            String filePath     = file.getAbsolutePath();
            String base         = filePath.substring(0, filePath.lastIndexOf('.'));
            File tapFile        = new File(base + ".tap");

            if (!tapFile.exists()) {
                // Tal vez sea un snapshot .sna o .z80
                File sna = new File(base + ".sna");
                File z80 = new File(base + ".z80");

                if (sna.exists()) tapFile = sna; else if (z80.exists()) tapFile = z80; else {
                    terminalController.appendConsoleOutput("No se encontró un snapshot/ROM para ejecutar (buscando .tap/.sna/.z80)", consoleOutputArea);
                    return;
                }
            }

            String jspeccyPath = configModel.getConfigProperty("jspeccy_bin", "");
            
            if (jspeccyPath == null || jspeccyPath.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);

                alert.setTitle      ("Error al ejecutar emulador Spectrum");
                alert.setHeaderText ("No está configurado JSpeccy");
                alert.setContentText("Por favor, configura la ruta al ejecutable jspeccy.jar o bin en la configuración.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se ha configurado la ruta de JSpeccy.",consoleOutputArea);

                return;
            }

            try {
                EmulatorLauncher.launchJSpeccy(new File(jspeccyPath), tapFile, consoleOutputArea, null);
                terminalController.appendConsoleOutput("Lanzando JSpeccy con: " + tapFile.getName(), consoleOutputArea);
            } catch (Exception ex) {
                terminalController.appendConsoleOutput("Error al lanzar JSpeccy: " + ex.getMessage(), consoleOutputArea);
            }

        } else {
            terminalController.appendConsoleOutput("Ejecución automática solo soportada para GBDK/ROM Game Boy.", consoleOutputArea);
        }
    }

    /**
     * Devuelve el archivo asociado al tab seleccionado actualmente.
     * @param tabPane
     * @param tabFileMap
     * @return Archivo asociado o null si no hay tab seleccionado o no tiene archivo.
     */
    private File getCurrentFile(TabPane tabPane, Map<Tab, File> tabFileMap) {
        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) return null;

        return tabFileMap.get(tab);
    }
}
