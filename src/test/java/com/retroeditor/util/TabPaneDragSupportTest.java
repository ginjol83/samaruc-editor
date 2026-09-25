package com.retroeditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TabPaneDragSupportTest {

    @Test
    void insertionIndexBeforeFirstHeader() {
        assertEquals(0, TabPaneDragSupport.insertionIndex(List.of(100.0, 200.0, 300.0), 50.0));
    }

    @Test
    void insertionIndexBetweenHeaders() {
        assertEquals(1, TabPaneDragSupport.insertionIndex(List.of(100.0, 200.0, 300.0), 150.0));
        assertEquals(2, TabPaneDragSupport.insertionIndex(List.of(100.0, 200.0, 300.0), 250.0));
    }

    @Test
    void insertionIndexAfterLastHeader() {
        assertEquals(3, TabPaneDragSupport.insertionIndex(List.of(100.0, 200.0, 300.0), 1000.0));
    }

    @Test
    void insertionIndexExactlyOnCenterStaysBefore() {
        assertEquals(0, TabPaneDragSupport.insertionIndex(List.of(100.0, 200.0, 300.0), 100.0));
    }

    @Test
    void insertionIndexOnEmptyPaneIsZero() {
        assertEquals(0, TabPaneDragSupport.insertionIndex(List.of(), 42.0));
    }
}