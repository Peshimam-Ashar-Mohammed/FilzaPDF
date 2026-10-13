package com.filzapdf.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.annotations.AddAnnotationCommand;
import com.filzapdf.annotations.Annotation;
import com.filzapdf.annotations.AnnotationManager;
import com.filzapdf.annotations.AnnotationType;
import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.pdf.PdfTextEditorService;
import com.filzapdf.pdf.RecognizedTextBlock;
import com.filzapdf.renderer.PageCache;
import com.filzapdf.renderer.PdfPageRenderer;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class PdfViewerController {

    public enum InteractionMode {
        VIEW_BROWSE,
        EDIT_PDF_TEXT,
        ADD_NEW_TEXT,
        INSERT_IMAGE,
        ANNOTATE
    }

    public record PageCoordinateClick(int pageIndex, float pagePtX, float pagePtY) {}

    private static final int PRELOAD_DISTANCE = 2;
    private static final float BASE_DPI = 72f;
    private static final double PAGE_SPACING = 20.0;

    private final Stage stage;
    private final ScrollPane scrollPane;
    private final VBox pageContainer;

    private PdfDocumentService documentService;
    private final PdfTextEditorService textEditorService = new PdfTextEditorService();
    private PdfPageRenderer renderer;
    private PdfViewerModel model;
    private PageCache pageCache;

    private AnnotationManager annotationManager;
    private InteractionMode interactionMode = InteractionMode.VIEW_BROWSE;
    private AnnotationType activeAnnotationType = AnnotationType.HIGHLIGHT;
    private String activeAnnotationColor = "#FFEB3B";

    private final Map<Integer, StackPane> pageContainers = new HashMap<>();
    private final Map<Integer, ImageView> pageViews = new HashMap<>();
    private final Map<Integer, Canvas> overlayCanvases = new HashMap<>();
    private final Map<Integer, ProgressIndicator> loadingIndicators = new HashMap<>();
    private final Map<Integer, Label> errorLabels = new HashMap<>();

    // Cached recognized text blocks per page index
    private final Map<Integer, List<RecognizedTextBlock>> recognizedPageTextMap = new ConcurrentHashMap<>();
    private RecognizedTextBlock selectedTextBlock;
    private RecognizedTextBlock hoveredTextBlock;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, Future<?>> renderTasks = new ConcurrentHashMap<>();

    private Runnable onPageChanged;
    private Consumer<RecognizedTextBlock> onTextBlockSelected;
    private Consumer<PageCoordinateClick> onPageCoordinateClicked;
    private Runnable onDocumentModified;

    // Interactive drawing state on overlay canvas
    private double dragStartX;
    private double dragStartY;
    private List<double[]> currentFreehandPoints;
    private boolean isDraggingAnnotation = false;

    public PdfViewerController(Stage stage) {
        this.stage = stage;
        this.documentService = new PdfDocumentService();

        pageContainer = new VBox(PAGE_SPACING);
        pageContainer.setAlignment(Pos.TOP_CENTER);

        scrollPane = new ScrollPane(pageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> handleScroll());
    }

    public ScrollPane getRoot() {
        return scrollPane;
    }

    public void setAnnotationManager(AnnotationManager annotationManager) {
        this.annotationManager = annotationManager;
    }

    public void setOnPageChanged(Runnable callback) {
        this.onPageChanged = callback;
    }

    public void setOnTextBlockSelected(Consumer<RecognizedTextBlock> callback) {
        this.onTextBlockSelected = callback;
    }

    public void setOnPageCoordinateClicked(Consumer<PageCoordinateClick> callback) {
        this.onPageCoordinateClicked = callback;
    }

    public void setOnDocumentModified(Runnable callback) {
        this.onDocumentModified = callback;
    }

    public InteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(InteractionMode mode) {
        this.interactionMode = mode != null ? mode : InteractionMode.VIEW_BROWSE;
        scrollPane.setPannable(this.interactionMode == InteractionMode.VIEW_BROWSE);
        if (this.interactionMode == InteractionMode.EDIT_PDF_TEXT) {
            ensureTextRecognizedForCurrentWindow();
        }
        updateOverlayCursors();
        redrawAllOverlays();
    }

    public AnnotationType getActiveAnnotationType() {
        return activeAnnotationType;
    }

    public void setActiveAnnotationType(AnnotationType type) {
        this.activeAnnotationType = type != null ? type : AnnotationType.HIGHLIGHT;
    }

    public String getActiveAnnotationColor() {
        return activeAnnotationColor;
    }

    public void setActiveAnnotationColor(String colorHex) {
        this.activeAnnotationColor = colorHex != null ? colorHex : "#FFEB3B";
    }

    public RecognizedTextBlock getSelectedTextBlock() {
        return selectedTextBlock;
    }

    public void setSelectedTextBlock(RecognizedTextBlock block) {
        this.selectedTextBlock = block;
        redrawAllOverlays();
    }

    public List<RecognizedTextBlock> getRecognizedTextForPage(int pageIndex) {
        if (!hasDocument()) return List.of();
        return recognizedPageTextMap.computeIfAbsent(pageIndex, idx -> {
            try {
                return textEditorService.recognizePageText(getDocument(), idx);
            } catch (IOException e) {
                return List.of();
            }
        });
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
                return;
            }
            if (event.getCode() == KeyCode.RIGHT) {
                nextPage();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                previousPage();
                event.consume();
            } else if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.ADD) {
                zoomIn();
                event.consume();
            } else if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
                zoomOut();
                event.consume();
            } else if (event.isControlDown() && (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0)) {
                resetZoom();
                event.consume();
            }
        });
    }

    public void openDocument(PDDocument document) throws IOException {
        try {
            if (renderer != null && renderer.getDocument() != null && renderer.getDocument() != document) {
                closeDocument();
            } else {
                cancelAllTasks();
                if (pageCache != null) pageCache.clear();
            }
            renderer = new PdfPageRenderer(document);
            model = new PdfViewerModel(document.getNumberOfPages());
            pageCache = new PageCache(20);
            recognizedPageTextMap.clear();
            selectedTextBlock = null;
            hoveredTextBlock = null;

            buildPageContainers();
            programmaticScroll = true;
            try {
                scrollPane.setVvalue(0.0);
            } finally {
                programmaticScroll = false;
            }
            updateRenderedPages();
            notifyPageChanged();
        } catch (Exception e) {
            closeDocument();
            throw new IOException("Failed to open document", e);
        }
    }

    /**
     * Refreshes the view after pages or content in the underlying PDDocument have been modified
     * (e.g., text/font edited, page rotated, blank page added, page deleted, watermark applied).
     */
    public void refreshDocumentAfterEdit() {
        if (!hasDocument()) return;
        PDDocument doc = getDocument();
        int currentPage = Math.min(model.getCurrentPage(), Math.max(0, doc.getNumberOfPages() - 1));
        double currentZoom = model.getZoom();

        cancelAllTasks();
        if (pageCache != null) pageCache.clear();
        recognizedPageTextMap.clear();
        selectedTextBlock = null;
        hoveredTextBlock = null;

        renderer = new PdfPageRenderer(doc);
        model = new PdfViewerModel(doc.getNumberOfPages());
        model.setZoom(currentZoom);
        model.setCurrentPage(currentPage);

        buildPageContainers();
        if (interactionMode == InteractionMode.EDIT_PDF_TEXT) {
            ensureTextRecognizedForCurrentWindow();
        }
        updateRenderedPages();
        redrawAllOverlays();
        notifyPageChanged();
        if (onDocumentModified != null) {
            onDocumentModified.run();
        }
    }

    public boolean hasDocument() {
        return model != null && renderer != null;
    }

    public PdfViewerModel getModel() {
        return model;
    }

    public PDDocument getDocument() {
        return renderer == null ? null : renderer.getDocument();
    }

    public PdfPageRenderer getRenderer() {
        return renderer;
    }

    public boolean nextPage() {
        if (model != null && model.getCurrentPage() < model.getPageCount() - 1) {
            model.nextPage();
            updateRenderedPages();
            scrollToPage(model.getCurrentPage());
            notifyPageChanged();
            return true;
        }
        return false;
    }

    public boolean previousPage() {
        if (model != null && model.getCurrentPage() > 0) {
            model.previousPage();
            updateRenderedPages();
            scrollToPage(model.getCurrentPage());
            notifyPageChanged();
            return true;
        }
        return false;
    }

    public void closeDocument() {
        if (renderer != null && renderer.getDocument() != null) {
            try { renderer.getDocument().close(); } catch (Exception e) {}
        }

        cancelAllTasks();
        if (pageCache != null) pageCache.clear();
        recognizedPageTextMap.clear();
        selectedTextBlock = null;
        hoveredTextBlock = null;

        renderer = null;
        model = null;
        pageContainer.getChildren().clear();
        pageContainers.clear();
        pageViews.clear();
        overlayCanvases.clear();
        loadingIndicators.clear();
        errorLabels.clear();
        notifyPageChanged();
    }

    public void shutdown() {
        closeDocument();
        renderExecutor.shutdownNow();
    }

    public void setCurrentPage(int pageIndex) {
        if (model != null && pageIndex >= 0 && pageIndex < model.getPageCount()) {
            model.setCurrentPage(pageIndex);
            updateRenderedPages();
            scrollToPage(pageIndex);
            notifyPageChanged();
        }
    }

    public void setZoom(double zoom) {
        if (model != null) {
            model.setZoom(zoom);
            cancelAllTasks();
            resizeContainers();
            updateRenderedPages();
            redrawAllOverlays();
        }
    }

    public void setFitWidth() {
        if (model != null && renderer != null) {
            double availableWidth = scrollPane.getViewportBounds().getWidth();
            double pageWidth = renderer.getPageWidth(model.getCurrentPage());
            if (availableWidth > 0 && pageWidth > 0) {
                setZoom((availableWidth - 30) / pageWidth);
            }
        }
    }

    public void setFitPage() {
        if (model != null && renderer != null) {
            double availableWidth = scrollPane.getViewportBounds().getWidth();
            double availableHeight = scrollPane.getViewportBounds().getHeight();
            double pageWidth = renderer.getPageWidth(model.getCurrentPage());
            double pageHeight = renderer.getPageHeight(model.getCurrentPage());
            if (availableWidth > 0 && availableHeight > 0) {
                double zoom = Math.min((availableWidth - 30) / pageWidth, (availableHeight - 30) / pageHeight);
                setZoom(zoom);
            }
        }
    }

    public void zoomIn() {
        if (model != null) {
            model.zoomIn();
            setZoom(model.getZoom());
        }
    }

    public void zoomOut() {
        if (model != null) {
            model.zoomOut();
            setZoom(model.getZoom());
        }
    }

    public void resetZoom() {
        if (model != null) {
            model.resetZoom();
            setZoom(model.getZoom());
        }
    }

    private void buildPageContainers() {
        pageContainer.getChildren().clear();
        pageContainers.clear();
        pageViews.clear();
        overlayCanvases.clear();
        loadingIndicators.clear();
        errorLabels.clear();

        int pageCount = model.getPageCount();
        for (int i = 0; i < pageCount; i++) {
            final int pageIndex = i;
            StackPane container = new StackPane();
            container.setAlignment(Pos.CENTER);
            container.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 8, 0, 0, 2);");

            ImageView imageView = new ImageView();
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            Canvas overlay = new Canvas();
            installOverlayHandlers(pageIndex, overlay);

            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setVisible(false);
            spinner.setMaxSize(40, 40);

            Label errorLabel = new Label("Error loading page");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            errorLabel.setVisible(false);

            container.getChildren().addAll(imageView, overlay, spinner, errorLabel);

            pageViews.put(i, imageView);
            overlayCanvases.put(i, overlay);
            loadingIndicators.put(i, spinner);
            errorLabels.put(i, errorLabel);
            pageContainers.put(i, container);

            pageContainer.getChildren().add(container);
        }
        resizeContainers();
        updateOverlayCursors();
    }

    private void installOverlayHandlers(int pageIndex, Canvas canvas) {
        canvas.setOnMouseMoved(e -> {
            if (!hasDocument() || interactionMode != InteractionMode.EDIT_PDF_TEXT) return;
            double zoom = model.getZoom();
            double ptX = e.getX() / zoom;
            double ptY = e.getY() / zoom;

            List<RecognizedTextBlock> blocks = getRecognizedTextForPage(pageIndex);
            RecognizedTextBlock hit = null;
            for (RecognizedTextBlock b : blocks) {
                if (b.containsPoint(ptX, ptY)) {
                    hit = b;
                    break;
                }
            }
            if (hit != hoveredTextBlock) {
                hoveredTextBlock = hit;
                canvas.setCursor(hit != null ? Cursor.HAND : Cursor.TEXT);
                redrawOverlay(pageIndex);
            }
        });

        canvas.setOnMousePressed(e -> {
            if (!hasDocument()) return;
            double zoom = model.getZoom();
            float ptX = (float) (e.getX() / zoom);
            float ptY = (float) (e.getY() / zoom);

            if (interactionMode == InteractionMode.EDIT_PDF_TEXT) {
                List<RecognizedTextBlock> blocks = getRecognizedTextForPage(pageIndex);
                RecognizedTextBlock hit = null;
                for (RecognizedTextBlock b : blocks) {
                    if (b.containsPoint(ptX, ptY)) {
                        hit = b;
                        break;
                    }
                }
                selectedTextBlock = hit;
                redrawOverlay(pageIndex);
                if (hit != null && onTextBlockSelected != null) {
                    onTextBlockSelected.accept(hit);
                }
                e.consume();
            } else if (interactionMode == InteractionMode.ADD_NEW_TEXT || interactionMode == InteractionMode.INSERT_IMAGE) {
                if (onPageCoordinateClicked != null) {
                    onPageCoordinateClicked.accept(new PageCoordinateClick(pageIndex, ptX, ptY));
                }
                e.consume();
            } else if (interactionMode == InteractionMode.ANNOTATE) {
                dragStartX = ptX;
                dragStartY = ptY;
                isDraggingAnnotation = true;
                if (activeAnnotationType == AnnotationType.FREEHAND) {
                    currentFreehandPoints = new ArrayList<>();
                    currentFreehandPoints.add(new double[]{ptX, ptY});
                } else if (activeAnnotationType == AnnotationType.NOTE) {
                    isDraggingAnnotation = false;
                    promptAndCreateStickyNote(pageIndex, ptX, ptY);
                }
                e.consume();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (!hasDocument() || interactionMode != InteractionMode.ANNOTATE || !isDraggingAnnotation) return;
            double zoom = model.getZoom();
            double ptX = e.getX() / zoom;
            double ptY = e.getY() / zoom;

            if (activeAnnotationType == AnnotationType.FREEHAND && currentFreehandPoints != null) {
                currentFreehandPoints.add(new double[]{ptX, ptY});
            }
            redrawOverlay(pageIndex);
            drawLivePreviewOnOverlay(canvas, dragStartX, dragStartY, ptX, ptY);
            e.consume();
        });

        canvas.setOnMouseReleased(e -> {
            if (!hasDocument() || interactionMode != InteractionMode.ANNOTATE || !isDraggingAnnotation) return;
            isDraggingAnnotation = false;
            double zoom = model.getZoom();
            double ptX = e.getX() / zoom;
            double ptY = e.getY() / zoom;

            commitAnnotation(pageIndex, dragStartX, dragStartY, ptX, ptY);
            redrawOverlay(pageIndex);
            e.consume();
        });
    }

    private void promptAndCreateStickyNote(int pageIndex, float ptX, float ptY) {
        if (annotationManager == null) return;
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Add Sticky Note");
        dialog.setHeaderText("Add Note on Page " + (pageIndex + 1));
        dialog.setContentText("Note text:");
        dialog.showAndWait().ifPresent(text -> {
            if (!text.trim().isEmpty()) {
                Annotation a = new Annotation();
                a.setType(AnnotationType.NOTE);
                a.setPageIndex(pageIndex);
                a.setX(ptX);
                a.setY(ptY);
                a.setWidth(145);
                a.setHeight(42);
                a.setColor(activeAnnotationColor);
                a.setNoteText(text.trim());
                annotationManager.executeCommand(new AddAnnotationCommand(annotationManager, a));
                redrawOverlay(pageIndex);
            }
        });
    }

    private void commitAnnotation(int pageIndex, double x1, double y1, double x2, double y2) {
        if (annotationManager == null) return;
        Annotation a = new Annotation();
        a.setType(activeAnnotationType);
        a.setPageIndex(pageIndex);
        a.setColor(activeAnnotationColor);

        if (activeAnnotationType == AnnotationType.FREEHAND) {
            if (currentFreehandPoints == null || currentFreehandPoints.size() < 2) return;
            a.setStrokePoints(new ArrayList<>(currentFreehandPoints));
            currentFreehandPoints = null;
        } else {
            double rx = Math.min(x1, x2);
            double ry = Math.min(y1, y2);
            double rw = Math.abs(x2 - x1);
            double rh = Math.abs(y2 - y1);
            if (rw < 4 && rh < 4) return;
            a.setX(rx);
            a.setY(ry);
            a.setWidth(rw);
            a.setHeight(Math.max(rh, 8));
        }

        annotationManager.executeCommand(new AddAnnotationCommand(annotationManager, a));
    }

    private void drawLivePreviewOnOverlay(Canvas canvas, double x1, double y1, double x2, double y2) {
        double zoom = model.getZoom();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Color c = Color.web(activeAnnotationColor);

        if (activeAnnotationType == AnnotationType.FREEHAND && currentFreehandPoints != null && currentFreehandPoints.size() >= 2) {
            gc.setStroke(c);
            gc.setLineWidth(2.2 * zoom);
            gc.beginPath();
            gc.moveTo(currentFreehandPoints.get(0)[0] * zoom, currentFreehandPoints.get(0)[1] * zoom);
            for (int i = 1; i < currentFreehandPoints.size(); i++) {
                gc.lineTo(currentFreehandPoints.get(i)[0] * zoom, currentFreehandPoints.get(i)[1] * zoom);
            }
            gc.stroke();
        } else {
            double rx = Math.min(x1, x2) * zoom;
            double ry = Math.min(y1, y2) * zoom;
            double rw = Math.abs(x2 - x1) * zoom;
            double rh = Math.max(8, Math.abs(y2 - y1)) * zoom;

            if (activeAnnotationType == AnnotationType.HIGHLIGHT) {
                gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.35));
                gc.fillRect(rx, ry, rw, rh);
            } else if (activeAnnotationType == AnnotationType.REDACT) {
                gc.setFill(Color.color(0, 0, 0, 0.85));
                gc.fillRect(rx, ry, rw, rh);
            } else {
                gc.setStroke(c);
                gc.setLineWidth(2.0 * zoom);
                gc.strokeRect(rx, ry, rw, rh);
            }
        }
    }

    public void redrawAllOverlays() {
        if (!hasDocument()) return;
        for (int i = 0; i < model.getPageCount(); i++) {
            redrawOverlay(i);
        }
    }

    public void redrawOverlay(int pageIndex) {
        Canvas canvas = overlayCanvases.get(pageIndex);
        if (canvas == null || !hasDocument()) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double zoom = model.getZoom();

        // 1. Draw saved annotations for this page
        if (annotationManager != null) {
            List<Annotation> list = annotationManager.getAnnotationsForPage(pageIndex);
            for (Annotation a : list) {
                drawAnnotationOnCanvas(gc, a, zoom);
            }
        }

            // 2. If in EDIT_PDF_TEXT mode, highlight recognized text blocks & font badges in Purple Theme
        if (interactionMode == InteractionMode.EDIT_PDF_TEXT) {
            List<RecognizedTextBlock> blocks = recognizedPageTextMap.get(pageIndex);
            if (blocks != null) {
                for (RecognizedTextBlock b : blocks) {
                    double bx = b.getX() * zoom;
                    double by = b.getY() * zoom;
                    double bw = b.getWidth() * zoom;
                    double bh = b.getHeight() * zoom;

                    boolean isSelected = (b == selectedTextBlock);
                    boolean isHovered = (b == hoveredTextBlock);

                    if (isSelected) {
                        gc.setFill(Color.rgb(139, 92, 246, 0.22));
                        gc.fillRect(bx - 2, by - 2, bw + 4, bh + 4);
                        gc.setStroke(Color.web("#7C3AED"));
                        gc.setLineWidth(2.2);
                        gc.setLineDashes(0);
                        gc.strokeRect(bx - 2, by - 2, bw + 4, bh + 4);
                    } else if (isHovered) {
                        gc.setFill(Color.rgb(139, 92, 246, 0.14));
                        gc.fillRect(bx - 1, by - 1, bw + 2, bh + 2);
                        gc.setStroke(Color.web("#8B5CF6"));
                        gc.setLineWidth(1.6);
                        gc.setLineDashes(0);
                        gc.strokeRect(bx - 1, by - 1, bw + 2, bh + 2);

                        // Draw floating font badge above hovered block
                        String badge = b.getDisplayFontSummary();
                        gc.setFill(Color.web("#4C1D95"));
                        double badgeW = Math.min(250, badge.length() * 6.5 + 14);
                        double badgeY = Math.max(0, by - 19);
                        gc.fillRoundRect(bx, badgeY, badgeW, 17, 5, 5);
                        gc.setFill(Color.web("#F3E8FF"));
                        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                        gc.fillText(badge, bx + 6, badgeY + 12);
                    } else {
                        gc.setStroke(Color.rgb(139, 92, 246, 0.55));
                        gc.setLineWidth(1.1);
                        gc.setLineDashes(4, 3);
                        gc.strokeRect(bx, by, bw, bh);
                        gc.setLineDashes(0);
                    }
                }
            }
        }
    }

    private void drawAnnotationOnCanvas(GraphicsContext gc, Annotation a, double zoom) {
        Color c = Color.web(a.getColor() != null ? a.getColor() : "#FFEB3B");
        double rx = a.getX() * zoom;
        double ry = a.getY() * zoom;
        double rw = a.getWidth() * zoom;
        double rh = a.getHeight() * zoom;

        switch (a.getType()) {
            case HIGHLIGHT:
                gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.38));
                gc.fillRect(rx, ry, rw, rh);
                break;
            case UNDERLINE:
                gc.setStroke(c);
                gc.setLineWidth(2.0 * zoom);
                gc.strokeLine(rx, ry + rh, rx + rw, ry + rh);
                break;
            case STRIKETHROUGH:
                gc.setStroke(c);
                gc.setLineWidth(2.0 * zoom);
                gc.strokeLine(rx, ry + rh / 2.0, rx + rw, ry + rh / 2.0);
                break;
            case RECTANGLE:
                gc.setStroke(c);
                gc.setLineWidth(2.0 * zoom);
                gc.strokeRect(rx, ry, rw, rh);
                break;
            case REDACT:
                gc.setFill(Color.BLACK);
                gc.fillRect(rx, ry, rw, rh);
                break;
            case FREEHAND:
                List<double[]> pts = a.getStrokePoints();
                if (pts != null && pts.size() >= 2) {
                    gc.setStroke(c);
                    gc.setLineWidth(2.2 * zoom);
                    gc.beginPath();
                    gc.moveTo(pts.get(0)[0] * zoom, pts.get(0)[1] * zoom);
                    for (int i = 1; i < pts.size(); i++) {
                        gc.lineTo(pts.get(i)[0] * zoom, pts.get(i)[1] * zoom);
                    }
                    gc.stroke();
                }
                break;
            case NOTE:
                gc.setFill(Color.web("#FFF59D"));
                gc.setStroke(Color.web("#FBC02D"));
                gc.setLineWidth(1.2);
                double nw = Math.max(rw, 130 * zoom);
                double nh = Math.max(rh, 36 * zoom);
                gc.fillRoundRect(rx, ry, nw, nh, 6, 6);
                gc.strokeRoundRect(rx, ry, nw, nh, 6, 6);
                if (a.getNoteText() != null) {
                    gc.setFill(Color.web("#212121"));
                    gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, Math.max(9, 10 * zoom)));
                    gc.fillText(a.getNoteText(), rx + 6 * zoom, ry + 15 * zoom, nw - 12 * zoom);
                }
                break;
            default:
                break;
        }
    }

    private void updateOverlayCursors() {
        Cursor c = switch (interactionMode) {
            case VIEW_BROWSE -> Cursor.DEFAULT;
            case EDIT_PDF_TEXT, ADD_NEW_TEXT -> Cursor.TEXT;
            case INSERT_IMAGE, ANNOTATE -> Cursor.CROSSHAIR;
        };
        for (Canvas canvas : overlayCanvases.values()) {
            canvas.setCursor(c);
        }
    }

    private void ensureTextRecognizedForCurrentWindow() {
        if (!hasDocument()) return;
        int currentPage = model.getCurrentPage();
        int start = Math.max(0, currentPage - PRELOAD_DISTANCE);
        int end = Math.min(model.getPageCount() - 1, currentPage + PRELOAD_DISTANCE);
        for (int i = start; i <= end; i++) {
            getRecognizedTextForPage(i);
        }
    }

    private void resizeContainers() {
        if (renderer == null || model == null) return;

        for (int i = 0; i < model.getPageCount(); i++) {
            StackPane container = pageContainers.get(i);
            ImageView imageView = pageViews.get(i);
            Canvas overlay = overlayCanvases.get(i);

            double width = renderer.getPageWidth(i) * model.getZoom();
            double height = renderer.getPageHeight(i) * model.getZoom();

            container.setPrefSize(width, height);
            container.setMinSize(width, height);
            container.setMaxSize(width, height);

            if (overlay != null) {
                overlay.setWidth(width);
                overlay.setHeight(height);
            }

            if (imageView.getImage() != null) {
                imageView.setFitWidth(width);
                imageView.setFitHeight(height);
            }
        }
    }

    private void updateRenderedPages() {
        if (model == null || renderer == null || pageCache == null) return;

        int currentPage = model.getCurrentPage();
        int pageCount = model.getPageCount();
        int start = Math.max(0, currentPage - PRELOAD_DISTANCE);
        int end = Math.min(pageCount - 1, currentPage + PRELOAD_DISTANCE);

        if (interactionMode == InteractionMode.EDIT_PDF_TEXT) {
            ensureTextRecognizedForCurrentWindow();
        }

        for (int i = 0; i < pageCount; i++) {
            if (i < start || i > end) {
                ImageView iv = pageViews.get(i);
                if (iv != null && iv.getImage() != null) {
                    iv.setImage(null);
                }
                cancelTask(i);
            } else {
                renderPageIntoView(i);
                redrawOverlay(i);
            }
        }
    }

    private void renderPageIntoView(int pageIndex) {
        ImageView imageView = pageViews.get(pageIndex);
        ProgressIndicator spinner = loadingIndicators.get(pageIndex);
        Label errLabel = errorLabels.get(pageIndex);
        if (imageView == null || spinner == null || errLabel == null || model == null || renderer == null) {
            return;
        }

        float dpi = (float) (BASE_DPI * model.getZoom());
        final PdfPageRenderer activeRenderer = this.renderer;

        if (pageCache.containsKey(pageIndex, dpi)) {
            BufferedImage cached = pageCache.get(pageIndex, dpi);
            setImageOnFxThread(pageIndex, cached);
            spinner.setVisible(false);
            errLabel.setVisible(false);
            return;
        }

        Future<?> existing = renderTasks.get(pageIndex);
        if (existing != null && !existing.isDone()) {
            return;
        }

        spinner.setVisible(true);
        errLabel.setVisible(false);

        Future<?> task = renderExecutor.submit(() -> {
            try {
                if (this.renderer != activeRenderer) return;
                BufferedImage image = activeRenderer.renderPage(pageIndex, dpi);

                if (this.renderer != activeRenderer) return;
                if (pageCache != null) {
                    pageCache.put(pageIndex, dpi, image);
                }

                Platform.runLater(() -> {
                    renderTasks.remove(pageIndex);
                    if (this.renderer == activeRenderer) {
                        setImageOnFxThread(pageIndex, image);
                        spinner.setVisible(false);
                        errLabel.setVisible(false);
                        redrawOverlay(pageIndex);
                    }
                });
            } catch (CancellationException e) {
                renderTasks.remove(pageIndex);
            } catch (Exception e) {
                renderTasks.remove(pageIndex);
                System.err.println("Error rendering page " + pageIndex + ": " + e.getMessage());
                Platform.runLater(() -> {
                    if (this.renderer == activeRenderer) {
                        spinner.setVisible(false);
                        errLabel.setVisible(true);
                    }
                });
            }
        });

        renderTasks.put(pageIndex, task);
    }

    private void setImageOnFxThread(int pageIndex, BufferedImage bufferedImage) {
        ImageView imageView = pageViews.get(pageIndex);
        if (imageView == null || renderer == null || model == null || bufferedImage == null) return;

        Image image = javafx.embed.swing.SwingFXUtils.toFXImage(bufferedImage, null);
        imageView.setImage(image);

        double width = renderer.getPageWidth(pageIndex) * model.getZoom();
        double height = renderer.getPageHeight(pageIndex) * model.getZoom();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
    }

    private void cancelTask(int pageIndex) {
        Future<?> task = renderTasks.remove(pageIndex);
        if (task != null) {
            // Use cancel(false) so we NEVER interrupt an active NIO FileChannel read inside PDFBox!
            task.cancel(false);
        }
    }

    private void cancelAllTasks() {
        for (Future<?> task : renderTasks.values()) {
            // Use cancel(false) so we NEVER interrupt an active NIO FileChannel read inside PDFBox!
            task.cancel(false);
        }
        renderTasks.clear();
    }

    private boolean programmaticScroll = false;

    private void handleScroll() {
        if (!programmaticScroll) {
            updateCurrentPageFromScroll();
        }
        updateRenderedPages();
    }

    private void updateCurrentPageFromScroll() {
        if (model == null || model.getPageCount() <= 1) return;
        if (scrollPane.getViewportBounds().getHeight() <= 0) return;

        double scrollPosition = scrollPane.getVvalue();
        double maxScroll = scrollPane.getVmax();
        if (maxScroll <= 0) return;

        double targetPosition = scrollPosition * maxScroll;
        double accumulatedHeight = 0;

        for (int i = 0; i < pageContainers.size(); i++) {
            StackPane container = pageContainers.get(i);
            accumulatedHeight += container.getBoundsInParent().getHeight() + PAGE_SPACING;

            if (targetPosition <= accumulatedHeight - (container.getBoundsInParent().getHeight() / 2)) {
                if (model.getCurrentPage() != i) {
                    model.setCurrentPage(i);
                    notifyPageChanged();
                }
                return;
            }
        }
    }

    private void scrollToPage(int pageIndex) {
        if (model == null || model.getPageCount() <= 1) return;

        double accumulatedHeight = 0;
        for (int i = 0; i < pageIndex; i++) {
            accumulatedHeight += pageContainers.get(i).getBoundsInParent().getHeight() + PAGE_SPACING;
        }

        double contentHeight = pageContainer.getBoundsInLocal().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();

        if (viewportHeight > 0 && contentHeight > viewportHeight) {
            double maxScroll = contentHeight - viewportHeight;
            programmaticScroll = true;
            try {
                scrollPane.setVvalue(Math.min(1.0, accumulatedHeight / maxScroll));
            } finally {
                programmaticScroll = false;
            }
        }
    }

    private void notifyPageChanged() {
        if (onPageChanged != null) {
            onPageChanged.run();
        }
    }
}