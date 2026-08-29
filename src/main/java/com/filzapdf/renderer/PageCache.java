package com.filzapdf.renderer;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

public class PageCache {

    private final int maxSize;

    private final Map<String, BufferedImage> cache;

    public PageCache(int maxSize) {
        this.maxSize = maxSize;

        this.cache = new LinkedHashMap<>(
                maxSize,
                0.75f,
                true
        ) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<String, BufferedImage> eldest
            ) {
                return size() > PageCache.this.maxSize;
            }
        };
    }

    public BufferedImage get(
            int pageIndex,
            float dpi
    ) {
        return cache.get(key(pageIndex, dpi));
    }

    public void put(
            int pageIndex,
            float dpi,
            BufferedImage image
    ) {
        cache.put(
                key(pageIndex, dpi),
                image
        );
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private String key(
            int pageIndex,
            float dpi
    ) {
        return pageIndex + ":" + dpi;
    }
}