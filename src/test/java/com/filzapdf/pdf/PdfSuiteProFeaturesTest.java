package com.filzapdf.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.filzapdf.annotations.AddAnnotationCommand;
import com.filzapdf.annotations.Annotation;
import com.filzapdf.annotations.AnnotationManager;
import com.filzapdf.annotations.AnnotationType;
import com.filzapdf.annotations.PdfAnnotationPersistenceService;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock.BlockType;
import com.filzapdf.pdf.PdfToolboxService.PageSource;

class PdfSuiteProFeaturesTest {

    @TempDir
    Path tempDir;

    @Test
    void recognizesAndEditsTextAndFontInPdfAndScrubsOriginalContentStream() throws Exception {
        PdfTextEditorService editor = new PdfTextEditorService();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(FontName.TIMES_BOLD), 18f);
                cs.newLineAtOffset(72f, 700f);
                cs.showText("Original Header Title");
                cs.endText();
            }

            List<RecognizedTextBlock> blocks = editor.recognizePageText(doc, 0);
            assertFalse(blocks.isEmpty(), "Should recognize text block on page");

            RecognizedTextBlock first = blocks.get(0);
            assertTrue(first.getText().contains("Original Header Title"));
            assertEquals("Times", first.getFontFamily());
            assertTrue(first.isBold());
            assertEquals(18.0f, first.getFontSize(), 0.5f);

            // Replace text & change font to Courier Italic 14pt
            editor.replaceTextBlock(
                    doc, first, "Updated Custom Title", "Courier", false, true, 14f, "#003399", "#FFFFFF"
            );

            // Add brand new text block
            editor.addTextToPage(doc, 0, "Extra Added Line", 100f, 200f, "Helvetica", true, false, 12f, "#000000");

            List<RecognizedTextBlock> afterBlocks = editor.recognizePageText(doc, 0);
            boolean foundUpdated = afterBlocks.stream().anyMatch(b -> b.getText().contains("Updated Custom Title"));
            boolean foundAdded = afterBlocks.stream().anyMatch(b -> b.getText().contains("Extra Added Line"));
            boolean stillHasOriginal = afterBlocks.stream().anyMatch(b -> b.getText().contains("Original Header Title"));
            assertTrue(foundUpdated, "Should recognize updated text after replacement");
            assertTrue(foundAdded, "Should recognize newly added text on page");
            assertFalse(stillHasOriginal, "Original text should be scrubbed from the page content stream");
        }
    }

    @Test
    void secureRedactionRemovesTextFromContentStreamAndCannotBeExtractedAfterSave() throws Exception {
        PdfAnnotationPersistenceService persistence = new PdfAnnotationPersistenceService();
        AnnotationManager manager = new AnnotationManager();
        File savedPdf = tempDir.resolve("redacted_document.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            float pageHeight = page.getMediaBox().getHeight(); // 792 pt

            // Write a public header near top (y_pdf = 700 -> topDownY ~ 74..92)
            // and a secret SSN line below it (y_pdf = 600 -> topDownY ~ 174..192)
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(FontName.HELVETICA_BOLD), 14f);
                cs.newLineAtOffset(72f, 700f);
                cs.showText("Public Header: Annual Financial Summary");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(FontName.HELVETICA), 14f);
                cs.newLineAtOffset(72f, 600f);
                cs.showText("CONFIDENTIAL-SSN-999-00-1234");
                cs.endText();
            }

            // Create a REDACT annotation covering the secret line at y_pdf=600 (topDownY = 792 - 600 = 192)
            float redactTopDownY = pageHeight - 620f; // 172 pt from top
            Annotation redact = new Annotation();
            redact.setType(AnnotationType.REDACT);
            redact.setPageIndex(0);
            redact.setX(60);
            redact.setY(redactTopDownY);
            redact.setWidth(320);
            redact.setHeight(35);
            redact.setColor("#000000");
            manager.executeCommand(new AddAnnotationCommand(manager, redact));

            persistence.burnAnnotationsIntoDocument(doc, manager);
            doc.save(savedPdf);
        }

        // Reload saved PDF from disk and verify via PDFTextStripper that the redacted text is completely unrecoverable
        try (PDDocument reloaded = Loader.loadPDF(savedPdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(reloaded);

            assertTrue(extractedText.contains("Public Header: Annual Financial Summary"),
                    "Unredacted public text must remain intact");
            assertFalse(extractedText.contains("CONFIDENTIAL-SSN"),
                    "Redacted text must be scrubbed from the PDF content stream and unrecoverable");
            assertFalse(extractedText.contains("999-00-1234"),
                    "Redacted sensitive numbers must not be extractable after redaction");
        }
    }

    @Test
    void mergesTwoPdfsWithCustomPageSequenceAndRecipes() throws Exception {
        PdfToolboxService toolbox = new PdfToolboxService();
        PdfTextEditorService editor = new PdfTextEditorService();

        File pdfA = tempDir.resolve("docA.pdf").toFile();
        File pdfB = tempDir.resolve("docB.pdf").toFile();

        createSampleMultiPagePdf(pdfA, "DocA", 3);
        createSampleMultiPagePdf(pdfB, "DocB", 4);

        List<File> files = List.of(pdfA, pdfB);
        List<Integer> counts = List.of(3, 4);

        // 1 page from A, 2 pages from B, then remaining from A ("A:1, B:1-2, A:2-end")
        List<PageSource> recipeSeq = toolbox.parseMergeRecipe("A:1, B:1-2, A:2-end", files, counts);
        assertEquals(5, recipeSeq.size());
        assertEquals(0, recipeSeq.get(0).getDocIndex());
        assertEquals(0, recipeSeq.get(0).getPageIndex());
        assertEquals(1, recipeSeq.get(1).getDocIndex());
        assertEquals(0, recipeSeq.get(1).getPageIndex());
        assertEquals(1, recipeSeq.get(2).getDocIndex());
        assertEquals(1, recipeSeq.get(2).getPageIndex());

        try (PDDocument merged = toolbox.mergeCustomPages(files, recipeSeq)) {
            assertEquals(5, merged.getNumberOfPages());
            assertTrue(editor.recognizePageText(merged, 0).get(0).getText().contains("DocA_Page_1"));
            assertTrue(editor.recognizePageText(merged, 1).get(0).getText().contains("DocB_Page_1"));
            assertTrue(editor.recognizePageText(merged, 2).get(0).getText().contains("DocB_Page_2"));
            assertTrue(editor.recognizePageText(merged, 3).get(0).getText().contains("DocA_Page_2"));
            assertTrue(editor.recognizePageText(merged, 4).get(0).getText().contains("DocA_Page_3"));
        }

        // All from A then All from B
        List<PageSource> allSeq = toolbox.buildSequentialAllSequence(files, counts);
        assertEquals(7, allSeq.size());
    }

    @Test
    void createsAndRendersPdfPagesFromUploadedImagesAndAppendsToOpenPdf() throws Exception {
        PdfCreatorService creator = new PdfCreatorService();

        File img1 = tempDir.resolve("img1.png").toFile();
        File img2 = tempDir.resolve("img2.jpg").toFile();
        File img3 = tempDir.resolve("img3_argb.png").toFile();
        writeSamplePng(img1, 300, 200, Color.BLUE);
        writeSampleJpg(img2, 400, 300, Color.MAGENTA);
        writeSampleArgbPng(img3, 250, 250, new Color(124, 58, 237, 200));

        try (PDDocument pdf = creator.createPdfFromImages(List.of(img1, img2), "A4", false, 20f)) {
            assertEquals(2, pdf.getNumberOfPages());

            // Verify PdfPageRenderer renders both image pages cleanly at crisp HiDPI (150 DPI)
            com.filzapdf.renderer.PdfPageRenderer pageRenderer = new com.filzapdf.renderer.PdfPageRenderer(pdf);
            BufferedImage rendered0 = pageRenderer.renderPage(0, 150f);
            BufferedImage rendered1 = pageRenderer.renderPage(1, 150f);
            assertNotNull(rendered0);
            assertNotNull(rendered1);
            assertTrue(rendered0.getWidth() > 1000 && rendered0.getHeight() > 1000);
            assertTrue(rendered1.getWidth() > 1000 && rendered1.getHeight() > 1000);

            // Append a 3rd image page into the already-open PDDocument
            int appended = creator.appendImagesAsPages(pdf, List.of(img3), "FIT_IMAGE", false, 10f);
            assertEquals(1, appended);
            assertEquals(3, pdf.getNumberOfPages());

            BufferedImage rendered2 = pageRenderer.renderPage(2, 150f);
            assertNotNull(rendered2);
            assertTrue(rendered2.getWidth() > 0 && rendered2.getHeight() > 0);
        }
    }

    @Test
    void createsAndEditsWordDocxAndExportsToPdf() throws Exception {
        WordDocumentService wordService = new WordDocumentService();
        PdfTextEditorService textEditor = new PdfTextEditorService();

        List<DocumentBlock> blocks = List.of(
                new DocumentBlock(BlockType.HEADING_1, "Project Proposal"),
                new DocumentBlock(BlockType.PARAGRAPH, "This document was created and edited in FilzaPDF Word Studio."),
                new DocumentBlock(BlockType.BULLET_ITEM, "Zero subscription fees forever")
        );

        File docxFile = tempDir.resolve("proposal.docx").toFile();
        wordService.saveAsDocx(blocks, docxFile);
        assertTrue(docxFile.exists() && docxFile.length() > 0);

        // Reload .docx and verify blocks
        List<DocumentBlock> reloaded = wordService.loadDocument(docxFile);
        assertEquals(3, reloaded.size());
        assertTrue(reloaded.get(0).getText().contains("Project Proposal"));

        // Convert .docx directly to PDF
        try (PDDocument pdf = wordService.convertWordFileToPdf(docxFile)) {
            assertNotNull(pdf);
            assertEquals(1, pdf.getNumberOfPages());
            List<RecognizedTextBlock> recognized = textEditor.recognizePageText(pdf, 0);
            assertTrue(recognized.stream().anyMatch(b -> b.getText().contains("Project Proposal")));

            // Export PDF back to Word (.docx)
            File exportedDocx = tempDir.resolve("exported_from_pdf.docx").toFile();
            wordService.exportPdfToDocx(pdf, exportedDocx);
            assertTrue(exportedDocx.exists() && exportedDocx.length() > 0);
        }
    }

    @Test
    void persistsVisualAnnotationsAndPageTools() throws Exception {
        PdfAnnotationPersistenceService persistence = new PdfAnnotationPersistenceService();
        PdfToolboxService toolbox = new PdfToolboxService();
        AnnotationManager manager = new AnnotationManager();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());

            Annotation highlight = new Annotation();
            highlight.setType(AnnotationType.HIGHLIGHT);
            highlight.setPageIndex(0);
            highlight.setX(50);
            highlight.setY(100);
            highlight.setWidth(180);
            highlight.setHeight(20);
            highlight.setColor("#FFEB3B");
            manager.executeCommand(new AddAnnotationCommand(manager, highlight));

            persistence.burnAnnotationsIntoDocument(doc, manager);

            toolbox.rotatePage(doc, 0, 90);
            assertEquals(90, doc.getPage(0).getRotation());

            toolbox.insertBlankPage(doc, 0, null);
            assertEquals(2, doc.getNumberOfPages());

            toolbox.addWatermarkToAllPages(doc, "DRAFT", 48f, "#999999", 0.3f);
            toolbox.addPageNumbers(doc, true, 10f);

            toolbox.deletePage(doc, 1);
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    private void createSampleMultiPagePdf(File target, String prefix, int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage p = new PDPage();
                doc.addPage(p);
                try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(FontName.HELVETICA_BOLD), 16f);
                    cs.newLineAtOffset(72f, 700f);
                    cs.showText(prefix + "_Page_" + (i + 1));
                    cs.endText();
                }
            }
            doc.save(target);
        }
    }

    private void writeSamplePng(File out, int w, int h, Color c) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "PNG", out);
    }

    private void writeSampleJpg(File out, int w, int h, Color c) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "JPG", out);
    }

    private void writeSampleArgbPng(File out, int w, int h, Color c) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillOval(10, 10, w - 20, h - 20);
        g.dispose();
        ImageIO.write(img, "PNG", out);
    }
}
