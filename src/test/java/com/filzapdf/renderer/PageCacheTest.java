package com.filzapdf.renderer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PageCacheTest {

    @Test
    void storesAndRetrievesPages() {
        PageCache cache = new PageCache(2);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        cache.put(0, 120, image);

        assertSame(image, cache.get(0, 120));
    }

    @Test
    void separatesDifferentDpiValues() {
        PageCache cache = new PageCache(2);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        cache.put(0, 120, image);

        assertNull(cache.get(0, 240));
    }

    @Test
    void removesOldestEntry() {
        PageCache cache = new PageCache(2);

        BufferedImage image1 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage image2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage image3 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

        cache.put(0, 120, image1);
        cache.put(1, 120, image2);
        cache.put(2, 120, image3);

        assertNull(cache.get(0, 120));
        assertSame(image2, cache.get(1, 120));
        assertSame(image3, cache.get(2, 120));
    }

    @Test
    void evictsEntriesWhenMemoryBudgetIsExceeded() {
        // Each 100x100 ARGB image is 40,000 bytes. Set budget to 90,000 bytes (fits at most 2 images).
        PageCache cache = new PageCache(20, 90_000L);

        BufferedImage img0 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        BufferedImage img1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        BufferedImage img2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        cache.put(0, 150f, img0);
        cache.put(1, 150f, img1);
        assertEquals(2, cache.size());
        assertTrue(cache.getUsedBytes() <= 90_000L);

        // Adding 3rd image exceeds 90KB budget -> oldest (page 0) must be evicted
        cache.put(2, 150f, img2);
        assertEquals(2, cache.size());
        assertNull(cache.get(0, 150f));
        assertSame(img1, cache.get(1, 150f));
        assertSame(img2, cache.get(2, 150f));
        assertTrue(cache.getUsedBytes() <= 90_000L);
    }

    @Test
    void returnsBestAvailableResolutionForSmoothZoomFallback() {
        PageCache cache = new PageCache(10);

        BufferedImage preview96 = new BufferedImage(80, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage crisp240 = new BufferedImage(200, 250, BufferedImage.TYPE_INT_RGB);

        cache.put(3, 96f, preview96);
        cache.put(3, 240f, crisp240);

        assertSame(crisp240, cache.getBestAvailableForPage(3));
        assertNull(cache.getBestAvailableForPage(99));
    }

    @Test
    void supportsConcurrentMultiThreadedAccessSafely() throws Exception {
        PageCache cache = new PageCache(16, 2_000_000L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < 8; t++) {
            final int threadId = t;
            tasks.add(() -> {
                for (int i = 0; i < 60; i++) {
                    int page = (threadId + i) % 12;
                    float dpi = (i % 3 == 0) ? 96f : (i % 3 == 1 ? 150f : 240f);
                    BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
                    cache.put(page, dpi, img);
                    cache.get(page, dpi);
                    cache.getBestAvailableForPage(page);
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get(); // Ensure no ConcurrentModificationException or assertion failure occurred
        }
        pool.shutdown();

        assertTrue(cache.size() <= 16);
        assertTrue(cache.getUsedBytes() >= 0 && cache.getUsedBytes() <= 2_000_000L);
        assertNotNull(cache.getBestAvailableForPage(0));
    }
}