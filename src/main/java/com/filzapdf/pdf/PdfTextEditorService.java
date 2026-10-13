package com.filzapdf.pdf;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Service for recognizing text blocks and fonts on PDF pages, editing existing text
 * in-place, adding new text boxes, and embedding images onto PDF pages.
 */
public class PdfTextEditorService {

    /**
     * Extracts recognized text lines/blocks on the given 0-based page index along with
     * font family, bold/italic flags, font size in points, and bounding box coordinates.
     */
    public List<RecognizedTextBlock> recognizePageText(PDDocument document, int pageIndex) throws IOException {
        if (document == null || pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            return new ArrayList<>();
        }

        PDPage page = document.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeight = mediaBox.getHeight();

        List<RecognizedTextBlock> blocks = new ArrayList<>();
        List<TextPosition> rawPositions = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                if (text != null && text.getUnicode() != null && !text.getUnicode().isEmpty()) {
                    rawPositions.add(text);
                }
            }
        };

        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        synchronized (document) {
            stripper.getText(document);
        }

        List<List<TextPosition>> segments = splitIntoSegments(rawPositions);
        for (List<TextPosition> seg : segments) {
            if (seg.isEmpty()) continue;
            StringBuilder sb = new StringBuilder();
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minTopY = Float.MAX_VALUE;
            float maxBottomY = -Float.MAX_VALUE;

            TextPosition first = seg.get(0);
            float fontSize = first.getFontSizeInPt();
            if (fontSize <= 0) {
                fontSize = first.getYScale();
            }
            if (fontSize <= 0) {
                fontSize = 12f;
            }

            String rawFont = "Helvetica";
            if (first.getFont() != null && first.getFont().getName() != null) {
                rawFont = first.getFont().getName();
            }

            for (int idx = 0; idx < seg.size(); idx++) {
                TextPosition tp = seg.get(idx);
                if (idx > 0) {
                    TextPosition prev = seg.get(idx - 1);
                    float prevEnd = prev.getXDirAdj() + prev.getWidthDirAdj();
                    float gap = tp.getXDirAdj() - prevEnd;
                    String prevU = prev.getUnicode() != null ? prev.getUnicode() : "";
                    String curU = tp.getUnicode() != null ? tp.getUnicode() : "";
                    float spaceThreshold = Math.max(1.5f, prev.getWidthOfSpace() * 0.35f);
                    if (gap > spaceThreshold && !prevU.endsWith(" ") && !curU.startsWith(" ")) {
                        sb.append(' ');
                    }
                }
                String u = tp.getUnicode();
                if (u != null) {
                    sb.append(u);
                }
                float x = tp.getXDirAdj();
                float w = tp.getWidthDirAdj();
                float yDir = tp.getYDirAdj();
                float h = tp.getHeightDir();
                if (h <= 0) h = fontSize;

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x + w);
                minTopY = Math.min(minTopY, yDir - h * 1.15f);
                maxBottomY = Math.max(maxBottomY, yDir + h * 0.30f);
            }

            String segText = sb.toString().trim();
            if (segText.isEmpty()) continue;

            String cleanRawFont = stripSubsetPrefix(rawFont);
            String family = detectFontFamily(cleanRawFont);
            boolean bold = detectBold(cleanRawFont, first.getFont());
            boolean italic = detectItalic(cleanRawFont, first.getFont());

            float boxX = Math.max(0f, minX);
            float boxY = Math.max(0f, minTopY);
            float boxW = Math.max(12f, maxX - minX);
            float boxH = Math.max(fontSize * 1.15f, maxBottomY - minTopY);

            float baselineTopY = first.getYDirAdj();
            float pdfBaselineX = first.getXDirAdj();
            float pdfBaselineY = pageHeight - baselineTopY;

            blocks.add(new RecognizedTextBlock(
                    pageIndex,
                    segText,
                    cleanRawFont,
                    family,
                    bold,
                    italic,
                    Math.round(fontSize * 10f) / 10f,
                    boxX,
                    boxY,
                    boxW,
                    boxH,
                    pdfBaselineX,
                    pdfBaselineY,
                    "#000000"
            ));
        }

        // Filter out blocks that were previously replaced/erased on this page
        List<String> replacedEntries = getReplacedEntries(page);
        if (!replacedEntries.isEmpty()) {
            List<String> remainingToSkip = new ArrayList<>(replacedEntries);
            blocks.removeIf(b -> {
                for (int i = 0; i < remainingToSkip.size(); i++) {
                    if (remainingToSkip.get(i).equals(b.getText())) {
                        remainingToSkip.remove(i);
                        return true;
                    }
                }
                return false;
            });
        }

        blocks.sort((a, b) -> {
            int cmpY = Float.compare(a.getY(), b.getY());
            if (Math.abs(a.getY() - b.getY()) > 4.0f) return cmpY;
            return Float.compare(a.getX(), b.getX());
        });

        return blocks;
    }

    private static final org.apache.pdfbox.cos.COSName FILZA_REPLACED_KEY =
            org.apache.pdfbox.cos.COSName.getPDFName("FilzaReplacedTexts");

    private void recordReplacedBlock(PDPage page, String oldText) {
        if (oldText == null || oldText.isEmpty()) return;
        org.apache.pdfbox.cos.COSBase base = page.getCOSObject().getDictionaryObject(FILZA_REPLACED_KEY);
        org.apache.pdfbox.cos.COSArray arr;
        if (base instanceof org.apache.pdfbox.cos.COSArray existing) {
            arr = existing;
        } else {
            arr = new org.apache.pdfbox.cos.COSArray();
            page.getCOSObject().setItem(FILZA_REPLACED_KEY, arr);
        }
        arr.add(new org.apache.pdfbox.cos.COSString(oldText));
    }

    private List<String> getReplacedEntries(PDPage page) {
        List<String> list = new ArrayList<>();
        org.apache.pdfbox.cos.COSBase base = page.getCOSObject().getDictionaryObject(FILZA_REPLACED_KEY);
        if (base instanceof org.apache.pdfbox.cos.COSArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                org.apache.pdfbox.cos.COSBase item = arr.getObject(i);
                if (item instanceof org.apache.pdfbox.cos.COSString cs) {
                    list.add(cs.getString());
                }
            }
        }
        return list;
    }

    private List<List<TextPosition>> splitIntoSegments(List<TextPosition> positions) {
        List<List<TextPosition>> result = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();

        for (int i = 0; i < positions.size(); i++) {
            TextPosition tp = positions.get(i);
            if (current.isEmpty()) {
                current.add(tp);
            } else {
                TextPosition prev = current.get(current.size() - 1);
                float prevEnd = prev.getXDirAdj() + prev.getWidthDirAdj();
                float gap = tp.getXDirAdj() - prevEnd;
                float threshold = Math.max(prev.getFontSizeInPt(), 10f) * 3.5f;
                float yDiff = Math.abs(tp.getYDirAdj() - prev.getYDirAdj());

                if (gap < -2.0f || gap > threshold || yDiff > 4.0f) {
                    result.add(current);
                    current = new ArrayList<>();
                }
                current.add(tp);
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    /**
     * Replaces an existing RecognizedTextBlock on its page by covering the original text bounding box
     * with the background fill color and rendering the updated text with the chosen font, size, and color.
     */
    public void replaceTextBlock(
            PDDocument document,
            RecognizedTextBlock block,
            String newText,
            String fontFamily,
            boolean bold,
            boolean italic,
            float fontSize,
            String textColorHex,
            String backgroundFillHex) throws IOException {

        PDPage page = document.getPage(block.getPageIndex());
        recordReplacedBlock(page, block.getText());
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeight = mediaBox.getHeight();

        PDFont font = resolveStandardFont(fontFamily, bold, italic);
        Color textColor = parseHexColor(textColorHex, Color.BLACK);
        Color bgColor = parseHexColor(backgroundFillHex, Color.WHITE);

        float rectX = Math.max(0f, block.getX() - 1.5f);
        float rectW = block.getWidth() + 4.0f;
        float rectH = block.getHeight() + 3.0f;
        float rectBottomY = pageHeight - (block.getY() + block.getHeight()) - 1.5f;

        try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            // Cover original text
            cs.setNonStrokingColor(bgColor);
            cs.addRect(rectX, rectBottomY, rectW, rectH);
            cs.fill();

            if (newText != null && !newText.isEmpty()) {
                cs.setNonStrokingColor(textColor);
                String[] lines = newText.split("\\r?\\n");
                float lineY = block.getPdfY();
                float lineLeading = fontSize * 1.2f;

                for (String line : lines) {
                    String safeLine = sanitizeForStandardFont(line);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(block.getPdfX(), lineY);
                    cs.showText(safeLine);
                    cs.endText();
                    lineY -= lineLeading;
                }
            }
        }
    }

    /**
     * Adds new styled text at any top-left coordinate (xTopLeft, yTopLeft) on a PDF page.
     */
    public void addTextToPage(
            PDDocument document,
            int pageIndex,
            String text,
            float xTopLeft,
            float yTopLeft,
            String fontFamily,
            boolean bold,
            boolean italic,
            float fontSize,
            String textColorHex) throws IOException {

        if (text == null || text.isEmpty()) return;
        PDPage page = document.getPage(pageIndex);
        float pageHeight = page.getMediaBox().getHeight();

        PDFont font = resolveStandardFont(fontFamily, bold, italic);
        Color textColor = parseHexColor(textColorHex, Color.BLACK);

        float baselineY = pageHeight - yTopLeft - fontSize;

        try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            cs.setNonStrokingColor(textColor);
            String[] lines = text.split("\\r?\\n");
            float currentY = baselineY;
            for (String line : lines) {
                String safeLine = sanitizeForStandardFont(line);
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(xTopLeft, currentY);
                cs.showText(safeLine);
                cs.endText();
                currentY -= fontSize * 1.25f;
            }
        }
    }

    /**
     * Embeds an image onto a PDF page at the specified top-left coordinates and dimensions.
     */
    public void addImageToPage(
            PDDocument document,
            int pageIndex,
            File imageFile,
            float xTopLeft,
            float yTopLeft,
            float width,
            float height) throws IOException {

        synchronized (document) {
            PDPage page = document.getPage(pageIndex);
            float pageHeight = page.getMediaBox().getHeight();
            PDImageXObject pdImage = PdfCreatorService.loadRobustImageForPdf(document, imageFile);

            float pdfY = pageHeight - yTopLeft - height;
            try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                cs.drawImage(pdImage, xTopLeft, pdfY, width, height);
            }
        }
    }

    public static PDFont resolveStandardFont(String family, boolean bold, boolean italic) {
        String norm = family == null ? "helvetica" : family.toLowerCase();
        if (norm.contains("times") || norm.contains("serif") || norm.contains("georgia") || norm.contains("cambria")) {
            if (bold && italic) return new PDType1Font(FontName.TIMES_BOLD_ITALIC);
            if (bold) return new PDType1Font(FontName.TIMES_BOLD);
            if (italic) return new PDType1Font(FontName.TIMES_ITALIC);
            return new PDType1Font(FontName.TIMES_ROMAN);
        } else if (norm.contains("courier") || norm.contains("mono") || norm.contains("consolas")) {
            if (bold && italic) return new PDType1Font(FontName.COURIER_BOLD_OBLIQUE);
            if (bold) return new PDType1Font(FontName.COURIER_BOLD);
            if (italic) return new PDType1Font(FontName.COURIER_OBLIQUE);
            return new PDType1Font(FontName.COURIER);
        } else {
            if (bold && italic) return new PDType1Font(FontName.HELVETICA_BOLD_OBLIQUE);
            if (bold) return new PDType1Font(FontName.HELVETICA_BOLD);
            if (italic) return new PDType1Font(FontName.HELVETICA_OBLIQUE);
            return new PDType1Font(FontName.HELVETICA);
        }
    }

    public static String stripSubsetPrefix(String rawFontName) {
        if (rawFontName == null) return "Helvetica";
        int plusIdx = rawFontName.indexOf('+');
        if (plusIdx == 6 && rawFontName.length() > 7) {
            return rawFontName.substring(7);
        }
        return rawFontName;
    }

    public static String detectFontFamily(String fontName) {
        if (fontName == null) return "Helvetica";
        String lower = fontName.toLowerCase();
        if (lower.contains("times") || lower.contains("georgia") || lower.contains("cambria")
                || lower.contains("garamond") || lower.contains("minion") || lower.contains("palatino")) {
            return "Times";
        }
        if (lower.contains("courier") || lower.contains("consolas") || lower.contains("mono") || lower.contains("menlo")) {
            return "Courier";
        }
        return "Helvetica";
    }

    public static boolean detectBold(String fontName, PDFont font) {
        if (fontName != null) {
            String lower = fontName.toLowerCase();
            if (lower.contains("bold") || lower.contains("black") || lower.contains("heavy") || lower.contains("semibold")) {
                return true;
            }
        }
        if (font != null && font.getFontDescriptor() != null) {
            return font.getFontDescriptor().isForceBold() || font.getFontDescriptor().getFontWeight() >= 600;
        }
        return false;
    }

    public static boolean detectItalic(String fontName, PDFont font) {
        if (fontName != null) {
            String lower = fontName.toLowerCase();
            if (lower.contains("italic") || lower.contains("oblique") || lower.contains("slanted")) {
                return true;
            }
        }
        if (font != null && font.getFontDescriptor() != null) {
            return font.getFontDescriptor().isItalic() || font.getFontDescriptor().getItalicAngle() != 0;
        }
        return false;
    }

    public static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || hex.trim().isEmpty()) return fallback;
        String clean = hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() == 8) clean = clean.substring(0, 6);
        try {
            int rgb = Integer.parseInt(clean, 16);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String sanitizeForStandardFont(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u2018' || c == '\u2019') sb.append('\'');
            else if (c == '\u201C' || c == '\u201D') sb.append('"');
            else if (c == '\u2013' || c == '\u2014') sb.append('-');
            else if (c == '\u2022') sb.append('*');
            else if (c == '\t') sb.append("    ");
            else if (c >= 32 && c <= 255) sb.append(c);
            else sb.append('?');
        }
        return sb.toString();
    }
}
