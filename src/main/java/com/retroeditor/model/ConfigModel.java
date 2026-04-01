package com.retroeditor.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Properties;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modelo de configuración que maneja las propiedades de configuración
 */
public class ConfigModel {
    public static final String Z88DK_CLIB_NONE = "none";
    public static final String Z88DK_CLIB_DEFAULT = "default";
    public static final String Z88DK_CLIB_NOCLIB = "noclib";
    public static final String Z88DK_CLIB_ANSI = "ansi";
    public static final String Z88DK_CLIB_NEW = "new";
    public static final String Z88DK_CLIB_SDCC_IX = "sdcc_ix";
    public static final String Z88DK_CLIB_SDCC_IY = "sdcc_iy";
    public static final String Z88DK_CLIB_CLANG_IX = "clang_ix";
    public static final String Z88DK_CLIB_CLANG_IY = "clang_iy";

    private static final List<String> Z88DK_CLIB_OPTIONS = List.of(
        Z88DK_CLIB_NONE,
        Z88DK_CLIB_DEFAULT,
        Z88DK_CLIB_NOCLIB,
        Z88DK_CLIB_ANSI,
        Z88DK_CLIB_NEW,
        Z88DK_CLIB_SDCC_IX,
        Z88DK_CLIB_SDCC_IY,
        Z88DK_CLIB_CLANG_IX,
        Z88DK_CLIB_CLANG_IY
    );

    private static final String COMPILER_GBDK      = "GBDK";
    private static final String COMPILER_Z88DK     = "z88dk";
    private static final String LEGACY_SPECTRUM    = "Spectrum";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY   = "JSpeccy";

    private final Properties     configProps             = new Properties();
    private final StringProperty idioma                  = new SimpleStringProperty(this, "idioma", "es");
    private final StringProperty gbdkBin                 = new SimpleStringProperty(this, "gbdk_bin", "");
    private final StringProperty compilador              = new SimpleStringProperty(this, "compilador", "gcc");
    private final StringProperty spectrumBin             = new SimpleStringProperty(this, "spectrum_bin", "");
    private final StringProperty compiladorSeleccionado  = new SimpleStringProperty(this, "compilador_seleccionado", "GBDK");
    private final StringProperty emuladorSeleccionado    = new SimpleStringProperty(this, "emulador_seleccionado", "Emulicious");
    private final StringProperty z88dkClibOption         = new SimpleStringProperty(this, "z88dk_clib_option", Z88DK_CLIB_NEW);
    private final BooleanProperty z88dkUseCrtOrgCode     = new SimpleBooleanProperty(this, "z88dk_use_pragma_crt_org_code_32768", true);
    private final StringProperty z88dkTarget             = new SimpleStringProperty(this, "z88dk_target", "+zx");
    private final BooleanProperty z88dkCreateApp         = new SimpleBooleanProperty(this, "z88dk_create_app", true);
    private final StringProperty z88dkDefines            = new SimpleStringProperty(this, "z88dk_defines", "");
    private final StringProperty z88dkIncludes           = new SimpleStringProperty(this, "z88dk_includes", "");
    private final StringProperty z88dkExtraArgs          = new SimpleStringProperty(this, "z88dk_extra_args", "");

    // GBDK optimization options
    public static final String GBDK_OPT_NONE = "none";
    public static final String GBDK_OPT_O1   = "-O1";
    public static final String GBDK_OPT_O2   = "-O2";
    public static final String GBDK_OPT_O3   = "-O3";
    public static final List<String> GBDK_OPT_LEVELS = List.of(GBDK_OPT_NONE, GBDK_OPT_O1, GBDK_OPT_O2, GBDK_OPT_O3);

    private final StringProperty  gbdkOptLevel     = new SimpleStringProperty (this, "gbdk_opt_level",     GBDK_OPT_NONE);
    private final BooleanProperty gbdkOptSpeed     = new SimpleBooleanProperty(this, "gbdk_opt_speed",     false);
    private final BooleanProperty gbdkOptSize      = new SimpleBooleanProperty(this, "gbdk_opt_size",      false);
    private final StringProperty  gbdkDefines      = new SimpleStringProperty(this, "gbdk_defines", "");
    private final StringProperty  gbdkIncludes     = new SimpleStringProperty(this, "gbdk_includes", "");
    private final StringProperty  gbdkExtraArgs    = new SimpleStringProperty(this, "gbdk_extra_args", "");

