package com.filzapdf.ui;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import com.filzapdf.persistence.RecentFilesService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Architectural Studio Home Launchpad view displayed when no document is active
 * or when toggled via the Home button.
 */
public class WelcomeDashboardView {

    private final RecentFilesService recentFilesService;
    private final Consumer<File> onOpenRecentFile;
    private final ScrollPane scrollPane;
    private final VBox recentBox;

    public WelcomeDashboardView(
            RecentFilesService recentFilesService,
            Runnable onOpenDocument,
            Runnable onImagesToPdf,
            Runnable onMergePdfs,
            Runnable onNewWordDoc,
            Runnable onEditWordDoc,
            Runnable onConvertWordToPdf,
            Runnable onShowAbout,
            Consumer<File> onOpenRecentFile) {
        this.recentFilesService = recentFilesService;
        this.onOpenRecentFile = onOpenRecentFile;
        this.recentBox = new VBox(8);
        this.recentBox.setMaxWidth(850);
        this.scrollPane = buildDashboard(
                onOpenDocument,
                onImagesToPdf,
                onMergePdfs,
                onNewWordDoc,
                onEditWordDoc,
                onConvertWordToPdf,
                onShowAbout
        );
        refreshRecentFiles();
    }

    public ScrollPane getRoot() {
        return scrollPane;
    }

    public void setVisibleAndManaged(boolean show) {
        scrollPane.setVisible(show);
        scrollPane.setManaged(show);
        if (show) {
            refreshRecentFiles();
        }
    }

    public boolean isVisible() {
        return scrollPane.isVisible();
    }

    private ScrollPane buildDashboard(
            Runnable onOpenDocument,
            Runnable onImagesToPdf,
            Runnable onMergePdfs,
            Runnable onNewWordDoc,
            Runnable onEditWordDoc,
            Runnable onConvertWordToPdf,
            Runnable onShowAbout) {

        Label brandTitle = new Label("FilzaPDF");
        brandTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        Label badge = new Label("STUDIO");
        badge.getStyleClass().add("studio-badge");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button storyBtn = new Button("About & Our Story", SvgIcons.icon(SvgIcons.INFO, 13));
        storyBtn.getStyleClass().add("surface-button");
        storyBtn.setGraphicTextGap(6);
        storyBtn.setOnAction(e -> onShowAbout.run());

        HBox titleRow = new HBox(10, brandTitle, badge, headerSpacer, storyBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label subtitle = new Label(
                "Free document tools. Your files. Your control. Open, edit, merge, or compose PDFs and Word documents locally.");
        subtitle.getStyleClass().add("muted-label");
        subtitle.setStyle("-fx-font-size: 12.5px;");

        VBox headerBox = new VBox(5, titleRow, subtitle);
        headerBox.setMaxWidth(850);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setMaxWidth(850);

        grid.add(createStudioCard(
                SvgIcons.FOLDER_OPEN,
                "Open Document",
                "Open any PDF, Word (.docx), or image file directly in the workspace.",
                "Ctrl+O",
                onOpenDocument
        ), 0, 0);

        grid.add(createStudioCard(
                SvgIcons.IMAGE,
                "Images to PDF",
                "Compile PNG, JPG, BMP, or GIF images into a paginated PDF document.",
                "Studio",
                onImagesToPdf
        ), 1, 0);

        grid.add(createStudioCard(
                SvgIcons.MERGE,
                "Merge & Compose PDFs",
                "Combine pages from multiple PDFs using custom sequences or recipes.",
                "Ctrl+M",
                onMergePdfs
        ), 2, 0);

        grid.add(createStudioCard(
                SvgIcons.FILE_TEXT,
                "New Document",
                "Author a formatted document with headings, lists, and images, then export to PDF.",
                "Ctrl+N",
                onNewWordDoc
        ), 0, 1);

        grid.add(createStudioCard(
                SvgIcons.TEXT_EDIT,
                "Edit Word (.docx)",
                "Open an existing Microsoft Word file for editing and direct PDF conversion.",
                ".docx",
                onEditWordDoc
        ), 1, 1);

        grid.add(createStudioCard(
                SvgIcons.ROTATE,
                "Convert Word to PDF",
                "Select a .docx file and render it straight into the PDF viewer.",
                "Quick Convert",
                onConvertWordToPdf
        ), 2, 1);

        VBox content = new VBox(22, headerBox, grid, recentBox);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(36, 28, 32, 28));

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        return sp;
    }

    private VBox createStudioCard(String svgPath, String titleText, String descText, String shortcutHint, Runnable action) {
        Node iconNode = SvgIcons.icon(svgPath, 16);
        StackPane iconBox = new StackPane(iconNode);
        iconBox.getStyleClass().add("card-icon-box");
        iconBox.setPrefSize(30, 30);
        iconBox.setMinSize(30, 30);
        iconBox.setMaxSize(30, 30);

        Label hint = new Label(shortcutHint);
        hint.getStyleClass().add("muted-label");
        hint.setStyle("-fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(iconBox, spacer, hint);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold;");

        Label desc = new Label(descText);
        desc.setWrapText(true);
        desc.getStyleClass().add("muted-label");
        desc.setStyle("-fx-font-size: 11.5px;");
        desc.setMinHeight(34);

        VBox card = new VBox(8, topRow, title, desc);
        card.getStyleClass().add("welcome-card");
        card.setPadding(new Insets(14));
        card.setPrefWidth(274);
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    public void refreshRecentFiles() {
        recentBox.getChildren().clear();
        if (recentFilesService == null) return;
        List<String> recents = recentFilesService.getRecentFiles();
        if (recents.isEmpty()) return;

        Label sectionTitle = new Label("RECENT FILES");
        sectionTitle.getStyleClass().add("muted-label");
        sectionTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        VBox listContainer = new VBox(4);
        int shown = 0;
        for (String path : recents) {
            File f = new File(path);
            if (!f.exists()) continue;
            if (shown++ >= 6) break;

            Node fileIcon = SvgIcons.icon(SvgIcons.FILE_TEXT, 14);
            Label nameLbl = new Label(f.getName());
            nameLbl.setStyle("-fx-font-weight: 600;");

            Label dirLbl = new Label(f.getParent() != null ? f.getParent() : "");
            dirLbl.getStyleClass().add("muted-label");
            dirLbl.setStyle("-fx-font-size: 11.5px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, fileIcon, nameLbl, spacer, dirLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.getStyleClass().add("welcome-card");
            row.setOnMouseClicked(e -> {
                if (onOpenRecentFile != null) {
                    onOpenRecentFile.accept(f);
                }
            });
            listContainer.getChildren().add(row);
        }

        if (!listContainer.getChildren().isEmpty()) {
            recentBox.getChildren().addAll(sectionTitle, listContainer);
        }
    }
}
