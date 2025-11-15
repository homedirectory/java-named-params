package homedir.named_params.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;

record Report(
        Diagnostic.Kind kind,
        CharSequence message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue)
{

    private static final Report EMPTY = new Report();

    public Report() {
        this(null, null, null, null, null);
    }

    public static Report report() {
        return EMPTY;
    }

    public Report kind(final Diagnostic.Kind kind) {
        return new Report(kind, message, element, annotation, annotationValue);
    }

    public Report message(final CharSequence message) {
        return new Report(kind, message, element, annotation, annotationValue);
    }

    public Report element(final Element element) {
        return new Report(kind, message, element, annotation, annotationValue);
    }

    public Report annotation(final AnnotationMirror annotation) {
        return new Report(kind, message, element, annotation, annotationValue);
    }

    public Report annotation(final Class<? extends Annotation> annotation) {
        return element.getAnnotationMirrors().stream()
                .filter(am -> ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotation.getCanonicalName()))
                .findFirst()
                .map(this::annotation)
                .orElse(this);
    }

    public Report annotationValue(final AnnotationValue annotationValue) {
        return new Report(kind, message, element, annotation, annotationValue);
    }

}
