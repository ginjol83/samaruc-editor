

package com.retroeditor.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modelo de configuración que maneja las propiedades de configuración
 */
public class ConfigModel {
    private final Properties     configProps             = new Properties();
    private final StringProperty idioma                  = new SimpleStringProperty(this, "idioma", "es");
    private final StringProperty gbdkBin                 = new SimpleStringProperty(this, "gbdk_bin", "");
    private final StringProperty compilador              = new SimpleStringProperty(this, "compilador", "gcc");
    private final StringProperty spectrumBin             = new SimpleStringProperty(this, "spectrum_bin", "");
    private final StringProperty jspeccyBin              = new SimpleStringProperty(this, "jspeccy_bin", "");
    private final StringProperty compiladorSeleccionado  = new SimpleStringProperty(this, "compilador_seleccionado", "GBDK");

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
            case "jspeccy_bin"              : return getJspeccyBin()           != null ? getJspeccyBin()           : defaultValue;
            case "compilador_seleccionado"  : return getCompiladorSeleccionado() != null ? getCompiladorSeleccionado() : defaultValue;
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
                case "jspeccy_bin":             setJspeccyBin(value);               break;
            case "compilador_seleccionado": setCompiladorSeleccionado(value);    break;
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
            idioma                  .set(configProps.getProperty("idioma", idioma.get()));
            compilador              .set(configProps.getProperty("compilador", compilador.get()));
            compiladorSeleccionado  .set(configProps.getProperty("compilador_seleccionado", compiladorSeleccionado.get()));
            gbdkBin                 .set(configProps.getProperty("gbdk_bin", gbdkBin.get()));
            spectrumBin             .set(configProps.getProperty("spectrum_bin", spectrumBin.get()));
            jspeccyBin              .set(configProps.getProperty("jspeccy_bin", jspeccyBin.get()));
        } catch (Exception e) {
            // Si no existe, usar valores por defecto
            idioma                  .set("es");
            compilador              .set("gcc");
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
            p.setProperty("idioma",                  getIdioma()                 != null ? getIdioma() : "es");
            p.setProperty("gbdk_bin",                getGbdkBin()                != null ? getGbdkBin() : "");
            p.setProperty("compilador",              getCompilador()             != null ? getCompilador() : "gcc");
            p.setProperty("spectrum_bin",            getSpectrumBin()            != null ? getSpectrumBin() : "");
            p.setProperty("jspeccy_bin",             getJspeccyBin()             != null ? getJspeccyBin() : "");
            p.setProperty("compilador_seleccionado", getCompiladorSeleccionado() != null ? getCompiladorSeleccionado() : "GBDK");
            
            p.store(fos, "Configuración de Retro Editor");
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

    public StringProperty jspeccyBinProperty()                { return jspeccyBin; }
    public String         getJspeccyBin()                     { return jspeccyBin.get(); }
    public void           setJspeccyBin(String v)             { jspeccyBin.set(v); }

    public StringProperty spectrumBinProperty()               { return spectrumBin; }
    public String         getSpectrumBin()                    { return spectrumBin.get(); }
    public void           setSpectrumBin(String v)             { spectrumBin.set(v); }

}