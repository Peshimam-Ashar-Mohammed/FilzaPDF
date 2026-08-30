package com.filzapdf.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.renderer.PageCache;
import com.filzapdf.renderer.PdfPageRenderer;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PdfViewerController {

    private static final int PRELOAD_DISTANCE = 2;
    private static final float BASE_DPI = 72f;
    private static final double PAGE_SPACING = 20.0;

    private final Stage stage;
    private final ScrollPane scrollPane;
    private final VBox pageContainer;

    private PdfDocumentService documentService;
    private PdfPageRenderer renderer;
    private PdfViewerModel model;
    private PageCache pageCache;

    private final Map<Integer, StackPane> pageContainers = new HashMap<>();
    private final Map<Integer, ImageView> pageViews = new HashMap<>();
    private final Map<Integer, ProgressIndicator> loadingIndicators = new HashMap<>();
    private final Map<Integer, Label> errorLabels = new HashMap<>();
    
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, Future<?>> renderTasks = new ConcurrentHashMap<>();

    private Runnable onPageChanged;

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

    public void setOnPageChanged(Runnable callback) {
        this.onPageChanged = callback;
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
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
            closeDocument();
            renderer = new PdfPageRenderer(document);
            model = new PdfViewerModel(document.getNumberOfPages());
            pageCache = new PageCache(20);
            
            buildPageContainers();
            updateRenderedPages();
            notifyPageChanged();
        } catch (Exception e) {
            closeDocument();
            throw new IOException("Failed to open document", e);
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
        
        renderer = null;
        model = null;
        pageContainer.getChildren().clear();
        pageContainers.clear();
        pageViews.clear();
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
            cancelAllTasks(); // Cancel all current renders when zoom changes
            resizeContainers();
            updateRenderedPages();
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
        loadingIndicators.clear();
        errorLabels.clear();

        int pageCount = model.getPageCount();
        for (int i = 0; i < pageCount; i++) {
            StackPane container = new StackPane();
            container.setAlignment(Pos.CENTER);
            container.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, gray, 5, 0, 0, 1);");

            ImageView imageView = new ImageView();
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setVisible(false);
            spinner.setMaxSize(40, 40);
            
            Label errorLabel = new Label("Error loading page");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            errorLabel.setVisible(false);

            container.getChildren().addAll(imageView, spinner, errorLabel);
            
            pageViews.put(i, imageView);
            loadingIndicators.put(i, spinner);
            errorLabels.put(i, errorLabel);
            pageContainers.put(i, container);
            
            pageContainer.getChildren().add(container);
        }
        resizeContainers();
    }
    
    private void resizeContainers() {
        if (renderer == null || model == null) return;
        
        for (int i = 0; i < model.getPageCount(); i++) {
            StackPane container = pageContainers.get(i);
            ImageView imageView = pageViews.get(i);
            
            double width = renderer.getPageWidth(i) * model.getZoom();
            double height = renderer.getPageHeight(i) * model.getZoom();
            
            container.setPrefSize(width, height);
            container.setMinSize(width, height);
            container.setMaxSize(width, height);
            
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

        // Release images and cancel tasks outside the window
        for (int i = 0; i < pageCount; i++) {
            if (i < start || i > end) {
                ImageView iv = pageViews.get(i);
                if (iv != null && iv.getImage() != null) {
                    iv.setImage(null);
                }
                cancelTask(i);
            } else {
                renderPageIntoView(i);
            }
        }
    }

    private void renderPageIntoView(int pageIndex) {
        ImageView imageView = pageViews.get(pageIndex);
        ProgressIndicator spinner = loadingIndicators.get(pageIndex);
        Label errLabel = errorLabels.get(pageIndex);
        
        float dpi = (float) (BASE_DPI * model.getZoom());
        
        if (pageCache.containsKey(pageIndex, dpi)) {
            BufferedImage cached = pageCache.get(pageIndex, dpi);
            setImageOnFxThread(pageIndex, cached);
            return;
        }

        // Avoid queueing if already rendering this page at this dpi
        if (renderTasks.containsKey(pageIndex) && !renderTasks.get(pageIndex).isDone()) {
            return;
        }

        spinner.setVisible(true);
        errLabel.setVisible(false);

        Future<?> task = renderExecutor.submit(() -> {
            try {
                BufferedImage image = renderer.renderPage(pageIndex, dpi);
                
                if (Thread.currentThread().isInterrupted()) return;
                
                pageCache.put(pageIndex, dpi, image);
                
                Platform.runLater(() -> {
                    setImageOnFxThread(pageIndex, image);
                    spinner.setVisible(false);
                });
            } catch (CancellationException e) {
                // Task cancelled, ignore
            } catch (Exception e) {
                System.err.println("Error rendering page " + pageIndex + ": " + e.getMessage());
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    errLabel.setVisible(true);
                });
            }
        });
        
        renderTasks.put(pageIndex, task);
    }
    
    private void setImageOnFxThread(int pageIndex, BufferedImage bufferedImage) {
        ImageView imageView = pageViews.get(pageIndex);
        if (imageView == null) return;
        
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
            task.cancel(true);
        }
    }
    
    private void cancelAllTasks() {
        for (Future<?> task : renderTasks.values()) {
            task.cancel(true);
        }
        renderTasks.clear();
    }

    private void handleScroll() {
        updateCurrentPageFromScroll();
        // Debounce or just update directly (can add a Timeline for debounce later if needed, but simple update is fine for now)
        updateRenderedPages();
    }

    private void updateCurrentPageFromScroll() {
        if (model == null || model.getPageCount() <= 1) return;

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
        
        if (contentHeight > viewportHeight) {
            double maxScroll = contentHeight - viewportHeight;
            scrollPane.setVvalue(Math.min(1.0, accumulatedHeight / maxScroll));
        }
    }

    private void notifyPageChanged() {
        if (onPageChanged != null) {
            onPageChanged.run();
        }
    }
}