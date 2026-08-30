package com.filzapdf.search;

public class SearchResult {
    private final int pageIndex;
    private final String matchText;
    private final int startIndex;
    private final int endIndex;
    private final String pageTextSnippet;

    public SearchResult(int pageIndex, String matchText, int startIndex, int endIndex, String pageTextSnippet) {
        this.pageIndex = pageIndex;
        this.matchText = matchText;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.pageTextSnippet = pageTextSnippet;
    }

    public int getPageIndex() { return pageIndex; }
    public String getMatchText() { return matchText; }
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public String getPageTextSnippet() { return pageTextSnippet; }
}
