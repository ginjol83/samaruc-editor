package com.retroeditor.service;

/**
 * Monitor centralizado para rastrear acciones del usuario en la aplicación.
 * Proporciona métodos estáticos para registrar diferentes tipos de eventos.
 */
public final class UserActionMonitor {

    private UserActionMonitor() {}

    // ============ ARCHIVOS ============
    
    public static void fileOpened(String fileName, long bytes) {
        AppLogger.logMonitor("FILE_OPEN", tr("log.monitor.file.open", "{0} ({1} bytes)", fileName, bytes));
    }

    public static void fileSaved(String fileName, int charCount) {
        AppLogger.logMonitor("FILE_SAVE", tr("log.monitor.file.save", "{0} ({1} caracteres)", fileName, charCount));
    }

    public static void fileClosed(String fileName) {
        AppLogger.logMonitor("FILE_CLOSE", fileName);
    }

    public static void fileCreated(String filePath) {
        AppLogger.logMonitor("FILE_NEW", filePath);
    }

    public static void fileOpenDialogShown(String fileName) {
        AppLogger.logMonitor("FILE_DIALOG_OPEN", fileName);
    }

    public static void fileSaveAsDialogShown(String fileName) {
        AppLogger.logMonitor("FILE_DIALOG_SAVE_AS", fileName);
    }

    // ============ PROYECTOS ============
    
    public static void projectOpened(String projectPath) {
        AppLogger.logMonitor("PROJECT_OPEN", projectPath);
    }

    public static void projectCreated(String projectPath) {
        AppLogger.logMonitor("PROJECT_NEW", projectPath);
    }

    public static void projectSet(String projectName) {
        String none = tr("log.monitor.project.none", "Ninguno");
        AppLogger.logMonitor("PROJECT_SET", projectName != null ? projectName : none);
    }

    // ============ COMPILACIÓN ============
    
    public static void compilationStarted(String compiler, String fileName) {
        AppLogger.logMonitor("COMPILE_START", tr("log.monitor.compile.start", "{0} | Archivo: {1}", compiler, fileName));
    }

    public static void compilationFinished(int exitCode, String fileName) {
        AppLogger.logMonitor("COMPILE_END", tr("log.monitor.compile.end", "Codigo: {0} | Archivo: {1}", exitCode, fileName));
    }

    public static void compilationError(String message) {
        AppLogger.logMonitor("COMPILE_ERROR", message);
    }

    // ============ EJECUCIÓN ============
    
    public static void executionStarted(String emulator, String fileName) {
        AppLogger.logMonitor("EXEC_START", tr("log.monitor.exec.start", "{0} | Archivo: {1}", emulator, fileName));
    }

    public static void emulatorLaunched(String emulatorName, String filePath) {
        AppLogger.logMonitor("EMULATOR_LAUNCH", tr("log.monitor.emulator.launch", "{0} | {1}", emulatorName, filePath));
    }

    public static void emulatorRunning(String emulatorName) {
        AppLogger.logMonitor("EMULATOR_ACTIVE", tr("log.monitor.emulator.active", "{0} en ejecucion", emulatorName));
    }

    public static void executionError(String message) {
        AppLogger.logMonitor("EXEC_ERROR", message);
    }

    // ============ BÚSQUEDA ============
    
    public static void findInFileDialogOpened() {
        AppLogger.logMonitor("SEARCH_FILE_OPEN", tr("log.monitor.search.file.open", "Panel de busqueda en archivo abierto"));
    }

    public static void findInProjectDialogOpened() {
        AppLogger.logMonitor("SEARCH_PROJECT_OPEN", tr("log.monitor.search.project.open", "Panel de busqueda en proyecto abierto"));
    }

    public static void searchExecuted(String term, String scope) {
        AppLogger.logMonitor("SEARCH_EXEC", tr("log.monitor.search.exec", "Termino: {0} | Ambito: {1}", term, scope));
    }

    public static void fileOpenedFromSearch(String fileName) {
        AppLogger.logMonitor("FILE_FROM_SEARCH", fileName);
    }

    // ============ CONFIGURACIÓN ============
    
