package com.filzapdf.annotations;

public class AddAnnotationCommand implements AnnotationCommand {
    private final AnnotationManager manager;
    private final Annotation annotation;
    public AddAnnotationCommand(AnnotationManager manager, Annotation annotation) {
        this.manager = manager;
        this.annotation = annotation;
    }
    @Override public void execute() { manager.addAnnotationInternal(annotation); }
    @Override public void undo() { manager.removeAnnotationInternal(annotation); }
}
