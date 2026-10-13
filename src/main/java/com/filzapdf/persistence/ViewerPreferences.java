package com.filzapdf.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ViewerPreferences {
    private static final Path PREFS_PATH = Paths.get(System.getProperty("user.home"), ".filzapdf", "prefs.properties");
    private final Properties props = new Properties();

    public ViewerPreferences() {
        load();
    }

    public void setLastPage(String filePath, int pageIndex) {
        props.setProperty("lastPage_" + filePath, String.valueOf(pageIndex));
        save();
    }

    public int getLastPage(String filePath) {
        String val = props.getProperty("lastPage_" + filePath);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public void setLastZoom(String filePath, double zoom) {
        props.setProperty("lastZoom_" + filePath, String.valueOf(zoom));
        save();
    }

    public double getLastZoom(String filePath) {
        String val = props.getProperty("lastZoom_" + filePath);
        return val != null ? Double.parseDouble(val) : -1.0;
    }

    public void setTheme(String theme) {
        props.setProperty("theme", theme);
        save();
    }

    public String getTheme() {
        return props.getProperty("theme", "dark");
    }

    private void load() {
        try {
            if (Files.exists(PREFS_PATH)) {
                try (FileInputStream in = new FileInputStream(PREFS_PATH.toFile())) {
                    props.load(in);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            Files.createDirectories(PREFS_PATH.getParent());
            try (FileOutputStream out = new FileOutputStream(PREFS_PATH.toFile())) {
                props.store(out, "FilzaPDF Preferences");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
