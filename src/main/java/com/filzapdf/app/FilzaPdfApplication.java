package com.filzapdf.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.filzapdf.annotations.AddAnnotationCommand;
import com.filzapdf.annotations.Annotation;
import com.filzapdf.annotations.AnnotationManager;
import com.filzapdf.annotations.AnnotationType;
import com.filzapdf.pdf.BookmarkNode;
import com.filzapdf.pdf.BookmarkService;
import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.persistence.RecentFilesService;
import com.filzapdf.persistence.ViewerPreferences;
import com.filzapdf.search.PdfTextSearchService;
import com.filzapdf.search.SearchResult;
import com.filzapdf.ui.PdfViewerController;
import com.filzapdf.ui.PdfViewerModel;
import com.filzapdf.ui.ThemeService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * FilzaPDF main application.
 *
 * Owns the Stage, Scene, MenuBar, toolbar, sidebar, status bar,
 * and delegates document/rendering work to PdfViewerController
 * and the service layer (search, annotations, persistence, bookmarks).
 */
public class FilzaPdfApplication extends Application {

    // ── Services ────────────────────────────────────────────────────────────
    private final PdfDocumentService   pdfDocumentService  = new PdfDocumentService();
    private final RecentFilesService   recentFilesService  = new RecentFilesService();
    private final ViewerPreferences    viewerPreferences   = new ViewerPreferences();
    private final PdfTextSearchService searchService       = new PdfTextSearchService();
    private final BookmarkService      bookmarkService     = new BookmarkService();
    private final AnnotationManager    annotationManager   = new AnnotationManager();
    private       ThemeService         themeService;

    // ── Controller ──────────────────────────────────────────────────────────
    private PdfViewerController controller;

    // ── State ────────────────────────────────────────────────────────────────
    private File    currentFile;
    private Stage   primaryStage;

    // ── Toolbar controls ────────────────────────────────────────────────────
    private Button    previousButton;
    private Button    nextButton;
    private Button    zoomOutButton;
    private Button    zoomInButton;
    private Button    fitWidthButton;
    private Button    fitPageButton;
    private Button    resetZoomButton;
    private Button    undoButton;
    private Button    redoButton;
    private TextField pageField;
    private Label     pageLabel;
    private Label     zoomLabel;

    // ── Search bar ──────────────────────────────────────────────────────────
    private HBox      searchBar;
    private TextField searchField;
    private Label     searchResultLabel;
    private CheckBox  caseSensitiveCheck;
    private List<SearchResult> lastSearchResults;

    // ── Status bar ──────────────────────────────────────────────────────────
    private Label statusPageLabel;
    private Label statusZoomLabel;
    private Label statusDocLabel;

    // ── Sidebar ─────────────────────────────────────────────────────────────
    private VBox     thumbnailContainer;
    private TreeView<String> bookmarkTree;

