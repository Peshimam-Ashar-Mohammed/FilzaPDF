import os

def create_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

base_dir = "C:/Users/HP/Desktop/Java/FilzaPdf"

# SECTION 3 - Search
create_file(f"{base_dir}/src/main/java/com/filzapdf/search/SearchResult.java", """package com.filzapdf.search;

public class SearchResult {
    private final int pageIndex;
    private final String matchText;
    private final int startIndex;
    private final int endIndex;
    private final String pageTextSnippet;

    public SearchResult(int pageIndex, String matchText, int startIndex, int endIndex, String pageTextSnippet) {
        this.pageIndex = pageIndex;
        this.matchText = matchText;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.pageTextSnippet = pageTextSnippet;
    }

    public int getPageIndex() { return pageIndex; }
    public String getMatchText() { return matchText; }
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public String getPageTextSnippet() { return pageTextSnippet; }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/search/PdfTextSearchService.java", """package com.filzapdf.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfTextSearchService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, String> pageTexts = new HashMap<>();
    private List<SearchResult> currentResults = new ArrayList<>();
    private int currentResultIndex = -1;

    public void extractTextInBackground(PDDocument document, Runnable onComplete) {
        pageTexts.clear();
        currentResults.clear();
        currentResultIndex = -1;

        if (document == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        executor.submit(() -> {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                int pages = document.getNumberOfPages();
                for (int i = 0; i < pages; i++) {
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(document);
                    pageTexts.put(i, text);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public List<SearchResult> search(String query, boolean caseSensitive) {
        currentResults.clear();
        currentResultIndex = -1;

        if (query == null || query.isEmpty()) return Collections.emptyList();

        Pattern pattern = caseSensitive ? Pattern.compile(Pattern.quote(query)) : Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < pageTexts.size(); i++) {
            String text = pageTexts.getOrDefault(i, "");
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                int snippetStart = Math.max(0, start - 20);
                int snippetEnd = Math.min(text.length(), end + 20);
                String snippet = text.substring(snippetStart, snippetEnd).replace("\\n", " ").replace("\\r", "");
                currentResults.add(new SearchResult(i, matcher.group(), start, end, snippet));
            }
        }
        
        if (!currentResults.isEmpty()) currentResultIndex = 0;
        return currentResults;
    }

    public SearchResult nextResult() {
        if (currentResults.isEmpty()) return null;
        currentResultIndex = (currentResultIndex + 1) % currentResults.size();
        return currentResults.get(currentResultIndex);
    }

    public SearchResult previousResult() {
        if (currentResults.isEmpty()) return null;
        currentResultIndex = (currentResultIndex - 1 + currentResults.size()) % currentResults.size();
        return currentResults.get(currentResultIndex);
    }

    public int getCurrentResultIndex() { return currentResultIndex; }
    public int getResultCount() { return currentResults.size(); }
    public void shutdown() { executor.shutdownNow(); }
}
""")

# SECTION 4 - Navigation & Persistence
create_file(f"{base_dir}/src/main/java/com/filzapdf/persistence/RecentFilesService.java", """package com.filzapdf.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecentFilesService {
    private static final Path RECENT_FILES_PATH = Paths.get(System.getProperty("user.home"), ".filzapdf", "recent.txt");
    private static final int MAX_RECENT_FILES = 10;
    
    private final List<String> recentFiles = new ArrayList<>();

    public RecentFilesService() {
        load();
    }

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

    public List<String> getRecentFiles() {
        recentFiles.removeIf(p -> !new File(p).exists());
        return new ArrayList<>(recentFiles);
    }

    private void load() {
        try {
            if (Files.exists(RECENT_FILES_PATH)) {
                recentFiles.addAll(Files.readAllLines(RECENT_FILES_PATH).stream()
                        .filter(p -> new File(p).exists())
                        .limit(MAX_RECENT_FILES)
                        .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            Files.createDirectories(RECENT_FILES_PATH.getParent());
            Files.write(RECENT_FILES_PATH, getRecentFiles());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/persistence/ViewerPreferences.java", """package com.filzapdf.persistence;

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
        return props.getProperty("theme", "light");
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
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/pdf/BookmarkNode.java", """package com.filzapdf.pdf;

import java.util.ArrayList;
import java.util.List;

public class BookmarkNode {
    private final String title;
    private final int pageIndex;
    private final List<BookmarkNode> children = new ArrayList<>();

    public BookmarkNode(String title, int pageIndex) {
        this.title = title;
        this.pageIndex = pageIndex;
    }

    public String getTitle() { return title; }
    public int getPageIndex() { return pageIndex; }
    public List<BookmarkNode> getChildren() { return children; }
    
    @Override
    public String toString() { return title; }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/pdf/BookmarkService.java", """package com.filzapdf.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

public class BookmarkService {

    public List<BookmarkNode> extractBookmarks(PDDocument document) throws IOException {
        List<BookmarkNode> bookmarks = new ArrayList<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            populateBookmarks(outline, bookmarks, document);
        }
        return bookmarks;
    }

    private void populateBookmarks(PDOutlineNode outlineNode, List<BookmarkNode> children, PDDocument document) throws IOException {
        PDOutlineItem current = outlineNode.getFirstChild();
        while (current != null) {
            int pageIndex = -1;
            try {
                if (current.getDestination() instanceof PDPageDestination) {
                    pageIndex = ((PDPageDestination) current.getDestination()).retrievePageNumber();
                } else if (current.getAction() instanceof PDActionGoTo) {
                    PDActionGoTo action = (PDActionGoTo) current.getAction();
                    if (action.getDestination() instanceof PDPageDestination) {
                        pageIndex = ((PDPageDestination) action.getDestination()).retrievePageNumber();
                    }
                }
            } catch (Exception e) {}

            BookmarkNode node = new BookmarkNode(current.getTitle(), pageIndex);
            children.add(node);
            populateBookmarks(current, node.getChildren(), document);
            current = current.getNextSibling();
        }
    }
}
""")

# SECTION 5 - Annotations
create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/AnnotationType.java", """package com.filzapdf.annotations;

public enum AnnotationType {
    HIGHLIGHT, UNDERLINE, STRIKETHROUGH, NOTE, FREEHAND
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/Annotation.java", """package com.filzapdf.annotations;

import java.util.List;

public class Annotation {
    private AnnotationType type;
    private int pageIndex;
    private double x, y, width, height;
    private String color;
    private String noteText;
    private List<double[]> strokePoints;
    
    public AnnotationType getType() { return type; }
    public void setType(AnnotationType type) { this.type = type; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public List<double[]> getStrokePoints() { return strokePoints; }
    public void setStrokePoints(List<double[]> strokePoints) { this.strokePoints = strokePoints; }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/AnnotationCommand.java", """package com.filzapdf.annotations;

public interface AnnotationCommand {
    void execute();
    void undo();
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/AddAnnotationCommand.java", """package com.filzapdf.annotations;

public class AddAnnotationCommand implements AnnotationCommand {
    private final AnnotationManager manager;
    private final Annotation annotation;

    public AddAnnotationCommand(AnnotationManager manager, Annotation annotation) {
        this.manager = manager;
        this.annotation = annotation;
    }

    @Override
    public void execute() {
        manager.addAnnotationInternal(annotation);
    }

    @Override
    public void undo() {
        manager.removeAnnotationInternal(annotation);
    }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/RemoveAnnotationCommand.java", """package com.filzapdf.annotations;

public class RemoveAnnotationCommand implements AnnotationCommand {
    private final AnnotationManager manager;
    private final Annotation annotation;

    public RemoveAnnotationCommand(AnnotationManager manager, Annotation annotation) {
        this.manager = manager;
        this.annotation = annotation;
    }

    @Override
    public void execute() {
        manager.removeAnnotationInternal(annotation);
    }

    @Override
    public void undo() {
        manager.addAnnotationInternal(annotation);
    }
}
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/annotations/AnnotationManager.java", """package com.filzapdf.annotations;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationManager {
    private final Map<Integer, List<Annotation>> pageAnnotations = new HashMap<>();
    private final Deque<AnnotationCommand> undoStack = new ArrayDeque<>();
    private final Deque<AnnotationCommand> redoStack = new ArrayDeque<>();
    private Runnable onAnnotationsChanged;

    public void setOnAnnotationsChanged(Runnable callback) {
        this.onAnnotationsChanged = callback;
    }

    public List<Annotation> getAnnotationsForPage(int pageIndex) {
        return pageAnnotations.getOrDefault(pageIndex, new ArrayList<>());
    }

    public void executeCommand(AnnotationCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();
        notifyChanged();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            AnnotationCommand cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
            notifyChanged();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            AnnotationCommand cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
            notifyChanged();
        }
    }

    void addAnnotationInternal(Annotation a) {
        pageAnnotations.computeIfAbsent(a.getPageIndex(), k -> new ArrayList<>()).add(a);
    }

    void removeAnnotationInternal(Annotation a) {
        List<Annotation> list = pageAnnotations.get(a.getPageIndex());
        if (list != null) list.remove(a);
    }

    private void notifyChanged() {
        if (onAnnotationsChanged != null) onAnnotationsChanged.run();
    }
}
""")

# TESTS
create_file(f"{base_dir}/src/test/java/com/filzapdf/persistence/RecentFilesServiceTest.java", """package com.filzapdf.persistence;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class RecentFilesServiceTest {
    @Test
    public void testAddFile() throws Exception {
        RecentFilesService svc = new RecentFilesService();
        File f = File.createTempFile("test", ".pdf");
        f.deleteOnExit();
        svc.addFile(f);
        assertTrue(svc.getRecentFiles().contains(f.getAbsolutePath()));
    }
}
""")

create_file(f"{base_dir}/src/test/java/com/filzapdf/persistence/ViewerPreferencesTest.java", """package com.filzapdf.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ViewerPreferencesTest {
    @Test
    public void testPrefs() {
        ViewerPreferences prefs = new ViewerPreferences();
        prefs.setTheme("dark");
        assertEquals("dark", prefs.getTheme());
    }
}
""")

create_file(f"{base_dir}/src/test/java/com/filzapdf/annotations/AnnotationManagerTest.java", """package com.filzapdf.annotations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AnnotationManagerTest {
    @Test
    public void testUndoRedo() {
        AnnotationManager mgr = new AnnotationManager();
        Annotation a = new Annotation();
        a.setPageIndex(0);
        
        mgr.executeCommand(new AddAnnotationCommand(mgr, a));
        assertEquals(1, mgr.getAnnotationsForPage(0).size());
        
        mgr.undo();
        assertEquals(0, mgr.getAnnotationsForPage(0).size());
        
        mgr.redo();
        assertEquals(1, mgr.getAnnotationsForPage(0).size());
    }
}
""")

create_file(f"{base_dir}/src/test/java/com/filzapdf/pdf/BookmarkServiceTest.java", """package com.filzapdf.pdf;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class BookmarkServiceTest {
    @Test
    public void testExtract() throws Exception {
        PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File("src/test/resources/sample.pdf"));
        BookmarkService svc = new BookmarkService();
        List<BookmarkNode> nodes = svc.extractBookmarks(doc);
        assertNotNull(nodes); // Might be empty for sample, but shouldn't throw
        doc.close();
    }
}
""")

# CSS
create_file(f"{base_dir}/src/main/resources/com/filzapdf/css/light.css", """
.root { -fx-background-color: #f4f4f4; }
.toolbar { -fx-background-color: #e0e0e0; }
""")

create_file(f"{base_dir}/src/main/resources/com/filzapdf/css/dark.css", """
.root { -fx-background-color: #2b2b2b; }
.label { -fx-text-fill: white; }
.toolbar { -fx-background-color: #3c3f41; }
.button { -fx-background-color: #4b4d4f; -fx-text-fill: white; }
""")

create_file(f"{base_dir}/src/main/java/com/filzapdf/ui/ThemeService.java", """package com.filzapdf.ui;

import com.filzapdf.persistence.ViewerPreferences;
import javafx.scene.Scene;

public class ThemeService {
    private final ViewerPreferences prefs;
    private Scene scene;

    public ThemeService(ViewerPreferences prefs) {
        this.prefs = prefs;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
        applyTheme(prefs.getTheme());
    }

    public void applyTheme(String theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        String css = getClass().getResource("/com/filzapdf/css/" + theme + ".css") != null 
                     ? getClass().getResource("/com/filzapdf/css/" + theme + ".css").toExternalForm() 
                     : null;
        if (css != null) scene.getStylesheets().add(css);
        prefs.setTheme(theme);
    }
    
    public void toggleTheme() {
        applyTheme("dark".equals(prefs.getTheme()) ? "light" : "dark");
    }
}
""")
