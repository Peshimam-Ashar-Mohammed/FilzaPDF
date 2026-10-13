package com.filzapdf.annotations;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import com.filzapdf.pdf.PdfTextEditorService;

/**
 * Persists visual annotations (Highlights, Underlines, Strikethroughs, Freehand Ink,
 * Rectangles, Redactions, and Sticky Notes) directly into PDF pages on save.
 * For {@link AnnotationType#REDACT}, first scrubs matching text tokens from the page's
 * underlying content stream before painting the opaque redaction block.
 */
public class PdfAnnotationPersistenceService {

    private final PdfTextEditorService textEditorService = new PdfTextEditorService();

    public void burnAnnotationsIntoDocument(PDDocument doc, AnnotationManager manager) throws IOException {
        if (doc == null || manager == null) return;

        synchronized (doc) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                List<Annotation> list = manager.getAnnotationsForPage(p);
                if (list == null || list.isEmpty()) continue;

                // First pass: scrub content-stream text tokens under any REDACT regions on this page
                for (Annotation a : list) {
                    if (a.getType() == AnnotationType.REDACT) {
                        textEditorService.redactTextInRegion(
                                doc,
                                p,
                                (float) a.getX(),
                                (float) a.getY(),
                                (float) a.getWidth(),
                                (float) a.getHeight()
                        );
                    }
                }

                PDPage page = doc.getPage(p);
                float pageHeight = page.getMediaBox().getHeight();

                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    for (Annotation a : list) {
                        renderAnnotationToStream(cs, a, pageHeight);
                    }
                }
            }
        }
    }

    private void renderAnnotationToStream(PDPageContentStream cs, Annotation a, float pageHeight) throws IOException {
        Color color = PdfTextEditorService.parseHexColor(a.getColor(), new Color(255, 235, 59));
        float x = (float) a.getX();
        float yTop = (float) a.getY();
        float w = (float) a.getWidth();
        float h = (float) a.getHeight();
        float pdfBottomY = pageHeight - (yTop + h);

        PDExtendedGraphicsState opaqueGs = new PDExtendedGraphicsState();
        opaqueGs.setNonStrokingAlphaConstant(1.0f);
        opaqueGs.setStrokingAlphaConstant(1.0f);

        switch (a.getType()) {
            case HIGHLIGHT: {
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.38f);
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(color);
                cs.addRect(x, pdfBottomY, w, h);
                cs.fill();
                cs.setGraphicsStateParameters(opaqueGs);
                break;
            }
            case UNDERLINE: {
                cs.setGraphicsStateParameters(opaqueGs);
                cs.setStrokingColor(color);
                cs.setLineWidth(2.0f);
                cs.moveTo(x, pdfBottomY);
                cs.lineTo(x + w, pdfBottomY);
                cs.stroke();
                break;
            }
            case STRIKETHROUGH: {
                cs.setGraphicsStateParameters(opaqueGs);
                cs.setStrokingColor(color);
                cs.setLineWidth(2.0f);
                float midY = pdfBottomY + h / 2f;
                cs.moveTo(x, midY);
                cs.lineTo(x + w, midY);
                cs.stroke();
                break;
            }
            case RECTANGLE: {
                cs.setGraphicsStateParameters(opaqueGs);
                cs.setStrokingColor(color);
                cs.setLineWidth(2.0f);
                cs.addRect(x, pdfBottomY, w, h);
                cs.stroke();
                break;
            }
            case REDACT: {
                cs.setGraphicsStateParameters(opaqueGs);
                cs.setNonStrokingColor(Color.BLACK);
                cs.addRect(x, pdfBottomY, w, h);
                cs.fill();
                break;
            }
            case FREEHAND: {
                List<double[]> pts = a.getStrokePoints();
                if (pts != null && pts.size() >= 2) {
                    cs.setGraphicsStateParameters(opaqueGs);
                    cs.setStrokingColor(color);
                    cs.setLineWidth(2.2f);
                    double[] first = pts.get(0);
                    cs.moveTo((float) first[0], pageHeight - (float) first[1]);
                    for (int i = 1; i < pts.size(); i++) {
                        double[] pt = pts.get(i);
                        cs.lineTo((float) pt[0], pageHeight - (float) pt[1]);
                    }
                    cs.stroke();
                }
                break;
            }
            case NOTE: {
                cs.setGraphicsStateParameters(opaqueGs);
                cs.setNonStrokingColor(new Color(255, 244, 140));
                cs.setStrokingColor(new Color(200, 170, 30));
                cs.setLineWidth(1.0f);
                float boxW = Math.max(w, 120f);
                float boxH = Math.max(h, 36f);
                float boxBottomY = pageHeight - (yTop + boxH);
                cs.addRect(x, boxBottomY, boxW, boxH);
                cs.fillAndStroke();

                if (a.getNoteText() != null && !a.getNoteText().isEmpty()) {
                    PDFont font = new PDType1Font(FontName.HELVETICA);
                    cs.setNonStrokingColor(Color.BLACK);
                    String safe = PdfTextEditorService.sanitizeForStandardFont(a.getNoteText());
                    cs.beginText();
                    cs.setFont(font, 9.5f);
                    cs.newLineAtOffset(x + 5f, boxBottomY + boxH - 13f);
                    cs.showText(safe.length() > 45 ? safe.substring(0, 45) : safe);
                    cs.endText();
                }
                break;
            }
            default:
                break;
        }
    }
}
