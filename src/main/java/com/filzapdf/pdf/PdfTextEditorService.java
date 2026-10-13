package com.filzapdf.pdf;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Service for recognizing text blocks and fonts on PDF pages, removing or replacing
 * text tokens in page content streams, adding new text boxes, and embedding images.
 */
public class PdfTextEditorService {

    private static final COSName FILZA_REPLACED_KEY = COSName.getPDFName("FilzaReplacedTexts");

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

        // Filter out any residual blocks recorded as replaced on this page
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

    /**
     * Permanently removes text within the given top-left coordinate rectangle {@code (rx, ry, rw, rh)}
     * from the PDF page's underlying content stream so that redacted text cannot be extracted
     * or recovered from the saved file.
     *
     * @return number of text blocks/tokens scrubbed from the page content stream
     */
    public int redactTextInRegion(PDDocument document, int pageIndex, float rx, float ry, float rw, float rh)
            throws IOException {
        synchronized (document) {
            List<RecognizedTextBlock> pageBlocks = recognizePageText(document, pageIndex);
            List<String> targets = new ArrayList<>();

            float rRight = rx + rw;
            float rBottom = ry + rh;

            for (RecognizedTextBlock b : pageBlocks) {
                float bRight = b.getX() + b.getWidth();
                float bBottom = b.getY() + b.getHeight();
                boolean intersects = b.getX() < rRight && bRight > rx && b.getY() < rBottom && bBottom > ry;
                if (intersects && b.getText() != null && !b.getText().isBlank()) {
                    targets.add(b.getText().trim());
                }
            }

            PDPage page = document.getPage(pageIndex);
            int removedFromStream = removeStringsFromPageContentStream(document, page, targets);
            for (String t : targets) {
                recordReplacedBlock(page, t);
            }
            return Math.max(targets.size(), removedFromStream);
        }
    }

