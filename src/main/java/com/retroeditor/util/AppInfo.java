package com.retroeditor.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Proporciona información sobre la aplicación, como su versión.
 */
public class AppInfo {
    private static String version = "dev";

    static {
        try (InputStream in = AppInfo.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                version = props.getProperty("version", "dev");
                // Si el filtrado no ha funcionado (por ejemplo, ejecución directa desde el IDE sin procesar recursos)
                if ("${project.version}".equals(version)) {
                    version = "dev";
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Devuelve la versión actual de la aplicación.
     * @return La versión del pom.xml si está disponible, de lo contrario "dev".
     */
    public static String getVersion() {
        return version;
    }
}
