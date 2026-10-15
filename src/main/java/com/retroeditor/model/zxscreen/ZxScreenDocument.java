package com.retroeditor.model.zxscreen;

import java.util.ArrayList;
import java.util.List;

import com.retroeditor.service.zxspectrum.ZxScreenAttribute;
import com.retroeditor.service.zxspectrum.ZxScreenEditorModel;

/**
 * Fichero de proyecto del editor de pantalla del ZX Spectrum (.zxscreen).
 * Guarda la matriz de atributos y la paleta con la que se estaba pintando, que es lo
 * unico que el editor modifica: los pixeles en si los pone el conversor PNG a bitmap.
 */
public class ZxScreenDocument {
    public static final String FORMAT = "samaruc.zxscreen";
    public static final int CURRENT_VERSION = 1;
    public static final String FILE_EXTENSION = "zxscreen";

    private String format = FORMAT;
    private int version = CURRENT_VERSION;
    private int attrCols = ZxScreenEditorModel.ATTR_COLS;
    private int attrRows = ZxScreenEditorModel.ATTR_ROWS;
    private int selectedInk = ZxScreenAttribute.WHITE;
    private int selectedPaper = ZxScreenAttribute.BLACK;
    private boolean selectedBright;
    private boolean selectedFlash;
    private List<Integer> attributes = new ArrayList<>();

    public ZxScreenDocument() {
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getAttrCols() {
        return attrCols;
    }

    public void setAttrCols(int attrCols) {
        this.attrCols = attrCols;
    }

    public int getAttrRows() {
        return attrRows;
    }

    public void setAttrRows(int attrRows) {
        this.attrRows = attrRows;
    }

    public int getSelectedInk() {
        return selectedInk;
    }

    public void setSelectedInk(int selectedInk) {
        this.selectedInk = selectedInk;
    }

    public int getSelectedPaper() {
        return selectedPaper;
    }

    public void setSelectedPaper(int selectedPaper) {
        this.selectedPaper = selectedPaper;
    }

    public boolean isSelectedBright() {
        return selectedBright;
    }

    public void setSelectedBright(boolean selectedBright) {
        this.selectedBright = selectedBright;
    }

    public boolean isSelectedFlash() {
        return selectedFlash;
    }

    public void setSelectedFlash(boolean selectedFlash) {
        this.selectedFlash = selectedFlash;
    }

    public List<Integer> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Integer> attributes) {
        this.attributes = attributes != null ? new ArrayList<>(attributes) : new ArrayList<>();
    }
}