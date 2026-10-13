package com.filzapdf.renderer;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe LRU cache for rendered PDF page images, bounded by both a maximum
 * entry count and a maximum memory budget in bytes.
 */
public class PageCache {

    public static final long DEFAULT_MAX_MEMORY_BYTES = 192L * 1024L * 1024L; // 192 MB

    private final int maxSize;
    private final long maxMemoryBytes;
    private final LinkedHashMap<String, BufferedImage> cache;
    private long usedBytes = 0L;

    public PageCache(int maxSize) {
        this(maxSize, DEFAULT_MAX_MEMORY_BYTES);
    }

    public PageCache(int maxSize, long maxMemoryBytes) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        if (maxMemoryBytes <= 0) {
            throw new IllegalArgumentException("maxMemoryBytes must be > 0");
        }
        this.maxSize = maxSize;
        this.maxMemoryBytes = maxMemoryBytes;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true);
    }

    public synchronized BufferedImage get(int pageIndex, float dpi) {
        return cache.get(key(pageIndex, dpi));
    }

    /**
     * Returns the highest-DPI cached image currently available for {@code pageIndex},
     * or {@code null} if no resolution of that page is cached. Useful as an instant
     * placeholder during zoom transitions before the target DPI finishes rendering.
     */
    public synchronized BufferedImage getBestAvailableForPage(int pageIndex) {
        String prefix = pageIndex + ":";
        BufferedImage best = null;
        float bestDpi = -1f;
        for (Map.Entry<String, BufferedImage> entry : cache.entrySet()) {
            String k = entry.getKey();
            if (k.startsWith(prefix)) {
                try {
                    float d = Float.parseFloat(k.substring(prefix.length()));
                    if (d > bestDpi) {
                        bestDpi = d;
                        best = entry.getValue();
                    }
                } catch (NumberFormatException ignored) {
                    if (best == null) {
                        best = entry.getValue();
                    }
                }
            }
        }
        return best;
    }

    public synchronized void put(int pageIndex, float dpi, BufferedImage image) {
        if (image == null) return;
        String k = key(pageIndex, dpi);
        BufferedImage previous = cache.put(k, image);
        if (previous != null) {
            usedBytes -= estimateBytes(previous);
        }
        usedBytes += estimateBytes(image);
        evictIfNeeded();
    }

    public synchronized boolean containsKey(int pageIndex, float dpi) {
        return cache.containsKey(key(pageIndex, dpi));
    }

    public synchronized void clear() {
        cache.clear();
        usedBytes = 0L;
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized long getUsedBytes() {
        return usedBytes;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public int getMaxSize() {
        return maxSize;
    }

    private void evictIfNeeded() {
        Iterator<Map.Entry<String, BufferedImage>> it = cache.entrySet().iterator();
        while (it.hasNext() && (cache.size() > maxSize || (usedBytes > maxMemoryBytes && cache.size() > 1))) {
            Map.Entry<String, BufferedImage> eldest = it.next();
            usedBytes -= estimateBytes(eldest.getValue());
            it.remove();
        }
        if (usedBytes < 0) {
            usedBytes = 0L;
        }
    }

    public static long estimateBytes(BufferedImage image) {
        if (image == null) return 0L;
        return (long) image.getWidth() * (long) image.getHeight() * 4L;
    }

    private String key(int pageIndex, float dpi) {
        int roundedHalfDpi = Math.round(dpi * 2f);
        return pageIndex + ":" + (roundedHalfDpi / 2f);
    }
}