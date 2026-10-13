package com.filzapdf.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfCreatorService;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock.Alignment;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock.BlockType;
import com.filzapdf.pdf.WordDocumentService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Word-like Document Creator & .docx Word Editor Studio.
 * Allows typing new rich documents from scratch or opening existing Microsoft Word (.docx)
 * files, editing paragraphs/headings/fonts/colors/images, saving back to .docx, or exporting
 * directly to a multi-page vector PDF.
 */
public class WordStudioDialog {

    private final Stage dialogStage;
    private final WordDocumentService wordService = new WordDocumentService();
    private final PdfCreatorService pdfCreatorService = new PdfCreatorService();
    private final Consumer<PDDocument> onOpenGeneratedPdf;

    private final ObservableList<DocumentBlock> blocks = FXCollections.observableArrayList();
    private ListView<DocumentBlock> blockListView;
    private TextArea blockTextArea;
    private ComboBox<BlockType> blockTypeCombo;
    private ComboBox<String> fontFamilyCombo;
    private Spinner<Integer> fontSizeSpinner;
    private ToggleButton boldToggle;
    private ToggleButton italicToggle;
    private ToggleButton underlineToggle;
    private ColorPicker colorPicker;
    private ComboBox<Alignment> alignmentCombo;
    private Label currentFileLabel;

    private File openedWordFile;
    private boolean updatingControls = false;

