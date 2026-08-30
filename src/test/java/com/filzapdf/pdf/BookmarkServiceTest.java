package com.filzapdf.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

class BookmarkServiceTest {

    @Test
    void returnsEmptyListForDocumentWithoutOutline() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            BookmarkService svc = new BookmarkService();
            List<BookmarkNode> nodes = svc.extractBookmarks(doc);
            assertNotNull(nodes);
            assertTrue(nodes.isEmpty());
        }
    }

    @Test
    void extractsTopLevelBookmarks() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page0 = new PDPage();
            PDPage page1 = new PDPage();
            doc.addPage(page0);
            doc.addPage(page1);

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PDOutlineItem item0 = new PDOutlineItem();
            item0.setTitle("Chapter 1");
            PDPageFitDestination dest0 = new PDPageFitDestination();
            dest0.setPage(page0);
            item0.setDestination(dest0);
            outline.addFirst(item0);

            PDOutlineItem item1 = new PDOutlineItem();
            item1.setTitle("Chapter 2");
            PDPageFitDestination dest1 = new PDPageFitDestination();
            dest1.setPage(page1);
            item1.setDestination(dest1);
            item0.insertSiblingAfter(item1);

            BookmarkService svc = new BookmarkService();
            List<BookmarkNode> nodes = svc.extractBookmarks(doc);

            assertEquals(2, nodes.size());
            assertEquals("Chapter 1", nodes.get(0).getTitle());
            assertEquals("Chapter 2", nodes.get(1).getTitle());
        }
    }

    @Test
    void extractsNestedBookmarks() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page0 = new PDPage();
            PDPage page1 = new PDPage();
            doc.addPage(page0);
            doc.addPage(page1);

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PDOutlineItem parent = new PDOutlineItem();
            parent.setTitle("Part 1");
            PDPageFitDestination destParent = new PDPageFitDestination();
            destParent.setPage(page0);
            parent.setDestination(destParent);
            outline.addFirst(parent);

            PDOutlineItem child = new PDOutlineItem();
            child.setTitle("Section 1.1");
            PDPageFitDestination destChild = new PDPageFitDestination();
            destChild.setPage(page1);
            child.setDestination(destChild);
            parent.addFirst(child);

            BookmarkService svc = new BookmarkService();
            List<BookmarkNode> nodes = svc.extractBookmarks(doc);

            assertEquals(1, nodes.size());
            assertEquals("Part 1", nodes.get(0).getTitle());
            assertFalse(nodes.get(0).getChildren().isEmpty());
            assertEquals("Section 1.1", nodes.get(0).getChildren().get(0).getTitle());
        }
    }

    @Test
    void bookmarkNodeHasCorrectPageIndex() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page0 = new PDPage();
            PDPage page1 = new PDPage();
            PDPage page2 = new PDPage();
            doc.addPage(page0);
            doc.addPage(page1);
            doc.addPage(page2);

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);

            PDOutlineItem item = new PDOutlineItem();
            item.setTitle("Page 3 Bookmark");
            PDPageFitDestination dest = new PDPageFitDestination();
            dest.setPage(page2);
            item.setDestination(dest);
            outline.addFirst(item);

            BookmarkService svc = new BookmarkService();
            List<BookmarkNode> nodes = svc.extractBookmarks(doc);

            assertEquals(1, nodes.size());
            assertEquals(2, nodes.get(0).getPageIndex()); // 0-based
        }
    }

    @Test
    void bookmarkNodeToStringReturnsTitle() {
        BookmarkNode node = new BookmarkNode("My Chapter", 3);
        assertEquals("My Chapter", node.toString());
    }

    @Test
    void bookmarkNodeChildrenInitiallyEmpty() {
        BookmarkNode node = new BookmarkNode("Root", 0);
        assertNotNull(node.getChildren());
        assertTrue(node.getChildren().isEmpty());
    }
}
