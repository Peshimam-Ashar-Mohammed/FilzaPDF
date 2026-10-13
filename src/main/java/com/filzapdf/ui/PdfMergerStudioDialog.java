package com.filzapdf.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.filzapdf.pdf.PdfToolboxService;
import com.filzapdf.pdf.PdfToolboxService.PageSource;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Interactive PDF Merger & Custom Page Composer Studio.
 * Allows merging 2 or more PDFs in any custom page order:
 * - All from Doc A then all from Doc B
 * - Alternating / Interleaved (e.g. 1 page from A, 2 pages from B)
 * - Custom Recipe string (e.g. "A:1, B:1-2, A:2-end")
 * - Interactive page-by-page queue with reorder and live page thumbnail preview.
 */
public class PdfMergerStudioDialog {

    private final Stage dialogStage;
    private final PdfToolboxService toolboxService = new PdfToolboxService();
    private final Consumer<PDDocument> onOpenMergedDocument;

    private final List<File> sourceFiles = new ArrayList<>();
    private final List<Integer> pageCounts = new ArrayList<>();
    private final ObservableList<String> sourceFileLabels = FXCollections.observableArrayList();
    private final ObservableList<PageSource> outputQueue = FXCollections.observableArrayList();

    private ComboBox<String> pickerDocCombo;
    private TextField pickerRangeField;
    private TextField recipeField;
    private Spinner<Integer> chunkASpinner;
    private Spinner<Integer> chunkBSpinner;
    private Label queueSummaryLabel;
    private ImageView previewImageView;
    private Label previewCaptionLabel;

    public PdfMergerStudioDialog(Stage owner, File initialFileIfAny, Consumer<PDDocument> onOpenMergedDocument) {
        this.onOpenMergedDocument = onOpenMergedDocument;
        this.dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.NONE);
        dialogStage.setTitle("Page Composer & Merger — FilzaPDF");

        if (initialFileIfAny != null && initialFileIfAny.exists()) {
            addSourcePdfFile(initialFileIfAny);
        }

        BorderPane root = buildUi();
        Scene scene = new Scene(root, 980, 660);
        if (owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialogStage.setScene(scene);
    }

    public void show() {
        dialogStage.show();
        dialogStage.toFront();
    }

