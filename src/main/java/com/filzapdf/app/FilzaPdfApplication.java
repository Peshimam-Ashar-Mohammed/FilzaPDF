package com.filzapdf.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.ui.PdfViewerController;
import com.filzapdf.ui.PdfViewerModel;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FilzaPdfApplication extends Application {

    private final PdfDocumentService pdfDocumentService =
            new PdfDocumentService();

    private final PdfViewerController controller =
            new PdfViewerController();

    private Label pageLabel;
    private Label zoomLabel;

    private Button previousButton;
    private Button nextButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private Button fitWidthButton;
    private Button fitPageButton;
    private Button resetZoomButton;

    private TextField pageField;

    private ScrollPane scrollPane;
    private VBox pageContainer;
    private VBox thumbnailContainer;

    private final List<ImageView> pageViews =
            new ArrayList<>();

    @Override
    public void start(Stage stage) {

        Button openButton =
                new Button("Open PDF");

        previousButton =
                new Button("Previous");

        nextButton =
                new Button("Next");

        zoomOutButton =
                new Button("-");

        zoomInButton =
                new Button("+");

        fitWidthButton =
                new Button("Fit Width");

        fitPageButton =
                new Button("Fit Page");

        resetZoomButton =
                new Button("100%");

        pageField =
                new TextField();

        pageField.setPrefWidth(60);
        pageField.setPromptText("Page");
        pageField.setFocusTraversable(false);

        pageLabel =
                new Label("of");

        zoomLabel =
                new Label("100%");

        pageContainer =
                new VBox(20);

        pageContainer.setAlignment(
                Pos.TOP_CENTER
        );
        pageContainer.setFocusTraversable(false);

        scrollPane =
                new ScrollPane(pageContainer);

        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFocusTraversable(false);

        thumbnailContainer =
                new VBox(10);

        thumbnailContainer.setAlignment(
                Pos.TOP_CENTER
        );

        thumbnailContainer.setPrefWidth(150);
        thumbnailContainer.setFocusTraversable(false);

        ScrollPane thumbnailScrollPane =
                new ScrollPane(thumbnailContainer);

        thumbnailScrollPane.setFitToWidth(true);
        thumbnailScrollPane.setFocusTraversable(false);

        scrollPane.vvalueProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateCurrentPageFromScroll()
        );

        openButton.setOnAction(
                event -> openPdf(stage)
        );

        previousButton.setOnAction(
                event -> showPreviousPage()
        );

        nextButton.setOnAction(
                event -> showNextPage()
        );

        zoomOutButton.setOnAction(
                event -> zoomOut()
        );

        zoomInButton.setOnAction(
                event -> zoomIn()
        );

        fitWidthButton.setOnAction(
                event -> fitWidth()
        );

        fitPageButton.setOnAction(
                event -> fitPage()
        );

        resetZoomButton.setOnAction(
                event -> resetZoom()
        );

        pageField.setOnAction(
                event -> jumpToPage()
        );

        setViewerControlsEnabled(false);

        HBox toolbar =
                new HBox(
                        10,
                        openButton,
                        previousButton,
                        nextButton,
                        pageField,
                        pageLabel,
                        zoomOutButton,
                        zoomLabel,
                        zoomInButton,
                        fitWidthButton,
                        fitPageButton,
                        resetZoomButton
                );

        toolbar.setAlignment(
                Pos.CENTER
        );
        toolbar.setFocusTraversable(false);

        BorderPane root =
                new BorderPane();

        root.setTop(toolbar);
        root.setLeft(thumbnailScrollPane);
        root.setCenter(scrollPane);

        Scene scene =
                new Scene(
                        root,
                        1200,
                        700
                );

        // Use Scene-level key handler for better event capture
        scene.setOnKeyPressed(event -> {

            if (!controller.hasDocument()) {
                return;
            }

            switch (event.getCode()) {
                case RIGHT:
                    showNextPage();
                    event.consume();
                    break;
                case LEFT:
                    showPreviousPage();
                    event.consume();
                    break;
                case PLUS:
                case EQUALS:
                case ADD:
                    zoomIn();
                    event.consume();
                    break;
                case MINUS:
                case SUBTRACT:
                    zoomOut();
                    event.consume();
                    break;
                case DIGIT0:
                case NUMPAD0:
                    if (event.isControlDown()) {
                        resetZoom();
                        event.consume();
                    }
                    break;
                case W:
                    fitWidth();
                    event.consume();
                    break;
                case P:
                    fitPage();
                    event.consume();
                    break;
                default:
                    break;
            }
        });

        stage.setTitle("FilzaPDF");
        stage.setScene(scene);
        stage.show();

        // Request focus to ensure keyboard events are captured
        root.requestFocus();
    }

    private void openPdf(Stage stage) {

        FileChooser fileChooser =
                new FileChooser();

        fileChooser.setTitle(
                "Open PDF"
        );

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "PDF Files",
                        "*.pdf"
                )
        );

        File file =
                fileChooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        try {

            closeCurrentDocument();

            PDDocument document =
                    pdfDocumentService.open(file);

            controller.openDocument(document);

            setViewerControlsEnabled(true);

            renderAllPages();
            renderThumbnails();

        } catch (
                IOException |
                RuntimeException e
        ) {
            e.printStackTrace();
        }
    }

