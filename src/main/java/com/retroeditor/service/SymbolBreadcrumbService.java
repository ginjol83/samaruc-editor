package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para analizar símbolos y generar la ruta de migas de pan (breadcrumb)
 * según la posición del cursor en el editor.
 *
 * La ruta no es un historial de lo declarado antes del cursor: cada elemento es un
 * ambito (region, funcion, etiqueta) que <b>contiene</b> a la linea del cursor, de
 * modo que la barra muestra `archivo › ambito externo › ambito interno`.
 *
 * El calculo se separa en dos pasos: {@link #analyze(String, String)} construye un
 * indice con rangos {@code [inicio, fin]} (se recomputa solo al cambiar el texto) y
 * {@link BreadcrumbIndex#pathAt(int)} resuelve la ruta del cursor (barato, se llama
 * al mover el cursor). La deteccion de simbolos vive en {@link SourceSymbolScanner},
 * compartida con la vista de estructura y el plegado.
 */
public class SymbolBreadcrumbService {

    public static final class BreadcrumbItem {
        private final String name;
        private final int line;
        private final String type; // "file", "function", "region", "label"

        public BreadcrumbItem(String name, int line, String type) {
            this.name = name;
            this.line = line;
            this.type = type;
        }

        public String getName() { return name; }
        public int getLine() { return line; }
        public String getType() { return type; }
    }

    /** Indice de ambitos del fichero, inmutable y valido para un texto concreto. */
    public static final class BreadcrumbIndex {
        private final String fileName;
        private final List<Scope> scopes;

        private BreadcrumbIndex(String fileName, List<Scope> scopes) {
            this.fileName = fileName;
            this.scopes = scopes;
        }

        /** Ruta de ambitos que contienen a la linea dada (1-indexada). */
        public List<BreadcrumbItem> pathAt(int caretLine) {
            List<BreadcrumbItem> items = new ArrayList<>();
            items.add(new BreadcrumbItem(fileName != null ? fileName : "editor", 1, "file"));
            for (Scope scope : scopes) {
                if (scope.start <= caretLine && caretLine <= scope.end) {
                    items.add(new BreadcrumbItem(scope.name, scope.start, scope.type));
                }
            }
            return items;
        }

        /**
         * Grupos de simbolos hermanos alineados con {@link #pathAt(int)}: el elemento
         * i contiene los simbolos del mismo nivel que el item i (para el archivo, sus
         * simbolos de primer nivel). Un grupo de menos de dos elementos no merece
         * desplegable.
         */
        public List<List<BreadcrumbItem>> siblingsAt(int caretLine) {
            List<Scope> containing = new ArrayList<>();
            for (Scope scope : scopes) {
                if (scope.start <= caretLine && caretLine <= scope.end) containing.add(scope);
            }
            List<List<BreadcrumbItem>> groups = new ArrayList<>();
            groups.add(topLevelItems());
            for (Scope scope : containing) {
                groups.add(siblingItems(scope));
            }
            return groups;
        }

        /** Padre: contenedor mas interno estricto (mismo nivel => nulo). */
        private Scope parentOf(Scope scope) {
            Scope parent = null;
            for (Scope candidate : scopes) {
                if (candidate == scope) continue;
                boolean strictlyContains = candidate.start <= scope.start && scope.end <= candidate.end
                        && !(candidate.start == scope.start && candidate.end == scope.end);
                if (!strictlyContains) continue;
                if (parent == null
                        || candidate.start > parent.start
                        || (candidate.start == parent.start && candidate.end < parent.end)) {
                    parent = candidate;
                }
            }
            return parent;
        }

        private List<BreadcrumbItem> topLevelItems() {
            List<BreadcrumbItem> items = new ArrayList<>();
            for (Scope scope : scopes) {
                if (parentOf(scope) == null) {
                    items.add(new BreadcrumbItem(scope.name, scope.start, scope.type));
                }
            }
            return items;
        }

        private List<BreadcrumbItem> siblingItems(Scope scope) {
            Scope parent = parentOf(scope);
            List<BreadcrumbItem> items = new ArrayList<>();
            for (Scope candidate : scopes) {
                if (parentOf(candidate) == parent) {
                    items.add(new BreadcrumbItem(candidate.name, candidate.start, candidate.type));
                }
            }
            return items;
        }
    }

    private static final class Scope {
        final String name;
        final int start; // 1-indexado, incluido
        final int end;   // 1-indexado, incluido
        final String type;

        Scope(String name, int start, int end, String type) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }

    private static final List<String> SCOPE_TYPES = List.of("function", "region", "label");

    public List<BreadcrumbItem> getBreadcrumbs(String text, String fileName, int caretLine) {
        return analyze(text, fileName).pathAt(caretLine);
    }

    /** Construye el indice de ambitos del texto. Se recomputa solo al cambiar el texto. */
    public BreadcrumbIndex analyze(String text, String fileName) {
        List<Scope> scopes = new ArrayList<>();
        for (SourceSymbolScanner.SourceSymbol symbol : SourceSymbolScanner.scan(text)) {
            if (SCOPE_TYPES.contains(symbol.getType())) {
                scopes.add(new Scope(symbol.getName(), symbol.getStartLine(),
                        symbol.getEndLine(), symbol.getType()));
            }
        }
        return new BreadcrumbIndex(fileName, scopes);
    }
}