    public static void configDialogOpened() {
        AppLogger.logMonitor("CONFIG_OPEN", tr("log.monitor.config.open", "Ventana de configuracion mostrada"));
    }

    public static void configDialogClosed() {
        AppLogger.logMonitor("CONFIG_CLOSE", tr("log.monitor.config.close", "Ventana de configuracion cerrada"));
    }

    public static void languageChanged(String newLanguage) {
        AppLogger.logMonitor("CONFIG_LANG", tr("log.monitor.config.lang", "Idioma cambiado a: {0}", newLanguage));
    }

    public static void compilerChanged(String newCompiler) {
        AppLogger.logMonitor("CONFIG_COMPILER", tr("log.monitor.config.compiler", "Compilador seleccionado: {0}", newCompiler));
    }

    public static void compilerPathUpdated(String compilerName, String path) {
        AppLogger.logMonitor("CONFIG_COMPILER_PATH", compilerName + ": " + path);
    }

    public static void emulatorPathUpdated(String emulatorName, String path) {
        AppLogger.logMonitor("CONFIG_EMULATOR_PATH", emulatorName + ": " + path);
    }

    public static void pluginInstalled(String pluginName) {
        AppLogger.logMonitor("PLUGIN_INSTALL", pluginName);
    }

    public static void pluginEnabled(String pluginName) {
        AppLogger.logMonitor("PLUGIN_ENABLE", pluginName);
    }

    public static void pluginDisabled(String pluginName) {
        AppLogger.logMonitor("PLUGIN_DISABLE", pluginName);
    }

    // ============ EDICIÓN ============
    
    public static void undoExecuted() {
        AppLogger.logMonitor("EDIT_UNDO", tr("log.monitor.edit.undo", "Deshacer"));
    }

    public static void redoExecuted() {
        AppLogger.logMonitor("EDIT_REDO", tr("log.monitor.edit.redo", "Rehacer"));
    }

    public static void cutExecuted() {
        AppLogger.logMonitor("EDIT_CUT", tr("log.monitor.edit.cut", "Cortar"));
    }

    public static void copyExecuted() {
        AppLogger.logMonitor("EDIT_COPY", tr("log.monitor.edit.copy", "Copiar"));
    }

    public static void pasteExecuted() {
        AppLogger.logMonitor("EDIT_PASTE", tr("log.monitor.edit.paste", "Pegar"));
    }

    public static void selectAllExecuted() {
        AppLogger.logMonitor("EDIT_SELECT_ALL", tr("log.monitor.edit.selectAll", "Seleccionar todo"));
    }

    public static void goToLineExecuted(int lineNumber) {
        AppLogger.logMonitor("EDIT_GOTO_LINE", tr("log.monitor.edit.goto", "Ir a linea: {0}", lineNumber));
    }

    // ============ PESTAÑAS ============
    
    public static void newTabCreated(String tabName) {
        AppLogger.logMonitor("TAB_NEW", tabName);
    }

    public static void tabClosed(String tabName) {
        AppLogger.logMonitor("TAB_CLOSE", tabName);
    }

    public static void tabSwitched(String tabName) {
        AppLogger.logMonitor("TAB_SWITCH", tabName);
    }

    // ============ AYUDA ============
    
    public static void manualOpened() {
        AppLogger.logMonitor("HELP_MANUAL", tr("log.monitor.help.manual", "Manual abierto"));
    }

    public static void creditsShown() {
        AppLogger.logMonitor("HELP_CREDITS", tr("log.monitor.help.credits", "Creditos mostrados"));
    }

    public static void licensesOpened() {
        AppLogger.logMonitor("HELP_LICENSES", tr("log.monitor.help.licenses", "Licencias de terceros abiertas"));
    }

    public static void pluginManualOpened() {
        AppLogger.logMonitor("HELP_PLUGIN_MANUAL", tr("log.monitor.help.plugins.manual", "Manual de plugins abierto"));
    }

    // ============ ERRORES ============
    
    public static void errorOccurred(String errorType, String message) {
        AppLogger.logMonitor("ERROR_" + errorType, message);
    }

    private static String tr(String key, String fallback, Object... args) {
        return AppLogger.i18n(key, fallback, args);
    }
}

