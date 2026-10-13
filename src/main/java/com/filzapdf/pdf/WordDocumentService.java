package com.filzapdf.pdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import com.filzapdf.pdf.PdfCreatorService.DocumentBlock;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock.Alignment;
import com.filzapdf.pdf.PdfCreatorService.DocumentBlock.BlockType;

/**
 * Service for opening, editing, and saving Microsoft Word (.docx) files,
 * converting Word files to PDF, and exporting PDFs to editable Word (.docx) files.
 */
public class WordDocumentService {

    private final PdfCreatorService pdfCreatorService = new PdfCreatorService();
    private final PdfTextEditorService textEditorService = new PdfTextEditorService();

    /**
     * Loads a .docx Word document (or plain .txt file) into a list of editable DocumentBlocks.
     */
    public List<DocumentBlock> loadDocument(File file) throws IOException {
        List<DocumentBlock> blocks = new ArrayList<>();
        if (file == null || !file.exists()) return blocks;

        if (file.getName().toLowerCase().endsWith(".txt") || file.getName().toLowerCase().endsWith(".md")) {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                if (line.startsWith("# ")) {
                    blocks.add(new DocumentBlock(BlockType.HEADING_1, line.substring(2).trim()));
                } else if (line.startsWith("## ")) {
                    blocks.add(new DocumentBlock(BlockType.HEADING_2, line.substring(3).trim()));
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    blocks.add(new DocumentBlock(BlockType.BULLET_ITEM, line.substring(2).trim()));
                } else {
                    blocks.add(new DocumentBlock(BlockType.PARAGRAPH, line));
                }
            }
            return blocks;
        }

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    parseParagraph(para, blocks);
                } else if (element instanceof XWPFTable table) {
                    parseTable(table, blocks);
                }
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(new DocumentBlock(BlockType.PARAGRAPH, ""));
        }
        return blocks;
    }

    private void parseParagraph(XWPFParagraph para, List<DocumentBlock> blocks) {
        // Check for embedded pictures in runs
        for (XWPFRun run : para.getRuns()) {
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                XWPFPictureData picData = pic.getPictureData();
                if (picData != null && picData.getData() != null && picData.getData().length > 0) {
                    DocumentBlock imgBlock = new DocumentBlock(BlockType.IMAGE, "");
                    imgBlock.setImageBytes(picData.getData());
                    blocks.add(imgBlock);
                }
            }
        }

        String text = para.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        DocumentBlock block = new DocumentBlock();
        String style = para.getStyle() != null ? para.getStyle().toLowerCase() : "";
        if (style.contains("heading1") || style.contains("heading 1") || style.equals("title")) {
            block.setType(BlockType.HEADING_1);
            block.setFontSize(22f);
            block.setBold(true);
        } else if (style.contains("heading2") || style.contains("heading 2") || style.contains("subtitle")) {
            block.setType(BlockType.HEADING_2);
            block.setFontSize(16f);
            block.setBold(true);
        } else if (para.getNumID() != null) {
            block.setType(BlockType.BULLET_ITEM);
            block.setFontSize(12f);
        } else {
            block.setType(BlockType.PARAGRAPH);
            block.setFontSize(12f);
        }

        block.setText(text);

        if (para.getAlignment() == ParagraphAlignment.CENTER) {
            block.setAlignment(Alignment.CENTER);
        } else if (para.getAlignment() == ParagraphAlignment.RIGHT) {
            block.setAlignment(Alignment.RIGHT);
        } else {
            block.setAlignment(Alignment.LEFT);
        }

        // Inspect first non-empty run for font styling
        for (XWPFRun run : para.getRuns()) {
            String rt = run.text();
            if (rt != null && !rt.trim().isEmpty()) {
                if (run.getFontFamily() != null) {
                    block.setFontFamily(PdfTextEditorService.detectFontFamily(run.getFontFamily()));
                }
                Double fs = run.getFontSizeAsDouble();
                if (fs != null && fs > 0) {
                    block.setFontSize(fs.floatValue());
                }
                if (run.isBold()) block.setBold(true);
                if (run.isItalic()) block.setItalic(true);
                if (run.getUnderline() != UnderlinePatterns.NONE) block.setUnderline(true);
                if (run.getColor() != null && run.getColor().length() == 6) {
                    block.setColorHex("#" + run.getColor());
                }
                break;
            }
        }

        blocks.add(block);
    }

    private void parseTable(XWPFTable table, List<DocumentBlock> blocks) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().trim());
            }
            String rowText = String.join("  |  ", cells);
            if (!rowText.trim().isEmpty()) {
                DocumentBlock b = new DocumentBlock(BlockType.PARAGRAPH, rowText);
                b.setFontFamily("Courier");
                b.setFontSize(11f);
                blocks.add(b);
            }
        }
    }

    /**
     * Saves a list of DocumentBlocks as a Microsoft Word (.docx) file.
     */
    public void saveAsDocx(List<DocumentBlock> blocks, File targetFile) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (blocks != null) {
                for (DocumentBlock block : blocks) {
                    XWPFParagraph p = doc.createParagraph();

                    if (block.getAlignment() == Alignment.CENTER) {
                        p.setAlignment(ParagraphAlignment.CENTER);
                    } else if (block.getAlignment() == Alignment.RIGHT) {
                        p.setAlignment(ParagraphAlignment.RIGHT);
                    } else {
                        p.setAlignment(ParagraphAlignment.LEFT);
                    }

                    if (block.getType() == BlockType.PAGE_BREAK) {
                        XWPFRun r = p.createRun();
                        r.addBreak(BreakType.PAGE);
                        continue;
                    }

                    if (block.getType() == BlockType.DIVIDER) {
                        XWPFRun r = p.createRun();
                        r.setColor("999999");
                        r.setText("________________________________________________________________________");
                        continue;
                    }

                    if (block.getType() == BlockType.IMAGE) {
                        XWPFRun r = p.createRun();
                        try {
                            if (block.getImageFile() != null && block.getImageFile().exists()) {
                                try (FileInputStream is = new FileInputStream(block.getImageFile())) {
                                    PictureType pt = guessPictureType(block.getImageFile().getName());
                                    r.addPicture(is, pt, block.getImageFile().getName(),
                                            Units.toEMU(360), Units.toEMU(240));
                                }
                            } else if (block.getImageBytes() != null && block.getImageBytes().length > 0) {
                                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(block.getImageBytes())) {
                                    r.addPicture(bais, PictureType.PNG, "embedded.png",
                                            Units.toEMU(360), Units.toEMU(240));
                                }
                            }
                        } catch (Exception ignored) {}
                        continue;
                    }

                    XWPFRun run = p.createRun();
                    String family = block.getFontFamily();
                    if ("Times".equalsIgnoreCase(family)) {
                        run.setFontFamily("Times New Roman");
                    } else if ("Courier".equalsIgnoreCase(family)) {
                        run.setFontFamily("Courier New");
                    } else {
                        run.setFontFamily("Calibri");
                    }

                    run.setFontSize(Math.max(8, Math.round(block.getFontSize())));
                    run.setBold(block.isBold());
                    run.setItalic(block.isItalic());
                    if (block.isUnderline()) {
                        run.setUnderline(UnderlinePatterns.SINGLE);
                    }

                    String hex = block.getColorHex();
                    if (hex != null && hex.startsWith("#") && hex.length() == 7) {
                        run.setColor(hex.substring(1));
                    }

                    String text = block.getText() != null ? block.getText() : "";
                    if (block.getType() == BlockType.BULLET_ITEM) {
                        text = "\u2022  " + text;
                    }
                    String[] lines = text.split("\\r?\\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        if (i > 0) run.addBreak();
                        run.setText(lines[i]);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                doc.write(fos);
            }
        }
    }

    /**
     * Converts a Word (.docx) file directly into a multi-page PDDocument (PDF).
     */
    public PDDocument convertWordFileToPdf(File docxFile) throws IOException {
        List<DocumentBlock> blocks = loadDocument(docxFile);
        return pdfCreatorService.createPdfFromDocumentBlocks(blocks);
    }

    /**
     * Exports an open PDDocument (PDF) into an editable Microsoft Word (.docx) file,
     * preserving recognized fonts, sizes, bold/italic styles, and page structure.
     */
    public void exportPdfToDocx(PDDocument pdfDoc, File targetDocxFile) throws IOException {
        List<DocumentBlock> blocks = new ArrayList<>();
        if (pdfDoc != null) {
            int pages = pdfDoc.getNumberOfPages();
            for (int p = 0; p < pages; p++) {
                if (p > 0) {
                    blocks.add(new DocumentBlock(BlockType.PAGE_BREAK, ""));
                }
                List<RecognizedTextBlock> recognized = textEditorService.recognizePageText(pdfDoc, p);
                for (RecognizedTextBlock rtb : recognized) {
                    BlockType bt = BlockType.PARAGRAPH;
                    if (rtb.getFontSize() >= 18f) {
                        bt = BlockType.HEADING_1;
                    } else if (rtb.getFontSize() >= 14.5f && rtb.isBold()) {
                        bt = BlockType.HEADING_2;
                    }
                    DocumentBlock db = new DocumentBlock(bt, rtb.getText());
                    db.setFontFamily(rtb.getFontFamily());
                    db.setFontSize(rtb.getFontSize());
                    db.setBold(rtb.isBold());
                    db.setItalic(rtb.isItalic());
                    db.setColorHex(rtb.getColorHex());
                    blocks.add(db);
                }
            }
        }
        saveAsDocx(blocks, targetDocxFile);
    }

    private PictureType guessPictureType(String fileName) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return PictureType.JPEG;
        if (lower.endsWith(".gif")) return PictureType.GIF;
        if (lower.endsWith(".bmp")) return PictureType.BMP;
        return PictureType.PNG;
    }
}