    /**
     * Obtener una propiedad de configuración con valor por defecto
     * @param key Clave de la propiedad
     * @param defaultValue Valor por defecto si la propiedad no existe
     * @return Valor de la propiedad o el valor por defecto
     */
    public String getConfigProperty(String key, String defaultValue) {
        switch (key) {
            case "idioma"                   : return getIdioma()                 != null ? getIdioma()                 : defaultValue;
            case "gbdk_bin"                 : return getGbdkBin()                != null ? getGbdkBin()                : defaultValue;
            case "compilador"               : return getCompilador()             != null ? getCompilador()             : defaultValue;
            case "spectrum_bin"             : return getSpectrumBin()            != null ? getSpectrumBin()            : defaultValue;
            case "compilador_seleccionado"  : return getCompiladorSeleccionado() != null ? getCompiladorSeleccionado() : defaultValue;
            case "emulador_seleccionado"    : return getEmuladorSeleccionado()   != null ? getEmuladorSeleccionado()   : defaultValue;
            case "z88dk_clib_option"        : return getZ88dkClibOption()        != null ? normalizeZ88dkClibOption(getZ88dkClibOption()) : defaultValue;
            case "z88dk_use_pragma_crt_org_code_32768": return Boolean.toString(isZ88dkUseCrtOrgCode());
            case "z88dk_target"             : return getZ88dkTarget()             != null ? getZ88dkTarget() : defaultValue;
            case "z88dk_create_app"         : return Boolean.toString(isZ88dkCreateApp());
            case "z88dk_defines"            : return getZ88dkDefines()            != null ? getZ88dkDefines() : defaultValue;
            case "z88dk_includes"           : return getZ88dkIncludes()           != null ? getZ88dkIncludes() : defaultValue;
            case "z88dk_extra_args"         : return getZ88dkExtraArgs()          != null ? getZ88dkExtraArgs() : defaultValue;
            case "gbdk_opt_level"  : return getGbdkOptLevel()  != null ? getGbdkOptLevel() : defaultValue;
            case "gbdk_opt_speed"  : return Boolean.toString(isGbdkOptSpeed());
            case "gbdk_opt_size"   : return Boolean.toString(isGbdkOptSize());
            case "gbdk_defines"    : return getGbdkDefines()   != null ? getGbdkDefines() : defaultValue;
            case "gbdk_includes"   : return getGbdkIncludes()  != null ? getGbdkIncludes() : defaultValue;
            case "gbdk_extra_args" : return getGbdkExtraArgs() != null ? getGbdkExtraArgs() : defaultValue;
            default                         : return configProps.getProperty(key, defaultValue);
        }
    }

    /**
     * Establecer una propiedad de configuración
     * @param key Clave de la propiedad
     * @param value Valor a establecer
     */
    public void setConfigProperty(String key, String value) {
        switch (key) {
            case "idioma":                  setIdioma(value);                    break;
            case "gbdk_bin":                setGbdkBin(value);                   break;
            case "compilador":              setCompilador(value);                break;
            case "spectrum_bin":            setSpectrumBin(value);               break;
            case "compilador_seleccionado": setCompiladorSeleccionado(value);    break;
            case "emulador_seleccionado":   setEmuladorSeleccionado(value);      break;
            case "z88dk_clib_option":       setZ88dkClibOption(normalizeZ88dkClibOption(value)); break;
            case "z88dk_use_pragma_crt_org_code_32768": setZ88dkUseCrtOrgCode(Boolean.parseBoolean(value)); break;
            case "z88dk_target":            setZ88dkTarget(value);               break;
            case "z88dk_create_app":        setZ88dkCreateApp(Boolean.parseBoolean(value)); break;
            case "z88dk_defines":           setZ88dkDefines(value);              break;
            case "z88dk_includes":          setZ88dkIncludes(value);             break;
            case "z88dk_extra_args":        setZ88dkExtraArgs(value);            break;
            case "gbdk_opt_level"  : setGbdkOptLevel(value);                                break;
            case "gbdk_opt_speed"  : setGbdkOptSpeed(Boolean.parseBoolean(value));          break;
            case "gbdk_opt_size"   : setGbdkOptSize(Boolean.parseBoolean(value));           break;
            case "gbdk_defines"    : setGbdkDefines(value);                                  break;
            case "gbdk_includes"   : setGbdkIncludes(value);                                 break;
            case "gbdk_extra_args" : setGbdkExtraArgs(value);                                break;
            default:                        configProps.setProperty(key, value); break;
        }
    }

