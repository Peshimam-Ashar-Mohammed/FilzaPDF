package com.filzapdf.annotations;

/**
 * Strategy interface for annotation tools.
 *
 * Each tool handles mouse events on a PDF page and produces annotations
 * via the AnnotationManager. Coordinates are in page-space (PDF points),
 * not screen pixels — callers must transform before invoking.
 */
public interface AnnotationTool {

    /**
     * Called when the user presses the mouse button on a page.
     *
     * @param manager   the annotation manager to execute commands against
     * @param pageIndex zero-based page index
     * @param x         x coordinate in PDF points
     * @param y         y coordinate in PDF points
     */
    void onMousePressed(AnnotationManager manager, int pageIndex, double x, double y);

    /**
     * Called continuously while the mouse is dragged after pressing.
     *
     * @param x current x coordinate in PDF points
     * @param y current y coordinate in PDF points
     */
    void onMouseDragged(double x, double y);

    /**
     * Called when the mouse button is released.
     *
     * @param manager   the annotation manager to commit the annotation to
     * @param pageIndex zero-based page index
     * @param x         release x coordinate in PDF points
     * @param y         release y coordinate in PDF points
     */
    void onMouseReleased(AnnotationManager manager, int pageIndex, double x, double y);
}
