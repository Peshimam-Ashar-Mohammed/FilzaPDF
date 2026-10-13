package com.filzapdf.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import com.filzapdf.pdf.PdfTextEditorService;
import com.filzapdf.pdf.PdfToolboxService;

import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Focused helper for PDF page and document operation dialogs (text/image insertion,
 * range extraction, watermarks, AES encryption, PNG export, and document metadata).
 */
public class PageOperationsDialogs {

    private final Stage ownerStage;
    private final PdfTextEditorService textEditorService;
    private final PdfToolboxService toolboxService;

    public PageOperationsDialogs(
            Stage ownerStage,
            PdfTextEditorService textEditorService,
            PdfToolboxService toolboxService) {
        this.ownerStage = ownerStage;
        this.textEditorService = textEditorService;
        this.toolboxService = toolboxService;
    }

    public void promptAddNewTextAt(
            PDDocument document,
            int pageIndex,
            float xPt,
            float yPt,
            Runnable onDocumentRefreshed) {
        if (document == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(ownerStage);
        dialog.setTitle("Insert Text — Page " + (pageIndex + 1));
        dialog.setHeaderText(String.format("Position: (%.0f, %.0f) pt", xPt, yPt));

        ButtonType insertBtnType = new ButtonType("Insert", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(insertBtnType, ButtonType.CANCEL);

        TextArea textInput = new TextArea();
        textInput.setPromptText("Enter text…");
        textInput.setPrefRowCount(3);

        ComboBox<String> fontCombo = new ComboBox<>(FXCollections.observableArrayList("Helvetica", "Times", "Courier"));
        fontCombo.setValue("Helvetica");

        Spinner<Double> sizeSpin = new Spinner<>(6.0, 96.0, 14.0, 1.0);
        CheckBox boldCb = new CheckBox("Bold");
        CheckBox italicCb = new CheckBox("Italic");
        ColorPicker colorPick = new ColorPicker(Color.BLACK);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(new Label("Text:"), 0, 0);
        grid.add(textInput, 1, 0, 3, 1);
        grid.add(new Label("Font:"), 0, 1);
        grid.add(fontCombo, 1, 1);
        grid.add(new Label("Size (pt):"), 2, 1);
        grid.add(sizeSpin, 3, 1);
        grid.add(boldCb, 1, 2);
        grid.add(italicCb, 2, 2);
        grid.add(colorPick, 3, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == insertBtnType);

        Optional<Boolean> res = dialog.showAndWait();
        if (res.isPresent() && res.get() && !textInput.getText().trim().isEmpty()) {
            try {
                textEditorService.addTextToPage(
                        document,
                        pageIndex,
                        textInput.getText(),
                        xPt,
                        yPt,
                        fontCombo.getValue(),
                        boldCb.isSelected(),
                        italicCb.isSelected(),
                        sizeSpin.getValue().floatValue(),
                        toHex(colorPick.getValue())
                );
                if (onDocumentRefreshed != null) {
                    onDocumentRefreshed.run();
                }
            } catch (Exception ex) {
                showError("Insert Text Failed", "Could not add text to page.", ex.getMessage());
            }
        }
    }

    public void promptInsertImageAt(
            PDDocument document,
            int pageIndex,
            float xPt,
            float yPt,
            Runnable onDocumentRefreshed) {
        if (document == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Select Image to Place on Page " + (pageIndex + 1));
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );
        File imgFile = fc.showOpenDialog(ownerStage);
        if (imgFile == null) return;

        TextInputDialog sizeDialog = new TextInputDialog("180x120");
        sizeDialog.initOwner(ownerStage);
        sizeDialog.setTitle("Image Dimensions");
        sizeDialog.setHeaderText("Width x Height in points (e.g. 180x120):");
        sizeDialog.showAndWait().ifPresent(dimStr -> {
            float w = 180f;
            float h = 120f;
            try {
                String[] parts = dimStr.toLowerCase().split("x");
                if (parts.length == 2) {
                    w = Float.parseFloat(parts[0].trim());
                    h = Float.parseFloat(parts[1].trim());
                }
            } catch (Exception ignored) {
            }
            try {
                textEditorService.addImageToPage(document, pageIndex, imgFile, xPt, yPt, w, h);
                if (onDocumentRefreshed != null) {
                    onDocumentRefreshed.run();
                }
            } catch (Exception ex) {
                showError("Place Image Failed", "Could not embed image onto page.", ex.getMessage());
            }
        });
    }

    public void showExtractPagesDialog(
            PDDocument document,
            BiConsumer<PDDocument, String> onOpenExtracted) {
        if (document == null) return;
        int total = document.getNumberOfPages();
        TextInputDialog d = new TextInputDialog("1-" + total);
        d.initOwner(ownerStage);
        d.setTitle("Extract Pages");
        d.setHeaderText("Page range (1.." + total + "), e.g. 1-3 or 2,4:");
        d.showAndWait().ifPresent(input -> {
            try {
                List<Integer> indices = new ArrayList<>();
                for (String part : input.split("[,;]+")) {
                    String s = part.trim();
                    if (s.isEmpty()) continue;
                    if (s.contains("-")) {
                        String[] b = s.split("-", 2);
                        int start = Integer.parseInt(b[0].trim());
                        int end = Integer.parseInt(b[1].trim());
                        for (int k = start; k <= end; k++) {
                            if (k >= 1 && k <= total) indices.add(k - 1);
                        }
                    } else {
                        int k = Integer.parseInt(s);
                        if (k >= 1 && k <= total) indices.add(k - 1);
                    }
                }
                if (!indices.isEmpty()) {
                    PDDocument extracted = toolboxService.extractPages(document, indices);
                    if (onOpenExtracted != null) {
                        onOpenExtracted.accept(extracted, "Extracted.pdf");
                    }
                }
            } catch (Exception ex) {
                showError("Extract Failed", "Invalid page range.", ex.getMessage());
            }
        });
    }

    public void showAddWatermarkDialog(PDDocument document, Runnable onRefreshed) {
        if (document == null) return;
        TextInputDialog d = new TextInputDialog("CONFIDENTIAL");
        d.initOwner(ownerStage);
        d.setTitle("Add Watermark");
        d.setHeaderText("Watermark text:");
        d.showAndWait().ifPresent(text -> {
            if (!text.trim().isEmpty()) {
                try {
                    toolboxService.addWatermarkToAllPages(document, text.trim(), 54f, "#6E56CF", 0.22f);
                    if (onRefreshed != null) {
                        onRefreshed.run();
                    }
                } catch (Exception ex) {
                    showError("Watermark Error", "Could not apply watermark.", ex.getMessage());
                }
            }
        });
    }

    public void showEncryptPdfDialog(PDDocument document, Runnable onProtected) {
        if (document == null) return;
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(ownerStage);
        dialog.setTitle("Encrypt PDF (256-bit AES)");
        dialog.setHeaderText("Set document password:");

        ButtonType protectBtn = new ButtonType("Apply", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(protectBtn, ButtonType.CANCEL);

        PasswordField pf = new PasswordField();
        pf.setPromptText("Password");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Password:"), pf));
        dialog.setResultConverter(b -> b == protectBtn ? pf.getText() : null);

        dialog.showAndWait().ifPresent(pwd -> {
            if (!pwd.isEmpty()) {
                try {
                    toolboxService.protectDocumentWithPassword(document, pwd, pwd);
                    if (onProtected != null) {
                        onProtected.run();
                    }
                } catch (Exception ex) {
                    showError("Encryption Error", "Failed to protect PDF.", ex.getMessage());
                }
            }
        });
    }

    public void showExportPagesToImagesDialog(
            PDDocument document,
            File currentFile,
            Consumer<String> onStatusUpdated) {
        if (document == null) return;
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Folder to Export Page Images (PNG)");
        File dir = dc.showDialog(ownerStage);
        if (dir == null) return;

        String base = currentFile != null ? currentFile.getName().replaceAll("\\.[^.]+$", "") : "page";
        try {
            List<File> exported = toolboxService.exportAllPagesAsImages(document, dir, base, 150f);
            if (onStatusUpdated != null) {
                onStatusUpdated.accept("Exported " + exported.size() + " PNG images");
            }
        } catch (Exception e) {
            showError("Export Failed", "Could not export pages.", e.getMessage());
        }
    }

    public void showDocumentPropertiesDialog(PDDocument doc, File currentFile) {
        if (doc == null) return;
        PDDocumentInformation info = doc.getDocumentInformation();

        String text = String.join("\n",
                "Title:    " + nvl(info.getTitle()),
                "Author:   " + nvl(info.getAuthor()),
                "Subject:  " + nvl(info.getSubject()),
                "Creator:  " + nvl(info.getCreator()),
                "Producer: " + nvl(info.getProducer()),
                "Pages:    " + doc.getNumberOfPages()
        );

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.initOwner(ownerStage);
        alert.setTitle("Document Properties");
        alert.setHeaderText(currentFile != null ? currentFile.getName() : "PDF Document");
        alert.setContentText(text);
        alert.showAndWait();
    }

    public void showError(String title, String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.initOwner(ownerStage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(detail != null ? detail : "Please check the file and try again.");
        alert.showAndWait();
        System.err.println("[ERROR] " + title + " | " + detail);
    }

    private static String nvl(String s) {
        return s != null && !s.isEmpty() ? s : "—";
    }

    private static String toHex(Color c) {
        if (c == null) return "#7C66DC";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