    // ── Thumbnail background loading ─────────────────────────────────────────
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "thumbnail-loader");
        t.setDaemon(true);
        return t;
    });
    private Future<?> thumbnailTask;

    // ── Menus that need dynamic update ───────────────────────────────────────
    private Menu recentFilesMenu;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.themeService  = new ThemeService(viewerPreferences);
        this.controller    = new PdfViewerController(stage);

        controller.setOnPageChanged(this::onPageChanged);
        annotationManager.setOnAnnotationsChanged(this::onAnnotationsChanged);

        BorderPane root = buildRoot();

        Scene scene = new Scene(root, 1280, 760);
        themeService.setScene(scene);
        controller.installKeyboardShortcuts(scene);
        installAppKeyboardShortcuts(scene);
        installDragAndDrop(scene);

        stage.setTitle("FilzaPDF");
        stage.setScene(scene);
        stage.show();

        setViewerControlsEnabled(false);
        root.requestFocus();
    }

    @Override
    public void stop() {
        saveCurrentFileState();
        if (controller != null)  controller.shutdown();
        if (searchService != null) searchService.shutdown();
        thumbnailExecutor.shutdownNow();
    }

    // ── Build scene graph ────────────────────────────────────────────────────

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setTop(buildTopSection());
        root.setLeft(buildSidebar());
        root.setCenter(controller.getRoot());
        root.setBottom(buildStatusBar());
        return root;
    }

    private VBox buildTopSection() {
        VBox top = new VBox();
        top.getChildren().addAll(buildMenuBar(), buildToolbar(), buildSearchBar());
        return top;
    }

    // ── MenuBar ──────────────────────────────────────────────────────────────

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(
            buildFileMenu(),
            buildEditMenu(),
            buildViewMenu(),
            buildNavigateMenu(),
            buildHelpMenu()
        );
        return menuBar;
    }

    private Menu buildFileMenu() {
        Menu file = new Menu("_File");

        MenuItem open   = new MenuItem("Open…");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        open.setOnAction(e -> openPdfDialog());

        recentFilesMenu = new Menu("Open Recent");
        refreshRecentFilesMenu();

        MenuItem close  = new MenuItem("Close");
        close.setOnAction(e -> closeDocument());

        MenuItem props  = new MenuItem("Document Properties…");
        props.setOnAction(e -> showDocumentProperties());

        MenuItem quit   = new MenuItem("Quit");
        quit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        quit.setOnAction(e -> primaryStage.close());

        file.getItems().addAll(open, recentFilesMenu, close,
                new SeparatorMenuItem(), props,
                new SeparatorMenuItem(), quit);
        return file;
    }

    private Menu buildEditMenu() {
        Menu edit = new Menu("_Edit");

        MenuItem undo = new MenuItem("Undo");
        undo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undo.setOnAction(e -> { annotationManager.undo(); refreshAnnotationButtons(); });

        MenuItem redo = new MenuItem("Redo");
        redo.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redo.setOnAction(e -> { annotationManager.redo(); refreshAnnotationButtons(); });

        MenuItem find = new MenuItem("Find…");
        find.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        find.setOnAction(e -> showSearchBar());

        edit.getItems().addAll(undo, redo, new SeparatorMenuItem(), find);
        return edit;
    }

    private Menu buildViewMenu() {
        Menu view = new Menu("_View");

        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
        zoomIn.setOnAction(e -> controller.zoomIn());

        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOut.setOnAction(e -> controller.zoomOut());

        MenuItem resetZoom = new MenuItem("Reset Zoom");
        resetZoom.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        resetZoom.setOnAction(e -> controller.resetZoom());

        MenuItem fitWidth = new MenuItem("Fit Width");
        fitWidth.setOnAction(e -> controller.setFitWidth());

        MenuItem fitPage = new MenuItem("Fit Page");
        fitPage.setOnAction(e -> controller.setFitPage());

        CheckMenuItem darkTheme = new CheckMenuItem("Dark Theme");
        darkTheme.setSelected("dark".equals(viewerPreferences.getTheme()));
        darkTheme.setOnAction(e -> {
            themeService.applyTheme(darkTheme.isSelected() ? "dark" : "light");
        });

        MenuItem fullscreen = new MenuItem("Fullscreen");
        fullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
        fullscreen.setOnAction(e -> primaryStage.setFullScreen(!primaryStage.isFullScreen()));

        view.getItems().addAll(zoomIn, zoomOut, resetZoom, new SeparatorMenuItem(),
                fitWidth, fitPage, new SeparatorMenuItem(),
                darkTheme, new SeparatorMenuItem(), fullscreen);
        return view;
    }

    private Menu buildNavigateMenu() {
        Menu nav = new Menu("_Navigate");

        MenuItem first = new MenuItem("First Page");
        first.setOnAction(e -> goToPage(0));

        MenuItem prev = new MenuItem("Previous Page");
        prev.setAccelerator(new KeyCodeCombination(KeyCode.LEFT));
        prev.setOnAction(e -> controller.previousPage());

        MenuItem next = new MenuItem("Next Page");
        next.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT));
        next.setOnAction(e -> controller.nextPage());

        MenuItem last = new MenuItem("Last Page");
        last.setOnAction(e -> {
            if (controller.hasDocument())
                goToPage(controller.getModel().getPageCount() - 1);
        });

        MenuItem goTo = new MenuItem("Go to Page…");
        goTo.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        goTo.setOnAction(e -> pageField.requestFocus());

        nav.getItems().addAll(first, prev, next, last, new SeparatorMenuItem(), goTo);
        return nav;
    }

    private Menu buildHelpMenu() {
        Menu help = new Menu("_Help");
        MenuItem about = new MenuItem("About FilzaPDF");
        about.setOnAction(e -> showAboutDialog());
        help.getItems().add(about);
        return help;
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        Button openButton   = new Button("Open");
        openButton.setTooltip(new Tooltip("Open PDF (Ctrl+O)"));
        openButton.setOnAction(e -> openPdfDialog());

        previousButton = new Button("◀");
        previousButton.setTooltip(new Tooltip("Previous Page (←)"));
        previousButton.setOnAction(e -> controller.previousPage());

        nextButton = new Button("▶");
        nextButton.setTooltip(new Tooltip("Next Page (→)"));
        nextButton.setOnAction(e -> controller.nextPage());

        pageField = new TextField();
        pageField.setPrefWidth(52);
        pageField.setPromptText("Page");
        pageField.setFocusTraversable(false);
        pageField.setOnAction(e -> jumpToPage());

        pageLabel = new Label("of 0");

        zoomOutButton = new Button("−");
        zoomOutButton.setTooltip(new Tooltip("Zoom Out (-)"));
        zoomOutButton.setOnAction(e -> controller.zoomOut());

        zoomLabel = new Label("100%");
        zoomLabel.setMinWidth(46);
        zoomLabel.setAlignment(Pos.CENTER);

        zoomInButton = new Button("+");
        zoomInButton.setTooltip(new Tooltip("Zoom In (+)"));
        zoomInButton.setOnAction(e -> controller.zoomIn());

        fitWidthButton = new Button("Fit W");
        fitWidthButton.setTooltip(new Tooltip("Fit Width (W)"));
        fitWidthButton.setOnAction(e -> controller.setFitWidth());

        fitPageButton = new Button("Fit P");
        fitPageButton.setTooltip(new Tooltip("Fit Page (P)"));
        fitPageButton.setOnAction(e -> controller.setFitPage());

        resetZoomButton = new Button("100%");
        resetZoomButton.setTooltip(new Tooltip("Reset Zoom (Ctrl+0)"));
        resetZoomButton.setOnAction(e -> controller.resetZoom());

        undoButton = new Button("↩");
        undoButton.setTooltip(new Tooltip("Undo Annotation (Ctrl+Z)"));
        undoButton.setOnAction(e -> { annotationManager.undo(); refreshAnnotationButtons(); });

        redoButton = new Button("↪");
        redoButton.setTooltip(new Tooltip("Redo Annotation (Ctrl+Y)"));
        redoButton.setOnAction(e -> { annotationManager.redo(); refreshAnnotationButtons(); });

        HBox toolbar = new HBox(6,
            openButton,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            previousButton, nextButton,
            pageField, pageLabel,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            zoomOutButton, zoomLabel, zoomInButton,
            fitWidthButton, fitPageButton, resetZoomButton,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            undoButton, redoButton
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));

        setViewerControlsEnabled(false);
        return toolbar;
    }

    // ── Search bar ───────────────────────────────────────────────────────────

    private HBox buildSearchBar() {
        searchField = new TextField();
        searchField.setPromptText("Search…");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> performSearch());

        Button searchNext = new Button("▼");
        searchNext.setTooltip(new Tooltip("Next Result"));
        searchNext.setOnAction(e -> navigateSearchResult(true));

        Button searchPrev = new Button("▲");
        searchPrev.setTooltip(new Tooltip("Previous Result"));
        searchPrev.setOnAction(e -> navigateSearchResult(false));

        caseSensitiveCheck = new CheckBox("Aa");
        caseSensitiveCheck.setTooltip(new Tooltip("Case Sensitive"));

        searchResultLabel = new Label("");

        Button closeSearch = new Button("✕");
        closeSearch.setOnAction(e -> hideSearchBar());

        searchBar = new HBox(6,
            new Label("Find:"), searchField,
            searchPrev, searchNext,
            caseSensitiveCheck, searchResultLabel,
            closeSearch
        );
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(3, 8, 3, 8));
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        return searchBar;
    }

    private void showSearchBar() {
        searchBar.setVisible(true);
        searchBar.setManaged(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void hideSearchBar() {
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        searchResultLabel.setText("");
        lastSearchResults = null;
    }

    private void performSearch() {
        if (!controller.hasDocument()) return;
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            searchResultLabel.setText("No query");
            return;
        }
        lastSearchResults = searchService.search(query, caseSensitiveCheck.isSelected());
        updateSearchResultLabel();
        if (!lastSearchResults.isEmpty()) {
            // Navigate to first result's page
            controller.setCurrentPage(lastSearchResults.get(0).getPageIndex());
        }
    }

    private void navigateSearchResult(boolean forward) {
        if (lastSearchResults == null || lastSearchResults.isEmpty()) {
            performSearch();
            return;
        }
        SearchResult result = forward ? searchService.nextResult() : searchService.previousResult();
        if (result != null) {
            controller.setCurrentPage(result.getPageIndex());
        }
        updateSearchResultLabel();
    }

    private void updateSearchResultLabel() {
        if (lastSearchResults == null || lastSearchResults.isEmpty()) {
            searchResultLabel.setText("No results");
        } else {
            int idx  = searchService.getCurrentResultIndex() + 1;
            int total = searchService.getResultCount();
            searchResultLabel.setText(idx + " / " + total);
        }
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────

    private TabPane buildSidebar() {
        // Thumbnail tab
        thumbnailContainer = new VBox(10);
        thumbnailContainer.setAlignment(Pos.TOP_CENTER);
        thumbnailContainer.setPrefWidth(140);

        ScrollPane thumbScroll = new ScrollPane(thumbnailContainer);
        thumbScroll.setFitToWidth(true);
        thumbScroll.setFocusTraversable(false);

        Tab thumbTab = new Tab("Pages", thumbScroll);
        thumbTab.setClosable(false);

        // Bookmarks tab
        bookmarkTree = new TreeView<>();
        bookmarkTree.setShowRoot(false);
        bookmarkTree.setRoot(new TreeItem<>("Bookmarks"));
        bookmarkTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) navigateToBookmark();
        });

        Tab bookmarkTab = new Tab("Bookmarks", bookmarkTree);
        bookmarkTab.setClosable(false);

        TabPane sidebar = new TabPane(thumbTab, bookmarkTab);
        sidebar.setPrefWidth(160);
        sidebar.setTabMinWidth(60);
        return sidebar;
    }

    // ── Status bar ───────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        statusPageLabel = new Label("No document");
        statusZoomLabel = new Label("");
        statusDocLabel  = new Label("");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, statusDocLabel, spacer, statusPageLabel, statusZoomLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(3, 8, 3, 8));
        bar.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // ── Keyboard shortcuts (app-level) ────────────────────────────────────────

    private void installAppKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F11:
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                    event.consume();
                    break;
                case F:
                    if (event.isControlDown()) { showSearchBar(); event.consume(); }
                    break;
                case Z:
                    if (event.isControlDown()) { annotationManager.undo(); refreshAnnotationButtons(); event.consume(); }
                    break;
                case Y:
                    if (event.isControlDown()) { annotationManager.redo(); refreshAnnotationButtons(); event.consume(); }
                    break;
                case W:
                    if (!event.isControlDown()) { controller.setFitWidth(); event.consume(); }
                    break;
                case P:
                    if (!event.isControlDown()) { controller.setFitPage(); event.consume(); }
                    break;
                case G:
                    if (event.isControlDown()) { pageField.requestFocus(); event.consume(); }
                    break;
                case ESCAPE:
                    if (searchBar.isVisible()) { hideSearchBar(); event.consume(); }
                    break;
                default:
                    break;
            }
        });
    }

    // ── Drag and drop ─────────────────────────────────────────────────────────

    private void installDragAndDrop(Scene scene) {
        scene.setOnDragOver((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().stream()
                    .anyMatch(f -> f.getName().toLowerCase().endsWith(".pdf"))) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        scene.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                db.getFiles().stream()
                    .filter(f -> f.getName().toLowerCase().endsWith(".pdf"))
                    .findFirst()
                    .ifPresent(this::openFile);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // ── Document lifecycle ────────────────────────────────────────────────────

    private void openPdfDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open PDF");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) openFile(file);
    }

    private void openFile(File file) {
        try {
            saveCurrentFileState();

            if (thumbnailTask != null) thumbnailTask.cancel(true);
            controller.closeDocument();
            searchService.search("", false); // clear results

            PDDocument document = pdfDocumentService.open(file);
            controller.openDocument(document);

            currentFile = file;
            recentFilesService.addFile(file);
            refreshRecentFilesMenu();

            setViewerControlsEnabled(true);
            primaryStage.setTitle("FilzaPDF — " + file.getName());

            // Restore last page/zoom for this file
            int lastPage = viewerPreferences.getLastPage(file.getAbsolutePath());
            double lastZoom = viewerPreferences.getLastZoom(file.getAbsolutePath());
            if (lastZoom > 0) controller.setZoom(lastZoom);
            if (lastPage > 0 && lastPage < controller.getModel().getPageCount()) {
                controller.setCurrentPage(lastPage);
            }

            // Background thumbnail loading
            renderThumbnailsInBackground();

            // Background bookmark extraction
            loadBookmarksInBackground(document);

            // Background text extraction for search
            searchService.extractTextInBackground(document, () ->
                Platform.runLater(() -> statusDocLabel.setText(file.getName() + " — ready for search")));

            statusDocLabel.setText(file.getName());
            hideSearchBar();

        } catch (IOException e) {
            showErrorDialog("Failed to open PDF",
                "The file could not be opened.", e.getMessage());
        }
    }

    private void closeDocument() {
        if (!controller.hasDocument()) return;
        saveCurrentFileState();
        if (thumbnailTask != null) thumbnailTask.cancel(true);
        controller.closeDocument();
        currentFile = null;
        thumbnailContainer.getChildren().clear();
        bookmarkTree.setRoot(new TreeItem<>("Bookmarks"));
        setViewerControlsEnabled(false);
        primaryStage.setTitle("FilzaPDF");
        updateStatusBar();
        hideSearchBar();
    }

    private void saveCurrentFileState() {
        if (currentFile != null && controller.hasDocument()) {
            PdfViewerModel model = controller.getModel();
            viewerPreferences.setLastPage(currentFile.getAbsolutePath(), model.getCurrentPage());
            viewerPreferences.setLastZoom(currentFile.getAbsolutePath(), model.getZoom());
        }
    }

    // ── Thumbnail loading ─────────────────────────────────────────────────────

    private void renderThumbnailsInBackground() {
        thumbnailContainer.getChildren().clear();
        if (!controller.hasDocument()) return;

        int pageCount = controller.getModel().getPageCount();

        // Place empty placeholder boxes immediately so layout is stable
        for (int i = 0; i < pageCount; i++) {
            final int idx = i;
            Label numLabel = new Label("Page " + (i + 1));
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            view.setFitWidth(110);
            view.setOnMouseClicked(e -> controller.setCurrentPage(idx));
            VBox box = new VBox(4, numLabel, view);
            box.setAlignment(Pos.CENTER);
            thumbnailContainer.getChildren().add(box);
        }

        thumbnailTask = thumbnailExecutor.submit(() -> {
            for (int i = 0; i < pageCount; i++) {
                if (Thread.currentThread().isInterrupted()) break;
                final int pageIndex = i;
                try {
                    BufferedImage img = controller.getRenderer().renderPage(pageIndex, 25f);
                    if (Thread.currentThread().isInterrupted()) break;
                    Platform.runLater(() -> {
                        if (pageIndex < thumbnailContainer.getChildren().size()) {
                            VBox box = (VBox) thumbnailContainer.getChildren().get(pageIndex);
                            ImageView view = (ImageView) box.getChildren().get(1);
                            view.setImage(SwingFXUtils.toFXImage(img, null));
                        }
                    });
                } catch (Exception e) {
                    System.err.println("Thumbnail error page " + pageIndex + ": " + e.getMessage());
                }
            }
        });
    }

    // ── Bookmark loading ──────────────────────────────────────────────────────

    private void loadBookmarksInBackground(PDDocument document) {
        bookmarkTree.setRoot(new TreeItem<>("Bookmarks"));
        thumbnailExecutor.submit(() -> {
            try {
                List<BookmarkNode> nodes = bookmarkService.extractBookmarks(document);
                Platform.runLater(() -> populateBookmarkTree(nodes));
            } catch (Exception e) {
                System.err.println("Bookmark extraction error: " + e.getMessage());
            }
        });
    }

    private void populateBookmarkTree(List<BookmarkNode> nodes) {
        TreeItem<String> root = new TreeItem<>("Bookmarks");
        root.setExpanded(true);
        for (BookmarkNode node : nodes) {
            root.getChildren().add(buildTreeItem(node));
        }
        bookmarkTree.setRoot(root);
    }

    private TreeItem<String> buildTreeItem(BookmarkNode node) {
        // Store page index in the title so we can navigate on click
        String label = node.getTitle() != null ? node.getTitle() : "(untitled)";
        TreeItem<String> item = new TreeItem<>(label + "\u0000" + node.getPageIndex());
        item.setExpanded(true);
        for (BookmarkNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private void navigateToBookmark() {
        TreeItem<String> selected = bookmarkTree.getSelectionModel().getSelectedItem();
        if (selected == null || !controller.hasDocument()) return;
        String value = selected.getValue();
        if (value == null) return;
        // Parse encoded page index
        int sep = value.lastIndexOf('\u0000');
        if (sep < 0) return;
        try {
            int pageIndex = Integer.parseInt(value.substring(sep + 1));
            if (pageIndex >= 0) controller.setCurrentPage(pageIndex);
        } catch (NumberFormatException ignored) {}
    }

    // ── Recent files menu ─────────────────────────────────────────────────────

    private void refreshRecentFilesMenu() {
        recentFilesMenu.getItems().clear();
        List<String> recents = recentFilesService.getRecentFiles();
        if (recents.isEmpty()) {
            MenuItem none = new MenuItem("(none)");
            none.setDisable(true);
            recentFilesMenu.getItems().add(none);
        } else {
            for (String path : recents) {
                File f = new File(path);
                MenuItem item = new MenuItem(f.getName());
                item.setOnAction(e -> openFile(f));
                recentFilesMenu.getItems().add(item);
            }
            recentFilesMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clear = new MenuItem("Clear Recent");
            clear.setOnAction(e -> clearRecentFiles());
            recentFilesMenu.getItems().add(clear);
        }
    }

    private void clearRecentFiles() {
        recentFilesService.clearAll();
        refreshRecentFilesMenu();
    }

    // ── Document Properties dialog ────────────────────────────────────────────

    private void showDocumentProperties() {
        if (!controller.hasDocument()) return;

        org.apache.pdfbox.pdmodel.PDDocument doc = controller.getDocument();
        org.apache.pdfbox.pdmodel.PDDocumentInformation info = doc.getDocumentInformation();

        String text = String.join("\n",
            "Title:    " + nvl(info.getTitle()),
            "Author:   " + nvl(info.getAuthor()),
            "Subject:  " + nvl(info.getSubject()),
            "Creator:  " + nvl(info.getCreator()),
            "Producer: " + nvl(info.getProducer()),
            "Pages:    " + doc.getNumberOfPages()
        );

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Document Properties");
        alert.setHeaderText(currentFile != null ? currentFile.getName() : "PDF Document");
        alert.setContentText(text);
        alert.showAndWait();
    }

    // ── Annotation helpers ────────────────────────────────────────────────────

    /** Demo: adds a placeholder annotation on the current page. Real tools would use AnnotationTool implementations. */
    @SuppressWarnings("unused")
    private void addDemoAnnotation(AnnotationType type, int pageIndex, double x, double y, double w, double h) {
        Annotation a = new Annotation();
        a.setType(type);
        a.setPageIndex(pageIndex);
        a.setX(x); a.setY(y);
        a.setWidth(w); a.setHeight(h);
        a.setColor("#FFFF00");
        annotationManager.executeCommand(new AddAnnotationCommand(annotationManager, a));
    }

    private void onAnnotationsChanged() {
        refreshAnnotationButtons();
    }

    private void refreshAnnotationButtons() {
        // Buttons are always present; undo/redo gracefully no-op when stacks are empty
        // (AnnotationManager already guards them)
    }

    // ── Page navigation helpers ───────────────────────────────────────────────

    private void goToPage(int pageIndex) {
        if (controller.hasDocument()) controller.setCurrentPage(pageIndex);
    }

    private void jumpToPage() {
        if (!controller.hasDocument()) return;
        try {
            int target = Integer.parseInt(pageField.getText()) - 1;
            int count  = controller.getModel().getPageCount();
            if (target >= 0 && target < count) {
                controller.setCurrentPage(target);
            } else {
                updateLabels();
            }
        } catch (NumberFormatException e) {
            updateLabels();
        }
    }

    // ── Label / status update ─────────────────────────────────────────────────

    private void onPageChanged() {
        updateLabels();
        updateStatusBar();
    }

    private void updateLabels() {
        if (!controller.hasDocument()) {
            pageLabel.setText("of 0");
            pageField.setText("");
            zoomLabel.setText("100%");
            return;
        }
        PdfViewerModel model = controller.getModel();
        pageLabel.setText("of " + model.getPageCount());
        pageField.setText(String.valueOf(model.getCurrentPage() + 1));
        zoomLabel.setText(String.format("%.0f%%", model.getZoom() * 100));
    }

    private void updateStatusBar() {
        if (!controller.hasDocument()) {
            statusPageLabel.setText("No document");
            statusZoomLabel.setText("");
            return;
        }
        PdfViewerModel model = controller.getModel();
        statusPageLabel.setText("Page " + (model.getCurrentPage() + 1) + " of " + model.getPageCount());
        statusZoomLabel.setText(String.format("Zoom %.0f%%", model.getZoom() * 100));
    }

    // ── UI control state ──────────────────────────────────────────────────────

    private void setViewerControlsEnabled(boolean enabled) {
        previousButton .setDisable(!enabled);
        nextButton     .setDisable(!enabled);
        zoomOutButton  .setDisable(!enabled);
        zoomInButton   .setDisable(!enabled);
        fitWidthButton .setDisable(!enabled);
        fitPageButton  .setDisable(!enabled);
        resetZoomButton.setDisable(!enabled);
        undoButton     .setDisable(!enabled);
        redoButton     .setDisable(!enabled);
        pageField      .setDisable(!enabled);
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void showErrorDialog(String title, String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        // Show user-friendly message; developer detail goes to stderr, not to user
        alert.setContentText("An error occurred. Please check the file and try again.");
        alert.showAndWait();
        System.err.println("[ERROR] " + title + " | " + detail);
    }

    private void showAboutDialog() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About FilzaPDF");
        alert.setHeaderText("FilzaPDF v0.1.0");
        alert.setContentText(
            "A professional Java desktop PDF reader.\n\n" +
            "Built with:\n" +
            "  • Java 21\n" +
            "  • JavaFX 21\n" +
            "  • Apache PDFBox 3.0.6\n\n" +
            "Keyboard shortcuts:\n" +
            "  Ctrl+O  Open PDF\n" +
            "  Ctrl+F  Find\n" +
            "  ← / →   Previous / Next page\n" +
            "  + / −   Zoom in / out\n" +
            "  Ctrl+0  Reset zoom\n" +
            "  F11     Fullscreen\n" +
            "  Ctrl+Z  Undo annotation\n" +
            "  Ctrl+Y  Redo annotation"
        );
        alert.showAndWait();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String nvl(String s) {
        return s != null && !s.isEmpty() ? s : "—";
    }

    public static void main(String[] args) {
        launch(args);
    }
}