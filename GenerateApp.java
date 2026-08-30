import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GenerateApp {
    public static void main(String[] args) throws IOException {
        String baseDir = "C:/Users/HP/Desktop/Java/FilzaPdf";

        createFile(baseDir + "/src/main/java/com/filzapdf/persistence/RecentFilesService.java", 
            "package com.filzapdf.persistence;\n\n" +
            "import java.io.File;\n" +
            "import java.io.IOException;\n" +
            "import java.nio.file.Files;\n" +
            "import java.nio.file.Path;\n" +
            "import java.nio.file.Paths;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n" +
            "import java.util.stream.Collectors;\n\n" +
            "public class RecentFilesService {\n" +
            "    private static final Path RECENT_FILES_PATH = Paths.get(System.getProperty(\"user.home\"), \".filzapdf\", \"recent.txt\");\n" +
            "    private static final int MAX_RECENT_FILES = 10;\n" +
            "    private final List<String> recentFiles = new ArrayList<>();\n\n" +
            "    public RecentFilesService() {\n" +
            "        load();\n" +
            "    }\n\n" +
            "    public void addFile(File file) {\n" +
            "        if (file == null) return;\n" +
            "        String path = file.getAbsolutePath();\n" +
            "        recentFiles.remove(path);\n" +
            "        recentFiles.add(0, path);\n" +
            "        if (recentFiles.size() > MAX_RECENT_FILES) {\n" +
            "            recentFiles.remove(recentFiles.size() - 1);\n" +
            "        }\n" +
            "        save();\n" +
            "    }\n\n" +
            "    public List<String> getRecentFiles() {\n" +
            "        recentFiles.removeIf(p -> !new File(p).exists());\n" +
            "        return new ArrayList<>(recentFiles);\n" +
            "    }\n\n" +
            "    private void load() {\n" +
            "        try {\n" +
            "            if (Files.exists(RECENT_FILES_PATH)) {\n" +
            "                recentFiles.addAll(Files.readAllLines(RECENT_FILES_PATH).stream()\n" +
            "                        .filter(p -> new File(p).exists())\n" +
            "                        .limit(MAX_RECENT_FILES)\n" +
            "                        .collect(Collectors.toList()));\n" +
            "            }\n" +
            "        } catch (IOException e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n\n" +
            "    private void save() {\n" +
            "        try {\n" +
            "            Files.createDirectories(RECENT_FILES_PATH.getParent());\n" +
            "            Files.write(RECENT_FILES_PATH, getRecentFiles());\n" +
            "        } catch (IOException e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/persistence/ViewerPreferences.java",
            "package com.filzapdf.persistence;\n\n" +
            "import java.io.FileInputStream;\n" +
            "import java.io.FileOutputStream;\n" +
            "import java.io.IOException;\n" +
            "import java.nio.file.Files;\n" +
            "import java.nio.file.Path;\n" +
            "import java.nio.file.Paths;\n" +
            "import java.util.Properties;\n\n" +
            "public class ViewerPreferences {\n" +
            "    private static final Path PREFS_PATH = Paths.get(System.getProperty(\"user.home\"), \".filzapdf\", \"prefs.properties\");\n" +
            "    private final Properties props = new Properties();\n\n" +
            "    public ViewerPreferences() {\n" +
            "        load();\n" +
            "    }\n\n" +
            "    public void setLastPage(String filePath, int pageIndex) {\n" +
            "        props.setProperty(\"lastPage_\" + filePath, String.valueOf(pageIndex));\n" +
            "        save();\n" +
            "    }\n\n" +
            "    public int getLastPage(String filePath) {\n" +
            "        String val = props.getProperty(\"lastPage_\" + filePath);\n" +
            "        return val != null ? Integer.parseInt(val) : 0;\n" +
            "    }\n\n" +
            "    public void setLastZoom(String filePath, double zoom) {\n" +
            "        props.setProperty(\"lastZoom_\" + filePath, String.valueOf(zoom));\n" +
            "        save();\n" +
            "    }\n\n" +
            "    public double getLastZoom(String filePath) {\n" +
            "        String val = props.getProperty(\"lastZoom_\" + filePath);\n" +
            "        return val != null ? Double.parseDouble(val) : -1.0;\n" +
            "    }\n\n" +
            "    public void setTheme(String theme) {\n" +
            "        props.setProperty(\"theme\", theme);\n" +
            "        save();\n" +
            "    }\n\n" +
            "    public String getTheme() {\n" +
            "        return props.getProperty(\"theme\", \"light\");\n" +
            "    }\n\n" +
            "    private void load() {\n" +
            "        try {\n" +
            "            if (Files.exists(PREFS_PATH)) {\n" +
            "                try (FileInputStream in = new FileInputStream(PREFS_PATH.toFile())) {\n" +
            "                    props.load(in);\n" +
            "                }\n" +
            "            }\n" +
            "        } catch (IOException e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n\n" +
            "    private void save() {\n" +
            "        try {\n" +
            "            Files.createDirectories(PREFS_PATH.getParent());\n" +
            "            try (FileOutputStream out = new FileOutputStream(PREFS_PATH.toFile())) {\n" +
            "                props.store(out, \"FilzaPDF Preferences\");\n" +
            "            }\n" +
            "        } catch (IOException e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/pdf/BookmarkNode.java",
            "package com.filzapdf.pdf;\n\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n\n" +
            "public class BookmarkNode {\n" +
            "    private final String title;\n" +
            "    private final int pageIndex;\n" +
            "    private final List<BookmarkNode> children = new ArrayList<>();\n\n" +
            "    public BookmarkNode(String title, int pageIndex) {\n" +
            "        this.title = title;\n" +
            "        this.pageIndex = pageIndex;\n" +
            "    }\n\n" +
            "    public String getTitle() { return title; }\n" +
            "    public int getPageIndex() { return pageIndex; }\n" +
            "    public List<BookmarkNode> getChildren() { return children; }\n" +
            "    \n" +
            "    @Override\n" +
            "    public String toString() { return title; }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/pdf/BookmarkService.java",
            "package com.filzapdf.pdf;\n\n" +
            "import java.io.IOException;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n\n" +
            "import org.apache.pdfbox.pdmodel.PDDocument;\n" +
            "import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;\n" +
            "import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;\n" +
            "import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;\n" +
            "import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;\n" +
            "import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;\n\n" +
            "public class BookmarkService {\n" +
            "    public List<BookmarkNode> extractBookmarks(PDDocument document) throws IOException {\n" +
            "        List<BookmarkNode> bookmarks = new ArrayList<>();\n" +
            "        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();\n" +
            "        if (outline != null) {\n" +
            "            populateBookmarks(outline, bookmarks, document);\n" +
            "        }\n" +
            "        return bookmarks;\n" +
            "    }\n\n" +
            "    private void populateBookmarks(PDOutlineNode outlineNode, List<BookmarkNode> children, PDDocument document) throws IOException {\n" +
            "        PDOutlineItem current = outlineNode.getFirstChild();\n" +
            "        while (current != null) {\n" +
            "            int pageIndex = -1;\n" +
            "            try {\n" +
            "                if (current.getDestination() instanceof PDPageDestination) {\n" +
            "                    pageIndex = ((PDPageDestination) current.getDestination()).retrievePageNumber();\n" +
            "                } else if (current.getAction() instanceof PDActionGoTo) {\n" +
            "                    PDActionGoTo action = (PDActionGoTo) current.getAction();\n" +
            "                    if (action.getDestination() instanceof PDPageDestination) {\n" +
            "                        pageIndex = ((PDPageDestination) action.getDestination()).retrievePageNumber();\n" +
            "                    }\n" +
            "                }\n" +
            "            } catch (Exception e) {}\n" +
            "            BookmarkNode node = new BookmarkNode(current.getTitle(), pageIndex);\n" +
            "            children.add(node);\n" +
            "            populateBookmarks(current, node.getChildren(), document);\n" +
            "            current = current.getNextSibling();\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/AnnotationType.java",
            "package com.filzapdf.annotations;\n\n" +
            "public enum AnnotationType {\n" +
            "    HIGHLIGHT, UNDERLINE, STRIKETHROUGH, NOTE, FREEHAND\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/Annotation.java",
            "package com.filzapdf.annotations;\n\n" +
            "import java.util.List;\n\n" +
            "public class Annotation {\n" +
            "    private AnnotationType type;\n" +
            "    private int pageIndex;\n" +
            "    private double x, y, width, height;\n" +
            "    private String color;\n" +
            "    private String noteText;\n" +
            "    private List<double[]> strokePoints;\n" +
            "    public AnnotationType getType() { return type; }\n" +
            "    public void setType(AnnotationType type) { this.type = type; }\n" +
            "    public int getPageIndex() { return pageIndex; }\n" +
            "    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }\n" +
            "    public double getX() { return x; }\n" +
            "    public void setX(double x) { this.x = x; }\n" +
            "    public double getY() { return y; }\n" +
            "    public void setY(double y) { this.y = y; }\n" +
            "    public double getWidth() { return width; }\n" +
            "    public void setWidth(double width) { this.width = width; }\n" +
            "    public double getHeight() { return height; }\n" +
            "    public void setHeight(double height) { this.height = height; }\n" +
            "    public String getColor() { return color; }\n" +
            "    public void setColor(String color) { this.color = color; }\n" +
            "    public String getNoteText() { return noteText; }\n" +
            "    public void setNoteText(String noteText) { this.noteText = noteText; }\n" +
            "    public List<double[]> getStrokePoints() { return strokePoints; }\n" +
            "    public void setStrokePoints(List<double[]> strokePoints) { this.strokePoints = strokePoints; }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/AnnotationCommand.java",
            "package com.filzapdf.annotations;\n\n" +
            "public interface AnnotationCommand {\n" +
            "    void execute();\n" +
            "    void undo();\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/AddAnnotationCommand.java",
            "package com.filzapdf.annotations;\n\n" +
            "public class AddAnnotationCommand implements AnnotationCommand {\n" +
            "    private final AnnotationManager manager;\n" +
            "    private final Annotation annotation;\n" +
            "    public AddAnnotationCommand(AnnotationManager manager, Annotation annotation) {\n" +
            "        this.manager = manager;\n" +
            "        this.annotation = annotation;\n" +
            "    }\n" +
            "    @Override public void execute() { manager.addAnnotationInternal(annotation); }\n" +
            "    @Override public void undo() { manager.removeAnnotationInternal(annotation); }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/RemoveAnnotationCommand.java",
            "package com.filzapdf.annotations;\n\n" +
            "public class RemoveAnnotationCommand implements AnnotationCommand {\n" +
            "    private final AnnotationManager manager;\n" +
            "    private final Annotation annotation;\n" +
            "    public RemoveAnnotationCommand(AnnotationManager manager, Annotation annotation) {\n" +
            "        this.manager = manager;\n" +
            "        this.annotation = annotation;\n" +
            "    }\n" +
            "    @Override public void execute() { manager.removeAnnotationInternal(annotation); }\n" +
            "    @Override public void undo() { manager.addAnnotationInternal(annotation); }\n" +
            "}\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/annotations/AnnotationManager.java",
            "package com.filzapdf.annotations;\n\n" +
            "import java.util.ArrayDeque;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.Deque;\n" +
            "import java.util.HashMap;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n\n" +
            "public class AnnotationManager {\n" +
            "    private final Map<Integer, List<Annotation>> pageAnnotations = new HashMap<>();\n" +
            "    private final Deque<AnnotationCommand> undoStack = new ArrayDeque<>();\n" +
            "    private final Deque<AnnotationCommand> redoStack = new ArrayDeque<>();\n" +
            "    private Runnable onAnnotationsChanged;\n" +
            "    public void setOnAnnotationsChanged(Runnable callback) { this.onAnnotationsChanged = callback; }\n" +
            "    public List<Annotation> getAnnotationsForPage(int pageIndex) { return pageAnnotations.getOrDefault(pageIndex, new ArrayList<>()); }\n" +
            "    public void executeCommand(AnnotationCommand cmd) {\n" +
            "        cmd.execute();\n" +
            "        undoStack.push(cmd);\n" +
            "        redoStack.clear();\n" +
            "        notifyChanged();\n" +
            "    }\n" +
            "    public void undo() {\n" +
            "        if (!undoStack.isEmpty()) {\n" +
            "            AnnotationCommand cmd = undoStack.pop();\n" +
            "            cmd.undo();\n" +
            "            redoStack.push(cmd);\n" +
            "            notifyChanged();\n" +
            "        }\n" +
            "    }\n" +
            "    public void redo() {\n" +
            "        if (!redoStack.isEmpty()) {\n" +
            "            AnnotationCommand cmd = redoStack.pop();\n" +
            "            cmd.execute();\n" +
            "            undoStack.push(cmd);\n" +
            "            notifyChanged();\n" +
            "        }\n" +
            "    }\n" +
            "    void addAnnotationInternal(Annotation a) { pageAnnotations.computeIfAbsent(a.getPageIndex(), k -> new ArrayList<>()).add(a); }\n" +
            "    void removeAnnotationInternal(Annotation a) {\n" +
            "        List<Annotation> list = pageAnnotations.get(a.getPageIndex());\n" +
            "        if (list != null) list.remove(a);\n" +
            "    }\n" +
            "    private void notifyChanged() { if (onAnnotationsChanged != null) onAnnotationsChanged.run(); }\n" +
            "}\n");

        createFile(baseDir + "/src/test/java/com/filzapdf/persistence/RecentFilesServiceTest.java",
            "package com.filzapdf.persistence;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import java.io.File;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n" +
            "public class RecentFilesServiceTest {\n" +
            "    @Test\n" +
            "    public void testAddFile() throws Exception {\n" +
            "        RecentFilesService svc = new RecentFilesService();\n" +
            "        File f = File.createTempFile(\"test\", \".pdf\");\n" +
            "        f.deleteOnExit();\n" +
            "        svc.addFile(f);\n" +
            "        assertTrue(svc.getRecentFiles().contains(f.getAbsolutePath()));\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/test/java/com/filzapdf/persistence/ViewerPreferencesTest.java",
            "package com.filzapdf.persistence;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n" +
            "public class ViewerPreferencesTest {\n" +
            "    @Test\n" +
            "    public void testPrefs() {\n" +
            "        ViewerPreferences prefs = new ViewerPreferences();\n" +
            "        prefs.setTheme(\"dark\");\n" +
            "        assertEquals(\"dark\", prefs.getTheme());\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/test/java/com/filzapdf/annotations/AnnotationManagerTest.java",
            "package com.filzapdf.annotations;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n" +
            "public class AnnotationManagerTest {\n" +
            "    @Test\n" +
            "    public void testUndoRedo() {\n" +
            "        AnnotationManager mgr = new AnnotationManager();\n" +
            "        Annotation a = new Annotation();\n" +
            "        a.setPageIndex(0);\n" +
            "        mgr.executeCommand(new AddAnnotationCommand(mgr, a));\n" +
            "        assertEquals(1, mgr.getAnnotationsForPage(0).size());\n" +
            "        mgr.undo();\n" +
            "        assertEquals(0, mgr.getAnnotationsForPage(0).size());\n" +
            "        mgr.redo();\n" +
            "        assertEquals(1, mgr.getAnnotationsForPage(0).size());\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/test/java/com/filzapdf/pdf/BookmarkServiceTest.java",
            "package com.filzapdf.pdf;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import org.apache.pdfbox.pdmodel.PDDocument;\n" +
            "import java.io.File;\n" +
            "import java.util.List;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n" +
            "public class BookmarkServiceTest {\n" +
            "    @Test\n" +
            "    public void testExtract() throws Exception {\n" +
            "        PDDocument doc = org.apache.pdfbox.Loader.loadPDF(new File(\"src/test/resources/sample.pdf\"));\n" +
            "        BookmarkService svc = new BookmarkService();\n" +
            "        List<BookmarkNode> nodes = svc.extractBookmarks(doc);\n" +
            "        assertNotNull(nodes);\n" +
            "        doc.close();\n" +
            "    }\n" +
            "}\n");

        createFile(baseDir + "/src/main/resources/com/filzapdf/css/light.css",
            ".root { -fx-background-color: #f4f4f4; }\n.toolbar { -fx-background-color: #e0e0e0; }\n");

        createFile(baseDir + "/src/main/resources/com/filzapdf/css/dark.css",
            ".root { -fx-background-color: #2b2b2b; }\n.label { -fx-text-fill: white; }\n.toolbar { -fx-background-color: #3c3f41; }\n.button { -fx-background-color: #4b4d4f; -fx-text-fill: white; }\n");

        createFile(baseDir + "/src/main/java/com/filzapdf/ui/ThemeService.java",
            "package com.filzapdf.ui;\n\n" +
            "import com.filzapdf.persistence.ViewerPreferences;\n" +
            "import javafx.scene.Scene;\n\n" +
            "public class ThemeService {\n" +
            "    private final ViewerPreferences prefs;\n" +
            "    private Scene scene;\n" +
            "    public ThemeService(ViewerPreferences prefs) { this.prefs = prefs; }\n" +
            "    public void setScene(Scene scene) {\n" +
            "        this.scene = scene;\n" +
            "        applyTheme(prefs.getTheme());\n" +
            "    }\n" +
            "    public void applyTheme(String theme) {\n" +
            "        if (scene == null) return;\n" +
            "        scene.getStylesheets().clear();\n" +
            "        try {\n" +
            "            String css = getClass().getResource(\"/com/filzapdf/css/\" + theme + \".css\").toExternalForm();\n" +
            "            scene.getStylesheets().add(css);\n" +
            "        } catch (Exception e) {}\n" +
            "        prefs.setTheme(theme);\n" +
            "    }\n" +
            "    public void toggleTheme() { applyTheme(\"dark\".equals(prefs.getTheme()) ? \"light\" : \"dark\"); }\n" +
            "}\n");

        System.out.println("Done generating files.");
    }

    private static void createFile(String pathStr, String content) throws IOException {
        Path path = Paths.get(pathStr);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
