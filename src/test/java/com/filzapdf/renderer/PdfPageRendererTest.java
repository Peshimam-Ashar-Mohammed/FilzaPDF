package com.filzapdf.renderer;

import java.awt.image.BufferedImage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

class PdfPageRendererTest {

    @Test
    void shouldReturnPageCount() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            assertEquals(2, renderer.getPageCount());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnPageDimensions() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            assertTrue(renderer.getPageWidth(0) > 0);
            assertTrue(renderer.getPageHeight(0) > 0);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldReturnPageAspectRatio() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            assertTrue(renderer.getPageAspectRatio(0) > 0);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shouldRenderPage() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            BufferedImage image = renderer.renderPage(0, 72);

            assertNotNull(image);
            assertTrue(image.getWidth() > 0);
            assertTrue(image.getHeight() > 0);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @SuppressWarnings("unused")
    void shouldRejectInvalidPageIndex() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> renderer.renderPage(1, 72)
            );
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @SuppressWarnings("unused")
    void shouldRejectInvalidDpi() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            PdfPageRenderer renderer = new PdfPageRenderer(document);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> renderer.renderPage(0, 0)
            );
        } catch (Exception e) {
            fail(e);
        }
    }
}