    public WordStudioDialog(Stage owner, File initialWordFile, Consumer<PDDocument> onOpenGeneratedPdf) {
        this.onOpenGeneratedPdf = onOpenGeneratedPdf;
        this.dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.setTitle("Word & Document Studio — FilzaPDF");

        BorderPane root = buildUi();
        Scene scene = new Scene(root, 1020, 680);
        if (owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialogStage.setScene(scene);

        if (initialWordFile != null && initialWordFile.exists()) {
            loadWordFile(initialWordFile);
        } else {
            initStarterDocument();
        }
    }

    public void show() {
        dialogStage.show();
        dialogStage.toFront();
    }

    private void initStarterDocument() {
        blocks.clear();
        DocumentBlock h1 = new DocumentBlock(BlockType.HEADING_1, "Untitled Document");
        DocumentBlock p1 = new DocumentBlock(BlockType.PARAGRAPH,
                "Compose rich text paragraphs, headings, lists, and figures, or open an existing Microsoft Word (.docx) document and export directly to vector PDF.");
        blocks.addAll(h1, p1);
        blockListView.getSelectionModel().select(0);
    }

    private BorderPane buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        // Top Ribbon / Toolbar
        VBox topSection = new VBox(8, buildFileBar(), buildFormattingToolbar(), new Separator());
        topSection.setPadding(new Insets(0, 0, 8, 0));
        root.setTop(topSection);

        // Left: Document Outline / Blocks list
        blockListView = new ListView<>(blocks);
        blockListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentBlock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String prefix = switch (item.getType()) {
                        case HEADING_1 -> "H1 · ";
                        case HEADING_2 -> "H2 · ";
                        case BULLET_ITEM -> "List · ";
                        case IMAGE -> "Image · ";
                        case PAGE_BREAK -> "── Page Break ──";
                        case DIVIDER -> "── Divider ──";
                        case PARAGRAPH -> "¶ · ";
                    };
                    String snippet = item.getText() != null ? item.getText().replace("\n", " ").trim() : "";
                    if (snippet.length() > 38) snippet = snippet.substring(0, 38) + "…";
                    if (item.getType() == BlockType.IMAGE && item.getImageFile() != null) {
                        snippet = item.getImageFile().getName();
                    }
                    setText(prefix + snippet);
                }
            }
        });
        VBox.setVgrow(blockListView, Priority.ALWAYS);

        blockListView.getSelectionModel().selectedItemProperty().addListener((obs, oldB, newB) -> {
            populateEditorFromBlock(newB);
        });

        Button addParaBtn = new Button("Paragraph");
        addParaBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 11));
        addParaBtn.getStyleClass().add("surface-button");
        addParaBtn.setOnAction(e -> addNewBlock(new DocumentBlock(BlockType.PARAGRAPH, "New paragraph text...")));

        Button addH1Btn = new Button("Heading 1");
        addH1Btn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 11));
        addH1Btn.getStyleClass().add("surface-button");
        addH1Btn.setOnAction(e -> addNewBlock(new DocumentBlock(BlockType.HEADING_1, "Section Heading")));

        Button addBulletBtn = new Button("Bullet");
        addBulletBtn.setGraphic(SvgIcons.icon(SvgIcons.PLUS, 11));
        addBulletBtn.getStyleClass().add("surface-button");
        addBulletBtn.setOnAction(e -> addNewBlock(new DocumentBlock(BlockType.BULLET_ITEM, "List item")));

        Button addImageBtn = new Button("Image");
        addImageBtn.setGraphic(SvgIcons.icon(SvgIcons.IMAGE, 11));
        addImageBtn.getStyleClass().add("surface-button");
        addImageBtn.setOnAction(e -> insertImageBlock());

        Button addBreakBtn = new Button("Page Break");
        addBreakBtn.getStyleClass().add("surface-button");
        addBreakBtn.setOnAction(e -> addNewBlock(new DocumentBlock(BlockType.PAGE_BREAK, "")));

        Button addDividerBtn = new Button("Divider");
        addDividerBtn.getStyleClass().add("surface-button");
        addDividerBtn.setOnAction(e -> addNewBlock(new DocumentBlock(BlockType.DIVIDER, "")));

        HBox addRow1 = new HBox(6, addParaBtn, addH1Btn, addBulletBtn);
        HBox addRow2 = new HBox(6, addImageBtn, addDividerBtn, addBreakBtn);

        Button upBtn = new Button("Move Up");
        upBtn.getStyleClass().add("surface-button");
        upBtn.setOnAction(e -> {
            int i = blockListView.getSelectionModel().getSelectedIndex();
            if (i > 0) {
                Collections.swap(blocks, i, i - 1);
                blockListView.getSelectionModel().select(i - 1);
            }
        });

        Button downBtn = new Button("Move Down");
        downBtn.getStyleClass().add("surface-button");
        downBtn.setOnAction(e -> {
            int i = blockListView.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < blocks.size() - 1) {
                Collections.swap(blocks, i, i + 1);
                blockListView.getSelectionModel().select(i + 1);
            }
        });

        Button delBtn = new Button("Delete Block");
        delBtn.getStyleClass().add("surface-button");
        delBtn.setOnAction(e -> {
            int i = blockListView.getSelectionModel().getSelectedIndex();
            if (i >= 0 && blocks.size() > 1) {
                blocks.remove(i);
            }
        });

        HBox orderRow = new HBox(6, upBtn, downBtn, delBtn);

        Label outlineLbl = new Label("DOCUMENT STRUCTURE");
        outlineLbl.getStyleClass().add("section-caption");

        VBox leftPane = new VBox(8,
                outlineLbl,
                blockListView,
                addRow1,
                addRow2,
                orderRow
        );
        leftPane.setPadding(new Insets(6));

        // Right: Active Section / Page Text Editor
        blockTextArea = new TextArea();
        blockTextArea.setWrapText(true);
        blockTextArea.setPromptText("Enter section content…");
        VBox.setVgrow(blockTextArea, Priority.ALWAYS);

        blockTextArea.textProperty().addListener((obs, oldText, newText) -> {
            if (updatingControls) return;
            DocumentBlock sel = blockListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                sel.setText(newText);
                blockListView.refresh();
                updateTextAreaPreviewStyle(sel);
            }
        });

        Label editorLbl = new Label("SECTION EDITOR");
        editorLbl.getStyleClass().add("section-caption");

        VBox rightPane = new VBox(6,
                editorLbl,
                blockTextArea
        );
        rightPane.setPadding(new Insets(6));

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.33);
        root.setCenter(split);

        // Bottom Bar
        root.setBottom(buildBottomBar());
        return root;
    }

    private HBox buildFileBar() {
        Button newDocBtn = new Button("New Document");
        newDocBtn.setGraphic(SvgIcons.icon(SvgIcons.FILE_PLUS, 13));
        newDocBtn.getStyleClass().add("surface-button");
        newDocBtn.setOnAction(e -> {
            openedWordFile = null;
            currentFileLabel.setText("Untitled Document");
            initStarterDocument();
        });

        Button openWordBtn = new Button("Open Word (.docx)…");
        openWordBtn.setGraphic(SvgIcons.icon(SvgIcons.FOLDER_OPEN, 13));
        openWordBtn.getStyleClass().add("surface-button");
        openWordBtn.setOnAction(e -> promptOpenWordFile());

        Button saveWordBtn = new Button("Save as Word (.docx)…");
        saveWordBtn.setGraphic(SvgIcons.icon(SvgIcons.SAVE, 13));
        saveWordBtn.getStyleClass().add("surface-button");
        saveWordBtn.setOnAction(e -> promptSaveWordFile());

        currentFileLabel = new Label("Untitled Document");
        currentFileLabel.setStyle("-fx-opacity: 0.72; -fx-padding: 0 0 0 12;");

        HBox bar = new HBox(8, newDocBtn, openWordBtn, saveWordBtn, currentFileLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFormattingToolbar() {
        blockTypeCombo = new ComboBox<>(FXCollections.observableArrayList(BlockType.values()));
        blockTypeCombo.setPrefWidth(135);
        blockTypeCombo.setOnAction(e -> applyFormattingToSelectedBlock());

        fontFamilyCombo = new ComboBox<>(FXCollections.observableArrayList("Helvetica", "Times", "Courier"));
        fontFamilyCombo.setPrefWidth(115);
        fontFamilyCombo.setOnAction(e -> applyFormattingToSelectedBlock());

        fontSizeSpinner = new Spinner<>(8, 72, 12);
        fontSizeSpinner.setPrefWidth(70);
        fontSizeSpinner.valueProperty().addListener((obs, o, n) -> applyFormattingToSelectedBlock());

        boldToggle = new ToggleButton("B");
        boldToggle.setStyle("-fx-font-weight: bold;");
        boldToggle.setOnAction(e -> applyFormattingToSelectedBlock());

        italicToggle = new ToggleButton("I");
        italicToggle.setStyle("-fx-font-style: italic;");
        italicToggle.setOnAction(e -> applyFormattingToSelectedBlock());

        underlineToggle = new ToggleButton("U");
        underlineToggle.setStyle("-fx-underline: true;");
        underlineToggle.setOnAction(e -> applyFormattingToSelectedBlock());

        colorPicker = new ColorPicker(Color.web("#111111"));
        colorPicker.setPrefWidth(105);
        colorPicker.setOnAction(e -> applyFormattingToSelectedBlock());

        alignmentCombo = new ComboBox<>(FXCollections.observableArrayList(Alignment.values()));
        alignmentCombo.setPrefWidth(100);
        alignmentCombo.setOnAction(e -> applyFormattingToSelectedBlock());

        HBox bar = new HBox(8,
                new Label("Style"), blockTypeCombo,
                new Label("Font"), fontFamilyCombo,
                new Label("Pt"), fontSizeSpinner,
                boldToggle, italicToggle, underlineToggle,
                new Label("Fill"), colorPicker,
                new Label("Align"), alignmentCombo
        );
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildBottomBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportOpenBtn = new Button("Export to PDF & Open in Workspace");
        exportOpenBtn.setGraphic(SvgIcons.icon(SvgIcons.CHECK, 13));
        exportOpenBtn.getStyleClass().add("accent-button");
        exportOpenBtn.setOnAction(e -> exportToPdf(false));

        Button exportSaveBtn = new Button("Export & Save PDF As…");
        exportSaveBtn.setGraphic(SvgIcons.icon(SvgIcons.SAVE, 13));
        exportSaveBtn.getStyleClass().add("surface-button");
        exportSaveBtn.setOnAction(e -> exportToPdf(true));

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("surface-button");
        closeBtn.setOnAction(e -> dialogStage.close());

        HBox bottom = new HBox(10, spacer, exportSaveBtn, exportOpenBtn, closeBtn);
        bottom.setPadding(new Insets(12, 0, 0, 0));
        return bottom;
    }

    private void populateEditorFromBlock(DocumentBlock b) {
        updatingControls = true;
        try {
            if (b == null) {
                blockTextArea.setText("");
                blockTextArea.setDisable(true);
                return;
            }
            boolean isTextEditable = (b.getType() != BlockType.IMAGE
                    && b.getType() != BlockType.PAGE_BREAK
                    && b.getType() != BlockType.DIVIDER);
            blockTextArea.setDisable(!isTextEditable);
            blockTextArea.setText(b.getText());
            blockTypeCombo.setValue(b.getType());
            fontFamilyCombo.setValue(b.getFontFamily() != null ? b.getFontFamily() : "Helvetica");
            fontSizeSpinner.getValueFactory().setValue(Math.max(8, Math.round(b.getFontSize())));
            boldToggle.setSelected(b.isBold());
            italicToggle.setSelected(b.isItalic());
            underlineToggle.setSelected(b.isUnderline());
            alignmentCombo.setValue(b.getAlignment() != null ? b.getAlignment() : Alignment.LEFT);
            try {
                colorPicker.setValue(Color.web(b.getColorHex() != null ? b.getColorHex() : "#111111"));
            } catch (Exception ignored) {
                colorPicker.setValue(Color.BLACK);
            }
            updateTextAreaPreviewStyle(b);
        } finally {
            updatingControls = false;
        }
    }

    private void applyFormattingToSelectedBlock() {
        if (updatingControls) return;
        DocumentBlock sel = blockListView.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        BlockType newType = blockTypeCombo.getValue();
        if (newType != null && newType != sel.getType()) {
            sel.setType(newType);
            if (newType == BlockType.HEADING_1) {
                fontSizeSpinner.getValueFactory().setValue(22);
                boldToggle.setSelected(true);
            } else if (newType == BlockType.HEADING_2) {
                fontSizeSpinner.getValueFactory().setValue(16);
                boldToggle.setSelected(true);
            }
        }
        sel.setFontFamily(fontFamilyCombo.getValue());
        sel.setFontSize(fontSizeSpinner.getValue());
        sel.setBold(boldToggle.isSelected());
        sel.setItalic(italicToggle.isSelected());
        sel.setUnderline(underlineToggle.isSelected());
        sel.setAlignment(alignmentCombo.getValue());
        sel.setColorHex(toHexString(colorPicker.getValue()));

        blockListView.refresh();
        updateTextAreaPreviewStyle(sel);
    }

    private void updateTextAreaPreviewStyle(DocumentBlock b) {
        if (b == null) return;
        String fxFont = switch (b.getFontFamily() != null ? b.getFontFamily() : "Helvetica") {
            case "Times" -> "Times New Roman";
            case "Courier" -> "Consolas";
            default -> "Segoe UI";
        };
        String weight = b.isBold() ? "bold" : "normal";
        String posture = b.isItalic() ? "italic" : "normal";
        int size = Math.min(32, Math.max(11, Math.round(b.getFontSize())));
        blockTextArea.setStyle(String.format(
                "-fx-font-family: '%s'; -fx-font-size: %dpx; -fx-font-weight: %s; -fx-font-style: %s;",
                fxFont, size, weight, posture
        ));
    }

    private void addNewBlock(DocumentBlock block) {
        int idx = blockListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < blocks.size()) {
            blocks.add(idx + 1, block);
            blockListView.getSelectionModel().select(idx + 1);
        } else {
            blocks.add(block);
            blockListView.getSelectionModel().select(blocks.size() - 1);
        }
        blockTextArea.requestFocus();
    }

    private void insertImageBlock() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Insert Image into Document");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );
        File f = fc.showOpenDialog(dialogStage);
        if (f != null) {
            DocumentBlock imgBlock = new DocumentBlock(BlockType.IMAGE, "");
            imgBlock.setImageFile(f);
            addNewBlock(imgBlock);
        }
    }

    private void promptOpenWordFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Word Document (.docx) or Text File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Word & Text Documents", "*.docx", "*.txt", "*.md"),
                new FileChooser.ExtensionFilter("Microsoft Word (.docx)", "*.docx"),
                new FileChooser.ExtensionFilter("Text / Markdown", "*.txt", "*.md")
        );
        File file = fc.showOpenDialog(dialogStage);
        if (file != null) {
            loadWordFile(file);
        }
    }

    private void loadWordFile(File file) {
        try {
            List<DocumentBlock> loaded = wordService.loadDocument(file);
            openedWordFile = file;
            currentFileLabel.setText("Editing: " + file.getName());
            blocks.setAll(loaded);
            if (!blocks.isEmpty()) {
                blockListView.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            Alert alert = new Alert(AlertType.ERROR, "Failed to open Word file: " + e.getMessage());
            alert.initOwner(dialogStage);
            alert.showAndWait();
        }
    }

    private void promptSaveWordFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save as Microsoft Word (.docx)");
        fc.setInitialFileName(openedWordFile != null ? openedWordFile.getName() : "Document.docx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx"));
        File out = fc.showSaveDialog(dialogStage);
        if (out != null) {
            try {
                wordService.saveAsDocx(new ArrayList<>(blocks), out);
                openedWordFile = out;
                currentFileLabel.setText("Saved: " + out.getName());
            } catch (Exception e) {
                Alert alert = new Alert(AlertType.ERROR, "Failed to save .docx: " + e.getMessage());
                alert.initOwner(dialogStage);
                alert.showAndWait();
            }
        }
    }

    private void exportToPdf(boolean saveToDisk) {
        try {
            PDDocument pdfDoc = pdfCreatorService.createPdfFromDocumentBlocks(new ArrayList<>(blocks));
            if (saveToDisk) {
                FileChooser saver = new FileChooser();
                saver.setTitle("Export Document as PDF");
                String base = openedWordFile != null
                        ? openedWordFile.getName().replaceAll("\\.[^.]+$", "")
                        : "Created_Document";
                saver.setInitialFileName(base + ".pdf");
                saver.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
                File out = saver.showSaveDialog(dialogStage);
                if (out != null) {
                    pdfDoc.save(out);
                } else {
                    pdfDoc.close();
                    return;
                }
            }
            if (onOpenGeneratedPdf != null) {
                onOpenGeneratedPdf.accept(pdfDoc);
            } else {
                pdfDoc.close();
            }
            dialogStage.close();
        } catch (Exception e) {
            Alert alert = new Alert(AlertType.ERROR, "Failed to export PDF: " + e.getMessage());
            alert.initOwner(dialogStage);
            alert.showAndWait();
        }
    }

    private static String toHexString(Color c) {
        if (c == null) return "#000000";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
