package com.filzapdf.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfTextSearchService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, String> pageTexts = new HashMap<>();
    private List<SearchResult> currentResults = new ArrayList<>();
    private int currentResultIndex = -1;
    private PDDocument currentDocument;
    private Runnable onExtractionComplete;

    public void extractTextInBackground(PDDocument document, Runnable onComplete) {
        this.currentDocument = document;
        this.onExtractionComplete = onComplete;
        this.pageTexts.clear();
        this.currentResults.clear();
        this.currentResultIndex = -1;

        if (document == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        executor.submit(() -> {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                int pages = document.getNumberOfPages();
                for (int i = 0; i < pages; i++) {
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(document);
                    pageTexts.put(i, text);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (onExtractionComplete != null) {
                    onExtractionComplete.run();
                }
            }
        });
    }

    public List<SearchResult> search(String query, boolean caseSensitive) {
        currentResults.clear();
        currentResultIndex = -1;

        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }

        int pages = pageTexts.size();
        Pattern pattern = caseSensitive ? 
            Pattern.compile(Pattern.quote(query)) : 
            Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < pages; i++) {
            String text = pageTexts.getOrDefault(i, "");
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                
                int snippetStart = Math.max(0, start - 20);
                int snippetEnd = Math.min(text.length(), end + 20);
                String snippet = text.substring(snippetStart, snippetEnd).replace("\n", " ").replace("\r", "");
                
                currentResults.add(new SearchResult(i, matcher.group(), start, end, snippet));
            }
        }
        
        if (!currentResults.isEmpty()) {
            currentResultIndex = 0;
        }

        return currentResults;
    }

    public SearchResult nextResult() {
        if (currentResults.isEmpty()) return null;
        currentResultIndex = (currentResultIndex + 1) % currentResults.size();
        return currentResults.get(currentResultIndex);
    }

    public SearchResult previousResult() {
        if (currentResults.isEmpty()) return null;
        currentResultIndex = (currentResultIndex - 1 + currentResults.size()) % currentResults.size();
        return currentResults.get(currentResultIndex);
    }

    public int getCurrentResultIndex() {
        return currentResultIndex;
    }
    
    public int getResultCount() {
        return currentResults.size();
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
}