private void renderAllPages() {

    pageContainer.getChildren().clear();
    pageViews.clear();

    PdfViewerModel model =
            controller.getModel();

    int currentPage =
            model.getCurrentPage();

    int pageCount =
            model.getPageCount();

    try {

        float dpi =
                (float) (
                        120 *
                        model.getZoom()
                );

        for (
                int pageIndex = 0;
                pageIndex < pageCount;
                pageIndex++
        ) {

            ImageView imageView =
                    new ImageView();

            imageView.setPreserveRatio(true);

            pageViews.add(imageView);

            pageContainer
                    .getChildren()
                    .add(imageView);

            if (
                    Math.abs(
                            pageIndex - currentPage
                    ) <= 2
            ) {

                BufferedImage image =
                        controller.renderPage(
                                pageIndex,
                                dpi
                        );

                imageView.setImage(
                        SwingFXUtils.toFXImage(
                                image,
                                null
                        )
                );
            }
        }

        updateLabels();

    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void renderVisiblePages() {

    if (!controller.hasDocument()) {
        return;
    }

    PdfViewerModel model =
            controller.getModel();

    int currentPage =
            model.getCurrentPage();

    int pageCount =
            model.getPageCount();

    float dpi =
            (float) (
                    120 *
                    model.getZoom()
            );

    int start =
            Math.max(
                    0,
                    currentPage - 2
            );

    int end =
            Math.min(
                    pageCount - 1,
                    currentPage + 2
            );

    try {

        for (
                int pageIndex = start;
                pageIndex <= end;
                pageIndex++
        ) {

            ImageView imageView =
                    pageViews.get(pageIndex);

            if (imageView.getImage() != null) {
                continue;
            }

            BufferedImage image =
                    controller.renderPage(
                            pageIndex,
                            dpi
                    );

            imageView.setImage(
                    SwingFXUtils.toFXImage(
                            image,
                            null
                    )
            );
        }

    } catch (IOException e) {
        e.printStackTrace();
    }
}


        private void renderThumbnails() {

            thumbnailContainer
                    .getChildren()
                    .clear();

            try {

                PdfViewerModel model =
                        controller.getModel();

            for (
                    int pageIndex = 0;
                    pageIndex < model.getPageCount();
                    pageIndex++
            ) {

                BufferedImage image =
                        controller.renderPage(
                                pageIndex,
                                25
                        );

                ImageView thumbnail =
                        new ImageView(
                                SwingFXUtils.toFXImage(
                                        image,
                                        null
                                )
                        );

                thumbnail.setPreserveRatio(
                        true
                );

                thumbnail.setFitWidth(110);

                final int selectedPage =
                        pageIndex;

                thumbnail.setOnMouseClicked(
                        event -> {

                            controller.setCurrentPage(
                                    selectedPage
                            );

                            scrollToCurrentPage();
                            updateLabels();
                        }
                );

                Label label =
                        new Label(
                                "Page " +
                                (pageIndex + 1)
                        );

                VBox box =
                        new VBox(
                                5,
                                label,
                                thumbnail
                        );

                box.setAlignment(
                        Pos.CENTER
                );

                thumbnailContainer
                        .getChildren()
                        .add(box);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCurrentPageFromScroll() {

        if (
                !controller.hasDocument() ||
                pageViews.isEmpty()
        ) {
            return;
        }

        PdfViewerModel model =
                controller.getModel();

        double scrollPosition =
                scrollPane.getVvalue();

        double maxScroll =
                scrollPane.getVmax();

        if (maxScroll <= 0) {
            return;
        }

        double targetPosition =
                scrollPosition * maxScroll;

        double accumulatedHeight = 0;

        for (
                int i = 0;
                i < pageViews.size();
                i++
        ) {

            accumulatedHeight +=
                    pageViews
                            .get(i)
                            .getBoundsInParent()
                            .getHeight();

            if (
                    targetPosition <=
                    accumulatedHeight
            ) {

                if (
                        model.getCurrentPage() != i
                ) {

                    controller.setCurrentPage(i);
                    updateLabels();
                }

                return;
            }
        }
    }

    private void showNextPage() {

        if (controller.nextPage()) {
            scrollToCurrentPage();
            updateLabels();
        }
    }

    private void showPreviousPage() {

        if (controller.previousPage()) {
            scrollToCurrentPage();
            updateLabels();
        }
    }

    private void scrollToCurrentPage() {

        if (pageViews.isEmpty()) {
            return;
        }

        int pageIndex =
                controller
                        .getModel()
                        .getCurrentPage();

        if (
                pageIndex < 0 ||
                pageIndex >= pageViews.size()
        ) {
            return;
        }

        ImageView target =
                pageViews.get(pageIndex);

        double targetY =
                target
                        .getBoundsInParent()
                        .getMinY();

        double contentHeight =
                pageContainer
                        .getBoundsInLocal()
                        .getHeight();

        double viewportHeight =
                scrollPane
                        .getViewportBounds()
                        .getHeight();

        if (
                contentHeight <=
                viewportHeight
        ) {
            return;
        }

        double scrollableHeight =
                contentHeight -
                viewportHeight;

        scrollPane.setVvalue(
                targetY / scrollableHeight
        );
    }

    private void jumpToPage() {

        if (!controller.hasDocument()) {
            return;
        }

        try {

            int requestedPage =
                    Integer.parseInt(
                            pageField.getText()
                    );

            int target =
                    requestedPage - 1;

            int pageCount =
                    controller
                            .getModel()
                            .getPageCount();

            if (
                    target < 0 ||
                    target >= pageCount
            ) {
                updateLabels();
                return;
            }

            controller.setCurrentPage(target);

            scrollToCurrentPage();
            updateLabels();

        } catch (NumberFormatException e) {
            updateLabels();
        }
    }

    private void zoomIn() {

        if (!controller.hasDocument()) {
            return;
        }

        controller.zoomIn();
        renderAllPages();
    }

    private void zoomOut() {

        if (!controller.hasDocument()) {
            return;
        }

        controller.zoomOut();
        renderAllPages();
    }

    private void resetZoom() {

        if (!controller.hasDocument()) {
            return;
        }

        controller.resetZoom();
        renderAllPages();
    }

    private void fitWidth() {

        if (!controller.hasDocument()) {
            return;
        }

        double availableWidth =
                scrollPane
                        .getViewportBounds()
                        .getWidth();

        int pageIndex =
                controller
                        .getModel()
                        .getCurrentPage();

        double pageWidth =
                controller
                        .getDocument()
                        .getPage(pageIndex)
                        .getMediaBox()
                        .getWidth();

        if (availableWidth <= 0) {
            return;
        }

        controller.setZoom(
                availableWidth / pageWidth
        );

        controller.setFitWidth();

        renderAllPages();
    }

    private void fitPage() {

        if (!controller.hasDocument()) {
            return;
        }

        double availableWidth =
                scrollPane
                        .getViewportBounds()
                        .getWidth();

        double availableHeight =
                scrollPane
                        .getViewportBounds()
                        .getHeight();

        int pageIndex =
                controller
                        .getModel()
                        .getCurrentPage();

        double pageWidth =
                controller
                        .getDocument()
                        .getPage(pageIndex)
                        .getMediaBox()
                        .getWidth();

        double pageHeight =
                controller
                        .getDocument()
                        .getPage(pageIndex)
                        .getMediaBox()
                        .getHeight();

        double widthZoom =
                availableWidth / pageWidth;

        double heightZoom =
                availableHeight / pageHeight;

        controller.setZoom(
                Math.min(
                        widthZoom,
                        heightZoom
                )
        );

        controller.setFitPage();

        renderAllPages();
    }

    private void updateLabels() {

        if (!controller.hasDocument()) {
            return;
        }

        PdfViewerModel model =
                controller.getModel();

        int pageIndex =
                model.getCurrentPage();

        pageLabel.setText(
                "of " +
                model.getPageCount()
        );

        pageField.setText(
                String.valueOf(pageIndex + 1)
        );

        zoomLabel.setText(
                Math.round(
                        model.getZoom() * 100
                ) +
                "%"
        );

        previousButton.setDisable(
                pageIndex == 0
        );

        nextButton.setDisable(
                pageIndex ==
                        model.getPageCount() - 1
        );
    }

    private void setViewerControlsEnabled(
            boolean enabled
    ) {

        previousButton.setDisable(!enabled);
        nextButton.setDisable(!enabled);
        zoomOutButton.setDisable(!enabled);
        zoomInButton.setDisable(!enabled);
        fitWidthButton.setDisable(!enabled);
        fitPageButton.setDisable(!enabled);
        resetZoomButton.setDisable(!enabled);
        pageField.setDisable(!enabled);
    }

    private void closeCurrentDocument() {

        controller.closeDocument();

        pageViews.clear();

        if (pageContainer != null) {
            pageContainer
                    .getChildren()
                    .clear();
        }

        if (thumbnailContainer != null) {
            thumbnailContainer
                    .getChildren()
                    .clear();
        }

        if (pageLabel != null) {
            pageLabel.setText("of");
        }

        if (pageField != null) {
            pageField.clear();
        }

        if (zoomLabel != null) {
            zoomLabel.setText("100%");
        }

        if (previousButton != null) {
            setViewerControlsEnabled(false);
        }
    }

    @Override
    public void stop() {
        closeCurrentDocument();
    }

    public static void main(String[] args) {
        launch(args);
    }
}