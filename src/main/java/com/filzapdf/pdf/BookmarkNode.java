package com.filzapdf.pdf;

import java.util.ArrayList;
import java.util.List;

public class BookmarkNode {
    private final String title;
    private final int pageIndex;
    private final List<BookmarkNode> children = new ArrayList<>();

    public BookmarkNode(String title, int pageIndex) {
        this.title = title;
        this.pageIndex = pageIndex;
    }

    public String getTitle() { return title; }
    public int getPageIndex() { return pageIndex; }
    public List<BookmarkNode> getChildren() { return children; }
    
    @Override
    public String toString() { return title; }
}
