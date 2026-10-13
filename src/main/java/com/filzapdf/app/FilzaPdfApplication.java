package com.filzapdf.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import com.filzapdf.annotations.AddAnnotationCommand;
import com.filzapdf.annotations.Annotation;
import com.filzapdf.annotations.AnnotationManager;
import com.filzapdf.annotations.AnnotationType;
import com.filzapdf.annotations.PdfAnnotationPersistenceService;
import com.filzapdf.pdf.BookmarkNode;
import com.filzapdf.pdf.BookmarkService;
import com.filzapdf.pdf.PdfCreatorService;
import com.filzapdf.pdf.PdfDocumentService;
import com.filzapdf.pdf.PdfTextEditorService;
import com.filzapdf.pdf.PdfToolboxService;
import com.filzapdf.pdf.RecognizedTextBlock;
import com.filzapdf.pdf.WordDocumentService;
import com.filzapdf.persistence.RecentFilesService;
import com.filzapdf.persistence.ViewerPreferences;
import com.filzapdf.search.PdfTextSearchService;
import com.filzapdf.search.SearchResult;
import com.filzapdf.ui.AboutFilzaDialog;
import com.filzapdf.ui.ImageToPdfDialog;
import com.filzapdf.ui.PageOperationsDialogs;
import com.filzapdf.ui.PdfMergerStudioDialog;
import com.filzapdf.ui.PdfTextInspectorPane;
import com.filzapdf.ui.PdfViewerController;
import com.filzapdf.ui.PdfViewerController.InteractionMode;
import com.filzapdf.ui.PdfViewerController.PageCoordinateClick;
import com.filzapdf.ui.PdfViewerModel;
import com.filzapdf.ui.SvgIcons;
import com.filzapdf.ui.ThemeService;
import com.filzapdf.ui.WelcomeDashboardView;
import com.filzapdf.ui.WordStudioDialog;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * FilzaPDF Studio — Professional PDF & Document Workspace.
 */
public class FilzaPdfApplication extends Application {

    // ── Services ────────────────────────────────────────────────────────────
    private final PdfDocumentService              pdfDocumentService    = new PdfDocumentService();
    private final PdfCreatorService               pdfCreatorService     = new PdfCreatorService();
    private final PdfTextEditorService            textEditorService     = new PdfTextEditorService();
    private final PdfToolboxService               toolboxService        = new PdfToolboxService();
    private final WordDocumentService             wordDocumentService   = new WordDocumentService();
    private final PdfAnnotationPersistenceService annotationPersistence = new PdfAnnotationPersistenceService();
    private final RecentFilesService              recentFilesService    = new RecentFilesService();
    private final ViewerPreferences               viewerPreferences     = new ViewerPreferences();
    private final PdfTextSearchService            searchService         = new PdfTextSearchService();
    private final BookmarkService                 bookmarkService       = new BookmarkService();
    private final AnnotationManager               annotationManager     = new AnnotationManager();
    private       ThemeService                    themeService;

    // ── Controller, Welcome Screen & Inspector ──────────────────────────────
    private BorderPane            rootPane;
    private StackPane             centerViewportStack;
    private WelcomeDashboardView  welcomeDashboardView;
    private PageOperationsDialogs pageOperationsDialogs;
    private PdfViewerController   controller;
    private PdfTextInspectorPane  textInspectorPane;

    // ── Contextual Sub-Toolbars & Guided Banner ─────────────────────────────
    private HBox  contextualSubBar;
    private HBox  editSubGroup;
    private HBox  markupSubGroup;
    private HBox  organizeSubGroup;
    private HBox  guidedBanner;
    private Label guidedBannerLabel;

    // ── Segmented Workspace Switcher ────────────────────────────────────────
    private ToggleGroup  workspaceSegmentGroup;
    private ToggleButton segViewBtn;
    private ToggleButton segEditBtn;
    private ToggleButton segMarkupBtn;
    private ToggleButton segOrganizeBtn;

    // ── State ────────────────────────────────────────────────────────────────
    private File    currentFile;
    private Stage   primaryStage;
    private boolean hasUnsavedChanges = false;

    // ── Toolbar controls ────────────────────────────────────────────────────
    private Button    homeButton;
    private Button    saveButton;
    private Button    saveAsButton;
    private Button    exportWordButton;
    private Button    themeToggleButton;
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

    // ── Editing & Tool bar controls ─────────────────────────────────────────
    private ToggleGroup  modeToggleGroup;
    private ToggleButton browseModeBtn;
    private ToggleButton editTextModeBtn;
    private ToggleButton addTextModeBtn;
    private ToggleButton placeImageModeBtn;
    private ToggleButton highlightModeBtn;
    private ToggleButton underlineModeBtn;
    private ToggleButton strikeModeBtn;
    private ToggleButton freehandModeBtn;
    private ToggleButton rectModeBtn;
    private ToggleButton redactModeBtn;
    private ToggleButton noteModeBtn;
    private ColorPicker  annotColorPicker;

    private Button addImagePagesBtn;
    private Button rotateCwBtn;
    private Button insertBlankBtn;
    private Button deletePageBtn;
    private Button watermarkBtn;
    private Button pageNumsBtn;

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
    private Label statusModeLabel;

    // ── Sidebar ─────────────────────────────────────────────────────────────
    private VBox             thumbnailContainer;
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
        this.themeService = new ThemeService(viewerPreferences);
        this.controller   = new PdfViewerController(stage);
        this.pageOperationsDialogs = new PageOperationsDialogs(stage, textEditorService, toolboxService);

        controller.setAnnotationManager(annotationManager);
        controller.setOnPageChanged(this::onPageChanged);
        controller.setOnTextBlockSelected(this::onRecognizedTextBlockSelected);
        controller.setOnPageCoordinateClicked(this::onPageCoordinateClicked);
        controller.setOnDocumentModified(() -> {
            hasUnsavedChanges = true;
            renderThumbnailsInBackground();
            updateTitleAndStatus();
        });
        annotationManager.setOnAnnotationsChanged(this::onAnnotationsChanged);

        this.textInspectorPane = new PdfTextInspectorPane(controller);
        this.rootPane = buildRoot();

        Scene scene = new Scene(rootPane, 1180, 720);
        themeService.setScene(scene);

        controller.installKeyboardShortcuts(scene);
        installAppKeyboardShortcuts(scene);
        installDragAndDrop(scene);

