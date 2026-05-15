package com.retroeditor.model;

import java.util.List;
import java.util.Properties;

import com.retroeditor.service.PluginMarketplaceService;

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
    private static final String COMPILER_MAKEFILE  = "Makefile";
    private static final String LEGACY_SPECTRUM    = "Spectrum";
    private static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    private static final String Z88DK_PROFILE_CPC = "cpc";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY   = "JSpeccy";
    private static final String EMULATOR_CPCBOX_WEB = "CPCBoxWeb";

    private final Properties     configProps             = new Properties();
    private final StringProperty idioma                  = new SimpleStringProperty(this, "idioma", "es");
    private final StringProperty projectReadmeLanguage   = new SimpleStringProperty(this, "project_readme_language", "en");
    private final StringProperty gbdkBin                 = new SimpleStringProperty(this, "gbdk_bin", "");
    private final StringProperty compilador              = new SimpleStringProperty(this, "compilador", "gcc");
    private final StringProperty spectrumBin             = new SimpleStringProperty(this, "spectrum_bin", "");
    private final StringProperty cpcBin                  = new SimpleStringProperty(this, "cpc_bin", "");
    private final StringProperty compiladorSeleccionado  = new SimpleStringProperty(this, "compilador_seleccionado", "GBDK");
    private final StringProperty emuladorSeleccionado    = new SimpleStringProperty(this, "emulador_seleccionado", "Emulicious");
    private final StringProperty z88dkProfile            = new SimpleStringProperty(this, "z88dk_profile", Z88DK_PROFILE_SPECTRUM);
    private final StringProperty z88dkClibOption         = new SimpleStringProperty(this, "z88dk_clib_option", Z88DK_CLIB_NEW);
    private final BooleanProperty z88dkUseCrtOrgCode     = new SimpleBooleanProperty(this, "z88dk_use_pragma_crt_org_code_32768", true);
    private final StringProperty z88dkTarget             = new SimpleStringProperty(this, "z88dk_target", "+zx");
    private final BooleanProperty z88dkCreateApp         = new SimpleBooleanProperty(this, "z88dk_create_app", true);
    private final StringProperty z88dkDefines            = new SimpleStringProperty(this, "z88dk_defines", "");
    private final StringProperty z88dkIncludes           = new SimpleStringProperty(this, "z88dk_includes", "");
    private final StringProperty z88dkExtraArgs          = new SimpleStringProperty(this, "z88dk_extra_args", "");
    private final StringProperty z88dkCpcClibOption      = new SimpleStringProperty(this, "z88dk_cpc_clib_option", Z88DK_CLIB_NEW);
    private final BooleanProperty z88dkCpcUseCrtOrgCode  = new SimpleBooleanProperty(this, "z88dk_cpc_use_pragma_crt_org_code_32768", false);
    private final StringProperty z88dkCpcTarget          = new SimpleStringProperty(this, "z88dk_cpc_target", "+cpc");
    private final BooleanProperty z88dkCpcCreateApp      = new SimpleBooleanProperty(this, "z88dk_cpc_create_app", true);
    private final StringProperty z88dkCpcDefines         = new SimpleStringProperty(this, "z88dk_cpc_defines", "");
    private final StringProperty z88dkCpcIncludes        = new SimpleStringProperty(this, "z88dk_cpc_includes", "");
    private final StringProperty z88dkCpcExtraArgs       = new SimpleStringProperty(this, "z88dk_cpc_extra_args", "");
    private final StringProperty marketplaceCatalogUrl   = new SimpleStringProperty(this, "marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL);
    private final BooleanProperty autoApplyDetectedProjectProfile = new SimpleBooleanProperty(this, "project_detect_auto_apply", true);
    private final StringProperty autoApplyDetectedProjectProfileThreshold = new SimpleStringProperty(this, "project_detect_auto_apply_threshold", "75");

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
    private final StringProperty  makeExecutable   = new SimpleStringProperty(this, "make_executable", "");
    private final StringProperty  makeBuildTarget  = new SimpleStringProperty(this, "make_build_target", "");
    private final StringProperty  makeRunTarget    = new SimpleStringProperty(this, "make_run_target", "run");
    private final StringProperty  makeExtraArgs    = new SimpleStringProperty(this, "make_extra_args", "");

    /**
     * Obtener una propiedad de configuración con valor por defecto
     * @param key Clave de la propiedad
     * @param defaultValue Valor por defecto si la propiedad no existe
     * @return Valor de la propiedad o el valor por defecto
     */
    public String getConfigProperty(String key, String defaultValue) {
        switch (key) {
            case "idioma"                   : return getIdioma()                 != null ? getIdioma()                 : defaultValue;
            case "project_readme_language"  : return getProjectReadmeLanguage()  != null ? getProjectReadmeLanguage()  : defaultValue;
            case "gbdk_bin"                 : return getGbdkBin()                != null ? getGbdkBin()                : defaultValue;
            case "compilador"               : return getCompilador()             != null ? getCompilador()             : defaultValue;
            case "spectrum_bin"             : return getSpectrumBin()            != null ? getSpectrumBin()            : defaultValue;
            case "cpc_bin"                  : return getCpcBin()                 != null ? getCpcBin()                 : defaultValue;
            case "compilador_seleccionado"  : return getCompiladorSeleccionado() != null ? getCompiladorSeleccionado() : defaultValue;
            case "emulador_seleccionado"    : return getEmuladorSeleccionado()   != null ? getEmuladorSeleccionado()   : defaultValue;
            case "z88dk_profile"            : return getZ88dkProfile()           != null ? getZ88dkProfile()           : defaultValue;
            case "z88dk_clib_option"        : return getZ88dkClibOption()        != null ? normalizeZ88dkClibOption(getZ88dkClibOption()) : defaultValue;
            case "z88dk_use_pragma_crt_org_code_32768": return Boolean.toString(isZ88dkUseCrtOrgCode());
            case "z88dk_target"             : return getZ88dkTarget()             != null ? getZ88dkTarget() : defaultValue;
            case "z88dk_create_app"         : return Boolean.toString(isZ88dkCreateApp());
            case "z88dk_defines"            : return getZ88dkDefines()            != null ? getZ88dkDefines() : defaultValue;
            case "z88dk_includes"           : return getZ88dkIncludes()           != null ? getZ88dkIncludes() : defaultValue;
            case "z88dk_extra_args"         : return getZ88dkExtraArgs()          != null ? getZ88dkExtraArgs() : defaultValue;
            case "z88dk_cpc_clib_option"    : return getZ88dkCpcClibOption()      != null ? normalizeZ88dkClibOption(getZ88dkCpcClibOption()) : defaultValue;
            case "z88dk_cpc_use_pragma_crt_org_code_32768": return Boolean.toString(isZ88dkCpcUseCrtOrgCode());
            case "z88dk_cpc_target"         : return getZ88dkCpcTarget()          != null ? getZ88dkCpcTarget() : defaultValue;
            case "z88dk_cpc_create_app"     : return Boolean.toString(isZ88dkCpcCreateApp());
            case "z88dk_cpc_defines"        : return getZ88dkCpcDefines()         != null ? getZ88dkCpcDefines() : defaultValue;
            case "z88dk_cpc_includes"       : return getZ88dkCpcIncludes()        != null ? getZ88dkCpcIncludes() : defaultValue;
            case "z88dk_cpc_extra_args"     : return getZ88dkCpcExtraArgs()       != null ? getZ88dkCpcExtraArgs() : defaultValue;
            case "marketplace_catalog_url"  : return getMarketplaceCatalogUrl()    != null ? getMarketplaceCatalogUrl() : defaultValue;
            case "project_detect_auto_apply": return Boolean.toString(isAutoApplyDetectedProjectProfile());
            case "project_detect_auto_apply_threshold": return getAutoApplyDetectedProjectProfileThreshold();
            case "gbdk_opt_level"  : return getGbdkOptLevel()  != null ? getGbdkOptLevel() : defaultValue;
            case "gbdk_opt_speed"  : return Boolean.toString(isGbdkOptSpeed());
            case "gbdk_opt_size"   : return Boolean.toString(isGbdkOptSize());
            case "gbdk_defines"    : return getGbdkDefines()   != null ? getGbdkDefines() : defaultValue;
            case "gbdk_includes"   : return getGbdkIncludes()  != null ? getGbdkIncludes() : defaultValue;
            case "gbdk_extra_args" : return getGbdkExtraArgs() != null ? getGbdkExtraArgs() : defaultValue;
            case "make_executable" : return getMakeExecutable() != null ? getMakeExecutable() : defaultValue;
            case "make_build_target": return getMakeBuildTarget() != null ? getMakeBuildTarget() : defaultValue;
            case "make_run_target"  : return getMakeRunTarget() != null ? getMakeRunTarget() : defaultValue;
            case "make_extra_args"  : return getMakeExtraArgs() != null ? getMakeExtraArgs() : defaultValue;
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
            case "project_readme_language": setProjectReadmeLanguage(value);     break;
            case "gbdk_bin":                setGbdkBin(value);                   break;
            case "compilador":              setCompilador(value);                break;
            case "spectrum_bin":            setSpectrumBin(value);               break;
            case "cpc_bin":                 setCpcBin(value);                    break;
            case "compilador_seleccionado": setCompiladorSeleccionado(value);    break;
            case "emulador_seleccionado":   setEmuladorSeleccionado(value);      break;
            case "z88dk_profile":           setZ88dkProfile(value);              break;
            case "z88dk_clib_option":       setZ88dkClibOption(normalizeZ88dkClibOption(value)); break;
            case "z88dk_use_pragma_crt_org_code_32768": setZ88dkUseCrtOrgCode(Boolean.parseBoolean(value)); break;
            case "z88dk_target":            setZ88dkTarget(value);               break;
            case "z88dk_create_app":        setZ88dkCreateApp(Boolean.parseBoolean(value)); break;
            case "z88dk_defines":           setZ88dkDefines(value);              break;
            case "z88dk_includes":          setZ88dkIncludes(value);             break;
            case "z88dk_extra_args":        setZ88dkExtraArgs(value);            break;
            case "z88dk_cpc_clib_option":   setZ88dkCpcClibOption(normalizeZ88dkClibOption(value)); break;
            case "z88dk_cpc_use_pragma_crt_org_code_32768": setZ88dkCpcUseCrtOrgCode(Boolean.parseBoolean(value)); break;
            case "z88dk_cpc_target":        setZ88dkCpcTarget(value);            break;
            case "z88dk_cpc_create_app":    setZ88dkCpcCreateApp(Boolean.parseBoolean(value)); break;
            case "z88dk_cpc_defines":       setZ88dkCpcDefines(value);           break;
            case "z88dk_cpc_includes":      setZ88dkCpcIncludes(value);          break;
            case "z88dk_cpc_extra_args":    setZ88dkCpcExtraArgs(value);         break;
            case "marketplace_catalog_url": setMarketplaceCatalogUrl(value);      break;
            case "project_detect_auto_apply": setAutoApplyDetectedProjectProfile(Boolean.parseBoolean(value)); break;
            case "project_detect_auto_apply_threshold": setAutoApplyDetectedProjectProfileThreshold(value); break;
            case "gbdk_opt_level"  : setGbdkOptLevel(value);                                break;
            case "gbdk_opt_speed"  : setGbdkOptSpeed(Boolean.parseBoolean(value));          break;
            case "gbdk_opt_size"   : setGbdkOptSize(Boolean.parseBoolean(value));           break;
            case "gbdk_defines"    : setGbdkDefines(value);                                  break;
            case "gbdk_includes"   : setGbdkIncludes(value);                                 break;
            case "gbdk_extra_args" : setGbdkExtraArgs(value);                                break;
            case "make_executable" : setMakeExecutable(value);                               break;
            case "make_build_target": setMakeBuildTarget(value);                             break;
            case "make_run_target"  : setMakeRunTarget(value);                               break;
            case "make_extra_args"  : setMakeExtraArgs(value);                               break;
            default:                        configProps.setProperty(key, value); break;
        }
    }

    public void applyProperties(Properties props) {
        Properties source = props != null ? props : new Properties();
        configProps.clear();
        configProps.putAll(source);

        String compilerSelection = normalizeCompilerSelection(
            source.getProperty("compilador_seleccionado", compiladorSeleccionado.get())
        );
        String profile = normalizeZ88dkProfile(source.getProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        idioma                 .set(source.getProperty("idioma", "es"));
        projectReadmeLanguage  .set(normalizeReadmeLanguage(source.getProperty("project_readme_language", "en")));
        compilador             .set(source.getProperty("compilador", "gcc"));
        compiladorSeleccionado .set(compilerSelection);
        emuladorSeleccionado   .set(deriveEmulatorForCompilerAndProfile(compilerSelection, profile));
        gbdkBin                .set(source.getProperty("gbdk_bin", ""));
        spectrumBin            .set(source.getProperty("spectrum_bin", ""));
        cpcBin                 .set(source.getProperty("cpc_bin", source.getProperty("spectrum_bin", "")));
        z88dkProfile           .set(profile);
        z88dkClibOption        .set(normalizeZ88dkClibOption(source.getProperty("z88dk_clib_option", Z88DK_CLIB_NEW)));
        z88dkUseCrtOrgCode     .set(Boolean.parseBoolean(source.getProperty("z88dk_use_pragma_crt_org_code_32768", "true")));
        z88dkTarget            .set(source.getProperty("z88dk_target", "+zx"));
        z88dkCreateApp         .set(Boolean.parseBoolean(source.getProperty("z88dk_create_app", "true")));
        z88dkDefines           .set(source.getProperty("z88dk_defines", ""));
        z88dkIncludes          .set(source.getProperty("z88dk_includes", ""));
        z88dkExtraArgs         .set(source.getProperty("z88dk_extra_args", ""));
        z88dkCpcClibOption     .set(normalizeZ88dkClibOption(source.getProperty("z88dk_cpc_clib_option", source.getProperty("z88dk_clib_option", Z88DK_CLIB_NEW))));
        z88dkCpcUseCrtOrgCode  .set(Boolean.parseBoolean(source.getProperty("z88dk_cpc_use_pragma_crt_org_code_32768", "false")));
        z88dkCpcTarget         .set(source.getProperty("z88dk_cpc_target", "+cpc"));
        z88dkCpcCreateApp      .set(Boolean.parseBoolean(source.getProperty("z88dk_cpc_create_app", "true")));
        z88dkCpcDefines        .set(source.getProperty("z88dk_cpc_defines", ""));
        z88dkCpcIncludes       .set(source.getProperty("z88dk_cpc_includes", ""));
        z88dkCpcExtraArgs      .set(source.getProperty("z88dk_cpc_extra_args", ""));
        marketplaceCatalogUrl   .set(source.getProperty("marketplace_catalog_url", PluginMarketplaceService.DEFAULT_MARKETPLACE_URL));
        autoApplyDetectedProjectProfile.set(Boolean.parseBoolean(source.getProperty("project_detect_auto_apply", "true")));
        autoApplyDetectedProjectProfileThreshold.set(source.getProperty("project_detect_auto_apply_threshold", "75"));
        gbdkOptLevel           .set(source.getProperty("gbdk_opt_level", GBDK_OPT_NONE));
        gbdkOptSpeed           .set(Boolean.parseBoolean(source.getProperty("gbdk_opt_speed", "false")));
        gbdkOptSize            .set(Boolean.parseBoolean(source.getProperty("gbdk_opt_size",  "false")));
        gbdkDefines            .set(source.getProperty("gbdk_defines", ""));
        gbdkIncludes           .set(source.getProperty("gbdk_includes", ""));
        gbdkExtraArgs          .set(source.getProperty("gbdk_extra_args", ""));
        makeExecutable         .set(source.getProperty("make_executable", ""));
        makeBuildTarget        .set(source.getProperty("make_build_target", ""));
        makeRunTarget          .set(source.getProperty("make_run_target", "run"));
        makeExtraArgs          .set(source.getProperty("make_extra_args", ""));
    }

    public Properties toProperties() {
        Properties p = new Properties();
        String compilerSelection = normalizeCompilerSelection(getCompiladorSeleccionado());
        p.setProperty("idioma", getIdioma() != null ? getIdioma() : "es");
        p.setProperty("project_readme_language", getProjectReadmeLanguage() != null ? getProjectReadmeLanguage() : "en");
        p.setProperty("gbdk_bin", getGbdkBin() != null ? getGbdkBin() : "");
        p.setProperty("compilador", getCompilador() != null ? getCompilador() : "gcc");
        p.setProperty("spectrum_bin", getSpectrumBin() != null ? getSpectrumBin() : "");
        p.setProperty("cpc_bin", getCpcBin() != null ? getCpcBin() : "");
        p.setProperty("compilador_seleccionado", compilerSelection);
        p.setProperty("z88dk_profile", getZ88dkProfile());
        p.setProperty("emulador_seleccionado", deriveEmulatorForCompilerAndProfile(compilerSelection, getZ88dkProfile()));
        p.setProperty("z88dk_clib_option", normalizeZ88dkClibOption(getZ88dkClibOption()));
        p.setProperty("z88dk_use_pragma_crt_org_code_32768", Boolean.toString(isZ88dkUseCrtOrgCode()));
        p.setProperty("z88dk_target", getZ88dkTarget() != null ? getZ88dkTarget() : "+zx");
        p.setProperty("z88dk_create_app", Boolean.toString(isZ88dkCreateApp()));
        p.setProperty("z88dk_defines", getZ88dkDefines() != null ? getZ88dkDefines() : "");
        p.setProperty("z88dk_includes", getZ88dkIncludes() != null ? getZ88dkIncludes() : "");
        p.setProperty("z88dk_extra_args", getZ88dkExtraArgs() != null ? getZ88dkExtraArgs() : "");
        p.setProperty("z88dk_cpc_clib_option", normalizeZ88dkClibOption(getZ88dkCpcClibOption()));
        p.setProperty("z88dk_cpc_use_pragma_crt_org_code_32768", Boolean.toString(isZ88dkCpcUseCrtOrgCode()));
        p.setProperty("z88dk_cpc_target", getZ88dkCpcTarget() != null ? getZ88dkCpcTarget() : "+cpc");
        p.setProperty("z88dk_cpc_create_app", Boolean.toString(isZ88dkCpcCreateApp()));
        p.setProperty("z88dk_cpc_defines", getZ88dkCpcDefines() != null ? getZ88dkCpcDefines() : "");
        p.setProperty("z88dk_cpc_includes", getZ88dkCpcIncludes() != null ? getZ88dkCpcIncludes() : "");
        p.setProperty("z88dk_cpc_extra_args", getZ88dkCpcExtraArgs() != null ? getZ88dkCpcExtraArgs() : "");
        p.setProperty("marketplace_catalog_url", getMarketplaceCatalogUrl() != null ? getMarketplaceCatalogUrl() : PluginMarketplaceService.DEFAULT_MARKETPLACE_URL);
        p.setProperty("project_detect_auto_apply", Boolean.toString(isAutoApplyDetectedProjectProfile()));
        p.setProperty("project_detect_auto_apply_threshold", getAutoApplyDetectedProjectProfileThreshold());
        p.setProperty("gbdk_opt_level", getGbdkOptLevel() != null ? getGbdkOptLevel() : GBDK_OPT_NONE);
        p.setProperty("gbdk_opt_speed", Boolean.toString(isGbdkOptSpeed()));
        p.setProperty("gbdk_opt_size", Boolean.toString(isGbdkOptSize()));
        p.setProperty("gbdk_defines", getGbdkDefines() != null ? getGbdkDefines() : "");
        p.setProperty("gbdk_includes", getGbdkIncludes() != null ? getGbdkIncludes() : "");
        p.setProperty("gbdk_extra_args", getGbdkExtraArgs() != null ? getGbdkExtraArgs() : "");
        p.setProperty("make_executable", getMakeExecutable() != null ? getMakeExecutable() : "");
        p.setProperty("make_build_target", getMakeBuildTarget() != null ? getMakeBuildTarget() : "");
        p.setProperty("make_run_target", getMakeRunTarget() != null ? getMakeRunTarget() : "run");
        p.setProperty("make_extra_args", getMakeExtraArgs() != null ? getMakeExtraArgs() : "");
        return p;
    }

    /**
     * Cambiar el idioma de la aplicación
     * @param selected Idioma seleccionado (por ejemplo, "English" o "Spanish")
     */
    public void changeLanguage(String selected ) {
        String lang = selected != null && selected.equals("English") ? "en" : "es";
        setIdioma(lang);
    }


    // ----- JavaFX property accessors -----
    public StringProperty idiomaProperty()                    { return idioma; }
    public String         getIdioma()                         { return idioma.get(); }
    public void           setIdioma(String v)                 { idioma.set(v); }

    public StringProperty projectReadmeLanguageProperty()     { return projectReadmeLanguage; }
    public String         getProjectReadmeLanguage()          { return normalizeReadmeLanguage(projectReadmeLanguage.get()); }
    public void           setProjectReadmeLanguage(String v)  { projectReadmeLanguage.set(normalizeReadmeLanguage(v)); }

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

    public StringProperty cpcBinProperty()                    { return cpcBin; }
    public String         getCpcBin()                         { return cpcBin.get(); }
    public void           setCpcBin(String v)                 { cpcBin.set(v); }

    public StringProperty emuladorSeleccionadoProperty()      { return emuladorSeleccionado; }
    public String         getEmuladorSeleccionado()           { return emuladorSeleccionado.get(); }
    public void           setEmuladorSeleccionado(String v)   { emuladorSeleccionado.set(v); }

    public StringProperty z88dkProfileProperty()              { return z88dkProfile; }
    public String         getZ88dkProfile()                   { return normalizeZ88dkProfile(z88dkProfile.get()); }
    public void           setZ88dkProfile(String v)           { z88dkProfile.set(normalizeZ88dkProfile(v)); }

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

    public StringProperty z88dkCpcClibOptionProperty()       { return z88dkCpcClibOption; }
    public String         getZ88dkCpcClibOption()            { return normalizeZ88dkClibOption(z88dkCpcClibOption.get()); }
    public void           setZ88dkCpcClibOption(String v)    { z88dkCpcClibOption.set(normalizeZ88dkClibOption(v)); }

    public BooleanProperty z88dkCpcUseCrtOrgCodeProperty()   { return z88dkCpcUseCrtOrgCode; }
    public boolean         isZ88dkCpcUseCrtOrgCode()         { return z88dkCpcUseCrtOrgCode.get(); }
    public void            setZ88dkCpcUseCrtOrgCode(boolean v) { z88dkCpcUseCrtOrgCode.set(v); }

    public StringProperty  z88dkCpcTargetProperty()          { return z88dkCpcTarget; }
    public String          getZ88dkCpcTarget()               { return z88dkCpcTarget.get(); }
    public void            setZ88dkCpcTarget(String v)       { z88dkCpcTarget.set(v != null && !v.isBlank() ? v.trim() : "+cpc"); }

    public BooleanProperty z88dkCpcCreateAppProperty()       { return z88dkCpcCreateApp; }
    public boolean         isZ88dkCpcCreateApp()             { return z88dkCpcCreateApp.get(); }
    public void            setZ88dkCpcCreateApp(boolean v)   { z88dkCpcCreateApp.set(v); }

    public StringProperty  z88dkCpcDefinesProperty()         { return z88dkCpcDefines; }
    public String          getZ88dkCpcDefines()              { return z88dkCpcDefines.get(); }
    public void            setZ88dkCpcDefines(String v)      { z88dkCpcDefines.set(v != null ? v.trim() : ""); }

    public StringProperty  z88dkCpcIncludesProperty()        { return z88dkCpcIncludes; }
    public String          getZ88dkCpcIncludes()             { return z88dkCpcIncludes.get(); }
    public void            setZ88dkCpcIncludes(String v)     { z88dkCpcIncludes.set(v != null ? v.trim() : ""); }

    public StringProperty  z88dkCpcExtraArgsProperty()       { return z88dkCpcExtraArgs; }
    public String          getZ88dkCpcExtraArgs()            { return z88dkCpcExtraArgs.get(); }
    public void            setZ88dkCpcExtraArgs(String v)    { z88dkCpcExtraArgs.set(v != null ? v.trim() : ""); }

    public StringProperty marketplaceCatalogUrlProperty()    { return marketplaceCatalogUrl; }
    public String         getMarketplaceCatalogUrl()         { return marketplaceCatalogUrl.get(); }
    public void           setMarketplaceCatalogUrl(String v) {
        marketplaceCatalogUrl.set(v != null && !v.isBlank() ? v.trim() : PluginMarketplaceService.DEFAULT_MARKETPLACE_URL);
    }

    public BooleanProperty autoApplyDetectedProjectProfileProperty() { return autoApplyDetectedProjectProfile; }
    public boolean         isAutoApplyDetectedProjectProfile()       { return autoApplyDetectedProjectProfile.get(); }
    public void            setAutoApplyDetectedProjectProfile(boolean v) {
        autoApplyDetectedProjectProfile.set(v);
    }

    public StringProperty autoApplyDetectedProjectProfileThresholdProperty() { return autoApplyDetectedProjectProfileThreshold; }
    public String         getAutoApplyDetectedProjectProfileThreshold() {
        String raw = autoApplyDetectedProjectProfileThreshold.get();
        return raw != null && !raw.isBlank() ? raw.trim() : "75";
    }
    public void           setAutoApplyDetectedProjectProfileThreshold(String v) {
        autoApplyDetectedProjectProfileThreshold.set(v != null && !v.isBlank() ? v.trim() : "75");
    }

    public int getAutoApplyDetectedProjectProfileThresholdInt() {
        try {
            int parsed = Integer.parseInt(getAutoApplyDetectedProjectProfileThreshold());
            if (parsed < 0) return 0;
            if (parsed > 100) return 100;
            return parsed;
        } catch (Exception ignored) {
            return 75;
        }
    }

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

    public StringProperty  makeExecutableProperty()            { return makeExecutable; }
    public String          getMakeExecutable()                 { return makeExecutable.get(); }
    public void            setMakeExecutable(String v)         { makeExecutable.set(v != null ? v.trim() : ""); }

    public StringProperty  makeBuildTargetProperty()           { return makeBuildTarget; }
    public String          getMakeBuildTarget()                { return makeBuildTarget.get(); }
    public void            setMakeBuildTarget(String v)        { makeBuildTarget.set(v != null ? v.trim() : ""); }

    public StringProperty  makeRunTargetProperty()             { return makeRunTarget; }
    public String          getMakeRunTarget()                  { return makeRunTarget.get(); }
    public void            setMakeRunTarget(String v)          { makeRunTarget.set(v != null ? v.trim() : "run"); }

    public StringProperty  makeExtraArgsProperty()             { return makeExtraArgs; }
    public String          getMakeExtraArgs()                  { return makeExtraArgs.get(); }
    public void            setMakeExtraArgs(String v)          { makeExtraArgs.set(v != null ? v.trim() : ""); }

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
        if (compiler == null) return COMPILER_GBDK;

        String normalized = compiler.trim();

        if (COMPILER_MAKEFILE.equalsIgnoreCase(normalized)) {
            return COMPILER_MAKEFILE;
        }

        if (COMPILER_Z88DK.equalsIgnoreCase(normalized)
            || LEGACY_SPECTRUM.equalsIgnoreCase(normalized)
            || "Z88DK".equalsIgnoreCase(normalized)
            || normalized.toLowerCase().contains("z88dk")) {
            return COMPILER_Z88DK;
        }

        return COMPILER_GBDK;
    }

    private String normalizeZ88dkProfile(String profile) {
        return Z88DK_PROFILE_CPC.equalsIgnoreCase(profile) ? Z88DK_PROFILE_CPC : Z88DK_PROFILE_SPECTRUM;
    }

    private String normalizeReadmeLanguage(String lang) {
        if (lang == null) return "en";
        String normalized = lang.trim().toLowerCase();
        return "es".equals(normalized) ? "es" : "en";
    }

    private String deriveEmulatorForCompilerAndProfile(String compiler, String profile) {
        if (COMPILER_Z88DK.equalsIgnoreCase(normalizeCompilerSelection(compiler))) {
            return Z88DK_PROFILE_CPC.equalsIgnoreCase(normalizeZ88dkProfile(profile)) ? EMULATOR_CPCBOX_WEB : EMULATOR_JSPECCY;
        }
        return EMULATOR_EMULICIOUS;
    }

    public void applyGameBoyProfile() {
        setCompiladorSeleccionado(COMPILER_GBDK);
        setEmuladorSeleccionado(EMULATOR_EMULICIOUS);
    }

    public void applySpectrumProfile() {
        setCompiladorSeleccionado(COMPILER_Z88DK);
        setZ88dkProfile(Z88DK_PROFILE_SPECTRUM);
        setEmuladorSeleccionado(EMULATOR_JSPECCY);
        setZ88dkTarget(getZ88dkTarget());
    }

    public void applyCpcProfile() {
        setCompiladorSeleccionado(COMPILER_Z88DK);
        setZ88dkProfile(Z88DK_PROFILE_CPC);
        setEmuladorSeleccionado(EMULATOR_CPCBOX_WEB);
        setZ88dkCpcTarget(getZ88dkCpcTarget());
    }

}

