package com.filzapdf.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Crisp 16x16 vector SVG path icons for a native, minimalist studio interface.
 */
public final class SvgIcons {

    private SvgIcons() {}

    public static Node icon(String svgPathContent) {
        return icon(svgPathContent, 14);
    }

    public static Node icon(String svgPathContent, double size) {
        SVGPath path = new SVGPath();
        path.setContent(svgPathContent);
        path.getStyleClass().add("svg-icon");

        StackPane pane = new StackPane(path);
        pane.setAlignment(Pos.CENTER);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.getStyleClass().add("svg-icon-container");
        return pane;
    }

    // Standard 16x16 / 24x24 scaled crisp paths
    public static final String HOME =
            "M3 7.5L8 3l5 4.5v5.5a1 1 0 0 1-1 1H9.5v-4h-3v4H4a1 1 0 0 1-1-1V7.5z";

    public static final String FOLDER_OPEN =
            "M2 4.5A1.5 1.5 0 0 1 3.5 3h3.172a1.5 1.5 0 0 1 1.06.44l.829.828a.5.5 0 0 0 .353.147H12.5A1.5 1.5 0 0 1 14 5.915V11.5A1.5 1.5 0 0 1 12.5 13h-9A1.5 1.5 0 0 1 2 11.5v-7z";

    public static final String SAVE =
            "M3 2.5A.5.5 0 0 1 3.5 2h7.793a.5.5 0 0 1 .354.146l1.707 1.707a.5.5 0 0 1 .146.354V12.5a.5.5 0 0 1-.5.5h-9.5a.5.5 0 0 1-.5-.5v-10zm2 0v3h5v-3H5zm-1 7v2.5h8V9.5H4z";

    public static final String CHEVRON_LEFT =
            "M9.78 3.22a.75.75 0 0 1 0 1.06L6.06 8l3.72 3.72a.75.75 0 1 1-1.06 1.06L4.47 8.53a.75.75 0 0 1 0-1.06l4.25-4.25a.75.75 0 0 1 1.06 0z";

    public static final String CHEVRON_RIGHT =
            "M6.22 3.22a.75.75 0 0 1 1.06 0l4.25 4.25a.75.75 0 0 1 0 1.06l-4.25 4.25a.75.75 0 0 1-1.06-1.06L9.94 8 6.22 4.28a.75.75 0 0 1 0-1.06z";

    public static final String MINUS =
            "M3.5 7.25h9a.75.75 0 0 1 0 1.5h-9a.75.75 0 0 1 0-1.5z";

    public static final String PLUS =
            "M8 3.25a.75.75 0 0 1 .75.75v3.25H12a.75.75 0 0 1 0 1.5H8.75V12a.75.75 0 0 1-1.5 0V8.75H4a.75.75 0 0 1 0-1.5h3.25V4A.75.75 0 0 1 8 3.25z";

    public static final String MERGE =
            "M3 3h4v4H3V3zm6 0h4v4H9V3zM6 9h4v4H6V9zm-1-1.5L7.5 9m3-1.5L8.5 9";

    public static final String IMAGE =
            "M2.5 3A1.5 1.5 0 0 0 1 4.5v7A1.5 1.5 0 0 0 2.5 13h11a1.5 1.5 0 0 0 1.5-1.5v-7A1.5 1.5 0 0 0 13.5 3h-11zm3 2.5a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm8 6h-11l2.75-3.5 2 2.5 3.25-4.25L13.5 11.5z";

    public static final String FILE_TEXT =
            "M4 2a1.5 1.5 0 0 0-1.5 1.5v9A1.5 1.5 0 0 0 4 14h8a1.5 1.5 0 0 0 1.5-1.5V5.621a1.5 1.5 0 0 0-.44-1.06L10.44 1.94A1.5 1.5 0 0 0 9.378 1.5H4zm1.5 4.5h5v1h-5v-1zm0 2.5h5v1h-5V9zm0 2.5h3v1h-3v-1z";

