package com.filzapdf.renderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PdfPageRenderer {

    private final PDFRenderer renderer;

    public PdfPageRenderer(PDDocument document) {
        this.renderer = new PDFRenderer(document);
    }

    public BufferedImage renderPage(int pageIndex, float dpi)
            throws IOException {
        return renderer.renderImageWithDPI(pageIndex, dpi);
    }
}