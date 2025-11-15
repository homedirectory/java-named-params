package homedir.named_params.processor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

interface IMessagerReportable {

    Diagnostic.Kind kind();

    CharSequence message();

    Element element();

    AnnotationMirror annotation();

    AnnotationValue annotationValue();

    default void reportTo(Messager messager) {
        final var kind = kind() != null ? kind() : Diagnostic.Kind.ERROR;
        if (element() == null) {
            messager.printMessage(kind, message());
        }
        else if (annotation() == null) {
            messager.printMessage(kind, message(), element());
        }
        else if (annotationValue() == null) {
            messager.printMessage(kind, message(), element(), annotation());
        }
        else {
            messager.printMessage(kind, message(), element(), annotation(), annotationValue());
        }
    }

}