        stage.setTitle("FilzaPDF");
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        setViewerControlsEnabled(false);
        showWelcomeScreen(true);
        rootPane.requestFocus();
    }

    @Override
    public void stop() {
        saveCurrentFileState();
        if (controller != null) controller.shutdown();
        if (searchService != null) searchService.shutdown();
        thumbnailExecutor.shutdownNow();
    }

    // ── Build scene graph ────────────────────────────────────────────────────

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setTop(buildTopSection());
        root.setLeft(buildSidebar());

        welcomeDashboardView = new WelcomeDashboardView(
                recentFilesService,
                this::openPdfOrWordDialog,
                this::openImageToPdfStudio,
                this::openPdfMergerStudio,
                () -> openWordStudio(null),
                this::promptAndOpenWordFile,
                this::directConvertWordToPdf,
                this::showAboutDialog,
                this::openAnySupportedFile
        );
        centerViewportStack = new StackPane(controller.getRoot(), welcomeDashboardView.getRoot());
        root.setCenter(centerViewportStack);

        root.setBottom(buildStatusBar());
        return root;
    }

    private VBox buildTopSection() {
        VBox top = new VBox();
        top.getChildren().addAll(
                buildMenuBar(),
                buildMainToolbar(),
                buildContextualSubBar(),
                buildGuidedBanner(),
                buildSearchBar()
        );
        return top;
    }

    private void refreshWelcomeRecentFiles() {
        if (welcomeDashboardView != null) {
            welcomeDashboardView.refreshRecentFiles();
        }
    }

    private void showWelcomeScreen(boolean show) {
        if (welcomeDashboardView != null) {
            welcomeDashboardView.setVisibleAndManaged(show);
        }
    }

    // ── Guided Mode Banner ───────────────────────────────────────────────────

    private HBox buildGuidedBanner() {
        guidedBannerLabel = new Label("");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button doneBtn = new Button("Done");
        doneBtn.getStyleClass().add("surface-button");
        doneBtn.setOnAction(e -> {
            segViewBtn.setSelected(true);
            switchWorkspaceSegment("VIEW");
        });

        guidedBanner = new HBox(12, guidedBannerLabel, spacer, doneBtn);
        guidedBanner.getStyleClass().add("guided-banner");
        guidedBanner.setAlignment(Pos.CENTER_LEFT);
        guidedBanner.setPadding(new Insets(5, 14, 5, 14));
        guidedBanner.setVisible(false);
        guidedBanner.setManaged(false);
        return guidedBanner;
    }

    private void setGuidedBannerMessage(String msg) {
        if (msg == null || msg.isEmpty()) {
            guidedBanner.setVisible(false);
            guidedBanner.setManaged(false);
        } else {
            guidedBannerLabel.setText(msg);
            guidedBanner.setVisible(true);
            guidedBanner.setManaged(true);
        }
    }

    // ── MenuBar ──────────────────────────────────────────────────────────────

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(
                buildFileMenu(),
                buildEditMenu(),
                buildPagesAndMergeMenu(),
                buildWordAndCreateMenu(),
                buildViewMenu(),
                buildNavigateMenu(),
                buildHelpMenu()
        );
        return menuBar;
    }

    private Menu buildFileMenu() {
        Menu file = new Menu("_File");

        MenuItem open = new MenuItem("Open…");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        open.setOnAction(e -> openPdfOrWordDialog());

        recentFilesMenu = new Menu("Open Recent");
        refreshRecentFilesMenu();

        MenuItem save = new MenuItem("Save");
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        save.setOnAction(e -> saveCurrentPdf());

        MenuItem saveAs = new MenuItem("Save As…");
        saveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAs.setOnAction(e -> saveCurrentPdfAs());

        MenuItem exportWord = new MenuItem("Export PDF to Word (.docx)…");
        exportWord.setOnAction(e -> exportCurrentPdfToWord());

        MenuItem exportImages = new MenuItem("Export Pages as PNG…");
        exportImages.setOnAction(e -> exportPagesToImagesDialog());

        MenuItem close = new MenuItem("Close Document");
        close.setOnAction(e -> closeDocument());

        MenuItem props = new MenuItem("Document Properties…");
        props.setOnAction(e -> showDocumentProperties());

        MenuItem quit = new MenuItem("Quit");
        quit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        quit.setOnAction(e -> primaryStage.close());

        file.getItems().addAll(
                open, recentFilesMenu,
                new SeparatorMenuItem(),
                save, saveAs,
                new SeparatorMenuItem(),
                exportWord, exportImages,
                new SeparatorMenuItem(),
                props, close,
                new SeparatorMenuItem(),
                quit
        );
        return file;
    }

    private Menu buildEditMenu() {
        Menu edit = new Menu("_Edit");

        MenuItem undo = new MenuItem("Undo");
        undo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undo.setOnAction(e -> { annotationManager.undo(); controller.redrawAllOverlays(); refreshAnnotationButtons(); });

        MenuItem redo = new MenuItem("Redo");
        redo.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redo.setOnAction(e -> { annotationManager.redo(); controller.redrawAllOverlays(); refreshAnnotationButtons(); });

        MenuItem find = new MenuItem("Find…");
        find.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        find.setOnAction(e -> showSearchBar());

        MenuItem editPdfText = new MenuItem("Edit PDF Text & Fonts");
        editPdfText.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN));
        editPdfText.setOnAction(e -> {
            segEditBtn.setSelected(true);
            switchWorkspaceSegment("EDIT");
        });

        MenuItem addNewText = new MenuItem("Add Text on Page");
        addNewText.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN));
        addNewText.setOnAction(e -> {
            segEditBtn.setSelected(true);
            switchWorkspaceSegment("EDIT");
            addTextModeBtn.setSelected(true);
            activateInteractionMode(InteractionMode.ADD_NEW_TEXT, null);
        });

        MenuItem clearAnnots = new MenuItem("Clear Annotations");
        clearAnnots.setOnAction(e -> {
            annotationManager.clear();
            controller.redrawAllOverlays();
            refreshAnnotationButtons();
        });

        edit.getItems().addAll(
                undo, redo,
                new SeparatorMenuItem(),
                find,
                new SeparatorMenuItem(),
                editPdfText, addNewText, clearAnnots
        );
        return edit;
    }

    private Menu buildPagesAndMergeMenu() {
        Menu pages = new Menu("_Pages");

        MenuItem mergeStudio = new MenuItem("Merge & Compose PDFs…");
        mergeStudio.setAccelerator(new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN));
        mergeStudio.setOnAction(e -> openPdfMergerStudio());

        MenuItem addImgPages = new MenuItem("Insert Image(s) as PDF Pages…");
        addImgPages.setOnAction(e -> quickUploadImagesAsPages());

        MenuItem rotateCw = new MenuItem("Rotate Page 90° CW");
        rotateCw.setOnAction(e -> rotateCurrentPage(90));

        MenuItem rotateCcw = new MenuItem("Rotate Page 90° CCW");
        rotateCcw.setOnAction(e -> rotateCurrentPage(-90));

        MenuItem rotateAll = new MenuItem("Rotate All Pages 90° CW");
        rotateAll.setOnAction(e -> {
            if (!controller.hasDocument()) return;
            toolboxService.rotateAllPages(controller.getDocument(), 90);
            controller.refreshDocumentAfterEdit();
        });

        MenuItem insertBlank = new MenuItem("Insert Blank Page");
        insertBlank.setOnAction(e -> insertBlankPageAfterCurrent());

        MenuItem deletePage = new MenuItem("Delete Current Page");
        deletePage.setOnAction(e -> deleteCurrentPage());

        MenuItem extractPages = new MenuItem("Extract Pages to PDF…");
        extractPages.setOnAction(e -> extractPagesDialog());

        MenuItem watermark = new MenuItem("Add Watermark…");
        watermark.setOnAction(e -> addWatermarkDialog());

        MenuItem pageNums = new MenuItem("Add Page Numbers");
        pageNums.setOnAction(e -> addPageNumbersToDocument());

        MenuItem encrypt = new MenuItem("Password Protect PDF…");
        encrypt.setOnAction(e -> encryptPdfDialog());

        pages.getItems().addAll(
                mergeStudio, addImgPages,
                new SeparatorMenuItem(),
                rotateCw, rotateCcw, rotateAll,
                new SeparatorMenuItem(),
                insertBlank, deletePage, extractPages,
                new SeparatorMenuItem(),
                watermark, pageNums, encrypt
        );
        return pages;
    }

    private Menu buildWordAndCreateMenu() {
        Menu create = new Menu("_Studio");

        MenuItem newWordDoc = new MenuItem("New Document (Word Studio)…");
        newWordDoc.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newWordDoc.setOnAction(e -> openWordStudio(null));

        MenuItem openWordDoc = new MenuItem("Open Word (.docx)…");
        openWordDoc.setOnAction(e -> promptAndOpenWordFile());

        MenuItem quickWordToPdf = new MenuItem("Convert Word (.docx) to PDF…");
        quickWordToPdf.setOnAction(e -> directConvertWordToPdf());

        MenuItem imagesToPdf = new MenuItem("Images to PDF…");
        imagesToPdf.setOnAction(e -> openImageToPdfStudio());

        MenuItem pdfToWord = new MenuItem("Export PDF to Word (.docx)…");
        pdfToWord.setOnAction(e -> exportCurrentPdfToWord());

        create.getItems().addAll(
                newWordDoc,
                openWordDoc,
                quickWordToPdf,
                new SeparatorMenuItem(),
                imagesToPdf,
                new SeparatorMenuItem(),
                pdfToWord
        );
        return create;
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

        CheckMenuItem toggleInspector = new CheckMenuItem("Text & Font Inspector");
        toggleInspector.setOnAction(e -> showTextInspectorPane(toggleInspector.isSelected()));

        MenuItem switchTheme = new MenuItem("Toggle Dark / Light Theme");
        switchTheme.setOnAction(e -> themeService.toggleTheme());

        MenuItem fullscreen = new MenuItem("Fullscreen");
        fullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
        fullscreen.setOnAction(e -> primaryStage.setFullScreen(!primaryStage.isFullScreen()));

        view.getItems().addAll(
                zoomIn, zoomOut, resetZoom, new SeparatorMenuItem(),
                fitWidth, fitPage, new SeparatorMenuItem(),
                toggleInspector, switchTheme, new SeparatorMenuItem(),
                fullscreen
        );
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
        Menu help = new Menu("_About");
        MenuItem about = new MenuItem("About FilzaPDF — Our Story");
        about.setOnAction(e -> showAboutDialog());
        help.getItems().add(about);
        return help;
    }

    // ── Primary Studio Command Bar ───────────────────────────────────────────

    private HBox buildMainToolbar() {
        homeButton = new Button("", SvgIcons.icon(SvgIcons.HOME, 14));
        homeButton.setTooltip(new Tooltip("Home Launchpad"));
        homeButton.setOnAction(e -> showWelcomeScreen(!welcomeDashboardView.isVisible() || !controller.hasDocument()));

        Button openButton = new Button("Open", SvgIcons.icon(SvgIcons.FOLDER_OPEN, 14));
        openButton.getStyleClass().add("accent-button");
        openButton.setGraphicTextGap(6);
        openButton.setTooltip(new Tooltip("Open PDF, Word (.docx), or Images (Ctrl+O)"));
        openButton.setOnAction(e -> openPdfOrWordDialog());

        saveButton = new Button("Save", SvgIcons.icon(SvgIcons.SAVE, 14));
        saveButton.setGraphicTextGap(6);
        saveButton.setTooltip(new Tooltip("Save PDF (Ctrl+S)"));
        saveButton.setOnAction(e -> saveCurrentPdf());

        saveAsButton = new Button("Save As");
        saveAsButton.setTooltip(new Tooltip("Save PDF As (Ctrl+Shift+S)"));
        saveAsButton.setOnAction(e -> saveCurrentPdfAs());

        previousButton = new Button("", SvgIcons.icon(SvgIcons.CHEVRON_LEFT, 13));
        previousButton.setTooltip(new Tooltip("Previous Page (←)"));
        previousButton.setOnAction(e -> controller.previousPage());

        nextButton = new Button("", SvgIcons.icon(SvgIcons.CHEVRON_RIGHT, 13));
        nextButton.setTooltip(new Tooltip("Next Page (→)"));
        nextButton.setOnAction(e -> controller.nextPage());

        pageField = new TextField();
        pageField.setPrefWidth(42);
        pageField.setAlignment(Pos.CENTER);
        pageField.setPromptText("—");
        pageField.setFocusTraversable(false);
        pageField.setOnAction(e -> jumpToPage());

        pageLabel = new Label("/ 0");
        pageLabel.getStyleClass().add("muted-label");

        zoomOutButton = new Button("", SvgIcons.icon(SvgIcons.MINUS, 13));
        zoomOutButton.setTooltip(new Tooltip("Zoom Out (-)"));
        zoomOutButton.setOnAction(e -> controller.zoomOut());

        zoomLabel = new Label("100%");
        zoomLabel.setMinWidth(42);
        zoomLabel.setAlignment(Pos.CENTER);

        zoomInButton = new Button("", SvgIcons.icon(SvgIcons.PLUS, 13));
        zoomInButton.setTooltip(new Tooltip("Zoom In (+)"));
        zoomInButton.setOnAction(e -> controller.zoomIn());

        fitWidthButton = new Button("Fit W");
        fitWidthButton.setTooltip(new Tooltip("Fit Width (W)"));
        fitWidthButton.setOnAction(e -> controller.setFitWidth());

        fitPageButton = new Button("Fit P");
        fitPageButton.setTooltip(new Tooltip("Fit Page (P)"));
        fitPageButton.setOnAction(e -> controller.setFitPage());

        resetZoomButton = new Button("1:1");
        resetZoomButton.setTooltip(new Tooltip("Actual Size 100% (Ctrl+0)"));
        resetZoomButton.setOnAction(e -> controller.resetZoom());

        // Segmented Workspace Mode Bar (View | Edit PDF | Markup | Organize)
        workspaceSegmentGroup = new ToggleGroup();
        segViewBtn = new ToggleButton("View");
        segViewBtn.setToggleGroup(workspaceSegmentGroup);
        segViewBtn.setSelected(true);
        segViewBtn.setOnAction(e -> switchWorkspaceSegment("VIEW"));

        segEditBtn = new ToggleButton("Edit PDF");
        segEditBtn.setToggleGroup(workspaceSegmentGroup);
        segEditBtn.setOnAction(e -> switchWorkspaceSegment("EDIT"));

        segMarkupBtn = new ToggleButton("Markup");
        segMarkupBtn.setToggleGroup(workspaceSegmentGroup);
        segMarkupBtn.setOnAction(e -> switchWorkspaceSegment("MARKUP"));

        segOrganizeBtn = new ToggleButton("Organize");
        segOrganizeBtn.setToggleGroup(workspaceSegmentGroup);
        segOrganizeBtn.setOnAction(e -> switchWorkspaceSegment("ORGANIZE"));

        HBox segmentedBox = new HBox(2, segViewBtn, segEditBtn, segMarkupBtn, segOrganizeBtn);
        segmentedBox.getStyleClass().add("segmented-bar");
        segmentedBox.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Right Studio Quick Actions
        Button mergeStudioBtn = new Button("Merge", SvgIcons.icon(SvgIcons.MERGE, 13));
        mergeStudioBtn.getStyleClass().add("surface-button");
        mergeStudioBtn.setGraphicTextGap(5);
        mergeStudioBtn.setTooltip(new Tooltip("Merge & Compose PDFs (Ctrl+M)"));
        mergeStudioBtn.setOnAction(e -> openPdfMergerStudio());

        Button imgToPdfBtn = new Button("Images", SvgIcons.icon(SvgIcons.IMAGE, 13));
        imgToPdfBtn.getStyleClass().add("surface-button");
        imgToPdfBtn.setGraphicTextGap(5);
        imgToPdfBtn.setTooltip(new Tooltip("Create or Insert PDF Pages from Images"));
        imgToPdfBtn.setOnAction(e -> openImageToPdfStudio());

        Button wordStudioBtn = new Button("Word", SvgIcons.icon(SvgIcons.FILE_TEXT, 13));
        wordStudioBtn.getStyleClass().add("surface-button");
        wordStudioBtn.setGraphicTextGap(5);
        wordStudioBtn.setTooltip(new Tooltip("Create or Edit .docx Documents & Export to PDF (Ctrl+N)"));
        wordStudioBtn.setOnAction(e -> openWordStudio(null));

        exportWordButton = new Button(".docx");
        exportWordButton.getStyleClass().add("surface-button");
        exportWordButton.setTooltip(new Tooltip("Export Current PDF to Microsoft Word (.docx)"));
        exportWordButton.setOnAction(e -> exportCurrentPdfToWord());

        Button aboutTopBtn = new Button("About", SvgIcons.icon(SvgIcons.INFO, 13));
        aboutTopBtn.getStyleClass().add("surface-button");
        aboutTopBtn.setGraphicTextGap(5);
        aboutTopBtn.setTooltip(new Tooltip("About FilzaPDF — Why I Built This & Our Vision"));
        aboutTopBtn.setOnAction(e -> showAboutDialog());

        themeToggleButton = new Button("", SvgIcons.icon(SvgIcons.THEME, 14));
        themeToggleButton.setTooltip(new Tooltip("Toggle Dark / Light Theme"));
        themeToggleButton.setOnAction(e -> themeService.toggleTheme());

        HBox toolbar = new HBox(5,
                homeButton, openButton, saveButton, saveAsButton,
                new Separator(Orientation.VERTICAL),
                previousButton, pageField, pageLabel, nextButton,
                new Separator(Orientation.VERTICAL),
                zoomOutButton, zoomLabel, zoomInButton, fitWidthButton, fitPageButton, resetZoomButton,
                new Separator(Orientation.VERTICAL),
                segmentedBox,
                spacer,
                mergeStudioBtn, imgToPdfBtn, wordStudioBtn, exportWordButton,
                new Separator(Orientation.VERTICAL),
                aboutTopBtn, themeToggleButton
        );
        toolbar.getStyleClass().add("toolbar-row");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        return toolbar;
    }

    // ── Contextual Sub-Bar (Only visible when Edit PDF, Markup, or Organize is selected) ──

    private HBox buildContextualSubBar() {
        modeToggleGroup = new ToggleGroup();

        browseModeBtn = new ToggleButton("Select / Pan", SvgIcons.icon(SvgIcons.CURSOR, 13));
        browseModeBtn.setToggleGroup(modeToggleGroup);
        browseModeBtn.setSelected(true);
        browseModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.VIEW_BROWSE, null));

        // 1. Edit PDF Sub-Group
        editTextModeBtn = new ToggleButton("Recognize & Edit Text", SvgIcons.icon(SvgIcons.TEXT_EDIT, 13));
        editTextModeBtn.setGraphicTextGap(6);
        editTextModeBtn.setToggleGroup(modeToggleGroup);
        editTextModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.EDIT_PDF_TEXT, null));

        addTextModeBtn = new ToggleButton("Insert Text Block", SvgIcons.icon(SvgIcons.PLUS, 13));
        addTextModeBtn.setGraphicTextGap(5);
        addTextModeBtn.setToggleGroup(modeToggleGroup);
        addTextModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ADD_NEW_TEXT, null));

        placeImageModeBtn = new ToggleButton("Place Image on Page", SvgIcons.icon(SvgIcons.IMAGE, 13));
        placeImageModeBtn.setGraphicTextGap(5);
        placeImageModeBtn.setToggleGroup(modeToggleGroup);
        placeImageModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.INSERT_IMAGE, null));

        editSubGroup = new HBox(6, editTextModeBtn, addTextModeBtn, placeImageModeBtn);
        editSubGroup.setAlignment(Pos.CENTER_LEFT);

        // 2. Markup Sub-Group
        highlightModeBtn = new ToggleButton("Highlight", SvgIcons.icon(SvgIcons.HIGHLIGHT, 13));
        highlightModeBtn.setGraphicTextGap(5);
        highlightModeBtn.setToggleGroup(modeToggleGroup);
        highlightModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.HIGHLIGHT));

        underlineModeBtn = new ToggleButton("Underline");
        underlineModeBtn.setToggleGroup(modeToggleGroup);
        underlineModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.UNDERLINE));

        strikeModeBtn = new ToggleButton("Strikethrough");
        strikeModeBtn.setToggleGroup(modeToggleGroup);
        strikeModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.STRIKETHROUGH));

        freehandModeBtn = new ToggleButton("Freehand Ink", SvgIcons.icon(SvgIcons.PEN, 13));
        freehandModeBtn.setGraphicTextGap(5);
        freehandModeBtn.setToggleGroup(modeToggleGroup);
        freehandModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.FREEHAND));

        rectModeBtn = new ToggleButton("Rectangle");
        rectModeBtn.setToggleGroup(modeToggleGroup);
        rectModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.RECTANGLE));

        redactModeBtn = new ToggleButton("Redact");
        redactModeBtn.setToggleGroup(modeToggleGroup);
        redactModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.REDACT));

        noteModeBtn = new ToggleButton("Sticky Note");
        noteModeBtn.setToggleGroup(modeToggleGroup);
        noteModeBtn.setOnAction(e -> activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.NOTE));

        annotColorPicker = new ColorPicker(Color.web("#7C66DC"));
        annotColorPicker.setPrefWidth(82);
        annotColorPicker.setTooltip(new Tooltip("Markup Color"));
        annotColorPicker.setOnAction(e -> controller.setActiveAnnotationColor(toHex(annotColorPicker.getValue())));
        controller.setActiveAnnotationColor("#7C66DC");

        undoButton = new Button("Undo", SvgIcons.icon(SvgIcons.UNDO, 13));
        undoButton.setGraphicTextGap(4);
        undoButton.setOnAction(e -> {
            annotationManager.undo();
            controller.redrawAllOverlays();
            refreshAnnotationButtons();
        });

        redoButton = new Button("Redo", SvgIcons.icon(SvgIcons.REDO, 13));
        redoButton.setGraphicTextGap(4);
        redoButton.setOnAction(e -> {
            annotationManager.redo();
            controller.redrawAllOverlays();
            refreshAnnotationButtons();
        });

        markupSubGroup = new HBox(6,
                highlightModeBtn, underlineModeBtn, strikeModeBtn, freehandModeBtn,
                rectModeBtn, redactModeBtn, noteModeBtn,
                new Separator(Orientation.VERTICAL),
                annotColorPicker, undoButton, redoButton
        );
        markupSubGroup.setAlignment(Pos.CENTER_LEFT);

        // 3. Organize Pages Sub-Group
        addImagePagesBtn = new Button("Insert Image Page(s)", SvgIcons.icon(SvgIcons.IMAGE, 13));
        addImagePagesBtn.setGraphicTextGap(5);
        addImagePagesBtn.setOnAction(e -> quickUploadImagesAsPages());

        insertBlankBtn = new Button("Insert Blank Page", SvgIcons.icon(SvgIcons.PLUS, 13));
        insertBlankBtn.setGraphicTextGap(5);
        insertBlankBtn.setOnAction(e -> insertBlankPageAfterCurrent());

        rotateCwBtn = new Button("Rotate 90°", SvgIcons.icon(SvgIcons.ROTATE, 13));
        rotateCwBtn.setGraphicTextGap(5);
        rotateCwBtn.setOnAction(e -> rotateCurrentPage(90));

        deletePageBtn = new Button("Delete Page", SvgIcons.icon(SvgIcons.TRASH, 13));
        deletePageBtn.setGraphicTextGap(5);
        deletePageBtn.setOnAction(e -> deleteCurrentPage());

        Button extractBtn = new Button("Extract Range…");
        extractBtn.setOnAction(e -> extractPagesDialog());

        watermarkBtn = new Button("Watermark…");
        watermarkBtn.setOnAction(e -> addWatermarkDialog());

        pageNumsBtn = new Button("Number Pages");
        pageNumsBtn.setOnAction(e -> addPageNumbersToDocument());

        Button encryptBtn = new Button("Encrypt PDF…");
        encryptBtn.setOnAction(e -> encryptPdfDialog());

        organizeSubGroup = new HBox(6,
                addImagePagesBtn, insertBlankBtn, rotateCwBtn, deletePageBtn,
                new Separator(Orientation.VERTICAL),
                extractBtn, watermarkBtn, pageNumsBtn, encryptBtn
        );
        organizeSubGroup.setAlignment(Pos.CENTER_LEFT);

        contextualSubBar = new HBox(8);
        contextualSubBar.getStyleClass().add("ribbon-row");
        contextualSubBar.setAlignment(Pos.CENTER_LEFT);
        contextualSubBar.setPadding(new Insets(5, 14, 5, 14));
        contextualSubBar.setVisible(false);
        contextualSubBar.setManaged(false);
        return contextualSubBar;
    }

    private void switchWorkspaceSegment(String segment) {
        if (workspaceSegmentGroup.getSelectedToggle() == null) {
            segViewBtn.setSelected(true);
            segment = "VIEW";
        }
        contextualSubBar.getChildren().clear();

        switch (segment) {
            case "EDIT" -> {
                contextualSubBar.getChildren().add(editSubGroup);
                contextualSubBar.setVisible(true);
                contextualSubBar.setManaged(true);
                editTextModeBtn.setSelected(true);
                activateInteractionMode(InteractionMode.EDIT_PDF_TEXT, null);
            }
            case "MARKUP" -> {
                contextualSubBar.getChildren().add(markupSubGroup);
                contextualSubBar.setVisible(true);
                contextualSubBar.setManaged(true);
                highlightModeBtn.setSelected(true);
                activateInteractionMode(InteractionMode.ANNOTATE, AnnotationType.HIGHLIGHT);
            }
            case "ORGANIZE" -> {
                contextualSubBar.getChildren().add(organizeSubGroup);
                contextualSubBar.setVisible(true);
                contextualSubBar.setManaged(true);
                browseModeBtn.setSelected(true);
                activateInteractionMode(InteractionMode.VIEW_BROWSE, null);
            }
            default -> {
                contextualSubBar.setVisible(false);
                contextualSubBar.setManaged(false);
                browseModeBtn.setSelected(true);
                activateInteractionMode(InteractionMode.VIEW_BROWSE, null);
            }
        }
    }

    private void activateInteractionMode(InteractionMode mode, AnnotationType annotType) {
        if (annotType != null) {
            controller.setActiveAnnotationType(annotType);
        }
        controller.setInteractionMode(mode);

        if (mode == InteractionMode.EDIT_PDF_TEXT) {
            showTextInspectorPane(true);
            textInspectorPane.refreshForCurrentPage();
            statusModeLabel.setText("Edit Text & Fonts");
            setGuidedBannerMessage("Select any highlighted text block on the page to inspect or modify its text, font family, size, or color.");
        } else {
            showTextInspectorPane(false);
            if (mode == InteractionMode.VIEW_BROWSE) {
                statusModeLabel.setText("View");
                setGuidedBannerMessage(null);
            } else if (mode == InteractionMode.ADD_NEW_TEXT) {
                statusModeLabel.setText("Insert Text");
                setGuidedBannerMessage("Click anywhere on a page to insert a new styled text block at that position.");
            } else if (mode == InteractionMode.INSERT_IMAGE) {
                statusModeLabel.setText("Place Image");
                setGuidedBannerMessage("Click anywhere on a page to place an image or signature.");
            } else if (mode == InteractionMode.ANNOTATE) {
                statusModeLabel.setText("Markup: " + controller.getActiveAnnotationType());
                String hint = controller.getActiveAnnotationType() == AnnotationType.NOTE
                        ? "Click on any page to place a note annotation."
                        : "Click and drag across the page to apply " + controller.getActiveAnnotationType().name().toLowerCase() + " markup.";
                setGuidedBannerMessage(hint);
            }
        }
    }

    private void showTextInspectorPane(boolean show) {
        if (rootPane == null) return;
        if (show) {
            rootPane.setRight(textInspectorPane);
            textInspectorPane.refreshForCurrentPage();
        } else {
            rootPane.setRight(null);
        }
    }

    private void onRecognizedTextBlockSelected(RecognizedTextBlock block) {
        showTextInspectorPane(true);
        textInspectorPane.selectBlock(block);
    }

    private void onPageCoordinateClicked(PageCoordinateClick click) {
        if (!controller.hasDocument()) return;
        if (controller.getInteractionMode() == InteractionMode.ADD_NEW_TEXT) {
            promptAddNewTextAt(click.pageIndex(), click.pagePtX(), click.pagePtY());
        } else if (controller.getInteractionMode() == InteractionMode.INSERT_IMAGE) {
            promptInsertImageAt(click.pageIndex(), click.pagePtX(), click.pagePtY());
        }
    }

    private void promptAddNewTextAt(int pageIndex, float xPt, float yPt) {
        pageOperationsDialogs.promptAddNewTextAt(controller.getDocument(), pageIndex, xPt, yPt, controller::refreshDocumentAfterEdit);
    }

    private void promptInsertImageAt(int pageIndex, float xPt, float yPt) {
        pageOperationsDialogs.promptInsertImageAt(controller.getDocument(), pageIndex, xPt, yPt, controller::refreshDocumentAfterEdit);
    }

    // ── Search bar ───────────────────────────────────────────────────────────

    private HBox buildSearchBar() {
        searchField = new TextField();
        searchField.setPromptText("Find in document…");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> performSearch());

        Button searchPrev = new Button("", SvgIcons.icon(SvgIcons.CHEVRON_LEFT, 12));
        searchPrev.setTooltip(new Tooltip("Previous Match"));
        searchPrev.setOnAction(e -> navigateSearchResult(false));

        Button searchNext = new Button("", SvgIcons.icon(SvgIcons.CHEVRON_RIGHT, 12));
        searchNext.setTooltip(new Tooltip("Next Match"));
        searchNext.setOnAction(e -> navigateSearchResult(true));

        caseSensitiveCheck = new CheckBox("Match Case");

        searchResultLabel = new Label("");
        searchResultLabel.getStyleClass().add("muted-label");

        Button closeSearch = new Button("Close");
        closeSearch.setOnAction(e -> hideSearchBar());

        searchBar = new HBox(8,
                SvgIcons.icon(SvgIcons.SEARCH, 13), searchField,
                searchPrev, searchNext,
                caseSensitiveCheck, searchResultLabel,
                closeSearch
        );
        searchBar.getStyleClass().add("toolbar-row");
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(5, 14, 5, 14));
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
            searchResultLabel.setText("0 matches");
        } else {
            int idx   = searchService.getCurrentResultIndex() + 1;
            int total = searchService.getResultCount();
            searchResultLabel.setText(idx + " of " + total);
        }
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────

    private TabPane buildSidebar() {
        thumbnailContainer = new VBox(12);
        thumbnailContainer.setAlignment(Pos.TOP_CENTER);
        thumbnailContainer.setPadding(new Insets(10, 6, 10, 6));
        thumbnailContainer.setPrefWidth(148);

        ScrollPane thumbScroll = new ScrollPane(thumbnailContainer);
        thumbScroll.setFitToWidth(true);
        thumbScroll.setFocusTraversable(false);

        Tab thumbTab = new Tab("Thumbnails", thumbScroll);
        thumbTab.setClosable(false);

        bookmarkTree = new TreeView<>();
        bookmarkTree.setShowRoot(false);
        bookmarkTree.setRoot(new TreeItem<>("Outline"));
        bookmarkTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) navigateToBookmark();
        });

        Tab bookmarkTab = new Tab("Outline", bookmarkTree);
        bookmarkTab.setClosable(false);

        TabPane sidebar = new TabPane(thumbTab, bookmarkTab);
        sidebar.setPrefWidth(176);
        sidebar.setTabMinWidth(70);
        return sidebar;
    }

    // ── Status bar ───────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        statusPageLabel = new Label("No document");
        statusZoomLabel = new Label("");
        statusDocLabel  = new Label("Ready");
        statusModeLabel = new Label("View");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(18, statusDocLabel, spacer, statusModeLabel, statusPageLabel, statusZoomLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 14, 4, 14));
        return bar;
    }

    // ── Keyboard shortcuts (app-level) ────────────────────────────────────────

    private void installAppKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
                if (event.getCode() == KeyCode.ESCAPE && searchBar.isVisible()) {
                    hideSearchBar();
                    event.consume();
                }
                return;
            }
            switch (event.getCode()) {
                case F11:
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                    event.consume();
                    break;
                case F:
                    if (event.isControlDown()) { showSearchBar(); event.consume(); }
                    break;
                case S:
                    if (event.isControlDown() && event.isShiftDown()) {
                        saveCurrentPdfAs();
                        event.consume();
                    } else if (event.isControlDown()) {
                        saveCurrentPdf();
                        event.consume();
                    }
                    break;
                case M:
                    if (event.isControlDown()) { openPdfMergerStudio(); event.consume(); }
                    break;
                case N:
                    if (event.isControlDown()) { openWordStudio(null); event.consume(); }
                    break;
                case E:
                    if (event.isControlDown()) {
                        segEditBtn.setSelected(true);
                        switchWorkspaceSegment("EDIT");
                        event.consume();
                    }
                    break;
                case Z:
                    if (event.isControlDown()) {
                        annotationManager.undo();
                        controller.redrawAllOverlays();
                        refreshAnnotationButtons();
                        event.consume();
                    }
                    break;
                case Y:
                    if (event.isControlDown()) {
                        annotationManager.redo();
                        controller.redrawAllOverlays();
                        refreshAnnotationButtons();
                        event.consume();
                    }
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
                    if (searchBar.isVisible()) {
                        hideSearchBar();
                        event.consume();
                    } else if (controller.getInteractionMode() != InteractionMode.VIEW_BROWSE) {
                        segViewBtn.setSelected(true);
                        switchWorkspaceSegment("VIEW");
                        event.consume();
                    }
                    break;
                default:
                    break;
            }
        });
    }

    // ── Drag and drop (PDF, Word .docx, and Images) ──────────────────────────

    private void installDragAndDrop(Scene scene) {
        scene.setOnDragOver((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().stream()
                    .anyMatch(f -> {
                        String n = f.getName().toLowerCase();
                        return n.endsWith(".pdf") || n.endsWith(".docx") || ImageToPdfDialog.isSupportedImageFile(f);
                    })) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        scene.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> droppedImages = new ArrayList<>();
                for (File f : db.getFiles()) {
                    if (ImageToPdfDialog.isSupportedImageFile(f)) {
                        droppedImages.add(f);
                    }
                }
                if (!droppedImages.isEmpty()) {
                    handleSelectedImages(droppedImages);
                    success = true;
                } else {
                    for (File f : db.getFiles()) {
                        String n = f.getName().toLowerCase();
                        if (n.endsWith(".pdf")) {
                            openFile(f);
                            success = true;
                            break;
                        } else if (n.endsWith(".docx")) {
                            openWordStudio(f);
                            success = true;
                            break;
                        }
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // ── Document lifecycle ────────────────────────────────────────────────────

    private void openPdfOrWordDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Document or Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "All Supported Files (*.pdf, *.docx, *.png, *.jpg, *.jpeg, *.bmp, *.gif)",
                        "*.pdf", "*.docx", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("Word Documents (*.docx)", "*.docx"),
                new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.bmp, *.gif)",
                        "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );
        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files == null || files.isEmpty()) return;

        List<File> images = new ArrayList<>();
        for (File f : files) {
            if (ImageToPdfDialog.isSupportedImageFile(f)) {
                images.add(f);
            }
        }
        if (!images.isEmpty()) {
            handleSelectedImages(images);
            return;
        }

        openAnySupportedFile(files.get(0));
    }

    private void openAnySupportedFile(File file) {
        if (file == null || !file.exists()) return;
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".md")) {
            openWordStudio(file);
        } else if (ImageToPdfDialog.isSupportedImageFile(file)) {
            handleSelectedImages(List.of(file));
        } else {
            openFile(file);
        }
    }

    private void handleSelectedImages(List<File> images) {
        if (images == null || images.isEmpty()) return;

        if (controller.hasDocument()) {
            ButtonType appendType = new ButtonType("Append to Current PDF", ButtonData.YES);
            ButtonType newPdfType = new ButtonType("Create New PDF", ButtonData.NO);
            Alert ask = new Alert(AlertType.CONFIRMATION,
                    "Append " + images.size() + " image(s) to the current document, or create a new PDF?",
                    appendType, newPdfType, ButtonType.CANCEL);
            ask.initOwner(primaryStage);
            ask.setTitle("Import Images");
            ask.setHeaderText(null);
            Optional<ButtonType> choice = ask.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;

            if (choice.get() == appendType) {
                try {
                    int prevCount = controller.getModel().getPageCount();
                    int added = pdfCreatorService.appendImagesAsPages(controller.getDocument(), images, "A4", false, 20f);
                    if (added > 0) {
                        showWelcomeScreen(false);
                        controller.refreshDocumentAfterEdit();
                        controller.setCurrentPage(prevCount);
                        statusDocLabel.setText("Appended " + added + " page(s)");
                    }
                } catch (Exception e) {
                    showErrorDialog("Import Failed", "Could not append image pages.", e.getMessage());
                }
                return;
            }
        }

        try {
            PDDocument created = pdfCreatorService.createPdfFromImages(images, "A4", false, 20f);
            String title = images.size() == 1
                    ? images.get(0).getName().replaceAll("\\.[^.]+$", "") + ".pdf"
                    : "Untitled_Images.pdf";
            openInMemoryDocument(created, title);
        } catch (Exception e) {
            showErrorDialog("Import Failed", "Could not convert selected images to PDF.", e.getMessage());
        }
    }

    private void quickUploadImagesAsPages() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Images");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.bmp, *.gif)",
                        "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );
        List<File> files = fc.showOpenMultipleDialog(primaryStage);
        if (files != null && !files.isEmpty()) {
            handleSelectedImages(files);
        }
    }

    private void openFile(File file) {
        try {
            saveCurrentFileState();

            if (thumbnailTask != null) thumbnailTask.cancel(false);
            controller.closeDocument();
            annotationManager.clear();
            searchService.search("", false);

            PDDocument document = pdfDocumentService.open(file);
            controller.openDocument(document);

            currentFile = file;
            hasUnsavedChanges = false;
            recentFilesService.addFile(file);
            refreshRecentFilesMenu();

            showWelcomeScreen(false);
            setViewerControlsEnabled(true);
            updateTitleAndStatus();

            int lastPage = viewerPreferences.getLastPage(file.getAbsolutePath());
            double lastZoom = viewerPreferences.getLastZoom(file.getAbsolutePath());
            if (lastZoom > 0 && Math.abs(lastZoom - 1.0) > 0.01) {
                controller.setZoom(lastZoom);
            }
            if (lastPage > 0 && lastPage < controller.getModel().getPageCount()) {
                controller.setCurrentPage(lastPage);
            }

            renderThumbnailsInBackground();
            loadBookmarksInBackground(document);

            searchService.extractTextInBackground(document, () ->
                    Platform.runLater(() -> statusDocLabel.setText(file.getName())));

            if (controller.getInteractionMode() == InteractionMode.EDIT_PDF_TEXT) {
                textInspectorPane.refreshForCurrentPage();
            }

            hideSearchBar();

        } catch (IOException e) {
            showErrorDialog("Failed to Open PDF",
                    "The file could not be opened.", e.getMessage());
        }
    }

    private void openInMemoryDocument(PDDocument doc, String displayTitle) {
        try {
            saveCurrentFileState();
            if (thumbnailTask != null) thumbnailTask.cancel(false);
            if (controller.getDocument() != doc) {
                controller.closeDocument();
            }
            annotationManager.clear();
            searchService.search("", false);

            showWelcomeScreen(false);
            controller.openDocument(doc);
            currentFile = null;
            hasUnsavedChanges = true;

            setViewerControlsEnabled(true);
            primaryStage.setTitle("FilzaPDF — " + displayTitle + " *");
            statusDocLabel.setText(displayTitle + " (" + doc.getNumberOfPages() + " pages, unsaved)");

            renderThumbnailsInBackground();
            loadBookmarksInBackground(doc);
            searchService.extractTextInBackground(doc, null);

            if (controller.getInteractionMode() == InteractionMode.EDIT_PDF_TEXT) {
                textInspectorPane.refreshForCurrentPage();
            }
            updateLabels();
            updateStatusBar();
        } catch (IOException e) {
            showErrorDialog("Failed to Load Document", "Error opening generated document", e.getMessage());
        }
    }

    private void saveCurrentPdf() {
        if (!controller.hasDocument()) return;
        if (currentFile == null) {
            saveCurrentPdfAs();
            return;
        }
        savePdfToFile(currentFile);
    }

    private void saveCurrentPdfAs() {
        if (!controller.hasDocument()) return;
        FileChooser saver = new FileChooser();
        saver.setTitle("Save PDF As");
        saver.setInitialFileName(currentFile != null ? currentFile.getName() : "Document.pdf");
        saver.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        File out = saver.showSaveDialog(primaryStage);
        if (out != null) {
            savePdfToFile(out);
        }
    }

    private void savePdfToFile(File targetFile) {
        if (!controller.hasDocument()) return;
        try {
            PDDocument doc = controller.getDocument();
            if (annotationManager.hasAnnotations()) {
                annotationPersistence.burnAnnotationsIntoDocument(doc, annotationManager);
                annotationManager.clear();
                controller.refreshDocumentAfterEdit();
            }
            synchronized (doc) {
                doc.save(targetFile);
            }
            currentFile = targetFile;
            hasUnsavedChanges = false;
            recentFilesService.addFile(targetFile);
            refreshRecentFilesMenu();
            updateTitleAndStatus();
            statusDocLabel.setText("Saved " + targetFile.getName());
        } catch (IOException e) {
            showErrorDialog("Save Failed", "Could not save PDF to " + targetFile.getName(), e.getMessage());
        }
    }

    private void closeDocument() {
        if (controller.hasDocument()) {
            saveCurrentFileState();
            if (thumbnailTask != null) thumbnailTask.cancel(false);
            controller.closeDocument();
            annotationManager.clear();
        }
        currentFile = null;
        hasUnsavedChanges = false;
        thumbnailContainer.getChildren().clear();
        bookmarkTree.setRoot(new TreeItem<>("Outline"));
        segViewBtn.setSelected(true);
        switchWorkspaceSegment("VIEW");
        setViewerControlsEnabled(false);
        showWelcomeScreen(true);
        primaryStage.setTitle("FilzaPDF");
        updateLabels();
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

    // ── Studio & Pro Tool Actions ─────────────────────────────────────────────

    private void openPdfMergerStudio() {
        PdfMergerStudioDialog studio = new PdfMergerStudioDialog(
                primaryStage,
                currentFile,
                mergedDoc -> openInMemoryDocument(mergedDoc, "Merged.pdf")
        );
        studio.show();
    }

    private void openImageToPdfStudio() {
        ImageToPdfDialog dialog = new ImageToPdfDialog(
                primaryStage,
                controller.hasDocument() ? controller.getDocument() : null,
                createdDoc -> openInMemoryDocument(createdDoc, "Images.pdf"),
                () -> {
                    showWelcomeScreen(false);
                    controller.refreshDocumentAfterEdit();
                    controller.setCurrentPage(controller.getModel().getPageCount() - 1);
                    statusDocLabel.setText("Appended image page(s)");
                }
        );
        dialog.show();
    }

    private void promptAndOpenWordFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Word (.docx) File");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Word Documents (*.docx, *.txt, *.md)", "*.docx", "*.txt", "*.md")
        );
        File f = fc.showOpenDialog(primaryStage);
        if (f != null) openWordStudio(f);
    }

    private void openWordStudio(File initialWordFile) {
        WordStudioDialog studio = new WordStudioDialog(
                primaryStage,
                initialWordFile,
                generatedPdf -> openInMemoryDocument(generatedPdf,
                        initialWordFile != null ? initialWordFile.getName().replaceAll("\\.[^.]+$", "") + ".pdf" : "Document.pdf")
        );
        studio.show();
    }

    private void directConvertWordToPdf() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Convert Word (.docx) to PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Files (*.docx)", "*.docx"));
        File in = fc.showOpenDialog(primaryStage);
        if (in == null) return;
        try {
            PDDocument pdf = wordDocumentService.convertWordFileToPdf(in);
            openInMemoryDocument(pdf, in.getName().replaceAll("\\.[^.]+$", "") + ".pdf");
        } catch (Exception e) {
            showErrorDialog("Conversion Failed", "Could not convert " + in.getName(), e.getMessage());
        }
    }

    private void exportCurrentPdfToWord() {
        if (!controller.hasDocument()) return;
        FileChooser saver = new FileChooser();
        saver.setTitle("Export PDF to Word (.docx)");
        String base = currentFile != null ? currentFile.getName().replaceAll("\\.[^.]+$", "") : "Exported";
        saver.setInitialFileName(base + ".docx");
        saver.getExtensionFilters().add(new FileChooser.ExtensionFilter("Microsoft Word (*.docx)", "*.docx"));
        File out = saver.showSaveDialog(primaryStage);
        if (out == null) return;

        try {
            wordDocumentService.exportPdfToDocx(controller.getDocument(), out);
            statusDocLabel.setText("Exported " + out.getName());
        } catch (Exception e) {
            showErrorDialog("Export Failed", "Could not export PDF to Word (.docx)", e.getMessage());
        }
    }

    private void exportPagesToImagesDialog() {
        pageOperationsDialogs.showExportPagesToImagesDialog(controller.getDocument(), currentFile, statusDocLabel::setText);
    }

    private void rotateCurrentPage(int degrees) {
        if (!controller.hasDocument()) return;
        int p = controller.getModel().getCurrentPage();
        toolboxService.rotatePage(controller.getDocument(), p, degrees);
        controller.refreshDocumentAfterEdit();
    }

    private void insertBlankPageAfterCurrent() {
        if (!controller.hasDocument()) return;
        int p = controller.getModel().getCurrentPage();
        toolboxService.insertBlankPage(controller.getDocument(), p, PDRectangle.A4);
        controller.refreshDocumentAfterEdit();
        controller.setCurrentPage(p + 1);
    }

    private void deleteCurrentPage() {
        if (!controller.hasDocument()) return;
        if (controller.getModel().getPageCount() <= 1) {
            Alert a = new Alert(AlertType.WARNING, "Cannot delete the only page in the document.");
            a.initOwner(primaryStage);
            a.showAndWait();
            return;
        }
        int p = controller.getModel().getCurrentPage();
        toolboxService.deletePage(controller.getDocument(), p);
        controller.refreshDocumentAfterEdit();
    }

    private void extractPagesDialog() {
        pageOperationsDialogs.showExtractPagesDialog(controller.getDocument(), this::openInMemoryDocument);
    }

    private void addWatermarkDialog() {
        pageOperationsDialogs.showAddWatermarkDialog(controller.getDocument(), controller::refreshDocumentAfterEdit);
    }

    private void addPageNumbersToDocument() {
        if (!controller.hasDocument()) return;
        try {
            toolboxService.addPageNumbers(controller.getDocument(), true, 10.5f);
            controller.refreshDocumentAfterEdit();
        } catch (Exception ex) {
            showErrorDialog("Page Numbering Failed", "Could not add page numbers.", ex.getMessage());
        }
    }

    private void encryptPdfDialog() {
        pageOperationsDialogs.showEncryptPdfDialog(controller.getDocument(), () -> {
            hasUnsavedChanges = true;
            updateTitleAndStatus();
            statusDocLabel.setText("Encryption applied — Save document to persist");
        });
    }

    // ── Thumbnail loading ─────────────────────────────────────────────────────

    private void renderThumbnailsInBackground() {
        thumbnailContainer.getChildren().clear();
        if (!controller.hasDocument()) return;

        int pageCount = controller.getModel().getPageCount();
        final var activeRenderer = controller.getRenderer();

        for (int i = 0; i < pageCount; i++) {
            final int idx = i;
            Label numLabel = new Label(String.valueOf(i + 1));
            numLabel.getStyleClass().add("muted-label");
            numLabel.setStyle("-fx-font-size: 11px;");
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(116);
            VBox box = new VBox(4, view, numLabel);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(4));
            box.setStyle("-fx-cursor: hand;");
            box.setOnMouseClicked(e -> {
                showWelcomeScreen(false);
                controller.setCurrentPage(idx);
            });
            thumbnailContainer.getChildren().add(box);
        }

        thumbnailTask = thumbnailExecutor.submit(() -> {
            for (int i = 0; i < pageCount; i++) {
                if (controller.getRenderer() != activeRenderer) break;
                final int pageIndex = i;
                try {
                    BufferedImage img = activeRenderer.renderPage(pageIndex, 96f);
                    if (controller.getRenderer() != activeRenderer) break;
                    Platform.runLater(() -> {
                        if (controller.getRenderer() == activeRenderer && pageIndex < thumbnailContainer.getChildren().size()) {
                            VBox box = (VBox) thumbnailContainer.getChildren().get(pageIndex);
                            ImageView view = (ImageView) box.getChildren().get(0);
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
        bookmarkTree.setRoot(new TreeItem<>("Outline"));
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
        TreeItem<String> root = new TreeItem<>("Outline");
        root.setExpanded(true);
        for (BookmarkNode node : nodes) {
            root.getChildren().add(buildTreeItem(node));
        }
        bookmarkTree.setRoot(root);
    }

    private TreeItem<String> buildTreeItem(BookmarkNode node) {
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
        int sep = value.lastIndexOf('\u0000');
        if (sep < 0) return;
        try {
            int pageIndex = Integer.parseInt(value.substring(sep + 1));
            if (pageIndex >= 0) {
                showWelcomeScreen(false);
                controller.setCurrentPage(pageIndex);
            }
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
                item.setOnAction(e -> openAnySupportedFile(f));
                recentFilesMenu.getItems().add(item);
            }
            recentFilesMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clear = new MenuItem("Clear Recent");
            clear.setOnAction(e -> clearRecentFiles());
            recentFilesMenu.getItems().add(clear);
        }
        refreshWelcomeRecentFiles();
    }

    private void clearRecentFiles() {
        recentFilesService.clearAll();
        refreshRecentFilesMenu();
    }

    // ── Document Properties dialog ────────────────────────────────────────────

    private void showDocumentProperties() {
        pageOperationsDialogs.showDocumentPropertiesDialog(controller.getDocument(), currentFile);
    }

    // ── Annotation helpers ────────────────────────────────────────────────────

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
        hasUnsavedChanges = true;
        refreshAnnotationButtons();
        updateTitleAndStatus();
    }

    private void refreshAnnotationButtons() {
        if (undoButton != null) undoButton.setDisable(!controller.hasDocument() || !annotationManager.canUndo());
        if (redoButton != null) redoButton.setDisable(!controller.hasDocument() || !annotationManager.canRedo());
    }

    // ── Page navigation helpers ───────────────────────────────────────────────

    private void goToPage(int pageIndex) {
        if (controller.hasDocument()) {
            showWelcomeScreen(false);
            controller.setCurrentPage(pageIndex);
        }
    }

    private void jumpToPage() {
        if (!controller.hasDocument()) return;
        try {
            int target = Integer.parseInt(pageField.getText()) - 1;
            int count  = controller.getModel().getPageCount();
            if (target >= 0 && target < count) {
                showWelcomeScreen(false);
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
        if (controller.hasDocument() && controller.getInteractionMode() == InteractionMode.EDIT_PDF_TEXT) {
            textInspectorPane.refreshForCurrentPage();
        }
    }

    private void updateTitleAndStatus() {
        if (!controller.hasDocument()) {
            primaryStage.setTitle("FilzaPDF");
            return;
        }
        String name = currentFile != null ? currentFile.getName() : "Untitled.pdf";
        primaryStage.setTitle("FilzaPDF — " + name + (hasUnsavedChanges ? " *" : ""));
    }

    private void updateLabels() {
        if (!controller.hasDocument()) {
            pageLabel.setText("/ 0");
            pageField.setText("");
            zoomLabel.setText("100%");
            return;
        }
        PdfViewerModel model = controller.getModel();
        pageLabel.setText("/ " + model.getPageCount());
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
        statusZoomLabel.setText(String.format("%.0f%%", model.getZoom() * 100));
    }

    // ── UI control state ──────────────────────────────────────────────────────

    private void setViewerControlsEnabled(boolean enabled) {
        saveButton       .setDisable(!enabled);
        saveAsButton     .setDisable(!enabled);
        exportWordButton .setDisable(!enabled);
        previousButton   .setDisable(!enabled);
        nextButton       .setDisable(!enabled);
        zoomOutButton    .setDisable(!enabled);
        zoomInButton     .setDisable(!enabled);
        fitWidthButton   .setDisable(!enabled);
        fitPageButton    .setDisable(!enabled);
        resetZoomButton  .setDisable(!enabled);
        pageField        .setDisable(!enabled);

        segViewBtn       .setDisable(!enabled);
        segEditBtn       .setDisable(!enabled);
        segMarkupBtn     .setDisable(!enabled);
        segOrganizeBtn   .setDisable(!enabled);

        refreshAnnotationButtons();
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void showErrorDialog(String title, String header, String detail) {
        pageOperationsDialogs.showError(title, header, detail);
    }

    private void showAboutDialog() {
        new AboutFilzaDialog(primaryStage).show();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String toHex(Color c) {
        if (c == null) return "#7C66DC";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static void main(String[] args) {
        launch(args);
    }
}