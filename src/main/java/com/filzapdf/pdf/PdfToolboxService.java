package com.filzapdf.pdf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

/**
 * Comprehensive PDF manipulation service providing custom page-by-page PDF merging,
 * splitting, page rotation, deletion, reordering, blank page insertion, watermarks,
 * page numbering, password encryption, and image export.
 */
public class PdfToolboxService {

    /**
     * Represents a specific page from a specific source document for custom merging.
     */
    public static class PageSource {
        private final int docIndex;      // 0-based index in the source files list
        private final File sourceFile;
        private final int pageIndex;     // 0-based page index in that source file

        public PageSource(int docIndex, File sourceFile, int pageIndex) {
            this.docIndex = docIndex;
            this.sourceFile = sourceFile;
            this.pageIndex = pageIndex;
        }

        public int getDocIndex() {
            return docIndex;
        }

        public File getSourceFile() {
            return sourceFile;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public String getDisplayLabel() {
            String name = sourceFile != null ? sourceFile.getName() : ("PDF #" + (docIndex + 1));
            return String.format("[Doc %s: %s] — Page %d",
                    docLetter(docIndex), name, pageIndex + 1);
        }

        public static String docLetter(int idx) {
            if (idx >= 0 && idx < 26) {
                return String.valueOf((char) ('A' + idx));
            }
            return String.valueOf(idx + 1);
        }
    }

    /**
     * Merges arbitrary pages from multiple source PDF files in the exact sequence requested.
     * Supports picking e.g. 1 page from Doc A, 2 pages from Doc B, all from A then all from B, etc.
     * Returns an independent in-memory PDDocument that can be viewed, edited, or saved.
     */
    public PDDocument mergeCustomPages(List<File> sourceFiles, List<PageSource> sequence) throws IOException {
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("Page sequence cannot be empty");
        }