    /**
     * Cargar configuración por defecto desde un archivo
     * @param configFile Archivo de configuración
     */
    public void getDefaultLanguage(File configFile){
        try (FileInputStream fis = new FileInputStream(configFile)) {
            configProps.load(fis);
            // Transfer to properties
            String compilerSelection = normalizeCompilerSelection(
                configProps.getProperty("compilador_seleccionado", compiladorSeleccionado.get())
            );
            idioma                  .set(configProps.getProperty("idioma", idioma.get()));
            compilador              .set(configProps.getProperty("compilador", compilador.get()));
            compiladorSeleccionado  .set(compilerSelection);
            emuladorSeleccionado    .set(deriveEmulatorForCompiler(compilerSelection));
            gbdkBin                 .set(configProps.getProperty("gbdk_bin", gbdkBin.get()));
            spectrumBin             .set(configProps.getProperty("spectrum_bin", spectrumBin.get()));
            z88dkClibOption         .set(normalizeZ88dkClibOption(configProps.getProperty("z88dk_clib_option", z88dkClibOption.get())));
            z88dkUseCrtOrgCode      .set(Boolean.parseBoolean(configProps.getProperty("z88dk_use_pragma_crt_org_code_32768", Boolean.toString(z88dkUseCrtOrgCode.get()))));
            z88dkTarget             .set(configProps.getProperty("z88dk_target", "+zx"));
            z88dkCreateApp          .set(Boolean.parseBoolean(configProps.getProperty("z88dk_create_app", "true")));
            z88dkDefines            .set(configProps.getProperty("z88dk_defines", ""));
            z88dkIncludes           .set(configProps.getProperty("z88dk_includes", ""));
            z88dkExtraArgs          .set(configProps.getProperty("z88dk_extra_args", ""));
            gbdkOptLevel            .set(configProps.getProperty("gbdk_opt_level", GBDK_OPT_NONE));
            gbdkOptSpeed            .set(Boolean.parseBoolean(configProps.getProperty("gbdk_opt_speed", "false")));
            gbdkOptSize             .set(Boolean.parseBoolean(configProps.getProperty("gbdk_opt_size",  "false")));
            gbdkDefines             .set(configProps.getProperty("gbdk_defines", ""));
            gbdkIncludes            .set(configProps.getProperty("gbdk_includes", ""));
            gbdkExtraArgs           .set(configProps.getProperty("gbdk_extra_args", ""));
        } catch (Exception e) {
            // Si no existe, usar valores por defecto
            idioma                  .set("es");
            compilador              .set("gcc");
            z88dkClibOption         .set(Z88DK_CLIB_NEW);
            z88dkUseCrtOrgCode      .set(true);
        }
    }

    /**
     * Cambiar el idioma de la aplicación
     * @param selected Idioma seleccionado (por ejemplo, "English" o "Spanish")
     */
    public void changeLanguage(String selected ) {
        String lang = selected != null && selected.equals("English") ? "en" : "es";
        setIdioma(lang);
    }

    /**
     * Gestionar el método de guardado del compilador
     * @param configFile Archivo de configuración
     * @param txtCompilador Texto del compilador a guardar
     */
    public void onSaveCompilador(File configFile,String txtCompilador) {
        setCompilador(txtCompilador);
        saveConfig(configFile);
    }

