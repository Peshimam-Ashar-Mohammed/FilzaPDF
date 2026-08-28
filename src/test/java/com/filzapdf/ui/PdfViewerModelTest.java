package com.filzapdf.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PdfViewerModelTest {

    @Test
    void startsOnFirstPage() {
        PdfViewerModel viewer = new PdfViewerModel(5);

        assertEquals(0, viewer.getCurrentPage());
        assertEquals(5, viewer.getPageCount());
    }

    @Test
    void nextPageMovesForward() {
        PdfViewerModel viewer = new PdfViewerModel(5);

        assertTrue(viewer.nextPage());
        assertEquals(1, viewer.getCurrentPage());
    }

    @Test
    void previousPageMovesBackward() {
        PdfViewerModel viewer = new PdfViewerModel(5);

        viewer.nextPage();

        assertTrue(viewer.previousPage());
        assertEquals(0, viewer.getCurrentPage());
    }

    @Test
    void cannotMovePastLastPage() {
        PdfViewerModel viewer = new PdfViewerModel(2);

        viewer.nextPage();

        assertFalse(viewer.nextPage());
        assertEquals(1, viewer.getCurrentPage());
    }

    @Test
    void cannotMoveBeforeFirstPage() {
        PdfViewerModel viewer = new PdfViewerModel(2);

        assertFalse(viewer.previousPage());
        assertEquals(0, viewer.getCurrentPage());
    }

    @Test
    void rejectsInvalidPageCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfViewerModel(0)
        );
    }
}