        List<PDDocument> openedDocs = new ArrayList<>();
        try (PDDocument tempMerged = new PDDocument()) {
            for (File f : sourceFiles) {
                openedDocs.add(Loader.loadPDF(f));
            }

            for (PageSource ps : sequence) {
                int dIdx = ps.getDocIndex();
                if (dIdx < 0 || dIdx >= openedDocs.size()) continue;
                PDDocument srcDoc = openedDocs.get(dIdx);
                int pIdx = ps.getPageIndex();
                if (pIdx < 0 || pIdx >= srcDoc.getNumberOfPages()) continue;

                PDPage srcPage = srcDoc.getPage(pIdx);
                PDPage imported = tempMerged.importPage(srcPage);
                imported.setRotation(srcPage.getRotation());
            }

            // Materialize into a standalone PDDocument so closing source docs won't invalidate streams
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            tempMerged.save(baos);
            return Loader.loadPDF(baos.toByteArray());
        } finally {
            for (PDDocument d : openedDocs) {
                try { d.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Builds a sequence where all pages of each file are appended in order (All from A, then All from B, ...).
     */
    public List<PageSource> buildSequentialAllSequence(List<File> files, List<Integer> pageCounts) {
        List<PageSource> list = new ArrayList<>();
        for (int d = 0; d < files.size(); d++) {
            int count = pageCounts.get(d);
            for (int p = 0; p < count; p++) {
                list.add(new PageSource(d, files.get(d), p));
            }
        }
        return list;
    }

    /**
     * Builds an alternating/interleaved sequence (e.g., take `chunkA` pages from Doc A,
     * then `chunkB` pages from Doc B, repeating until all pages from all documents are consumed).
     */
    public List<PageSource> buildInterleavedSequence(List<File> files, List<Integer> pageCounts, List<Integer> chunksPerDoc) {
        List<PageSource> list = new ArrayList<>();
        if (files.isEmpty()) return list;

        int[] cursors = new int[files.size()];
        boolean anyRemaining = true;

        while (anyRemaining) {
            anyRemaining = false;
            for (int d = 0; d < files.size(); d++) {
                int total = pageCounts.get(d);
                int chunk = (chunksPerDoc != null && d < chunksPerDoc.size() && chunksPerDoc.get(d) > 0)
                        ? chunksPerDoc.get(d) : 1;

                for (int c = 0; c < chunk && cursors[d] < total; c++) {
                    list.add(new PageSource(d, files.get(d), cursors[d]++));
                }
                if (cursors[d] < total) {
                    anyRemaining = true;
                }
            }
        }
        return list;
    }

    /**
     * Parses a custom merge recipe string such as:
     * "A:1, B:1-2, A:2-end, B:3-end" or "1:1, 2:1-2, 1:all"
     *
     * @param recipe     user recipe string
     * @param files      list of loaded PDF files
     * @param pageCounts page counts for each loaded PDF file
     * @return ordered list of PageSource items
     */
    public List<PageSource> parseMergeRecipe(String recipe, List<File> files, List<Integer> pageCounts) {
        List<PageSource> result = new ArrayList<>();
        if (recipe == null || recipe.trim().isEmpty() || files.isEmpty()) {
            return result;
        }

        String[] tokens = recipe.split("[,;]+");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) continue;

            int colonIdx = token.indexOf(':');
            if (colonIdx <= 0 || colonIdx >= token.length() - 1) {
                throw new IllegalArgumentException("Invalid token '" + token + "'. Expected format like A:1 or B:1-2 or A:all");
            }

            String docPart = token.substring(0, colonIdx).trim().toUpperCase(Locale.ROOT);
            String pagePart = token.substring(colonIdx + 1).trim().toLowerCase(Locale.ROOT);

            int docIndex = parseDocIdentifier(docPart, files.size());
            int maxPages = pageCounts.get(docIndex);
            File file = files.get(docIndex);

            if (pagePart.equals("all") || pagePart.equals("*")) {
                for (int p = 0; p < maxPages; p++) {
                    result.add(new PageSource(docIndex, file, p));
                }
            } else if (pagePart.contains("-")) {
                String[] bounds = pagePart.split("-", 2);
                int startPage = parsePageNumber(bounds[0].trim(), maxPages);
                int endPage = bounds[1].trim().equals("end") || bounds[1].trim().equals("last")
                        ? maxPages
                        : parsePageNumber(bounds[1].trim(), maxPages);

                if (startPage <= endPage) {
                    for (int p = startPage; p <= endPage; p++) {
                        result.add(new PageSource(docIndex, file, p - 1));
                    }
                } else {
                    // Support reverse range e.g. A:5-1
                    for (int p = startPage; p >= endPage; p--) {
                        result.add(new PageSource(docIndex, file, p - 1));
                    }
                }
            } else {
                int singlePage = parsePageNumber(pagePart, maxPages);
                result.add(new PageSource(docIndex, file, singlePage - 1));
            }
        }

        return result;
    }

    private int parseDocIdentifier(String docPart, int totalDocs) {
        if (docPart.startsWith("DOC")) {
            docPart = docPart.substring(3).trim();
        } else if (docPart.startsWith("PDF")) {
            docPart = docPart.substring(3).trim();
        }
        int idx;
        if (docPart.length() == 1 && docPart.charAt(0) >= 'A' && docPart.charAt(0) <= 'Z') {
            idx = docPart.charAt(0) - 'A';
        } else {
            try {
                idx = Integer.parseInt(docPart) - 1;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unknown document identifier: " + docPart);
            }
        }
        if (idx < 0 || idx >= totalDocs) {
            throw new IllegalArgumentException("Document '" + docPart + "' is out of range (loaded " + totalDocs + " documents)");
        }
        return idx;
    }

    private int parsePageNumber(String str, int maxPages) {
        try {
            int p = Integer.parseInt(str);
            if (p < 1 || p > maxPages) {
                throw new IllegalArgumentException("Page " + p + " is out of range (1.." + maxPages + ")");
            }
            return p;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid page number: " + str);
        }
    }

    // ── Page Manipulation Tools ───────────────────────────────────────────────

    public void rotatePage(PDDocument doc, int pageIndex, int deltaDegrees) {
        if (doc == null || pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) return;
        PDPage page = doc.getPage(pageIndex);
        int current = page.getRotation();
        int next = ((current + deltaDegrees) % 360 + 360) % 360;
        page.setRotation(next);
    }

    public void rotateAllPages(PDDocument doc, int deltaDegrees) {
        if (doc == null) return;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            rotatePage(doc, i, deltaDegrees);
        }
    }

    public void deletePage(PDDocument doc, int pageIndex) {
        if (doc == null || doc.getNumberOfPages() <= 1) {
            throw new IllegalStateException("Cannot delete the only remaining page in the document.");
        }
        if (pageIndex >= 0 && pageIndex < doc.getNumberOfPages()) {
            doc.removePage(pageIndex);
        }
    }

