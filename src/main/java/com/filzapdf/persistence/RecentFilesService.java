package com.filzapdf.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persists a list of recently-opened PDF files.
 *
 * Files that no longer exist on disk are silently filtered out on read.
 * At most MAX_RECENT_FILES entries are kept; the most recently added
 * entry always appears first (duplicates are moved to front).
 */
public class RecentFilesService {

    private static final Path DEFAULT_PATH =
            Paths.get(System.getProperty("user.home"), ".filzapdf", "recent.txt");
    private static final int MAX_RECENT_FILES = 10;

    private final Path storagePath;
    private final List<String> recentFiles = new ArrayList<>();

    /** Production constructor — persists to ~/.filzapdf/recent.txt */
    public RecentFilesService() {
        this(DEFAULT_PATH);
    }

    /**
     * Testing constructor — persists to the supplied path.
     * Allows isolated, non-polluting tests.
     */
    RecentFilesService(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    /**
     * Adds a file to the front of the recent list.
     * If the file is already present it is moved to the front.
     * Saves to disk after every change.
     */
    public void addFile(File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        recentFiles.remove(path);
        recentFiles.add(0, path);
        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(recentFiles.size() - 1);
        }
        save();
    }

    /**
     * Returns the current list of recent files, filtering out any that
     * no longer exist on disk.
     */
    public List<String> getRecentFiles() {
        recentFiles.removeIf(p -> !new File(p).exists());
        return new ArrayList<>(recentFiles);
    }

    /** Removes a specific file from the list and saves. */
    public void removeFile(File file) {
        if (file == null) return;
        recentFiles.remove(file.getAbsolutePath());
        save();
    }

    /** Clears all recent files and saves. */
    public void clearAll() {
        recentFiles.clear();
        save();
    }

    private void load() {
        try {
            if (Files.exists(storagePath)) {
                recentFiles.addAll(
                    Files.readAllLines(storagePath).stream()
                         .filter(p -> new File(p).exists())
                         .limit(MAX_RECENT_FILES)
                         .collect(Collectors.toList())
                );
            }
        } catch (IOException e) {
            System.err.println("Could not load recent files: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, getRecentFiles());
        } catch (IOException e) {
            System.err.println("Could not save recent files: " + e.getMessage());
        }
    }
}
