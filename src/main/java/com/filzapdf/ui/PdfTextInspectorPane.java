package com.filzapdf.ui;

import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.pdf.PdfTextEditorService;
import com.filzapdf.pdf.RecognizedTextBlock;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Right-side dockable inspector panel for recognizing and editing PDF text and fonts in-place.
 */
public class PdfTextInspectorPane extends VBox {

    private final PdfViewerController controller;
    private final PdfTextEditorService textEditorService = new PdfTextEditorService();

    private final ObservableList<RecognizedTextBlock> pageBlocks = FXCollections.observableArrayList();
    private final ListView<RecognizedTextBlock> blockListView;
    private final Label detectedFontLabel;
    private final TextArea editTextArea;
    private final ComboBox<String> fontFamilyCombo;
    private final Spinner<Double> fontSizeSpinner;
    private final CheckBox boldCheck;
    private final CheckBox italicCheck;
    private final ColorPicker textColorPicker;
    private final ColorPicker bgFillPicker;
    private final Button applyEditButton;
    private final Button eraseBlockButton;

    private RecognizedTextBlock activeBlock;

    public PdfTextInspectorPane(PdfViewerController controller) {
        super(10);
        this.controller = controller;
        setPadding(new Insets(14));
        setPrefWidth(308);
        setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 0 0 0 1;");

        Label header = new Label("Typography Inspector");
        header.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        Label hint = new Label("Select any text block on the page canvas or from the list below to edit its content and font.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.72;");

        blockListView = new ListView<>(pageBlocks);
        blockListView.setPrefHeight(165);
        blockListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(RecognizedTextBlock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String preview = item.getText().replace("\n", " ");
                    if (preview.length() > 28) preview = preview.substring(0, 28) + "…";
                    setText(String.format("%s  ·  %s %.0fpt", preview, item.getFontFamily(), item.getFontSize()));
                }
            }
        });

        blockListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectBlock(newV);
                controller.setSelectedTextBlock(newV);
            }
        });

        detectedFontLabel = new Label("Detected Font: —");
        detectedFontLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 11.5px;");

        editTextArea = new TextArea();
        editTextArea.setWrapText(true);
        editTextArea.setPrefRowCount(3);
        editTextArea.setPromptText("Select a text block on the page…");

        fontFamilyCombo = new ComboBox<>(FXCollections.observableArrayList("Helvetica", "Times", "Courier"));
        fontFamilyCombo.setValue("Helvetica");
        fontFamilyCombo.setPrefWidth(120);

        fontSizeSpinner = new Spinner<>(6.0, 96.0, 12.0, 0.5);
        fontSizeSpinner.setEditable(true);
        fontSizeSpinner.setPrefWidth(82);

        HBox fontRow = new HBox(8, new Label("Font"), fontFamilyCombo, new Label("Pt"), fontSizeSpinner);
        fontRow.setAlignment(Pos.CENTER_LEFT);

        boldCheck = new CheckBox("Bold");
        italicCheck = new CheckBox("Italic");
        HBox styleRow = new HBox(14, boldCheck, italicCheck);
        styleRow.setAlignment(Pos.CENTER_LEFT);

        textColorPicker = new ColorPicker(Color.BLACK);
        textColorPicker.setPrefWidth(96);
        bgFillPicker = new ColorPicker(Color.WHITE);
        bgFillPicker.setPrefWidth(96);

        HBox colorRow = new HBox(8,
                new Label("Fill"), textColorPicker,
                new Label("Bg"), bgFillPicker
        );
        colorRow.setAlignment(Pos.CENTER_LEFT);

        applyEditButton = new Button("Apply Changes");
        applyEditButton.setGraphic(SvgIcons.icon(SvgIcons.CHECK, 13));
        applyEditButton.getStyleClass().add("accent-button");
        applyEditButton.setMaxWidth(Double.MAX_VALUE);
        applyEditButton.setOnAction(e -> commitTextBlockEdit(false));

        eraseBlockButton = new Button("Remove Text Block");
        eraseBlockButton.setGraphic(SvgIcons.icon(SvgIcons.TRASH, 13));
        eraseBlockButton.getStyleClass().add("surface-button");
        eraseBlockButton.setMaxWidth(Double.MAX_VALUE);
        eraseBlockButton.setOnAction(e -> commitTextBlockEdit(true));

        Button rescanBtn = new Button("Refresh Page Blocks");
        rescanBtn.getStyleClass().add("surface-button");
        rescanBtn.setMaxWidth(Double.MAX_VALUE);
        rescanBtn.setOnAction(e -> refreshForCurrentPage());

        VBox.setVgrow(blockListView, Priority.ALWAYS);

        Label blocksSectionLbl = new Label("PAGE TEXT BLOCKS");
        blocksSectionLbl.getStyleClass().add("section-caption");

        Label editSectionLbl = new Label("CONTENT & TYPOGRAPHY");
        editSectionLbl.getStyleClass().add("section-caption");

        getChildren().addAll(
                header,
                hint,
                rescanBtn,
                blocksSectionLbl,
                blockListView,
                new Separator(),
                editSectionLbl,
                detectedFontLabel,
                editTextArea,
                fontRow,
                styleRow,
                colorRow,
                applyEditButton,
                eraseBlockButton
        );

        setEditorEnabled(false);
    }

    public void refreshForCurrentPage() {
        pageBlocks.clear();
        activeBlock = null;
        setEditorEnabled(false);
        if (!controller.hasDocument()) return;

        int currentPage = controller.getModel().getCurrentPage();
        List<RecognizedTextBlock> list = controller.getRecognizedTextForPage(currentPage);
        pageBlocks.addAll(list);
    }

    public void selectBlock(RecognizedTextBlock block) {
        this.activeBlock = block;
        if (block == null) {
            setEditorEnabled(false);
            detectedFontLabel.setText("Recognized Font: —");
            editTextArea.setText("");
            return;
        }
        setEditorEnabled(true);
        detectedFontLabel.setText("Detected: " + block.getRawFontName() + " (" + block.getFontSize() + " pt)");
        editTextArea.setText(block.getText());
        fontFamilyCombo.setValue(block.getFontFamily());
        fontSizeSpinner.getValueFactory().setValue((double) block.getFontSize());
        boldCheck.setSelected(block.isBold());
        italicCheck.setSelected(block.isItalic());
        try {
            textColorPicker.setValue(Color.web(block.getColorHex()));
        } catch (Exception e) {
            textColorPicker.setValue(Color.BLACK);
        }
        if (blockListView.getSelectionModel().getSelectedItem() != block) {
            blockListView.getSelectionModel().select(block);
        }
    }

    private void commitTextBlockEdit(boolean eraseOnly) {
        if (activeBlock == null || !controller.hasDocument()) return;
        PDDocument doc = controller.getDocument();
        String newText = eraseOnly ? "" : editTextArea.getText();
        String family = fontFamilyCombo.getValue();
        boolean bold = boldCheck.isSelected();
        boolean italic = italicCheck.isSelected();
        float size = fontSizeSpinner.getValue().floatValue();
        String textHex = toHex(textColorPicker.getValue());
        String bgHex = toHex(bgFillPicker.getValue());

        try {
            textEditorService.replaceTextBlock(
                    doc, activeBlock, newText, family, bold, italic, size, textHex, bgHex
            );
            controller.refreshDocumentAfterEdit();
            refreshForCurrentPage();
        } catch (Exception ex) {
            Alert alert = new Alert(AlertType.ERROR, "Failed to update PDF text: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private void setEditorEnabled(boolean enabled) {
        editTextArea.setDisable(!enabled);
        fontFamilyCombo.setDisable(!enabled);
        fontSizeSpinner.setDisable(!enabled);
        boldCheck.setDisable(!enabled);
        italicCheck.setDisable(!enabled);
        textColorPicker.setDisable(!enabled);
        bgFillPicker.setDisable(!enabled);
        applyEditButton.setDisable(!enabled);
        eraseBlockButton.setDisable(!enabled);
    }

    private static String toHex(Color c) {
        if (c == null) return "#000000";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
