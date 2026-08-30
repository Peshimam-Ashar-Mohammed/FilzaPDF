package com.filzapdf.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnotationManagerTest {

    private AnnotationManager manager;

    @BeforeEach
    void setup() {
        manager = new AnnotationManager();
    }

    private Annotation makeAnnotation(int pageIndex, AnnotationType type) {
        Annotation a = new Annotation();
        a.setPageIndex(pageIndex);
        a.setType(type);
        a.setX(10); a.setY(20); a.setWidth(100); a.setHeight(30);
        a.setColor("#FFFF00");
        return a;
    }

    @Test
    void addAnnotationAppearsOnPage() {
        Annotation a = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        manager.executeCommand(new AddAnnotationCommand(manager, a));

        assertEquals(1, manager.getAnnotationsForPage(0).size());
        assertEquals(AnnotationType.HIGHLIGHT, manager.getAnnotationsForPage(0).get(0).getType());
    }

    @Test
    void undoRemovesAnnotation() {
        Annotation a = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        manager.executeCommand(new AddAnnotationCommand(manager, a));
        assertEquals(1, manager.getAnnotationsForPage(0).size());

        manager.undo();
        assertEquals(0, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void redoRestoresAnnotation() {
        Annotation a = makeAnnotation(0, AnnotationType.UNDERLINE);
        manager.executeCommand(new AddAnnotationCommand(manager, a));
        manager.undo();
        assertEquals(0, manager.getAnnotationsForPage(0).size());

        manager.redo();
        assertEquals(1, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void undoRedoStacksAreMultiLevel() {
        Annotation a1 = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        Annotation a2 = makeAnnotation(0, AnnotationType.NOTE);
        manager.executeCommand(new AddAnnotationCommand(manager, a1));
        manager.executeCommand(new AddAnnotationCommand(manager, a2));
        assertEquals(2, manager.getAnnotationsForPage(0).size());

        manager.undo();
        assertEquals(1, manager.getAnnotationsForPage(0).size());

        manager.undo();
        assertEquals(0, manager.getAnnotationsForPage(0).size());

        manager.redo();
        assertEquals(1, manager.getAnnotationsForPage(0).size());

        manager.redo();
        assertEquals(2, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void newCommandClearsRedoStack() {
        Annotation a1 = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        Annotation a2 = makeAnnotation(0, AnnotationType.UNDERLINE);
        Annotation a3 = makeAnnotation(0, AnnotationType.STRIKETHROUGH);

        manager.executeCommand(new AddAnnotationCommand(manager, a1));
        manager.executeCommand(new AddAnnotationCommand(manager, a2));
        manager.undo(); // a2 in redo stack

        // New command should clear redo
        manager.executeCommand(new AddAnnotationCommand(manager, a3));
        manager.undo(); // undo a3
        manager.redo(); // redo a3

        // a2 should NOT be redoable
        assertEquals(2, manager.getAnnotationsForPage(0).size()); // a1 and a3
        assertFalse(manager.getAnnotationsForPage(0).contains(a2));
    }

    @Test
    void removeAnnotationCommand() {
        Annotation a = makeAnnotation(0, AnnotationType.FREEHAND);
        manager.executeCommand(new AddAnnotationCommand(manager, a));
        assertEquals(1, manager.getAnnotationsForPage(0).size());

        manager.executeCommand(new RemoveAnnotationCommand(manager, a));
        assertEquals(0, manager.getAnnotationsForPage(0).size());

        // Undo the remove
        manager.undo();
        assertEquals(1, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void annotationsAreSeparatedByPage() {
        Annotation a0 = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        Annotation a1 = makeAnnotation(1, AnnotationType.NOTE);
        Annotation a2 = makeAnnotation(1, AnnotationType.UNDERLINE);

        manager.executeCommand(new AddAnnotationCommand(manager, a0));
        manager.executeCommand(new AddAnnotationCommand(manager, a1));
        manager.executeCommand(new AddAnnotationCommand(manager, a2));

        assertEquals(1, manager.getAnnotationsForPage(0).size());
        assertEquals(2, manager.getAnnotationsForPage(1).size());
        assertEquals(0, manager.getAnnotationsForPage(99).size());
    }

    @Test
    void onAnnotationsChangedCallbackFires() {
        AtomicInteger callCount = new AtomicInteger(0);
        manager.setOnAnnotationsChanged(callCount::incrementAndGet);

        Annotation a = makeAnnotation(0, AnnotationType.HIGHLIGHT);
        manager.executeCommand(new AddAnnotationCommand(manager, a));
        assertEquals(1, callCount.get());

        manager.undo();
        assertEquals(2, callCount.get());

        manager.redo();
        assertEquals(3, callCount.get());
    }

    @Test
    void undoOnEmptyStackDoesNotThrow() {
        // Should silently no-op
        manager.undo();
        manager.undo();
        assertEquals(0, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void redoOnEmptyStackDoesNotThrow() {
        manager.redo();
        manager.redo();
        assertEquals(0, manager.getAnnotationsForPage(0).size());
    }

    @Test
    void allAnnotationTypesAreSupported() {
        for (AnnotationType type : AnnotationType.values()) {
            AnnotationManager m = new AnnotationManager();
            Annotation a = makeAnnotation(0, type);
            m.executeCommand(new AddAnnotationCommand(m, a));
            assertEquals(1, m.getAnnotationsForPage(0).size());
            assertEquals(type, m.getAnnotationsForPage(0).get(0).getType());
        }
    }
}
