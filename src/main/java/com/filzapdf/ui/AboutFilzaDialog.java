package com.filzapdf.ui;

import java.awt.Desktop;
import java.net.URI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Architectural "About FilzaPDF" studio dialog presenting the story, vision,
 * developer profile, and philosophy behind FilzaPDF.
 */
public class AboutFilzaDialog {

    private static final String GITHUB_URL = "https://github.com/Peshimam-Ashar-Mohammed";
    private static final String PORTFOLIO_URL = "https://peshimam-ashar-mohammed.github.io/Portfolio/";
    private static final String REPO_URL = "https://github.com/Peshimam-Ashar-Mohammed/FilzaPDF";

    private final Stage dialogStage;

    public AboutFilzaDialog(Stage owner) {
        this.dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.NONE);
        dialogStage.setTitle("About FilzaPDF");

        BorderPane root = buildUi();
        Scene scene = new Scene(root, 720, 620);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialogStage.setScene(scene);
    }

    public void show() {
        dialogStage.show();
        dialogStage.toFront();
    }

    private BorderPane buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18, 22, 16, 22));

        // ── Top Header ──
        Label title = new Label("About FilzaPDF");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 700;");

        Label badge = new Label("OPEN SOURCE · LOCAL-FIRST");
        badge.getStyleClass().add("studio-badge");

        HBox titleRow = new HBox(10, title, badge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label tagline = new Label("Free document tools. Your files. Your control.");
        tagline.getStyleClass().add("muted-label");
        tagline.setStyle("-fx-font-size: 12.5px;");

        VBox header = new VBox(4, titleRow, tagline, new Separator());
        header.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(header);

        // ── Scrollable Story Content ──
        VBox storyBox = new VBox(18);
        storyBox.setPadding(new Insets(8, 12, 12, 4));

        // 1. Why I Built This
        storyBox.getChildren().add(buildSectionCard(
                "WHY I BUILT THIS",
                "Powerful document tools shouldn't have to come with expensive subscriptions, unnecessary complexity, or dependence on cloud services.",
                new String[] {
                        "Many people rely on PDF editors and office software for everyday tasks such as reading documents, editing text, merging files, adding annotations, and converting documents. Yet some advanced capabilities are locked behind paid plans, while online tools may require users to upload private files to third-party servers.",
                        "I wanted to explore a different approach.",
                        "FilzaPDF is my attempt to build a free, local-first document productivity suite that puts users and their files first.",
                        "Built with Java, JavaFX, and Apache PDFBox, the project brings PDF viewing, document manipulation, annotations, image-to-PDF creation, and Word document workflows into one desktop application. My goal is to keep developing it into a practical, accessible alternative for people who want useful tools without mandatory subscriptions or unnecessary cloud dependency."
                }
        ));

        // 2. The Bigger Vision
        storyBox.getChildren().add(buildSectionCard(
                "THE BIGGER VISION",
                "FilzaPDF is part of a broader vision for Filza: an ecosystem of free and open-source productivity software.",
                new String[] {
                        "The long-term ambition includes tools for documents, spreadsheets, presentations, file management, and document synchronization. Rather than building software around subscription lock-in, the goal is to create useful applications that people can run, inspect, modify, and improve.",
                        "This is a long-term vision, and FilzaPDF is one step toward it."
                }
        ));

        // 3. Built to Learn, Built to Share
        storyBox.getChildren().add(buildSectionCard(
                "BUILT TO LEARN, BUILT TO SHARE",
                "I'm building FilzaPDF as an independent software project while pursuing my B.Tech in Computer Science and Engineering.",
                new String[] {
                        "Beyond the application itself, this project is an opportunity to explore real software engineering: PDF rendering, JavaFX UI architecture, background processing, caching, document formats, persistence, testing, and performance optimization.",
                        "I want this project to be useful to others, open to community contributions, and a foundation for learning through real-world development.",
                        "If you find the project interesting, have suggestions, or want to contribute, you're welcome to explore the repository and share your feedback."
                }
        ));

        // 4. About the Developer
        storyBox.getChildren().add(buildDeveloperCard());

        // 5. Our Philosophy
        storyBox.getChildren().add(buildPhilosophyCard());

        ScrollPane scrollPane = new ScrollPane(storyBox);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        // ── Bottom Bar ──
        Label footerQuote = new Label("FilzaPDF — Free document tools. Your files. Your control.");
        footerQuote.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("accent-button");
        closeBtn.setOnAction(e -> dialogStage.close());

        HBox bottomBar = new HBox(12, footerQuote, spacer, closeBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(12, 0, 0, 0));
        root.setBottom(bottomBar);

        return root;
    }

    private VBox buildSectionCard(String captionText, String leadText, String[] paragraphs) {
        Label caption = new Label(captionText);
        caption.getStyleClass().add("section-caption");

        Label lead = new Label(leadText);
        lead.setWrapText(true);
        lead.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 700;");

        VBox box = new VBox(8, caption, lead);
        for (String p : paragraphs) {
            Label pLbl = new Label(p);
            pLbl.setWrapText(true);
            pLbl.getStyleClass().add("muted-label");
            pLbl.setStyle("-fx-font-size: 12.5px;");
            box.getChildren().add(pLbl);
        }
        box.getStyleClass().add("card-pane");
        box.setPadding(new Insets(14, 16, 14, 16));
        return box;
    }

    private VBox buildDeveloperCard() {
        Label caption = new Label("ABOUT THE DEVELOPER");
        caption.getStyleClass().add("section-caption");

        Label bio = new Label(
                "I'm Ashar, a Computer Science and Engineering student interested in software development, Java, open-source software, Linux, and building practical tools that solve real problems.");
        bio.setWrapText(true);
        bio.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

        VBox linksBox = new VBox(6,
                createLinkRow("GitHub", GITHUB_URL),
                createLinkRow("Portfolio", PORTFOLIO_URL),
                createLinkRow("FilzaPDF Repository", REPO_URL)
        );
        linksBox.setPadding(new Insets(4, 0, 0, 0));

        VBox card = new VBox(8, caption, bio, linksBox);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }

    private HBox createLinkRow(String labelText, String url) {
        Label keyLbl = new Label(labelText + ":");
        keyLbl.setMinWidth(135);
        keyLbl.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");

        Hyperlink link = new Hyperlink(url);
        link.setGraphic(SvgIcons.icon(SvgIcons.EXTERNAL_LINK, 12));
        link.setGraphicTextGap(5);
        link.setTooltip(new Tooltip("Open in browser (or click Copy)"));
        link.setStyle("-fx-font-size: 12px; -fx-border-color: transparent; -fx-padding: 2 4 2 0;");
        link.setOnAction(e -> openUrl(url));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyBtn = new Button("Copy");
        copyBtn.getStyleClass().add("surface-button");
        copyBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("Copied");
        });

        HBox row = new HBox(8, keyLbl, link, spacer, copyBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildPhilosophyCard() {
        Label caption = new Label("OUR PHILOSOPHY");
        caption.getStyleClass().add("section-caption");

        VBox items = new VBox(6,
                createPhilosophyItem("Free to use", "Essential functionality should be accessible to everyone."),
                createPhilosophyItem("Local-first", "Your documents should remain under your control."),
                createPhilosophyItem("Open source", "Software should be transparent and open to improvement."),
                createPhilosophyItem("Interoperable", "Support common document formats wherever practical."),
                createPhilosophyItem("Community-driven", "Good software gets better when people can contribute.")
        );

        Label closing = new Label(
                "The ambition is straightforward: build useful alternatives, learn by creating them, and give people more control over the software they use.");
        closing.setWrapText(true);
        closing.getStyleClass().add("muted-label");
        closing.setStyle("-fx-font-size: 12.5px; -fx-padding: 4 0 0 0;");

        VBox card = new VBox(8, caption, items, closing);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }

    private HBox createPhilosophyItem(String title, String detail) {
        Label dot = new Label("•");
        dot.setStyle("-fx-font-weight: 700;");

        Label titleLbl = new Label(title + " —");
        titleLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 12.5px;");

        Label detailLbl = new Label(detail);
        detailLbl.getStyleClass().add("muted-label");
        detailLbl.setStyle("-fx-font-size: 12.5px;");
        detailLbl.setWrapText(true);

        HBox row = new HBox(6, dot, titleLbl, detailLbl);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }
}