    public void insertBlankPage(PDDocument doc, int afterPageIndex, PDRectangle pageSize) {
        if (doc == null) return;
        PDRectangle size = pageSize != null ? pageSize : PDRectangle.A4;
        PDPage newPage = new PDPage(size);

        if (afterPageIndex < 0 || doc.getNumberOfPages() == 0) {
            doc.getPages().insertBefore(newPage, doc.getPage(0));
        } else if (afterPageIndex >= doc.getNumberOfPages() - 1) {
            doc.addPage(newPage);
        } else {
            PDPage ref = doc.getPage(afterPageIndex);
            doc.getPages().insertAfter(newPage, ref);
        }
    }

    public PDDocument extractPages(PDDocument doc, List<Integer> zeroBasedIndices) throws IOException {
        try (PDDocument temp = new PDDocument()) {
            for (int idx : zeroBasedIndices) {
                if (idx >= 0 && idx < doc.getNumberOfPages()) {
                    PDPage src = doc.getPage(idx);
                    PDPage imp = temp.importPage(src);
                    imp.setRotation(src.getRotation());
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            temp.save(baos);
            return Loader.loadPDF(baos.toByteArray());
        }
    }

    public void addWatermarkToAllPages(
            PDDocument doc,
            String watermarkText,
            float fontSize,
            String colorHex,
            float opacity) throws IOException {

        if (doc == null || watermarkText == null || watermarkText.trim().isEmpty()) return;
        String safeText = PdfTextEditorService.sanitizeForStandardFont(watermarkText.trim());
        PDFont font = new PDType1Font(FontName.HELVETICA_BOLD);
        Color color = PdfTextEditorService.parseHexColor(colorHex, new Color(180, 180, 180));

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDRectangle box = page.getMediaBox();
            float cx = box.getWidth() / 2f;
            float cy = box.getHeight() / 2f;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(Math.max(0.05f, Math.min(1.0f, opacity)));
                cs.setGraphicsStateParameters(gs);

                cs.setNonStrokingColor(color);
                cs.beginText();
                cs.setFont(font, fontSize);
                Matrix m = Matrix.getRotateInstance(Math.toRadians(45), cx, cy);
                float textWidth = (font.getStringWidth(safeText) / 1000f) * fontSize;
                m.translate(-textWidth / 2f, -fontSize / 3f);
                cs.setTextMatrix(m);
                cs.showText(safeText);
                cs.endText();
            }
        }
    }

    public void addPageNumbers(PDDocument doc, boolean bottomFooter, float fontSize) throws IOException {
        if (doc == null) return;
        PDFont font = new PDType1Font(FontName.HELVETICA);
        int total = doc.getNumberOfPages();

        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            PDRectangle box = page.getMediaBox();
            String label = "Page " + (i + 1) + " of " + total;
            float textWidth = (font.getStringWidth(label) / 1000f) * fontSize;
            float x = (box.getWidth() - textWidth) / 2f;
            float y = bottomFooter ? 24f : (box.getHeight() - 28f);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(new Color(60, 60, 60));
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(x, y);
                cs.showText(label);
                cs.endText();
            }
        }
    }

    public void protectDocumentWithPassword(PDDocument doc, String userPassword, String ownerPassword) throws IOException {
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(true);
        String owner = (ownerPassword == null || ownerPassword.isEmpty()) ? userPassword : ownerPassword;
        StandardProtectionPolicy spp = new StandardProtectionPolicy(owner, userPassword, ap);
        spp.setEncryptionKeyLength(256);
        spp.setPermissions(ap);
        doc.protect(spp);
    }

    public List<File> exportAllPagesAsImages(PDDocument doc, File targetDirectory, String baseName, float dpi) throws IOException {
        List<File> exported = new ArrayList<>();
        if (doc == null || targetDirectory == null) return exported;
        if (!targetDirectory.exists()) targetDirectory.mkdirs();

        PDFRenderer renderer = new PDFRenderer(doc);
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            BufferedImage img = renderer.renderImageWithDPI(i, dpi);
            File out = new File(targetDirectory, String.format("%s_page_%03d.png", baseName, i + 1));
            ImageIO.write(img, "PNG", out);
            exported.add(out);
        }
        return exported;
    }
}
