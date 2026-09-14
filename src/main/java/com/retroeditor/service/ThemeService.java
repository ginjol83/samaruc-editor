package com.retroeditor.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.model.EditorTheme;

/**
 * Servicio de modos de apariencia.
 *
 * <p>Gestiona la lista de modos (incorporados + personalizados), su
 * persistencia en disco y la resolución del modo activo a partir de la
 * configuración, incluyendo la migración desde la antigua propiedad
 * {@code editor_appearance}.</p>
 */
public class ThemeService {

    public static final String THEMES_FILE_NAME = ".retroeditor-themes.json";
    public static final String DEFAULT_THEME_ID = BuiltInThemes.MODERN_DARK_ID;

    private final ThemeRepository repository;
    private final File themesFile;
    private final List<EditorTheme> customThemes = new ArrayList<>();

    public ThemeService() {
        this(new JsonThemeRepository(), defaultThemesFile());
    }

    public ThemeService(ThemeRepository repository, File themesFile) {
        this.repository = repository;
        this.themesFile = themesFile;
        reload();
    }

    /**
     * Archivo por defecto en el directorio del usuario ({@code user.home}).
     */
    private static File defaultThemesFile() {
        return new File(System.getProperty("user.home"), THEMES_FILE_NAME);
    }

    /**
     * Recarga los modos personalizados desde disco.
     */
    public void reload() {
        customThemes.clear();
        customThemes.addAll(repository.load(themesFile));
    }

    public File getThemesFile() {
        return themesFile;
    }

    /**
     * Todos los modos disponibles: primero los incorporados, después los personalizados.
     */
    public List<EditorTheme> getAllThemes() {
        List<EditorTheme> result = new ArrayList<>();
        result.addAll(BuiltInThemes.all());
        result.addAll(customThemes);
        return result;
    }

    public List<EditorTheme> getCustomThemes() {
        return new ArrayList<>(customThemes);
    }

    public boolean isCustom(String id) {
        return customThemes.stream().anyMatch(t -> id != null && id.equals(t.getId()));
    }

    public boolean isBuiltIn(String id) {
        return BuiltInThemes.isBuiltIn(id);
    }

    /**
     * Devuelve un modo por su id, o {@code null} si no existe.
     */
    public EditorTheme getTheme(String id) {
        if (id == null) return null;
        EditorTheme builtIn = BuiltInThemes.get(id);
        if (builtIn != null) return builtIn;
        for (EditorTheme theme : customThemes) {
            if (id.equals(theme.getId())) {
                return theme;
            }
        }
        return null;
    }

    /**
     * Devuelve un modo por su id, con el modo oscuro por defecto como respaldo.
     */
    public EditorTheme getThemeOrDefault(String id) {
        EditorTheme theme = getTheme(id);
        return theme != null ? theme : BuiltInThemes.modernDark();
    }

    /**
     * Resuelve el modo activo a partir de la configuración.
     *
     * <p>Si existe {@code editor_theme} y el modo es conocido se utiliza ese.
     * En caso contrario se migra la antigua {@code editor_appearance}
     * ({@code classic} → {@code classic}, el resto → {@code modern_dark}).
     * Como {@code editor_theme} tiene valor por defecto {@code modern_dark},
     * si además aparece {@code editor_appearance=classic} se interpreta como
     * una configuración legacy previa a la existencia de modos personalizados.</p>
     */
    public String resolveActiveThemeId(ConfigModel config) {
        String themeId = config != null ? config.getConfigProperty("editor_theme", null) : null;
        String appearance = config != null
            ? config.getConfigProperty("editor_appearance", BuiltInThemes.MODERN_DARK_ID)
            : BuiltInThemes.MODERN_DARK_ID;

        if (themeId != null && getTheme(themeId) != null) {
            // Migración legacy: cuando solo se configuró editor_appearance=classic,
            // editor_theme queda con su valor por defecto sin ser una elección explícita.
            if (BuiltInThemes.MODERN_DARK_ID.equals(themeId)
                && BuiltInThemes.CLASSIC_ID.equalsIgnoreCase(appearance)) {
                return BuiltInThemes.CLASSIC_ID;
            }
            return themeId;
        }

        if (BuiltInThemes.CLASSIC_ID.equalsIgnoreCase(appearance)) {
            return BuiltInThemes.CLASSIC_ID;
        }
        return DEFAULT_THEME_ID;
    }

    /**
     * Crea y persiste un modo personalizado a partir de otro existente.
     *
     * @param sourceId id del modo a copiar; {@code null} usa el modo activo por defecto
     * @param newName  nombre visible del nuevo modo
     * @return el modo creado
     */
    public EditorTheme duplicate(String sourceId, String newName) {
        EditorTheme source = getThemeOrDefault(sourceId);
        String name = newName != null && !newName.isBlank() ? newName.trim() : "Modo personalizado";

        EditorTheme copy = source.copy();
        copy.setBuiltIn(false);
        copy.setId(newCustomId(name));
        copy.setName(name);
        customThemes.add(copy);
        persist();
        return copy;
    }

    /**
     * Crea un modo personalizado nuevo partiendo del modo oscuro por defecto.
     */
    public EditorTheme createCustom(String newName) {
        return duplicate(DEFAULT_THEME_ID, newName);
    }

    /**
     * Inserta o actualiza un modo personalizado y lo persiste.
     *
     * @return el modo guardado (con el id normalizado/único calculado)
     */
    public EditorTheme saveCustom(EditorTheme theme) {
        if (theme == null) return null;

        String id = theme.getId();
        if (id == null || id.isBlank()) {
            id = newCustomId(theme.getName() != null ? theme.getName() : "Modo personalizado");
            theme.setId(id);
        }
        theme.setBuiltIn(false);

        final String finalId = id;
        customThemes.removeIf(t -> finalId.equals(t.getId()));
        customThemes.add(theme);
        persist();
        return theme;
    }

    /**
     * Elimina un modo personalizado y lo persiste.
     *
     * @return {@code true} si se eliminó; {@code false} si era incorporado o inexistente
     */
    public boolean deleteCustom(String id) {
        if (id == null || BuiltInThemes.isBuiltIn(id)) return false;
        boolean removed = customThemes.removeIf(t -> id.equals(t.getId()));
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Genera un id único y seguro a partir de un nombre.
     */
    public String newCustomId(String name) {
        String base = name != null
            ? name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "")
            : "";
        if (base.isEmpty()) {
            base = "modo-personalizado";
        }
        String candidate = base;
        int counter = 2;
        while (getTheme(candidate) != null) {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private void persist() {
        List<EditorTheme> safe = new ArrayList<>();
        for (EditorTheme theme : customThemes) {
            EditorTheme trimmed = theme.copy();
            trimmed.setBuiltIn(false);
            safe.add(trimmed);
        }
        repository.save(themesFile, safe);
    }
}