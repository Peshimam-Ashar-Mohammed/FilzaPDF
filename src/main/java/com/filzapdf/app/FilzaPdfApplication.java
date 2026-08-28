package com.filzapdf.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.renderer.PdfPageRenderer;
import com.filzapdf.ui.PdfViewerModel;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FilzaPdfApplication extends Application {

    private final PdfDocumentService pdfDocumentService =
            new PdfDocumentService();

    private PDDocument document;
    private PdfPageRenderer renderer;
    private PdfViewerModel viewerModel;

    private ImageView imageView;
    private Label pageLabel;
    private Label zoomLabel;
    private Button previousButton;
    private Button nextButton;
    private Button zoomOutButton;
    private Button zoomInButton;

    @Override
    public void start(Stage stage) {

        Button openButton = new Button("Open PDF");

        previousButton = new Button("Previous");
        nextButton = new Button("Next");

        zoomOutButton = new Button("-");
        zoomInButton = new Button("+");

        zoomLabel = new Label("100%");
        pageLabel = new Label("No PDF opened");

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);

        openButton.setOnAction(event -> openPdf(stage));
        previousButton.setOnAction(event -> showPreviousPage());
        nextButton.setOnAction(event -> showNextPage());

        zoomOutButton.setOnAction(event -> zoomOut());
        zoomInButton.setOnAction(event -> zoomIn());
        zoomLabel.setOnMouseClicked(event -> resetZoom());

        previousButton.setDisable(true);
        nextButton.setDisable(true);
        zoomOutButton.setDisable(true);
        zoomInButton.setDisable(true);

        HBox toolbar = new HBox(
                10,
                openButton,
                previousButton,
                nextButton,
                pageLabel,
                zoomOutButton,
                zoomLabel,
                zoomInButton
        );

        toolbar.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();

        root.setTop(toolbar);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 900, 700);

        stage.setTitle("FilzaPDF");
        stage.setScene(scene);
        stage.show();
    }

    private void openPdf(Stage stage) {

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Open PDF");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "PDF Files",
                        "*.pdf"
                )
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        try {
            closeCurrentDocument();

            document = pdfDocumentService.open(file);

            renderer = new PdfPageRenderer(document);

            viewerModel =
                    new PdfViewerModel(document.getNumberOfPages());

            zoomOutButton.setDisable(false);
            zoomInButton.setDisable(false);

            showCurrentPage();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void showCurrentPage() {

        try {

            int pageIndex =
                    viewerModel.getCurrentPage();

            float dpi =
                    (float) (120 * viewerModel.getZoom());

            BufferedImage renderedImage =
                    renderer.renderPage(pageIndex, dpi);

            imageView.setImage(
                    SwingFXUtils.toFXImage(
                            renderedImage,
                            null
                    )
            );

            pageLabel.setText(
                    "Page " +
                    (pageIndex + 1) +
                    " of " +
                    viewerModel.getPageCount()
            );

            zoomLabel.setText(
                    Math.round(
                            viewerModel.getZoom() * 100
                    ) + "%"
            );

            previousButton.setDisable(
                    pageIndex == 0
            );

            nextButton.setDisable(
                    pageIndex ==
                    viewerModel.getPageCount() - 1
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showNextPage() {

        if (viewerModel == null) {
            return;
        }

        if (viewerModel.nextPage()) {
            showCurrentPage();
        }
    }

    private void showPreviousPage() {

        if (viewerModel == null) {
            return;
        }

        if (viewerModel.previousPage()) {
            showCurrentPage();
        }
    }

    private void zoomIn() {

        if (viewerModel == null) {
            return;
        }

        viewerModel.zoomIn();
        showCurrentPage();
    }

    private void zoomOut() {

        if (viewerModel == null) {
            return;
        }

        viewerModel.zoomOut();
        showCurrentPage();
    }

    private void resetZoom() {

        if (viewerModel == null) {
            return;
        }

        viewerModel.resetZoom();
        showCurrentPage();
    }

    private void closeCurrentDocument() {

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
        viewerModel = null;
    }

    @Override
    public void stop() {
        closeCurrentDocument();
    }

    public static void main(String[] args) {
        launch(args);
    }
}