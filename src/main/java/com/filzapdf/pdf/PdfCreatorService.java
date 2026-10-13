package com.filzapdf.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javafx.embed.swing.SwingFXUtils;

/**
 * Service for creating new PDF documents from uploaded images or from rich Word-like
 * document blocks (paragraphs, headings, lists, fonts, colors, images).
 */
public class PdfCreatorService {

    /**
     * Represents a styled paragraph, heading, image, or divider in the Word-like Document Studio.
     */
    public static class DocumentBlock {
        public enum BlockType {
            PARAGRAPH, HEADING_1, HEADING_2, BULLET_ITEM, IMAGE, PAGE_BREAK, DIVIDER
        }

        public enum Alignment {
            LEFT, CENTER, RIGHT
        }

        private BlockType type = BlockType.PARAGRAPH;
        private String text = "";
        private String fontFamily = "Helvetica";
        private float fontSize = 12f;
        private boolean bold = false;
        private boolean italic = false;
        private boolean underline = false;
        private String colorHex = "#111111";
        private Alignment alignment = Alignment.LEFT;
        private File imageFile;
        private byte[] imageBytes;
        private float imageMaxWidth = 420f;

        public DocumentBlock() {}

        public DocumentBlock(BlockType type, String text) {
            this.type = type;
            this.text = text != null ? text : "";
            if (type == BlockType.HEADING_1) {
                this.fontSize = 22f;
                this.bold = true;
            } else if (type == BlockType.HEADING_2) {
                this.fontSize = 16f;
                this.bold = true;
            }
        }

