package com.filzapdf.ui;

public class PdfViewerModel {

    public enum ZoomMode {
        MANUAL,
        FIT_WIDTH,
        FIT_PAGE
    }

    private int currentPage;
    private final int pageCount;
    private double zoom;
    private ZoomMode zoomMode;

    public PdfViewerModel(int pageCount) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException(
                    "Page count must be greater than zero"
            );
        }

        this.pageCount = pageCount;
        this.currentPage = 0;
        this.zoom = 1.0;
        this.zoomMode = ZoomMode.MANUAL;
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

    public ZoomMode getZoomMode() {
        return zoomMode;
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
        zoomMode = ZoomMode.MANUAL;
        zoom = Math.min(zoom + 0.25, 4.0);
    }

    public void zoomOut() {
        zoomMode = ZoomMode.MANUAL;
        zoom = Math.max(zoom - 0.25, 0.25);
    }

    public void resetZoom() {
        zoomMode = ZoomMode.MANUAL;
        zoom = 1.0;
    }

    public void setFitWidth() {
        zoomMode = ZoomMode.FIT_WIDTH;
    }

    public void setFitPage() {
        zoomMode = ZoomMode.FIT_PAGE;
    }

    public void setZoom(double zoom) {
        if (zoom <= 0) {
            throw new IllegalArgumentException(
                    "Zoom must be greater than zero"
            );
        }

        this.zoom = zoom;
        this.zoomMode = ZoomMode.MANUAL;
    }


        public void setCurrentPage(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IllegalArgumentException(
                        "Invalid page index"
                );
            }

            currentPage = pageIndex;
        }


}