package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZxScreenEditorModelTest {
    @Test
    void defaultAttributesAreWhiteInkBlackPaper() {
        ZxScreenEditorModel model = new ZxScreenEditorModel();
        ZxScreenAttribute attr = model.getAttribute(0,0);
        assertEquals(ZxScreenAttribute.WHITE, attr.ink());
        assertEquals(ZxScreenAttribute.BLACK, attr.paper());
        assertFalse(attr.bright());
        assertFalse(attr.flash());
    }

    @Test
    void setAndGetAttribute() {
        ZxScreenEditorModel model = new ZxScreenEditorModel();
        ZxScreenAttribute newAttr = new ZxScreenAttribute(ZxScreenAttribute.RED, ZxScreenAttribute.BLUE, true, false);
        model.setAttribute(5, 10, newAttr);
        assertEquals(newAttr, model.getAttribute(5, 10));
    }

    @Test
    void attributeBytesRoundTrip() {
        ZxScreenEditorModel model = new ZxScreenEditorModel();
        model.setAttribute(0, 0, new ZxScreenAttribute(1,2,true,false));
        byte[] bytes = model.getAttributeBytes();
        ZxScreenEditorModel model2 = new ZxScreenEditorModel();
        model2.setAttributeBytes(bytes);
        assertEquals(model.getAttribute(0,0), model2.getAttribute(0,0));
    }
}