        public BlockType getType() { return type; }
        public void setType(BlockType type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text != null ? text : ""; }
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public float getFontSize() { return fontSize; }
        public void setFontSize(float fontSize) { this.fontSize = fontSize; }
        public boolean isBold() { return bold; }
        public void setBold(boolean bold) { this.bold = bold; }
        public boolean isItalic() { return italic; }
        public void setItalic(boolean italic) { this.italic = italic; }
        public boolean isUnderline() { return underline; }
        public void setUnderline(boolean underline) { this.underline = underline; }
        public String getColorHex() { return colorHex; }
        public void setColorHex(String colorHex) { this.colorHex = colorHex; }
        public Alignment getAlignment() { return alignment; }
        public void setAlignment(Alignment alignment) { this.alignment = alignment; }
        public File getImageFile() { return imageFile; }
        public void setImageFile(File imageFile) { this.imageFile = imageFile; }
        public byte[] getImageBytes() { return imageBytes; }
        public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }
        public float getImageMaxWidth() { return imageMaxWidth; }
        public void setImageMaxWidth(float imageMaxWidth) { this.imageMaxWidth = imageMaxWidth; }
    }

    /**
     * Robustly loads any image file (PNG, JPG, JPEG, BMP, GIF, etc.) into a PDImageXObject
     * by normalizing through BufferedImage (TYPE_INT_RGB) and LosslessFactory so that
     * progressive JPEGs, CMYK images, 16-bit PNGs, or transparent PNGs always render cleanly.
     */
    public static PDImageXObject loadRobustImageForPdf(PDDocument doc, File imgFile) throws IOException {
        if (imgFile == null || !imgFile.exists()) {
            throw new IOException("Image file does not exist: " + imgFile);
        }

        BufferedImage decoded = null;
        try {
            decoded = ImageIO.read(imgFile);
        } catch (Exception ignored) {}

        if (decoded == null) {
            try {
                javafx.scene.image.Image fxImg = new javafx.scene.image.Image(imgFile.toURI().toString());
                if (!fxImg.isError() && fxImg.getWidth() > 0 && fxImg.getHeight() > 0) {
                    decoded = SwingFXUtils.fromFXImage(fxImg, null);
                }
            } catch (Exception ignored) {}
        }

        if (decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0) {
            BufferedImage rgbImage = normalizeToRgbBufferedImage(decoded);
            return LosslessFactory.createFromImage(doc, rgbImage);
        }

        return PDImageXObject.createFromFileByContent(imgFile, doc);
    }

    public static PDImageXObject loadRobustImageBytesForPdf(PDDocument doc, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Image byte array is empty");
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0) {
                BufferedImage rgbImage = normalizeToRgbBufferedImage(decoded);
                return LosslessFactory.createFromImage(doc, rgbImage);
            }
        } catch (Exception ignored) {}
        return PDImageXObject.createFromByteArray(doc, bytes, "embedded");
    }

    private static BufferedImage normalizeToRgbBufferedImage(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * Appends one or more image files as new PDF pages into an existing PDDocument.
     *
     * @return number of pages successfully added
     */
    public int appendImagesAsPages(
            PDDocument targetDoc,
            List<File> imageFiles,
            String pageSizeMode,
            boolean landscape,
            float marginPt) throws IOException {

        if (targetDoc == null || imageFiles == null || imageFiles.isEmpty()) {
            return 0;
        }

        int addedCount = 0;
        synchronized (targetDoc) {
            for (File imgFile : imageFiles) {
                if (imgFile == null || !imgFile.exists()) continue;
                PDImageXObject pdImg = loadRobustImageForPdf(targetDoc, imgFile);
                float imgW = pdImg.getWidth();
                float imgH = pdImg.getHeight();
                if (imgW <= 0 || imgH <= 0) continue;

                PDRectangle pageRect;
                if ("FIT_IMAGE".equalsIgnoreCase(pageSizeMode)) {
                    // Scale extremely huge pixel dimensions down to reasonable PDF point size (max 842 pt)
                    float maxDim = Math.max(imgW, imgH);
                    float ptScale = maxDim > 1000f ? (792f / maxDim) : 1.0f;
                    float pageW = imgW * ptScale + marginPt * 2;
                    float pageH = imgH * ptScale + marginPt * 2;
                    pageRect = new PDRectangle(pageW, pageH);
                } else {
                    PDRectangle base = "LETTER".equalsIgnoreCase(pageSizeMode)
                            ? PDRectangle.LETTER : PDRectangle.A4;
                    pageRect = landscape
                            ? new PDRectangle(base.getHeight(), base.getWidth())
                            : new PDRectangle(base.getWidth(), base.getHeight());
                }

                PDPage page = new PDPage(pageRect);
                targetDoc.addPage(page);

                float availW = Math.max(20f, pageRect.getWidth() - marginPt * 2);
                float availH = Math.max(20f, pageRect.getHeight() - marginPt * 2);

                float scale = Math.min(availW / imgW, availH / imgH);
                float drawW = imgW * scale;
                float drawH = imgH * scale;
                float drawX = (pageRect.getWidth() - drawW) / 2f;
                float drawY = (pageRect.getHeight() - drawH) / 2f;

                try (PDPageContentStream cs = new PDPageContentStream(targetDoc, page)) {
                    cs.drawImage(pdImg, drawX, drawY, drawW, drawH);
                }
                addedCount++;
            }
        }
        return addedCount;
    }

    /**
     * Creates a multi-page PDF document from a list of image files.
     *
     * @param imageFiles   ordered list of image files (PNG, JPG, BMP, GIF)
     * @param pageSizeMode "A4", "LETTER", or "FIT_IMAGE"
     * @param landscape    true for landscape orientation (when A4 or LETTER)
     * @param marginPt     page margin in points
     * @return newly created PDDocument
     */
    public PDDocument createPdfFromImages(
            List<File> imageFiles,
            String pageSizeMode,
            boolean landscape,
            float marginPt) throws IOException {

        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        try (PDDocument tempDoc = new PDDocument()) {
            int added = appendImagesAsPages(tempDoc, imageFiles, pageSizeMode, landscape, marginPt);
            if (added == 0) {
                throw new IOException("No valid images could be loaded from the selected files.");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            tempDoc.save(baos);
            return Loader.loadPDF(baos.toByteArray());
        }
    }

    /**
     * Renders a list of Word-like DocumentBlocks into a paginated vector PDF document.
     */
    public PDDocument createPdfFromDocumentBlocks(List<DocumentBlock> blocks) throws IOException {
        try (PDDocument tempDoc = new PDDocument()) {
            PDRectangle pageSize = PDRectangle.A4;
            float margin = 50f;
            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();
            float usableWidth = pageWidth - margin * 2;

            PDPage currentPage = new PDPage(pageSize);
            tempDoc.addPage(currentPage);
            PDPageContentStream cs = new PDPageContentStream(tempDoc, currentPage);
            float cursorY = pageHeight - margin;

            try {
                if (blocks != null) {
                    for (DocumentBlock block : blocks) {
                        if (block.getType() == DocumentBlock.BlockType.PAGE_BREAK) {
                            cs.close();
                            currentPage = new PDPage(pageSize);
                            tempDoc.addPage(currentPage);
                            cs = new PDPageContentStream(tempDoc, currentPage);
                            cursorY = pageHeight - margin;
                            continue;
                        }

                        if (block.getType() == DocumentBlock.BlockType.DIVIDER) {
                            if (cursorY - 20f < margin) {
                                cs.close();
                                currentPage = new PDPage(pageSize);
                                tempDoc.addPage(currentPage);
                                cs = new PDPageContentStream(tempDoc, currentPage);
                                cursorY = pageHeight - margin;
                            }
                            cursorY -= 8f;
                            cs.setStrokingColor(new Color(180, 180, 180));
                            cs.setLineWidth(1f);
                            cs.moveTo(margin, cursorY);
                            cs.lineTo(pageWidth - margin, cursorY);
                            cs.stroke();
                            cursorY -= 12f;
                            continue;
                        }

                        if (block.getType() == DocumentBlock.BlockType.IMAGE) {
                            PDImageXObject pdImg = null;
                            if (block.getImageFile() != null && block.getImageFile().exists()) {
                                pdImg = loadRobustImageForPdf(tempDoc, block.getImageFile());
                            } else if (block.getImageBytes() != null && block.getImageBytes().length > 0) {
                                pdImg = loadRobustImageBytesForPdf(tempDoc, block.getImageBytes());
                            }
                            if (pdImg != null) {
                                float maxW = Math.min(usableWidth, block.getImageMaxWidth() > 0 ? block.getImageMaxWidth() : usableWidth);
                                float scale = Math.min(1.0f, maxW / pdImg.getWidth());
                                float drawW = pdImg.getWidth() * scale;
                                float drawH = pdImg.getHeight() * scale;
                                if (drawH > pageHeight - margin * 2) {
                                    float hScale = (pageHeight - margin * 2) / drawH;
                                    drawW *= hScale;
                                    drawH *= hScale;
                                }
                                if (cursorY - drawH < margin) {
                                    cs.close();
                                    currentPage = new PDPage(pageSize);
                                    tempDoc.addPage(currentPage);
                                    cs = new PDPageContentStream(tempDoc, currentPage);
                                    cursorY = pageHeight - margin;
                                }
                                float drawX = margin;
                                if (block.getAlignment() == DocumentBlock.Alignment.CENTER) {
                                    drawX = margin + (usableWidth - drawW) / 2f;
                                } else if (block.getAlignment() == DocumentBlock.Alignment.RIGHT) {
                                    drawX = pageWidth - margin - drawW;
                                }
                                cursorY -= drawH;
                                cs.drawImage(pdImg, drawX, cursorY, drawW, drawH);
                                cursorY -= 14f;
                            }
                            continue;
                        }

                        // Text block (PARAGRAPH, HEADING_1, HEADING_2, BULLET_ITEM)
                        PDFont font = PdfTextEditorService.resolveStandardFont(
                                block.getFontFamily(), block.isBold(), block.isItalic());
                        float fontSize = block.getFontSize() > 0 ? block.getFontSize() : 12f;
                        float leading = fontSize * 1.35f;
                        Color textColor = PdfTextEditorService.parseHexColor(block.getColorHex(), Color.BLACK);

                        if (block.getType() == DocumentBlock.BlockType.HEADING_1) {
                            cursorY -= 8f;
                        } else if (block.getType() == DocumentBlock.BlockType.HEADING_2) {
                            cursorY -= 5f;
                        }

                        String rawText = block.getText() != null ? block.getText() : "";
                        String[] explicitLines = rawText.split("\\r?\\n", -1);

                        for (int lIdx = 0; lIdx < explicitLines.length; lIdx++) {
                            String lineStr = explicitLines[lIdx];
                            if (block.getType() == DocumentBlock.BlockType.BULLET_ITEM && lIdx == 0) {
                                lineStr = "*  " + lineStr;
                            }
                            List<String> wrapped = wrapTextLine(lineStr, font, fontSize, usableWidth);
                            for (String wLine : wrapped) {
                                if (cursorY - leading < margin) {
                                    cs.close();
                                    currentPage = new PDPage(pageSize);
                                    tempDoc.addPage(currentPage);
                                    cs = new PDPageContentStream(tempDoc, currentPage);
                                    cursorY = pageHeight - margin;
                                }
                                cursorY -= leading;
                                String safe = PdfTextEditorService.sanitizeForStandardFont(wLine);
                                float lineW = (font.getStringWidth(safe) / 1000f) * fontSize;
                                float drawX = margin;
                                if (block.getAlignment() == DocumentBlock.Alignment.CENTER) {
                                    drawX = margin + Math.max(0f, (usableWidth - lineW) / 2f);
                                } else if (block.getAlignment() == DocumentBlock.Alignment.RIGHT) {
                                    drawX = margin + Math.max(0f, usableWidth - lineW);
                                }

                                cs.setNonStrokingColor(textColor);
                                cs.beginText();
                                cs.setFont(font, fontSize);
                                cs.newLineAtOffset(drawX, cursorY);
                                cs.showText(safe);
                                cs.endText();

                                if (block.isUnderline() && !safe.trim().isEmpty()) {
                                    cs.setStrokingColor(textColor);
                                    cs.setLineWidth(Math.max(0.5f, fontSize * 0.05f));
                                    cs.moveTo(drawX, cursorY - 2f);
                                    cs.lineTo(drawX + lineW, cursorY - 2f);
                                    cs.stroke();
                                }
                            }
                        }
                        cursorY -= fontSize * 0.4f;
                    }
                }
            } finally {
                cs.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            tempDoc.save(baos);
            return Loader.loadPDF(baos.toByteArray());
        }
    }

    private List<String> wrapTextLine(String line, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> wrapped = new ArrayList<>();
        String safe = PdfTextEditorService.sanitizeForStandardFont(line);
        if (safe.isEmpty()) {
            wrapped.add("");
            return wrapped;
        }

        String[] words = safe.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            float width = (font.getStringWidth(candidate) / 1000f) * fontSize;
            if (width <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                wrapped.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            wrapped.add(current.toString());
        }
        return wrapped;
    }
}
