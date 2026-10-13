package com.filzapdf.renderer;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;

/**
 * Thread-safe PDF page renderer backed by Apache PDFBox {@link PDFRenderer},
 * configured with high-quality anti-aliasing and bicubic interpolation hints
 * for sharp typography and vector graphics.
 */
public class PdfPageRenderer {

    private final PDDocument document;
    private final PDFRenderer pdfRenderer;

    public PdfPageRenderer(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        this.document = document;
        this.pdfRenderer = new PDFRenderer(document);
        this.pdfRenderer.setSubsamplingAllowed(false);

        RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        this.pdfRenderer.setRenderingHints(hints);
    }

    /**
     * Renders a single PDF page at the requested DPI.
     * Synchronized on the underlying PDDocument because Apache PDFBox's
     * PDDocument and PDFRenderer are not thread-safe for concurrent access.
     *
     * @param pageIndex zero-based page index
     * @param dpi rendering resolution
     * @return rendered page image
     */
    public BufferedImage renderPage(int pageIndex, float dpi) {
        if (dpi <= 0) {
            throw new IllegalArgumentException("DPI must be greater than zero");
        }

        synchronized (document) {
            validatePageIndex(pageIndex);
            try {
                float scale = dpi / 72f;
                return pdfRenderer.renderImage(pageIndex, scale, ImageType.RGB, RenderDestination.VIEW);
            } catch (IOException | IllegalArgumentException e) {
                throw new PdfPageRenderingException(
                        "Failed to render PDF page " + pageIndex,
                        e
                );
            }
        }
    }

    /**
     * Returns the width of a PDF page in points.
     */
    public float getPageWidth(int pageIndex) {
        synchronized (document) {
            validatePageIndex(pageIndex);
            int rot = document.getPage(pageIndex).getRotation();
            if (rot == 90 || rot == 270) {
                return document.getPage(pageIndex).getMediaBox().getHeight();
            }
            return document.getPage(pageIndex).getMediaBox().getWidth();
        }
    }

    /**
     * Returns the height of a PDF page in points.
     */
    public float getPageHeight(int pageIndex) {
        synchronized (document) {
            validatePageIndex(pageIndex);
            int rot = document.getPage(pageIndex).getRotation();
            if (rot == 90 || rot == 270) {
                return document.getPage(pageIndex).getMediaBox().getWidth();
            }
            return document.getPage(pageIndex).getMediaBox().getHeight();
        }
    }

    /**
     * Returns the aspect ratio of a PDF page.
     */
    public double getPageAspectRatio(int pageIndex) {
        synchronized (document) {
            float width = getPageWidth(pageIndex);
            float height = getPageHeight(pageIndex);
            return width / height;
        }
    }

    /**
     * Returns the number of pages in the document.
     */
    public int getPageCount() {
        synchronized (document) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Returns the underlying PDDocument.
     */
    public PDDocument getDocument() {
        return document;
    }

    private void validatePageIndex(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IllegalArgumentException(
                    "Invalid page index: " + pageIndex
            );
        }
    }

    /**
     * Runtime exception used when page rendering fails.
     */
    public static class PdfPageRenderingException extends RuntimeException {

        public PdfPageRenderingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}