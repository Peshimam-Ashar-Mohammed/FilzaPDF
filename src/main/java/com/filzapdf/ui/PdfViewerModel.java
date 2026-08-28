package com.filzapdf.ui;

public class PdfViewerModel {

    private int currentPage;
    private final int pageCount;
    private double zoom;

    public PdfViewerModel(int pageCount) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException(
                    "Page count must be greater than zero"
            );
        }

        this.pageCount = pageCount;
        this.currentPage = 0;
        this.zoom = 1.0;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return pageCount;
    }

    public double getZoom() {
        return zoom;
    }

    public boolean nextPage() {
        if (currentPage >= pageCount - 1) {
            return false;
        }

        currentPage++;
        return true;
    }

    public boolean previousPage() {
        if (currentPage <= 0) {
            return false;
        }

        currentPage--;
        return true;
    }

    public void zoomIn() {
        zoom = Math.min(zoom + 0.25, 4.0);
    }

    public void zoomOut() {
        zoom = Math.max(zoom - 0.25, 0.25);
    }

    public void resetZoom() {
        zoom = 1.0;
    }
}