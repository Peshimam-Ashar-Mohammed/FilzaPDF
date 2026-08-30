package com.filzapdf.annotations;

public class RemoveAnnotationCommand implements AnnotationCommand {
    private final AnnotationManager manager;
    private final Annotation annotation;
    public RemoveAnnotationCommand(AnnotationManager manager, Annotation annotation) {
        this.manager = manager;
        this.annotation = annotation;
    }
    @Override public void execute() { manager.removeAnnotationInternal(annotation); }
    @Override public void undo() { manager.addAnnotationInternal(annotation); }
}
