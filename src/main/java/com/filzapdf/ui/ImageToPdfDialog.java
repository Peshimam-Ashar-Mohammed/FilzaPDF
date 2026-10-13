package com.filzapdf.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfCreatorService;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Studio dialog for creating a new multi-page PDF by uploading one or more images
 * (PNG, JPG, BMP, GIF), or appending uploaded images as pages into the currently open PDF.
 */
public class ImageToPdfDialog {

    private final Stage dialogStage;
    private final PdfCreatorService creatorService = new PdfCreatorService();
    private final Consumer<PDDocument> onOpenCreatedPdf;
    private final PDDocument activeOpenDocument;
    private final Runnable onActiveDocumentPagesAppended;

    private final ObservableList<File> imageFiles = FXCollections.observableArrayList();
    private ListView<File> listView;
    private ComboBox<String> pageSizeCombo;
    private CheckBox landscapeCheck;
    private Spinner<Integer> marginSpinner;
    private ImageView previewView;
    private Label previewInfoLabel;
    private Label countBadgeLabel;

    public ImageToPdfDialog(Stage owner, Consumer<PDDocument> onOpenCreatedPdf) {
        this(owner, null, onOpenCreatedPdf, null);
    }

    public ImageToPdfDialog(
            Stage owner,
            PDDocument activeOpenDocument,
            Consumer<PDDocument> onOpenCreatedPdf,
            Runnable onActiveDocumentPagesAppended) {
        this.activeOpenDocument = activeOpenDocument;
        this.onOpenCreatedPdf = onOpenCreatedPdf;
        this.onActiveDocumentPagesAppended = onActiveDocumentPagesAppended;

        this.dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.setTitle("Images to PDF — FilzaPDF");

        BorderPane root = buildUi();
        Scene scene = new Scene(root, 840, 560);
        if (owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        installDragDrop(scene);
        dialogStage.setScene(scene);
    }

    public void show() {
        dialogStage.show();
        dialogStage.toFront();
    }

    private BorderPane buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));

        Label title = new Label("Images to PDF");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 700;");
        Label sub = new Label(
                "Add or drag & drop images (PNG, JPG, JPEG, BMP, GIF). Each image is rendered onto a vector PDF page.");
        sub.setStyle("-fx-opacity: 0.72; -fx-font-size: 12px;");
        VBox top = new VBox(4, title, sub, new Separator());
        top.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(top);

        listView = new ListView<>(imageFiles);
        listView.setPlaceholder(new Label("No images queued.\nClick 'Add Images' or drag & drop image files into this window."));
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("Page %d   ·   %s   (%.1f KB)",
                            getIndex() + 1, item.getName(), item.length() / 1024.0));
                }
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            updateImagePreview(newV);
        });

        imageFiles.addListener((ListChangeListener<File>) c -> {
            countBadgeLabel.setText(imageFiles.size() + " page(s)");
            if (!imageFiles.isEmpty() && listView.getSelectionModel().getSelectedItem() == null) {
                listView.getSelectionModel().select(0);
            }
        });

        Button addBtn = new Button("Add Images…");
        addBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 13));
        addBtn.getStyleClass().add("accent-button");
        addBtn.setOnAction(e -> chooseImages());

        Button upBtn = new Button("Move Up");
        upBtn.getStyleClass().add("surface-button");
        upBtn.setOnAction(e -> {
            int i = listView.getSelectionModel().getSelectedIndex();
            if (i > 0) {
                Collections.swap(imageFiles, i, i - 1);
                listView.getSelectionModel().select(i - 1);
            }
        });

        Button downBtn = new Button("Move Down");
        downBtn.getStyleClass().add("surface-button");
        downBtn.setOnAction(e -> {
            int i = listView.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < imageFiles.size() - 1) {
                Collections.swap(imageFiles, i, i + 1);
                listView.getSelectionModel().select(i + 1);
            }
        });

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("surface-button");
        removeBtn.setOnAction(e -> {
            int i = listView.getSelectionModel().getSelectedIndex();
            if (i >= 0) imageFiles.remove(i);
        });

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("surface-button");
        clearBtn.setOnAction(e -> imageFiles.clear());

        countBadgeLabel = new Label("0 page(s)");
        countBadgeLabel.getStyleClass().add("badge-pill");

        HBox listControls = new HBox(8, addBtn, upBtn, downBtn, removeBtn, clearBtn);
        listControls.setAlignment(Pos.CENTER_LEFT);

        // Layout options
        pageSizeCombo = new ComboBox<>(FXCollections.observableArrayList("A4", "LETTER", "FIT_IMAGE"));
        pageSizeCombo.getSelectionModel().select("A4");

        landscapeCheck = new CheckBox("Landscape");
        marginSpinner = new Spinner<>(0, 100, 20, 4);
        marginSpinner.setPrefWidth(74);

        HBox optionsBar = new HBox(12,
                new Label("Page Size"), pageSizeCombo,
                landscapeCheck,
                new Label("Margin (pt)"), marginSpinner
        );
        optionsBar.setAlignment(Pos.CENTER_LEFT);

        Label queueLbl = new Label("IMAGE QUEUE");
        queueLbl.getStyleClass().add("section-caption");

        HBox headerRow = new HBox(10, queueLbl, new Region(), countBadgeLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerRow.getChildren().get(1), Priority.ALWAYS);

        VBox centerBox = new VBox(8,
                headerRow,
                listView,
                listControls,
                new Separator(),
                optionsBar
        );
        HBox.setHgrow(centerBox, Priority.ALWAYS);

        // Right preview
        previewView = new ImageView();
        previewView.setPreserveRatio(true);
        previewView.setFitWidth(220);
        previewView.setFitHeight(290);
        previewInfoLabel = new Label("Select an image to preview");
        previewInfoLabel.setStyle("-fx-opacity: 0.68; -fx-font-size: 11.5px;");
        previewInfoLabel.setWrapText(true);

        Label previewHeader = new Label("PREVIEW");
        previewHeader.getStyleClass().add("section-caption");

        VBox rightBox = new VBox(10, previewHeader, previewView, previewInfoLabel);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPrefWidth(245);
        rightBox.getStyleClass().add("card-pane");

        root.setCenter(new HBox(14, centerBox, rightBox));

        // Bottom actions
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("Create & Open in Workspace");
        openBtn.setGraphic(SvgIcons.icon(SvgIcons.CHECK, 13));
        openBtn.getStyleClass().add("accent-button");
        openBtn.setOnAction(e -> buildPdf(false));

        Button appendToCurrentBtn = new Button("Append to Active PDF");
        appendToCurrentBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 13));
        appendToCurrentBtn.getStyleClass().add("surface-button");
        appendToCurrentBtn.setDisable(activeOpenDocument == null);
        appendToCurrentBtn.setOnAction(e -> appendToCurrentPdf());

        Button saveBtn = new Button("Save PDF As…");
        saveBtn.setGraphic(SvgIcons.icon(SvgIcons.SAVE, 13));
        saveBtn.getStyleClass().add("surface-button");
        saveBtn.setOnAction(e -> buildPdf(true));

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("surface-button");
        cancelBtn.setOnAction(e -> dialogStage.close());

        HBox bottom = new HBox(10, spacer, appendToCurrentBtn, saveBtn, openBtn, cancelBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(14, 0, 0, 0));
        root.setBottom(bottom);

        return root;
    }

    private void installDragDrop(Scene scene) {
        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        scene.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                for (File f : e.getDragboard().getFiles()) {
                    if (isSupportedImageFile(f)) {
                        imageFiles.add(f);
                    }
                }
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    public static boolean isSupportedImageFile(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    private void chooseImages() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Images for PDF Pages");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Image Files (*.png, *.jpg, *.jpeg, *.bmp, *.gif)",
                        "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("PNG Images (*.png)", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Images (*.jpg, *.jpeg)", "*.jpg", "*.jpeg")
        );
        List<File> files = fc.showOpenMultipleDialog(dialogStage);
        if (files != null && !files.isEmpty()) {
            imageFiles.addAll(files);
            listView.getSelectionModel().select(imageFiles.size() - files.size());
        }
    }

    private void updateImagePreview(File file) {
        if (file == null || !file.exists()) {
            previewView.setImage(null);
            previewInfoLabel.setText("Select an image to preview");
            return;
        }
        try {
            Image img = new Image(file.toURI().toString(), 220, 290, true, true);
            previewView.setImage(img);
            previewInfoLabel.setText(file.getName());
        } catch (Exception e) {
            previewInfoLabel.setText("Cannot preview image");
        }
    }

    private void appendToCurrentPdf() {
        if (activeOpenDocument == null) return;
        if (imageFiles.isEmpty()) {
            chooseImages();
            if (imageFiles.isEmpty()) return;
        }
        try {
            int added = creatorService.appendImagesAsPages(
                    activeOpenDocument,
                    new ArrayList<>(imageFiles),
                    pageSizeCombo.getValue(),
                    landscapeCheck.isSelected(),
                    marginSpinner.getValue()
            );
            if (added > 0 && onActiveDocumentPagesAppended != null) {
                onActiveDocumentPagesAppended.run();
            }
            dialogStage.close();
        } catch (Exception e) {
            Alert alert = new Alert(AlertType.ERROR, "Failed to append image pages: " + e.getMessage());
            alert.initOwner(dialogStage);
            alert.showAndWait();
        }
    }

    private void buildPdf(boolean saveToDisk) {
        if (imageFiles.isEmpty()) {
            chooseImages();
            if (imageFiles.isEmpty()) return;
        }
        try {
            PDDocument doc = creatorService.createPdfFromImages(
                    new ArrayList<>(imageFiles),
                    pageSizeCombo.getValue(),
                    landscapeCheck.isSelected(),
                    marginSpinner.getValue()
            );
            if (saveToDisk) {
                FileChooser saver = new FileChooser();
                saver.setTitle("Save Created PDF");
                saver.setInitialFileName("Images_Document.pdf");
                saver.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
                File out = saver.showSaveDialog(dialogStage);
                if (out != null) {
                    doc.save(out);
                } else {
                    doc.close();
                    return;
                }
            }
            if (onOpenCreatedPdf != null) {
                onOpenCreatedPdf.accept(doc);
            } else {
                doc.close();
            }
            dialogStage.close();
        } catch (Exception e) {
            Alert alert = new Alert(AlertType.ERROR, "Failed to create PDF from images: " + e.getMessage());
            alert.initOwner(dialogStage);
            alert.showAndWait();
        }
    }
}
