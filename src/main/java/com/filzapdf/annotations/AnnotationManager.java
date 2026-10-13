package com.filzapdf.annotations;

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
    public void setOnAnnotationsChanged(Runnable callback) { this.onAnnotationsChanged = callback; }
    public List<Annotation> getAnnotationsForPage(int pageIndex) { return pageAnnotations.getOrDefault(pageIndex, new ArrayList<>()); }
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
    void addAnnotationInternal(Annotation a) { pageAnnotations.computeIfAbsent(a.getPageIndex(), k -> new ArrayList<>()).add(a); }
    void removeAnnotationInternal(Annotation a) {
        List<Annotation> list = pageAnnotations.get(a.getPageIndex());
        if (list != null) list.remove(a);
    }
    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public boolean hasAnnotations() {
        return pageAnnotations.values().stream().anyMatch(l -> !l.isEmpty());
    }
    public void clear() {
        pageAnnotations.clear();
        undoStack.clear();
        redoStack.clear();
        notifyChanged();
    }
    private void notifyChanged() { if (onAnnotationsChanged != null) onAnnotationsChanged.run(); }
}
