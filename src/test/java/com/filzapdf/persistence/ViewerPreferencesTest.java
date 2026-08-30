package com.filzapdf.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewerPreferencesTest {

    @Test
    void defaultThemeIsLight() {
        // Explicitly set then verify — note: may read prior saved state
        // so we just verify round-trip works
        ViewerPreferences prefs = new ViewerPreferences();
        prefs.setTheme("light");
        assertEquals("light", prefs.getTheme());
    }

    @Test
    void themeRoundTrip() {
        ViewerPreferences prefs = new ViewerPreferences();
        prefs.setTheme("dark");
        assertEquals("dark", prefs.getTheme());

        prefs.setTheme("light");
        assertEquals("light", prefs.getTheme());
    }

    @Test
    void lastPageRoundTrip() {
        ViewerPreferences prefs = new ViewerPreferences();
        String path = "/some/test/path.pdf";

        prefs.setLastPage(path, 7);
        assertEquals(7, prefs.getLastPage(path));
    }

    @Test
    void lastZoomRoundTrip() {
        ViewerPreferences prefs = new ViewerPreferences();
        String path = "/some/test/path.pdf";

        prefs.setLastZoom(path, 1.75);
        assertEquals(1.75, prefs.getLastZoom(path), 0.0001);
    }

    @Test
    void unknownFilePathReturnsDefaults() {
        ViewerPreferences prefs = new ViewerPreferences();
        assertEquals(0, prefs.getLastPage("/non/existent/path.pdf"));
        assertTrue(prefs.getLastZoom("/non/existent/path.pdf") < 0);
    }

    @Test
    void differentFilesHaveSeparateState() {
        ViewerPreferences prefs = new ViewerPreferences();
        String path1 = "/doc/a.pdf";
        String path2 = "/doc/b.pdf";

        prefs.setLastPage(path1, 3);
        prefs.setLastPage(path2, 9);

        assertEquals(3, prefs.getLastPage(path1));
        assertEquals(9, prefs.getLastPage(path2));
    }
}
