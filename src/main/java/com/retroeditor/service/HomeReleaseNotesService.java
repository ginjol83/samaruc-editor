package com.retroeditor.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Carga las novedades de versión para la Home de la aplicación.
 *
 * Orden de origen:
 * 1) CHANGELOG.md en el directorio de trabajo.
 * 2) Mensaje de fallback proporcionado por el llamador.
 */
public class HomeReleaseNotesService {

    private static final String DEFAULT_VERSION = "dev";
    private static final String CHANGELOG_FILE_NAME = "CHANGELOG.md";
    private static final String POM_FILE_NAME = "pom.xml";

    public ReleaseNotes load(Path workingDirectory, String fallbackItem) {
        Path baseDir = workingDirectory != null ? workingDirectory : Path.of(".");
        String version = resolveVersion(baseDir.resolve(POM_FILE_NAME));

        ParsedChangelog parsed = parseChangelog(baseDir.resolve(CHANGELOG_FILE_NAME));
        List<String> items = new ArrayList<>(parsed.items());
        if (items.isEmpty()) {
            items.add(fallbackItem != null && !fallbackItem.isBlank()
                ? fallbackItem
                : "No release notes available.");
        }

        return new ReleaseNotes(version, List.copyOf(items), parsed.fromChangelog(), parsed.sectionTitle());
    }

    private String resolveVersion(Path pomPath) {
        if (pomPath == null || !Files.exists(pomPath)) {
            return DEFAULT_VERSION;
        }

        try (InputStream in = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }

            Element root = factory.newDocumentBuilder().parse(in).getDocumentElement();
            if (root == null) return DEFAULT_VERSION;

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                if (!"version".equals(node.getNodeName())) continue;

                String version = node.getTextContent();
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (Exception ignored) {
        }

        return DEFAULT_VERSION;
    }

    private ParsedChangelog parseChangelog(Path changelogPath) {
        if (changelogPath == null || !Files.exists(changelogPath) || !Files.isRegularFile(changelogPath)) {
            return new ParsedChangelog(false, "", List.of());
        }

        try {
            List<String> lines = Files.readAllLines(changelogPath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return new ParsedChangelog(false, "", List.of());

            int sectionStart = -1;
            String sectionTitle = "";
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i) != null ? lines.get(i).trim() : "";
                if (trimmed.startsWith("## ") && !trimmed.startsWith("## [")) {
                    // Si es una versión (## 1.1.0)
                    sectionStart = i;
                    sectionTitle = trimmed.substring(3).trim();
                    break;
                } else if (trimmed.startsWith("## [")) {
                    // Formato keepachangelog: ## [1.1.0] - 2023-10-05
                    sectionStart = i;
                    sectionTitle = trimmed.substring(4, trimmed.indexOf("]")).trim();
                    break;
                }
            }

            if (sectionStart < 0) {
                return new ParsedChangelog(false, "", List.of());
            }

            List<String> items = new ArrayList<>();
            String currentSubSection = "";
            for (int i = sectionStart + 1; i < lines.size(); i++) {
                String trimmed = lines.get(i) != null ? lines.get(i).trim() : "";
                
                // Si llegamos a la siguiente versión, paramos
                if (trimmed.startsWith("## ")) break;

                // Detectar sub-secciones (### Añadido, ### Mejorado)
                if (trimmed.startsWith("### ")) {
                    currentSubSection = trimmed.substring(4).trim();
                    continue;
                }

                String item = extractListItem(trimmed);
                if (item == null || item.isBlank()) continue;

                // Si hay sub-sección, la incluimos como prefijo para dar contexto
                String fullItem = currentSubSection.isEmpty() ? item : "[" + currentSubSection + "] " + item;
                items.add(fullItem);
                
                if (items.size() >= 12) break; // Aumentamos el límite para mostrar más novedades
            }

            return new ParsedChangelog(!items.isEmpty(), sectionTitle, items);
        } catch (Exception ignored) {
            return new ParsedChangelog(false, "", List.of());
        }
    }

    private String extractListItem(String line) {
        if (line == null || line.isBlank()) return null;

        if (line.startsWith("- ") || line.startsWith("* ")) {
            return line.substring(2).trim();
        }

        if (line.matches("^\\d+\\.\\s+.*$")) {
            return line.replaceFirst("^\\d+\\.\\s+", "").trim();
        }

        return null;
    }

    private record ParsedChangelog(boolean fromChangelog, String sectionTitle, List<String> items) {
    }

    public record ReleaseNotes(String version, List<String> items, boolean fromChangelog, String changelogSection) {
    }
}

