package com.filzapdf.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecentFilesServiceTest {

    /**
     * Instance-scoped @TempDir — JUnit 5 creates a fresh directory per test method.
     * Each test gets its own isolated recent.txt file.
     */
    @TempDir
    Path tempDir;

    /** Creates a fresh service backed by an isolated path for this test. */
    private RecentFilesService freshService() {
        return new RecentFilesService(tempDir.resolve("recent_" + UUID.randomUUID() + ".txt"));
    }

    @Test
    void addFileAppearsInRecentFiles() throws Exception {
        RecentFilesService svc = freshService();
        File f = File.createTempFile("filzatest", ".pdf");
        f.deleteOnExit();

        svc.addFile(f);
        assertTrue(svc.getRecentFiles().contains(f.getAbsolutePath()));
    }

    @Test
    void mostRecentFileAppearsFirst() throws Exception {
        RecentFilesService svc = freshService();
        File f1 = File.createTempFile("filzatest1", ".pdf");
        File f2 = File.createTempFile("filzatest2", ".pdf");
        f1.deleteOnExit();
        f2.deleteOnExit();

        svc.addFile(f1);
        svc.addFile(f2);

        List<String> recent = svc.getRecentFiles();
        assertEquals(f2.getAbsolutePath(), recent.get(0));
    }

    @Test
    void addingDuplicateMovesItToFront() throws Exception {
        RecentFilesService svc = freshService();
        File f1 = File.createTempFile("filzatest1", ".pdf");
        File f2 = File.createTempFile("filzatest2", ".pdf");
        f1.deleteOnExit();
        f2.deleteOnExit();

        svc.addFile(f1);
        svc.addFile(f2);
        svc.addFile(f1); // re-add f1 — should move to front

        List<String> recent = svc.getRecentFiles();
        assertEquals(f1.getAbsolutePath(), recent.get(0));
        assertEquals(2, recent.size()); // no duplicates
    }

    @Test
    void removeFileRemovesFromList() throws Exception {
        RecentFilesService svc = freshService();
        File f = File.createTempFile("filzatest", ".pdf");
        f.deleteOnExit();

        svc.addFile(f);
        assertTrue(svc.getRecentFiles().contains(f.getAbsolutePath()));

        svc.removeFile(f);
        assertFalse(svc.getRecentFiles().contains(f.getAbsolutePath()));
    }

    @Test
    void missingFilesFilteredOut() throws Exception {
        RecentFilesService svc = freshService();
        Path tmp = Files.createTempFile(tempDir, "filzamissing", ".pdf");
        File f = tmp.toFile();
        svc.addFile(f);
        Files.delete(tmp);

        assertFalse(svc.getRecentFiles().contains(f.getAbsolutePath()));
    }

    @Test
    void clearAllEmptiesList() throws Exception {
        RecentFilesService svc = freshService();
        File f = File.createTempFile("filzatest", ".pdf");
        f.deleteOnExit();
        svc.addFile(f);

        svc.clearAll();
        assertTrue(svc.getRecentFiles().isEmpty());
    }

    @Test
    void nullFileIsIgnored() {
        RecentFilesService svc = freshService();
        svc.addFile(null);    // must not throw
        svc.removeFile(null); // must not throw
    }

    @Test
    void listStartsEmptyForFreshService() {
        RecentFilesService svc = freshService(); // new UUID-named file, no prior state
        assertTrue(svc.getRecentFiles().isEmpty());
    }
}