    /**
     * Parses a page's PDF content stream and scrubs any string literals ({@code Tj}, {@code TJ},
     * {@code '}, {@code "}) matching or containing the target strings.
     */
    public int removeStringsFromPageContentStream(PDDocument document, PDPage page, List<String> targets)
            throws IOException {
        if (targets == null || targets.isEmpty()) return 0;

        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> tokens = parser.parse();
        if (tokens == null || tokens.isEmpty()) return 0;

        int modifiedCount = 0;
        List<String> remaining = new ArrayList<>();
        for (String t : targets) {
            if (t != null && !t.isBlank()) {
                remaining.add(t.trim());
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            Object tok = tokens.get(i);
            if (tok instanceof Operator op) {
                String opName = op.getName();
                if ("Tj".equals(opName) || "'".equals(opName) || "\"".equals(opName)) {
                    if (i > 0 && tokens.get(i - 1) instanceof COSString cosStr) {
                        String val = cosStr.getString();
                        String scrubbed = scrubTargetSubstrings(val, remaining);
                        if (!scrubbed.equals(val)) {
                            tokens.set(i - 1, new COSString(scrubbed));
                            modifiedCount++;
                        }
                    }
                } else if ("TJ".equals(opName)) {
                    if (i > 0 && tokens.get(i - 1) instanceof COSArray arr) {
                        StringBuilder combined = new StringBuilder();
                        for (int j = 0; j < arr.size(); j++) {
                            COSBase elem = arr.getObject(j);
                            if (elem instanceof COSString cs) {
                                combined.append(cs.getString());
                            }
                        }
                        String fullTj = combined.toString();
                        String scrubbedFull = scrubTargetSubstrings(fullTj, remaining);
                        if (!scrubbedFull.equals(fullTj)) {
                            // Replace array contents with single scrubbed COSString
                            arr.clear();
                            if (!scrubbedFull.isEmpty()) {
                                arr.add(new COSString(scrubbedFull));
                            }
                            modifiedCount++;
                        }
                    }
                }
            }
        }

        if (modifiedCount > 0) {
            PDStream newContents = new PDStream(document);
            try (OutputStream out = newContents.createOutputStream(COSName.FLATE_DECODE)) {
                ContentStreamWriter writer = new ContentStreamWriter(out);
                writer.writeTokens(tokens);
            }
            page.setContents(newContents);
        }
        return modifiedCount;
    }

    private String scrubTargetSubstrings(String source, List<String> targets) {
        if (source == null || source.isEmpty() || targets.isEmpty()) return source;
        String result = source;
        String normSource = normalizeWhitespace(result);

        for (int i = 0; i < targets.size(); i++) {
            String target = targets.get(i);
            String normTarget = normalizeWhitespace(target);
            if (normTarget.isEmpty()) continue;

            if (result.equals(target) || normSource.equals(normTarget)) {
                targets.remove(i);
                return "";
            }
            int idx = result.indexOf(target);
            if (idx >= 0) {
                result = result.substring(0, idx) + result.substring(idx + target.length());
                targets.remove(i);
                return result;
            }
        }
        return result;
    }

    private static String normalizeWhitespace(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private void recordReplacedBlock(PDPage page, String oldText) {
        if (oldText == null || oldText.isEmpty()) return;
        COSBase base = page.getCOSObject().getDictionaryObject(FILZA_REPLACED_KEY);
        COSArray arr;
        if (base instanceof COSArray existing) {
            arr = existing;
        } else {
            arr = new COSArray();
            page.getCOSObject().setItem(FILZA_REPLACED_KEY, arr);
        }
        arr.add(new COSString(oldText));
    }

    private List<String> getReplacedEntries(PDPage page) {
        List<String> list = new ArrayList<>();
        COSBase base = page.getCOSObject().getDictionaryObject(FILZA_REPLACED_KEY);
        if (base instanceof COSArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                COSBase item = arr.getObject(i);
                if (item instanceof COSString cs) {
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
     * Replaces an existing RecognizedTextBlock on its page by first attempting to remove the
     * original text operator from the page's content stream (preserving underlying backgrounds
     * whenever possible), and then writing the replacement text with Unicode TrueType or
     * Standard 14 font support.
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

        synchronized (document) {
            PDPage page = document.getPage(block.getPageIndex());
            int removedTokens = removeStringsFromPageContentStream(document, page, List.of(block.getText()));
            recordReplacedBlock(page, block.getText());

            PDRectangle mediaBox = page.getMediaBox();
            float pageHeight = mediaBox.getHeight();

            PDFont font = resolveFontForText(document, fontFamily, bold, italic, newText);
            Color textColor = parseHexColor(textColorHex, Color.BLACK);
            Color bgColor = parseHexColor(backgroundFillHex, null);

            float rectX = Math.max(0f, block.getX() - 1.5f);
            float rectW = block.getWidth() + 4.0f;
            float rectH = block.getHeight() + 3.0f;
            float rectBottomY = pageHeight - (block.getY() + block.getHeight()) - 1.5f;

            // Only paint background box if content-stream token could not be directly excised
            // (e.g. complex CID/subset stream) OR if the caller explicitly chose a non-white fill color.
            boolean explicitNonWhiteBg = bgColor != null && !(bgColor.getRed() == 255 && bgColor.getGreen() == 255 && bgColor.getBlue() == 255);
            boolean shouldPaintCoverRect = (removedTokens == 0 && bgColor != null) || explicitNonWhiteBg;

            try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                if (shouldPaintCoverRect && bgColor != null) {
                    cs.setNonStrokingColor(bgColor);
                    cs.addRect(rectX, rectBottomY, rectW, rectH);
                    cs.fill();
                }

                if (newText != null && !newText.isEmpty()) {
                    cs.setNonStrokingColor(textColor);
                    String[] lines = newText.split("\\r?\\n");
                    float lineY = block.getPdfY();
                    float lineLeading = fontSize * 1.2f;

                    for (String line : lines) {
                        String encodeable = filterEncodeable(font, line);
                        if (!encodeable.isEmpty()) {
                            cs.beginText();
                            cs.setFont(font, fontSize);
                            cs.newLineAtOffset(block.getPdfX(), lineY);
                            cs.showText(encodeable);
                            cs.endText();
                        }
                        lineY -= lineLeading;
                    }
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
        synchronized (document) {
            PDPage page = document.getPage(pageIndex);
            float pageHeight = page.getMediaBox().getHeight();

            PDFont font = resolveFontForText(document, fontFamily, bold, italic, text);
            Color textColor = parseHexColor(textColorHex, Color.BLACK);

            float baselineY = pageHeight - yTopLeft - fontSize;

            try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(textColor);
                String[] lines = text.split("\\r?\\n");
                float currentY = baselineY;
                for (String line : lines) {
                    String encodeable = filterEncodeable(font, line);
                    if (!encodeable.isEmpty()) {
                        cs.beginText();
                        cs.setFont(font, fontSize);
                        cs.newLineAtOffset(xTopLeft, currentY);
                        cs.showText(encodeable);
                        cs.endText();
                    }
                    currentY -= fontSize * 1.25f;
                }
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

    /**
     * Resolves a PDFont capable of encoding {@code sampleText}. Uses Standard 14 fonts for
     * fast, compact Latin/WinAnsi text, and automatically embeds a system Unicode TrueType font
     * ({@link PDType0Font}) when non-WinAnsi / Unicode characters are present.
     */
    public static PDFont resolveFontForText(
            PDDocument doc, String family, boolean bold, boolean italic, String sampleText) {
        PDFont standard = resolveStandardFont(family, bold, italic);
        if (sampleText == null || sampleText.isEmpty() || canFontEncodeAll(standard, sampleText)) {
            return standard;
        }
        if (doc != null) {
            PDFont unicodeFont = tryLoadSystemUnicodeFont(doc, family, bold, italic);
            if (unicodeFont != null) {
                return unicodeFont;
            }
        }
        return standard;
    }

    private static boolean canFontEncodeAll(PDFont font, String text) {
        try {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\r' || c == '\n') continue;
                font.encode(String.valueOf(c));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static PDFont tryLoadSystemUnicodeFont(PDDocument doc, String family, boolean bold, boolean italic) {
        String winFonts = System.getenv("WINDIR") != null ? System.getenv("WINDIR") + "\\Fonts\\" : "C:\\Windows\\Fonts\\";
        List<String> candidates = new ArrayList<>();
        String norm = family == null ? "helvetica" : family.toLowerCase();
        if (norm.contains("times") || norm.contains("serif")) {
            candidates.add(winFonts + (bold ? "timesbd.ttf" : "times.ttf"));
        } else if (norm.contains("courier") || norm.contains("mono")) {
            candidates.add(winFonts + (bold ? "courbd.ttf" : "cour.ttf"));
        }
        candidates.add(winFonts + (bold ? "segoeuib.ttf" : "segoeui.ttf"));
        candidates.add(winFonts + (bold ? "arialbd.ttf" : "arial.ttf"));
        candidates.add("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        candidates.add("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf");
        candidates.add("/System/Library/Fonts/Supplemental/Arial.ttf");

        for (String path : candidates) {
            File f = new File(path);
            if (f.isFile() && f.canRead()) {
                try {
                    return PDType0Font.load(doc, f);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static String filterEncodeable(PDFont font, String line) {
        if (line == null || line.isEmpty()) return "";
        if (font instanceof PDType0Font) {
            StringBuilder sb = new StringBuilder(line.length());
            for (int i = 0; i < line.length(); i++) {
                String ch = String.valueOf(line.charAt(i));
                try {
                    font.encode(ch);
                    sb.append(ch);
                } catch (Exception ignored) {
                    // Skip unmapped glyph in TrueType font
                }
            }
            return sb.toString();
        }
        return sanitizeForStandardFont(line);
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
