package com.filzapdf.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.renderer.PageCache;
import com.filzapdf.renderer.PdfPageRenderer;

public class PdfViewerController {

    private PDDocument document;
    private PdfPageRenderer renderer;
    private PdfViewerModel model;

    private final PageCache pageCache =
            new PageCache(10);

    public void openDocument(PDDocument document) {
        closeDocument();

        this.document = document;
        this.renderer = new PdfPageRenderer(document);
        this.model = new PdfViewerModel(
                document.getNumberOfPages()
        );

        pageCache.clear();
    }

    public void closeDocument() {

        pageCache.clear();

        if (document == null) {
            return;
        }

        try {
            document.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        document = null;
        renderer = null;
        model = null;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public PdfViewerModel getModel() {
        return model;
    }

    public PDDocument getDocument() {
        return document;
    }

    public BufferedImage renderCurrentPage()
            throws IOException {

        if (!hasDocument()) {
            throw new IllegalStateException(
                    "No PDF document is open"
            );
        }

        return renderPage(
                model.getCurrentPage(),
                (float) (120 * model.getZoom())
        );
    }

    public BufferedImage renderPage(
            int pageIndex,
            float dpi
    ) throws IOException {

        if (!hasDocument()) {
            throw new IllegalStateException(
                    "No PDF document is open"
            );
        }

        BufferedImage cached =
                pageCache.get(
                        pageIndex,
                        dpi
                );

        if (cached != null) {
            return cached;
        }

        BufferedImage rendered =
                renderer.renderPage(
                        pageIndex,
                        dpi
                );

        pageCache.put(
                pageIndex,
                dpi,
                rendered
        );

        return rendered;
    }

    public boolean nextPage() {
        return model != null &&
                model.nextPage();
    }

    public boolean previousPage() {
        return model != null &&
                model.previousPage();
    }

    public void setCurrentPage(int pageIndex) {
        if (model != null) {
            model.setCurrentPage(pageIndex);
        }
    }

    public void zoomIn() {
        if (model != null) {
            model.zoomIn();
        }
    }

    public void zoomOut() {
        if (model != null) {
            model.zoomOut();
        }
    }

    public void resetZoom() {
        if (model != null) {
            model.resetZoom();
        }
    }

    public void setZoom(double zoom) {
        if (model != null) {
            model.setZoom(zoom);
        }
    }

    public void setFitWidth() {
        if (model != null) {
            model.setFitWidth();
        }
    }

    public void setFitPage() {
        if (model != null) {
            model.setFitPage();
        }
    }
}