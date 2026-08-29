package com.filzapdf.ui;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PdfViewerControllerTest {

    @Test
    void opensDocument() {

        PdfViewerController controller =
                new PdfViewerController();

        PDDocument document =
                new PDDocument();

        document.addPage(new PDPage());

        controller.openDocument(document);

        assertTrue(
                controller.hasDocument()
        );

        assertNotNull(
                controller.getModel()
        );

        assertEquals(
                0,
                controller.getModel().getCurrentPage()
        );

        assertEquals(
                1,
                controller.getModel().getPageCount()
        );

        controller.closeDocument();
    }

    @Test
    void navigatesPages() {

        PdfViewerController controller =
                new PdfViewerController();

        PDDocument document =
                new PDDocument();

        document.addPage(new PDPage());
        document.addPage(new PDPage());

        controller.openDocument(document);

        assertTrue(
                controller.nextPage()
        );

        assertEquals(
                1,
                controller.getModel().getCurrentPage()
        );

        assertFalse(
                controller.nextPage()
        );

        controller.closeDocument();
    }

    @Test
    void closesDocument() {

        PdfViewerController controller =
                new PdfViewerController();

        PDDocument document =
                new PDDocument();

        document.addPage(new PDPage());

        controller.openDocument(document);

        controller.closeDocument();

        assertFalse(
                controller.hasDocument()
        );

        assertNull(
                controller.getModel()
        );
    }
}