    public static final String CURSOR =
            "M4 2l8 6.5-3.5.75L10.5 13 8.75 13.75 6.75 10 4 12.25V2z";

    public static final String TEXT_EDIT =
            "M3 3h10v2h-1V4H8.75v8H10.5v1h-5v-1h1.75V4H4v1H3V3z";

    public static final String HIGHLIGHT =
            "M9.5 2.5l4 4-6.5 6.5H3v-4l6.5-6.5zm-5 8.5l2.5 2.5";

    public static final String PEN =
            "M11.7 2.3a1 1 0 0 1 1.4 1.4l-8.4 8.4L2 13l.9-2.7 8.8-8z";

    public static final String UNDO =
            "M5.5 4L2 7.5 5.5 11V8.25h4.25a2.75 2.75 0 0 1 0 5.5H8v1.25h1.75a4 4 0 0 0 0-8H5.5V4z";

    public static final String REDO =
            "M10.5 4L14 7.5 10.5 11V8.25H6.25a2.75 2.75 0 0 0 0 5.5H8v1.25H6.25a4 4 0 0 1 0-8h4.25V4z";

    public static final String ROTATE =
            "M8 2.5a5.5 5.5 0 1 0 5.19 3.66h-1.35A4.25 4.25 0 1 1 8 3.75c1.15 0 2.19.46 2.95 1.2L9 6.89h4.5V2.39l-1.64 1.64A5.48 5.48 0 0 0 8 2.5z";

    public static final String TRASH =
            "M5.5 2h5l.5 1h3v1.25h-1v8.25A1.5 1.5 0 0 1 11.5 14h-7A1.5 1.5 0 0 1 3 12.5V4.25H2V3h3l.5-1zm-1 2.25v8.25h7V4.25h-7z";

    public static final String THEME =
            "M8 2a6 6 0 1 0 0 12A6 6 0 0 0 8 2zm0 1.25v9.5a4.75 4.75 0 0 1 0-9.5z";

    public static final String SEARCH =
            "M6.75 2a4.75 4.75 0 1 0 2.97 8.46l3.16 3.16a.65.65 0 0 0 .92-.92l-3.16-3.16A4.75 4.75 0 0 0 6.75 2zm0 1.25a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z";

    public static final String CHECK =
            "M13.28 4.22a.75.75 0 0 1 0 1.06l-6.25 6.25a.75.75 0 0 1-1.06 0L2.72 8.28a.75.75 0 0 1 1.06-1.06L6.5 9.94l5.72-5.72a.75.75 0 0 1 1.06 0z";

    public static final String FILE_PLUS =
            "M4 2a1.5 1.5 0 0 0-1.5 1.5v9A1.5 1.5 0 0 0 4 14h8a1.5 1.5 0 0 0 1.5-1.5V5.621a1.5 1.5 0 0 0-.44-1.06L10.44 1.94A1.5 1.5 0 0 0 9.378 1.5H4zm4 4.25a.65.65 0 0 1 .65.65v1.45h1.45a.65.65 0 0 1 0 1.3H8.65v1.45a.65.65 0 0 1-1.3 0V9.65H5.9a.65.65 0 0 1 0-1.3h1.45V6.9A.65.65 0 0 1 8 6.25z";

    public static final String INFO =
            "M8 1.75a6.25 6.25 0 1 0 0 12.5 6.25 6.25 0 0 0 0-12.5zM3 8a5 5 0 1 1 10 0A5 5 0 0 1 3 8zm5-2.75a.85.85 0 1 1 0 1.7.85.85 0 0 1 0-1.7zM7.35 7.5h1.3v3.5h-1.3V7.5z";

    public static final String EXTERNAL_LINK =
            "M9.5 2.5H13.5V6.5H12.25V4.63L7.94 8.94 7.06 8.06 11.37 3.75H9.5V2.5zM3.5 4H7.5V5.25H4.25V11.75H10.75V8.5H12V12a1 1 0 0 1-1 1H3.5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z";
}
