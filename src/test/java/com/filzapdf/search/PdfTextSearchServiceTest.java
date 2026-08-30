package com.filzapdf.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PdfTextSearchServiceTest {

    private PdfTextSearchService service;

    @AfterEach
    void teardown() {
        if (service != null) service.shutdown();
    }

    private PDDocument createDocWithText(String... pageTexts) throws IOException {
        PDDocument doc = new PDDocument();
        for (String text : pageTexts) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
        }
        return doc;
    }

    @Test
    void searchFindsMatchOnSinglePage() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("Hello FilzaPDF world");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("FilzaPDF", false);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).getPageIndex());
        assertEquals("FilzaPDF", results.get(0).getMatchText());

        doc.close();
    }

    @Test
    void searchAcrossMultiplePages() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("Page one keyword here", "Page two has keyword also");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("keyword", false);
        assertEquals(2, results.size());
        assertEquals(0, results.get(0).getPageIndex());
        assertEquals(1, results.get(1).getPageIndex());

        doc.close();
    }

    @Test
    void caseSensitiveSearchDistinguishesCase() throws Exception {
        service = new PdfTextSearchService();
        // Use completely distinct tokens so case-sensitive vs insensitive is clear
        PDDocument doc = createDocWithText("UPPERCASE lowercase MixedCase");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Case-sensitive: "UPPERCASE" should match only the all-caps version
        List<SearchResult> sensitive = service.search("UPPERCASE", true);
        assertEquals(1, sensitive.size());

        // Case-insensitive: "UPPERCASE" should match the all-caps version too (same here since unique)
        List<SearchResult> insensitive = service.search("UPPERCASE", false);
        assertTrue(insensitive.size() >= 1);

        // Verify case-sensitive rejects wrong case
        List<SearchResult> wrongCase = service.search("uppercase", true);
        assertEquals(0, wrongCase.size()); // strict: no match for lowercase query against uppercase text

        doc.close();
    }

    @Test
    void emptyQueryReturnsNoResults() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("Some text here");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("", false);
        assertTrue(results.isEmpty());

        doc.close();
    }

    @Test
    void noMatchReturnsEmptyList() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("Hello world");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("xyznotfound", false);
        assertTrue(results.isEmpty());
        assertEquals(0, service.getResultCount());

        doc.close();
    }

    @Test
    void nextAndPreviousNavigateCyclically() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("cat cat cat");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("cat", false);
        assertEquals(3, results.size());
        assertEquals(0, service.getCurrentResultIndex());

        SearchResult r1 = service.nextResult();
        assertNotNull(r1);
        assertEquals(1, service.getCurrentResultIndex());

        service.nextResult();
        assertEquals(2, service.getCurrentResultIndex());

        // Wraps around
        service.nextResult();
        assertEquals(0, service.getCurrentResultIndex());

        // Previous wraps backward
        SearchResult prev = service.previousResult();
        assertNotNull(prev);
        assertEquals(2, service.getCurrentResultIndex());

        doc.close();
    }

    @Test
    void resultContainsSnippet() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("context before TERM context after");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("TERM", false);
        assertEquals(1, results.size());
        assertNotNull(results.get(0).getPageTextSnippet());
        assertTrue(results.get(0).getPageTextSnippet().contains("TERM"));

        doc.close();
    }

    @Test
    void nullDocumentDoesNotCrash() throws InterruptedException {
        service = new PdfTextSearchService();
        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(null, latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        List<SearchResult> results = service.search("anything", false);
        assertTrue(results.isEmpty());
    }

    @Test
    void nextResultReturnsNullWhenNoResults() throws Exception {
        service = new PdfTextSearchService();
        PDDocument doc = createDocWithText("hello");

        CountDownLatch latch = new CountDownLatch(1);
        service.extractTextInBackground(doc, latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        service.search("nothere", false);
        assertNull(service.nextResult());
        assertNull(service.previousResult());

        doc.close();
    }
}
