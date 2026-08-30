package com.filzapdf.renderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PdfPageRenderer {

    private final PDDocument document;
    private final PDFRenderer pdfRenderer;

    public PdfPageRenderer(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        this.document = document;
        this.pdfRenderer = new PDFRenderer(document);
    }

    /**
     * Renders a single PDF page at the requested DPI.
     *
     * @param pageIndex zero-based page index
     * @param dpi rendering resolution
     * @return rendered page image
     */
    public BufferedImage renderPage(int pageIndex, float dpi) {
        validatePageIndex(pageIndex);

        if (dpi <= 0) {
            throw new IllegalArgumentException("DPI must be greater than zero");
        }

        try {
            return pdfRenderer.renderImageWithDPI(pageIndex, dpi);
        } catch (IOException | IllegalArgumentException e) {
            throw new PdfPageRenderingException(
                    "Failed to render PDF page " + pageIndex,
                    e
            );
        }
    }

    /**
     * Returns the width of a PDF page in points.
     */
    public float getPageWidth(int pageIndex) {
        validatePageIndex(pageIndex);
        return document.getPage(pageIndex).getMediaBox().getWidth();
    }

    /**
     * Returns the height of a PDF page in points.
     */
    public float getPageHeight(int pageIndex) {
        validatePageIndex(pageIndex);
        return document.getPage(pageIndex).getMediaBox().getHeight();
    }

    /**
     * Returns the aspect ratio of a PDF page.
     */
    public double getPageAspectRatio(int pageIndex) {
        float width = getPageWidth(pageIndex);
        float height = getPageHeight(pageIndex);

        return width / height;
    }

    /**
     * Returns the number of pages in the document.
     */
    public int getPageCount() {
        return document.getNumberOfPages();
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