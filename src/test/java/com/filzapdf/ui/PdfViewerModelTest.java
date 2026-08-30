package com.filzapdf.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PdfViewerModelTest {

    @Test
    void createsValidViewer() {

        PdfViewerModel model =
                new PdfViewerModel(10);

        assertEquals(0, model.getCurrentPage());
        assertEquals(10, model.getPageCount());
        assertEquals(1.0, model.getZoom());
        assertEquals(
                PdfViewerModel.ZoomMode.MANUAL,
                model.getZoomMode()
        );
    }

    @Test
    @SuppressWarnings("unused")
    void rejectsInvalidPageCount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfViewerModel(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfViewerModel(-1)
        );
    }

    @Test
    void nextPageWorks() {

        PdfViewerModel model =
                new PdfViewerModel(3);

        assertTrue(model.nextPage());
        assertEquals(1, model.getCurrentPage());

        assertTrue(model.nextPage());
        assertEquals(2, model.getCurrentPage());

        assertFalse(model.nextPage());
        assertEquals(2, model.getCurrentPage());
    }

    @Test
    void previousPageWorks() {

        PdfViewerModel model =
                new PdfViewerModel(3);

        model.nextPage();
        model.nextPage();

        assertTrue(model.previousPage());
        assertEquals(1, model.getCurrentPage());

        assertTrue(model.previousPage());
        assertEquals(0, model.getCurrentPage());

        assertFalse(model.previousPage());
        assertEquals(0, model.getCurrentPage());
    }

    @Test
    void zoomInWorks() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        model.zoomIn();

        assertEquals(1.25, model.getZoom());
    }

    @Test
    void zoomOutWorks() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        model.zoomOut();

        assertEquals(0.75, model.getZoom());
    }

    @Test
    void zoomHasLimits() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        for (int i = 0; i < 30; i++) {
            model.zoomOut();
        }

        assertEquals(0.25, model.getZoom());

        for (int i = 0; i < 50; i++) {
            model.zoomIn();
        }

        assertEquals(4.0, model.getZoom());
    }

    @Test
    void resetZoomWorks() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        model.zoomIn();
        model.zoomIn();

        model.resetZoom();

        assertEquals(1.0, model.getZoom());
        assertEquals(
                PdfViewerModel.ZoomMode.MANUAL,
                model.getZoomMode()
        );
    }

    @Test
    void fitModesWork() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        model.setFitWidth();

        assertEquals(
                PdfViewerModel.ZoomMode.FIT_WIDTH,
                model.getZoomMode()
        );

        model.setFitPage();

        assertEquals(
                PdfViewerModel.ZoomMode.FIT_PAGE,
                model.getZoomMode()
        );
    }

    @Test
    void setCurrentPageWorks() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        model.setCurrentPage(3);

        assertEquals(
                3,
                model.getCurrentPage()
        );
    }

    @Test
    @SuppressWarnings("unused")
    void rejectsInvalidCurrentPage() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> model.setCurrentPage(-1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> model.setCurrentPage(5)
        );
    }

    @Test
    @SuppressWarnings("unused")
    void rejectsInvalidZoom() {

        PdfViewerModel model =
                new PdfViewerModel(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> model.setZoom(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> model.setZoom(-1)
        );
    }
}