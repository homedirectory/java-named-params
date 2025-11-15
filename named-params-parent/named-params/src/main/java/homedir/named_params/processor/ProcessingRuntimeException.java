package homedir.named_params.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class ProcessingRuntimeException extends RuntimeException implements IMessagerReportable {

    private final /* @Nullable */ Report report;

    public ProcessingRuntimeException(String msg, Throwable cause) {
        this(msg, cause, null);
    }

    public ProcessingRuntimeException(String msg) {
        this(msg, null, null);
    }

    public ProcessingRuntimeException(String msg, Report report) {
        this(msg, null, report);
    }

    public ProcessingRuntimeException(String msg, Throwable cause, Report report) {
        super(msg, cause);
        this.report = report;
    }

    @Override
    public Diagnostic.Kind kind() {
        return report != null ? report.kind() : Diagnostic.Kind.ERROR;
    }

    @Override
    public CharSequence message() {
        return report != null && report.message() != null ? report.message() : getMessage();
    }

    @Override
    public Element element() {
        return report != null ? report.element() : null;
    }

    @Override
    public AnnotationMirror annotation() {
        return report != null ? report.annotation() : null;
    }

    @Override
    public AnnotationValue annotationValue() {
        return report != null ? report.annotationValue() : null;
    }

}
