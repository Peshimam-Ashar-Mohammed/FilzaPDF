package com.filzapdf.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

public class BookmarkService {
    public List<BookmarkNode> extractBookmarks(PDDocument document) throws IOException {
        List<BookmarkNode> bookmarks = new ArrayList<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            populateBookmarks(outline, bookmarks, document);
        }
        return bookmarks;
    }

    private void populateBookmarks(PDOutlineNode outlineNode, List<BookmarkNode> children, PDDocument document) throws IOException {
        PDOutlineItem current = outlineNode.getFirstChild();
        while (current != null) {
            int pageIndex = -1;
            try {
                if (current.getDestination() instanceof PDPageDestination) {
                    pageIndex = ((PDPageDestination) current.getDestination()).retrievePageNumber();
                } else if (current.getAction() instanceof PDActionGoTo) {
                    PDActionGoTo action = (PDActionGoTo) current.getAction();
                    if (action.getDestination() instanceof PDPageDestination) {
                        pageIndex = ((PDPageDestination) action.getDestination()).retrievePageNumber();
                    }
                }
            } catch (Exception e) {}
            BookmarkNode node = new BookmarkNode(current.getTitle(), pageIndex);
            children.add(node);
            populateBookmarks(current, node.getChildren(), document);
            current = current.getNextSibling();
        }
    }
}
