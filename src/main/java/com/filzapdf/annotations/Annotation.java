package com.filzapdf.annotations;

import java.util.List;

public class Annotation {
    private AnnotationType type;
    private int pageIndex;
    private double x, y, width, height;
    private String color;
    private String noteText;
    private List<double[]> strokePoints;
    public AnnotationType getType() { return type; }
    public void setType(AnnotationType type) { this.type = type; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public List<double[]> getStrokePoints() { return strokePoints; }
    public void setStrokePoints(List<double[]> strokePoints) { this.strokePoints = strokePoints; }
}
