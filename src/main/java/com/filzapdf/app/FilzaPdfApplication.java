package com.filzapdf.app;

import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.renderer.PdfPageRenderer;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.image.BufferedImage;
import java.io.File;

public class FilzaPdfApplication extends Application {

    private final PdfDocumentService pdfService =
            new PdfDocumentService();

    @Override
    public void start(Stage stage) {

        Button openButton = new Button("Open PDF");

        Label status = new Label("No PDF opened");

        ImageView pageView = new ImageView();

        pageView.setPreserveRatio(true);
        pageView.setFitWidth(700);

        StackPane viewer = new StackPane(pageView);

        BorderPane root = new BorderPane();

        root.setTop(openButton);
        root.setCenter(viewer);
        root.setBottom(status);

        openButton.setOnAction(event -> {

            FileChooser fileChooser = new FileChooser();

            fileChooser.setTitle("Open PDF");

            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "PDF files",
                            "*.pdf"
                    )
            );

            File file = fileChooser.showOpenDialog(stage);

            if (file == null) {
                return;
            }

            try (PDDocument document =
                         pdfService.open(file)) {

                int pages =
                        document.getNumberOfPages();

                PdfPageRenderer renderer =
                        new PdfPageRenderer(document);

                BufferedImage renderedPage =
                        renderer.renderPage(0);

                Image image =
                        SwingFXUtils.toFXImage(
                                renderedPage,
                                null
                        );

                pageView.setImage(image);

                status.setText(
                        "Opened: " +
                        file.getName() +
                        " | Pages: " +
                        pages
                );

            } catch (Exception e) {

                status.setText(
                        "Could not open PDF"
                );

                e.printStackTrace();
            }
        });

        Scene scene =
                new Scene(root, 1000, 700);

        stage.setTitle("FilzaPDF");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}