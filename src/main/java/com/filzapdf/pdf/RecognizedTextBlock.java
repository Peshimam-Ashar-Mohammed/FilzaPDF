package com.filzapdf.pdf;

/**
 * Represents a recognized text run/line on a PDF page, including its exact
 * coordinates (top-left origin for UI and bottom-left for PDFBox) and font metadata.
 */
public class RecognizedTextBlock {

    private final int pageIndex;
    private String text;
    private final String rawFontName;
    private String fontFamily; // "Helvetica", "Times", "Courier"
    private boolean bold;
    private boolean italic;
    private float fontSize;
    private final float x;      // Top-left X in points
    private final float y;      // Top-left Y in points
    private final float width;  // Width in points
    private final float height; // Height in points
    private final float pdfX;   // PDF baseline X
    private final float pdfY;   // PDF baseline Y
    private String colorHex;

    public RecognizedTextBlock(
            int pageIndex,
            String text,
            String rawFontName,
            String fontFamily,
            boolean bold,
            boolean italic,
            float fontSize,
            float x,
            float y,
            float width,
            float height,
            float pdfX,
            float pdfY,
            String colorHex) {
        this.pageIndex = pageIndex;
        this.text = text;
        this.rawFontName = rawFontName != null ? rawFontName : "Helvetica";
        this.fontFamily = fontFamily != null ? fontFamily : "Helvetica";
        this.bold = bold;
        this.italic = italic;
        this.fontSize = fontSize > 0 ? fontSize : 12f;
        this.x = x;
        this.y = y;
        this.width = Math.max(width, 10f);
        this.height = Math.max(height, this.fontSize);
        this.pdfX = pdfX;
        this.pdfY = pdfY;
        this.colorHex = colorHex != null ? colorHex : "#000000";
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getRawFontName() {
        return rawFontName;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getPdfX() {
        return pdfX;
    }

    public float getPdfY() {
        return pdfY;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getDisplayFontSummary() {
        StringBuilder sb = new StringBuilder(fontFamily);
        if (bold) sb.append(" Bold");
        if (italic) sb.append(" Italic");
        sb.append(String.format(" (%.1f pt)", fontSize));
        return sb.toString();
    }

    public boolean containsPoint(double pagePtX, double pagePtY) {
        double pad = 3.0;
        return pagePtX >= (x - pad) && pagePtX <= (x + width + pad)
                && pagePtY >= (y - pad) && pagePtY <= (y + height + pad);
    }
}
