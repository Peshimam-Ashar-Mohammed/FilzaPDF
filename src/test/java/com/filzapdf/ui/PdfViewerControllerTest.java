package com.filzapdf.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.stage.Stage;

class PdfViewerControllerTest {

    @BeforeAll
    static void initializeFxToolkit() throws Exception {

        if (Platform.isFxApplicationThread()) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);

        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // JavaFX was already initialized.
            latch.countDown();
        }

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "JavaFX toolkit failed to initialize"
            );
        }
    }

    private static <T> T runOnFxThread(
            FxCallable<T> action
    ) throws Exception {

        if (Platform.isFxApplicationThread()) {
            return action.call();
        }

        CountDownLatch latch = new CountDownLatch(1);

        ResultHolder<T> result = new ResultHolder<>();

        Platform.runLater(() -> {
            try {
                result.value = action.call();
            } catch (Throwable e) {
                result.error = e;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "JavaFX operation timed out"
            );
        }

        if (result.error != null) {
            if (result.error instanceof Exception) {
                throw (Exception) result.error;
            }

            if (result.error instanceof Error) {
                throw (Error) result.error;
            }

            throw new RuntimeException(result.error);
        }

        return result.value;
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }

    private static class ResultHolder<T> {
        private T value;
        private Throwable error;
    }

    @Test
    void opensDocument() throws Exception {

        runOnFxThread(() -> {

            PdfViewerController controller =
                    new PdfViewerController(new Stage());

            PDDocument document = new PDDocument();
            document.addPage(new PDPage());

            controller.openDocument(document);

            assertTrue(controller.hasDocument());

            assertNotNull(controller.getModel());

            assertEquals(
                    0,
                    controller.getModel().getCurrentPage()
            );

            assertEquals(
                    1,
                    controller.getModel().getPageCount()
            );

            controller.closeDocument();

            return null;
        });
    }

    @Test
    void navigatesPages() throws Exception {

        runOnFxThread(() -> {

            PdfViewerController controller =
                    new PdfViewerController(new Stage());

            PDDocument document = new PDDocument();

            document.addPage(new PDPage());
            document.addPage(new PDPage());

            controller.openDocument(document);

            assertTrue(controller.nextPage());

            assertEquals(
                    1,
                    controller.getModel().getCurrentPage()
            );

            assertFalse(controller.nextPage());

            controller.closeDocument();

            return null;
        });
    }

    @Test
    void closesDocument() throws Exception {

        runOnFxThread(() -> {

            PdfViewerController controller =
                    new PdfViewerController(new Stage());

            PDDocument document = new PDDocument();
            document.addPage(new PDPage());

            controller.openDocument(document);

            controller.closeDocument();

            assertFalse(controller.hasDocument());

            assertNull(controller.getModel());

            return null;
        });
    }
}