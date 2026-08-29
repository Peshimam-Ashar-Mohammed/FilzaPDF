package com.filzapdf.renderer;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class PageCacheTest {

    @Test
    void storesAndRetrievesPages() {

        PageCache cache =
                new PageCache(2);

        BufferedImage image =
                new BufferedImage(
                        100,
                        100,
                        BufferedImage.TYPE_INT_ARGB
                );

        cache.put(0, 120, image);

        assertSame(
                image,
                cache.get(0, 120)
        );
    }

    @Test
    void separatesDifferentDpiValues() {

        PageCache cache =
                new PageCache(2);

        BufferedImage image =
                new BufferedImage(
                        100,
                        100,
                        BufferedImage.TYPE_INT_ARGB
                );

        cache.put(0, 120, image);

        assertNull(
                cache.get(0, 240)
        );
    }

    @Test
    void removesOldestEntry() {

        PageCache cache =
                new PageCache(2);

        BufferedImage image1 =
                new BufferedImage(
                        10,
                        10,
                        BufferedImage.TYPE_INT_ARGB
                );

        BufferedImage image2 =
                new BufferedImage(
                        10,
                        10,
                        BufferedImage.TYPE_INT_ARGB
                );

        BufferedImage image3 =
                new BufferedImage(
                        10,
                        10,
                        BufferedImage.TYPE_INT_ARGB
                );

        cache.put(0, 120, image1);
        cache.put(1, 120, image2);
        cache.put(2, 120, image3);

        assertNull(
                cache.get(0, 120)
        );

        assertSame(
                image2,
                cache.get(1, 120)
        );

        assertSame(
                image3,
                cache.get(2, 120)
        );
    }
}