    /**
     * Gestionar el método de guardado de la configuración
     * @param configFile Archivo de configuración
     */
    public void saveConfig(File configFile) {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            Properties p = new Properties();
            String compilerSelection = normalizeCompilerSelection(getCompiladorSeleccionado());
            p.setProperty("idioma",                  getIdioma()                 != null ? getIdioma() : "es");
            p.setProperty("gbdk_bin",                getGbdkBin()                != null ? getGbdkBin() : "");
            p.setProperty("compilador",              getCompilador()             != null ? getCompilador() : "gcc");
            p.setProperty("spectrum_bin",            getSpectrumBin()            != null ? getSpectrumBin() : "");
            p.setProperty("compilador_seleccionado", compilerSelection);
            p.setProperty("emulador_seleccionado",   deriveEmulatorForCompiler(compilerSelection));
            p.setProperty("z88dk_clib_option",       normalizeZ88dkClibOption(getZ88dkClibOption()));
            p.setProperty("z88dk_use_pragma_crt_org_code_32768", Boolean.toString(isZ88dkUseCrtOrgCode()));
            p.setProperty("z88dk_target",             getZ88dkTarget() != null ? getZ88dkTarget() : "+zx");
            p.setProperty("z88dk_create_app",         Boolean.toString(isZ88dkCreateApp()));
            p.setProperty("z88dk_defines",            getZ88dkDefines() != null ? getZ88dkDefines() : "");
            p.setProperty("z88dk_includes",           getZ88dkIncludes() != null ? getZ88dkIncludes() : "");
            p.setProperty("z88dk_extra_args",         getZ88dkExtraArgs() != null ? getZ88dkExtraArgs() : "");
            p.setProperty("gbdk_opt_level",          getGbdkOptLevel() != null ? getGbdkOptLevel() : GBDK_OPT_NONE);
            p.setProperty("gbdk_opt_speed",          Boolean.toString(isGbdkOptSpeed()));
            p.setProperty("gbdk_opt_size",           Boolean.toString(isGbdkOptSize()));
            p.setProperty("gbdk_defines",            getGbdkDefines() != null ? getGbdkDefines() : "");
            p.setProperty("gbdk_includes",           getGbdkIncludes() != null ? getGbdkIncludes() : "");
            p.setProperty("gbdk_extra_args",         getGbdkExtraArgs() != null ? getGbdkExtraArgs() : "");

            p.store(fos, "Configuración de Samaruc");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----- JavaFX property accessors -----
    public StringProperty idiomaProperty()                    { return idioma; }
    public String         getIdioma()                         { return idioma.get(); }
    public void           setIdioma(String v)                 { idioma.set(v); }

    public StringProperty compiladorProperty()                { return compilador; }
    public String         getCompilador()                     { return compilador.get(); }
    public void           setCompilador(String v)             { compilador.set(v); }

    public StringProperty compiladorSeleccionadoProperty()    { return compiladorSeleccionado; }
    public String         getCompiladorSeleccionado()         { return compiladorSeleccionado.get(); }
    public void           setCompiladorSeleccionado(String v) { compiladorSeleccionado.set(v); }

    public StringProperty gbdkBinProperty()                   { return gbdkBin; }
    public String         getGbdkBin()                        { return gbdkBin.get(); }
    public void           setGbdkBin(String v)                { gbdkBin.set(v); }


    public StringProperty spectrumBinProperty()               { return spectrumBin; }
    public String         getSpectrumBin()                    { return spectrumBin.get(); }
    public void           setSpectrumBin(String v)             { spectrumBin.set(v); }

    public StringProperty emuladorSeleccionadoProperty()      { return emuladorSeleccionado; }
    public String         getEmuladorSeleccionado()           { return emuladorSeleccionado.get(); }
    public void           setEmuladorSeleccionado(String v)   { emuladorSeleccionado.set(v); }

    public BooleanProperty z88dkUseCrtOrgCodeProperty()       { return z88dkUseCrtOrgCode; }
    public boolean         isZ88dkUseCrtOrgCode()             { return z88dkUseCrtOrgCode.get(); }
    public void            setZ88dkUseCrtOrgCode(boolean v)   { z88dkUseCrtOrgCode.set(v); }

    public StringProperty  z88dkTargetProperty()              { return z88dkTarget; }
    public String          getZ88dkTarget()                   { return z88dkTarget.get(); }
    public void            setZ88dkTarget(String v)           { z88dkTarget.set(v != null && !v.isBlank() ? v.trim() : "+zx"); }

    public BooleanProperty z88dkCreateAppProperty()           { return z88dkCreateApp; }
    public boolean         isZ88dkCreateApp()                 { return z88dkCreateApp.get(); }
    public void            setZ88dkCreateApp(boolean v)       { z88dkCreateApp.set(v); }

    public StringProperty  z88dkDefinesProperty()             { return z88dkDefines; }
    public String          getZ88dkDefines()                  { return z88dkDefines.get(); }
    public void            setZ88dkDefines(String v)          { z88dkDefines.set(v != null ? v.trim() : ""); }

    public StringProperty  z88dkIncludesProperty()            { return z88dkIncludes; }
    public String          getZ88dkIncludes()                 { return z88dkIncludes.get(); }
    public void            setZ88dkIncludes(String v)         { z88dkIncludes.set(v != null ? v.trim() : ""); }

    public StringProperty  z88dkExtraArgsProperty()           { return z88dkExtraArgs; }
    public String          getZ88dkExtraArgs()                { return z88dkExtraArgs.get(); }
    public void            setZ88dkExtraArgs(String v)        { z88dkExtraArgs.set(v != null ? v.trim() : ""); }

    public StringProperty z88dkClibOptionProperty()          { return z88dkClibOption; }
    public String         getZ88dkClibOption()               { return normalizeZ88dkClibOption(z88dkClibOption.get()); }
    public void           setZ88dkClibOption(String v)       { z88dkClibOption.set(normalizeZ88dkClibOption(v)); }

    public static List<String> getSupportedZ88dkClibOptions() {
        return Z88DK_CLIB_OPTIONS;
    }

    // GBDK optimization accessors
    public StringProperty  gbdkOptLevelProperty()             { return gbdkOptLevel; }
    public String          getGbdkOptLevel()                  { return gbdkOptLevel.get(); }
    public void            setGbdkOptLevel(String v)          { gbdkOptLevel.set(v != null ? v : GBDK_OPT_NONE); }

    public BooleanProperty gbdkOptSpeedProperty()             { return gbdkOptSpeed; }
    public boolean         isGbdkOptSpeed()                   { return gbdkOptSpeed.get(); }
    public void            setGbdkOptSpeed(boolean v)         { gbdkOptSpeed.set(v); }

    public BooleanProperty gbdkOptSizeProperty()              { return gbdkOptSize; }
    public boolean         isGbdkOptSize()                    { return gbdkOptSize.get(); }
    public void            setGbdkOptSize(boolean v)          { gbdkOptSize.set(v); }

    public StringProperty  gbdkDefinesProperty()              { return gbdkDefines; }
    public String          getGbdkDefines()                   { return gbdkDefines.get(); }
    public void            setGbdkDefines(String v)           { gbdkDefines.set(v != null ? v.trim() : ""); }

    public StringProperty  gbdkIncludesProperty()             { return gbdkIncludes; }
    public String          getGbdkIncludes()                  { return gbdkIncludes.get(); }
    public void            setGbdkIncludes(String v)          { gbdkIncludes.set(v != null ? v.trim() : ""); }

    public StringProperty  gbdkExtraArgsProperty()            { return gbdkExtraArgs; }
    public String          getGbdkExtraArgs()                 { return gbdkExtraArgs.get(); }
    public void            setGbdkExtraArgs(String v)         { gbdkExtraArgs.set(v != null ? v.trim() : ""); }

    private String normalizeZ88dkClibOption(String option) {
        if (option == null || option.isBlank()) return Z88DK_CLIB_NEW;

        String normalized = option.trim().toLowerCase();

        if ("classic".equals(normalized)) {
            return Z88DK_CLIB_DEFAULT;
        }

        if (Z88DK_CLIB_OPTIONS.contains(normalized)) {
            return normalized;
        }

        return Z88DK_CLIB_NEW;
    }

    private String normalizeCompilerSelection(String compiler) {
        return compiler != null && (COMPILER_Z88DK.equalsIgnoreCase(compiler) || LEGACY_SPECTRUM.equalsIgnoreCase(compiler))
            ? COMPILER_Z88DK
            : COMPILER_GBDK;
    }

    private String deriveEmulatorForCompiler(String compiler) {
        return COMPILER_Z88DK.equalsIgnoreCase(normalizeCompilerSelection(compiler)) ? EMULATOR_JSPECCY : EMULATOR_EMULICIOUS;
    }

}

