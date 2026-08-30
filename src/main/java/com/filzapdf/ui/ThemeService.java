package com.filzapdf.ui;

import com.filzapdf.persistence.ViewerPreferences;
import javafx.scene.Scene;

public class ThemeService {
    private final ViewerPreferences prefs;
    private Scene scene;
    public ThemeService(ViewerPreferences prefs) { this.prefs = prefs; }
    public void setScene(Scene scene) {
        this.scene = scene;
        applyTheme(prefs.getTheme());
    }
    public void applyTheme(String theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        try {
            String css = getClass().getResource("/com/filzapdf/css/" + theme + ".css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {}
        prefs.setTheme(theme);
    }
    public void toggleTheme() { applyTheme("dark".equals(prefs.getTheme()) ? "light" : "dark"); }
}