    private BorderPane buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));

        // Header
        Label title = new Label("Page Composer & Merger");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 700;");
        Label subtitle = new Label(
                "Assemble pages from multiple PDFs in any sequence: sequential, alternating chunks, custom recipe strings, or manual drag-and-reorder.");
        subtitle.setStyle("-fx-opacity: 0.72; -fx-font-size: 12px;");
        VBox header = new VBox(4, title, subtitle, new Separator());
        header.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(header);

        // Left: Source PDFs + Quick Merge Presets + Recipe
        VBox leftPane = buildLeftSourcePane();
        leftPane.setPrefWidth(430);

        // Center: Output Page Sequence Queue
        VBox centerPane = buildCenterQueuePane();

        // Right: Page Thumbnail Preview
        VBox rightPane = buildRightPreviewPane();
        rightPane.setPrefWidth(215);

        HBox mainContent = new HBox(14, leftPane, centerPane, rightPane);
        HBox.setHgrow(centerPane, Priority.ALWAYS);
        root.setCenter(mainContent);

        // Bottom Action Bar
        root.setBottom(buildBottomActionBar());
        return root;
    }

    private VBox buildLeftSourcePane() {
        Label srcHeader = new Label("1. SOURCE DOCUMENTS");
        srcHeader.getStyleClass().add("section-caption");

        ListView<String> srcListView = new ListView<>(sourceFileLabels);
        srcListView.setPrefHeight(130);

        Button addFilesBtn = new Button("Add PDF Files…");
        addFilesBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 13));
        addFilesBtn.getStyleClass().add("accent-button");
        addFilesBtn.setOnAction(e -> promptAddPdfFiles());

        Button removeFileBtn = new Button("Remove Selected");
        removeFileBtn.getStyleClass().add("surface-button");
        removeFileBtn.setOnAction(e -> {
            int idx = srcListView.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < sourceFiles.size()) {
                sourceFiles.remove(idx);
                pageCounts.remove(idx);
                refreshSourceLabels();
                rebuildDefaultSequentialQueue();
            }
        });

        HBox srcButtons = new HBox(8, addFilesBtn, removeFileBtn);

        // Quick Merge Modes
        Label modeHeader = new Label("2. SEQUENCE PRESETS");
        modeHeader.getStyleClass().add("section-caption");

        Button allThenAllBtn = new Button("Sequential (All of Doc A, then All of Doc B)");
        allThenAllBtn.getStyleClass().add("surface-button");
        allThenAllBtn.setMaxWidth(Double.MAX_VALUE);
        allThenAllBtn.setOnAction(e -> rebuildDefaultSequentialQueue());

        chunkASpinner = new Spinner<>(1, 50, 1);
        chunkASpinner.setPrefWidth(64);
        chunkBSpinner = new Spinner<>(1, 50, 2);
        chunkBSpinner.setPrefWidth(64);

        Button interleaveBtn = new Button("Apply Interleave");
        interleaveBtn.getStyleClass().add("surface-button");
        interleaveBtn.setOnAction(e -> applyInterleavePreset());

        HBox interleaveRow = new HBox(6,
                new Label("Take"), chunkASpinner, new Label("from A, then"),
                chunkBSpinner, new Label("from B"), interleaveBtn);
        interleaveRow.setAlignment(Pos.CENTER_LEFT);

        // Custom Recipe Bar
        Label recipeHeader = new Label("3. CUSTOM PAGE RECIPE");
        recipeHeader.getStyleClass().add("section-caption");

        recipeField = new TextField("A:1, B:1-2, A:2-end");
        recipeField.setPromptText("Example: A:1, B:1-2, A:2-end");
        HBox.setHgrow(recipeField, Priority.ALWAYS);

        Button applyRecipeBtn = new Button("Apply Recipe");
        applyRecipeBtn.getStyleClass().add("surface-button");
        applyRecipeBtn.setOnAction(e -> applyRecipeFromField());

        HBox recipeRow = new HBox(6, recipeField, applyRecipeBtn);

        // Interactive Page Picker
        Label pickerHeader = new Label("4. APPEND SPECIFIC RANGE");
        pickerHeader.getStyleClass().add("section-caption");

        pickerDocCombo = new ComboBox<>();
        pickerDocCombo.setPrefWidth(135);
        pickerRangeField = new TextField("1");
        pickerRangeField.setPrefWidth(95);
        pickerRangeField.setPromptText("e.g. 1 or 1-3 or all");

        Button appendPagesBtn = new Button("Append");
        appendPagesBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 12));
        appendPagesBtn.getStyleClass().add("surface-button");
        appendPagesBtn.setOnAction(e -> appendPagesFromPicker());

        HBox pickerRow = new HBox(6, pickerDocCombo, new Label("Pages"), pickerRangeField, appendPagesBtn);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        refreshSourceLabels();

        VBox box = new VBox(10,
                srcHeader, srcListView, srcButtons,
                new Separator(),
                modeHeader, allThenAllBtn, interleaveRow,
                new Separator(),
                recipeHeader, recipeRow,
                new Separator(),
                pickerHeader, pickerRow
        );
        return box;
    }

    private VBox buildCenterQueuePane() {
        Label qHeader = new Label("OUTPUT PAGE QUEUE");
        qHeader.getStyleClass().add("section-caption");

        ListView<PageSource> queueListView = new ListView<>(outputQueue);
        queueListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PageSource item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%02d   ·   %s", getIndex() + 1, item.getDisplayLabel()));
                }
            }
        });
        VBox.setVgrow(queueListView, Priority.ALWAYS);

        queueListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            renderPreview(newV);
        });

        Button moveUpBtn = new Button("Move Up");
        moveUpBtn.getStyleClass().add("surface-button");
        moveUpBtn.setOnAction(e -> {
            int idx = queueListView.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                Collections.swap(outputQueue, idx, idx - 1);
                queueListView.getSelectionModel().select(idx - 1);
            }
        });

        Button moveDownBtn = new Button("Move Down");
        moveDownBtn.getStyleClass().add("surface-button");
        moveDownBtn.setOnAction(e -> {
            int idx = queueListView.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < outputQueue.size() - 1) {
                Collections.swap(outputQueue, idx, idx + 1);
                queueListView.getSelectionModel().select(idx + 1);
            }
        });

        Button removePageBtn = new Button("Remove");
        removePageBtn.getStyleClass().add("surface-button");
        removePageBtn.setOnAction(e -> {
            int idx = queueListView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                outputQueue.remove(idx);
                updateQueueSummary();
            }
        });

        Button clearQueueBtn = new Button("Clear");
        clearQueueBtn.getStyleClass().add("surface-button");
        clearQueueBtn.setOnAction(e -> {
            outputQueue.clear();
            updateQueueSummary();
        });

        queueSummaryLabel = new Label("Total pages in merged PDF: 0");
        queueSummaryLabel.setStyle("-fx-opacity: 0.75; -fx-font-size: 11.5px;");

        HBox queueBtns = new HBox(6, moveUpBtn, moveDownBtn, removePageBtn, clearQueueBtn);
        return new VBox(8, qHeader, queueListView, queueBtns, queueSummaryLabel);
    }

    private VBox buildRightPreviewPane() {
        Label pHeader = new Label("PAGE PREVIEW");
        pHeader.getStyleClass().add("section-caption");

        previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);
        previewImageView.setFitWidth(185);
        previewImageView.setFitHeight(260);

        previewCaptionLabel = new Label("Select any page in the queue to preview.");
        previewCaptionLabel.setStyle("-fx-opacity: 0.68; -fx-font-size: 11.5px;");
        previewCaptionLabel.setWrapText(true);

        VBox box = new VBox(10, pHeader, previewImageView, previewCaptionLabel);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("card-pane");
        return box;
    }

    private HBox buildBottomActionBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openInViewerBtn = new Button("Merge & Open in Workspace");
        openInViewerBtn.setGraphic(SvgIcons.icon(SvgIcons.CHECK, 13));
        openInViewerBtn.getStyleClass().add("accent-button");
        openInViewerBtn.setOnAction(e -> executeMerge(false));

        Button saveAsFileBtn = new Button("Save Merged PDF As…");
        saveAsFileBtn.setGraphic(SvgIcons.icon(SvgIcons.SAVE, 13));
        saveAsFileBtn.getStyleClass().add("surface-button");
        saveAsFileBtn.setOnAction(e -> executeMerge(true));

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("surface-button");
        closeBtn.setOnAction(e -> dialogStage.close());

        HBox bar = new HBox(10, spacer, saveAsFileBtn, openInViewerBtn, closeBtn);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(14, 0, 0, 0));
        return bar;
    }

    private void promptAddPdfFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PDF File(s) to Merge");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        List<File> selected = chooser.showOpenMultipleDialog(dialogStage);
        if (selected != null && !selected.isEmpty()) {
            for (File f : selected) {
                addSourcePdfFile(f);
            }
            refreshSourceLabels();
            rebuildDefaultSequentialQueue();
        }
    }

    private void addSourcePdfFile(File file) {
        try (PDDocument doc = Loader.loadPDF(file)) {
            sourceFiles.add(file);
            pageCounts.add(doc.getNumberOfPages());
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Cannot Open PDF", "Failed to read " + file.getName() + ": " + e.getMessage());
        }
    }

    private void refreshSourceLabels() {
        sourceFileLabels.clear();
        if (pickerDocCombo != null) pickerDocCombo.getItems().clear();
        for (int i = 0; i < sourceFiles.size(); i++) {
            String letter = PageSource.docLetter(i);
            String label = String.format("Doc %s: %s (%d pages)", letter, sourceFiles.get(i).getName(), pageCounts.get(i));
            sourceFileLabels.add(label);
            if (pickerDocCombo != null) {
                pickerDocCombo.getItems().add("Doc " + letter);
            }
        }
        if (pickerDocCombo != null && !pickerDocCombo.getItems().isEmpty()) {
            pickerDocCombo.getSelectionModel().select(0);
        }
    }

    private void rebuildDefaultSequentialQueue() {
        outputQueue.setAll(toolboxService.buildSequentialAllSequence(sourceFiles, pageCounts));
        updateQueueSummary();
    }

    private void applyInterleavePreset() {
        if (sourceFiles.isEmpty()) return;
        List<Integer> chunks = new ArrayList<>();
        chunks.add(chunkASpinner.getValue());
        chunks.add(chunkBSpinner.getValue());
        for (int i = 2; i < sourceFiles.size(); i++) {
            chunks.add(1);
        }
        outputQueue.setAll(toolboxService.buildInterleavedSequence(sourceFiles, pageCounts, chunks));
        updateQueueSummary();
    }

    private void applyRecipeFromField() {
        if (sourceFiles.isEmpty()) {
            showAlert(AlertType.WARNING, "No PDFs Loaded", "Please add at least one PDF file first.");
            return;
        }
        try {
            List<PageSource> parsed = toolboxService.parseMergeRecipe(recipeField.getText(), sourceFiles, pageCounts);
            outputQueue.setAll(parsed);
            updateQueueSummary();
        } catch (Exception ex) {
            showAlert(AlertType.ERROR, "Invalid Recipe", ex.getMessage());
        }
    }

    private void appendPagesFromPicker() {
        int docIdx = pickerDocCombo.getSelectionModel().getSelectedIndex();
        if (docIdx < 0 || docIdx >= sourceFiles.size()) return;
        String range = pickerRangeField.getText().trim();
        if (range.isEmpty()) range = "1";
        String token = PageSource.docLetter(docIdx) + ":" + range;
        try {
            List<PageSource> parsed = toolboxService.parseMergeRecipe(token, sourceFiles, pageCounts);
            outputQueue.addAll(parsed);
            updateQueueSummary();
        } catch (Exception ex) {
            showAlert(AlertType.ERROR, "Invalid Page Range", ex.getMessage());
        }
    }

    private void updateQueueSummary() {
        if (queueSummaryLabel != null) {
            queueSummaryLabel.setText("Total pages in merged PDF: " + outputQueue.size());
        }
    }

    private void renderPreview(PageSource ps) {
        if (ps == null || ps.getSourceFile() == null) {
            previewImageView.setImage(null);
            previewCaptionLabel.setText("Click any page in the queue to preview it.");
            return;
        }
        try (PDDocument doc = Loader.loadPDF(ps.getSourceFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(ps.getPageIndex(), 55f);
            previewImageView.setImage(SwingFXUtils.toFXImage(img, null));
            previewCaptionLabel.setText(ps.getDisplayLabel());
        } catch (Exception e) {
            previewCaptionLabel.setText("Preview unavailable");
        }
    }

    private void executeMerge(boolean saveToFile) {
        if (outputQueue.isEmpty()) {
            showAlert(AlertType.WARNING, "Queue is Empty", "Please add pages to the merge queue first.");
            return;
        }
        try {
            PDDocument merged = toolboxService.mergeCustomPages(sourceFiles, new ArrayList<>(outputQueue));
            if (saveToFile) {
                FileChooser saver = new FileChooser();
                saver.setTitle("Save Merged PDF");
                saver.setInitialFileName("Merged_Document.pdf");
                saver.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                File out = saver.showSaveDialog(dialogStage);
                if (out != null) {
                    merged.save(out);
                } else {
                    merged.close();
                    return;
                }
            }
            if (onOpenMergedDocument != null) {
                onOpenMergedDocument.accept(merged);
            } else {
                merged.close();
            }
            dialogStage.close();
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Merge Failed", "Could not merge PDFs: " + e.getMessage());
        }
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(dialogStage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
