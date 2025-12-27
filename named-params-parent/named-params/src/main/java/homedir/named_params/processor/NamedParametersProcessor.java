package homedir.named_params.processor;

import com.palantir.javapoet.*;
import homedir.named_params.NamedParameters;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.palantir.javapoet.MethodSpec.methodBuilder;
import static homedir.named_params.processor.Report.report;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@SupportedAnnotationTypes("homedir.named_params.NamedParameters")
public class NamedParametersProcessor extends AbstractProcessor {

    int roundNumber = 1;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        try {
            return process_(roundEnv);
        } catch (Exception e) {
            throw new RuntimeException("[%s] encountered an error.".formatted(NamedParametersProcessor.class.getCanonicalName()), e);
        }
    }

    private boolean process_(final RoundEnvironment roundEnv) {
        if (roundNumber == 1) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing round 1.");
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Root elements: %s".formatted(roundEnv.getRootElements()));
            // For now, only static methods are supported.
            roundEnv.getElementsAnnotatedWith(NamedParameters.class)
                    .stream()
                    .map(elt -> elt instanceof ExecutableElement it ? it : null)
                    .filter(Objects::nonNull)
                    .map(method -> {
                        try {
                            return processMethod(method);
                        } catch (RuntimeException e) {
                            if (e instanceof IMessagerReportable reportable) {
                                reportable.reportTo(processingEnv.getMessager());
                                return Optional.<JavaFile>empty();
                            }
                            else {
                                throw e;
                            }
                        }
                    })
                    .flatMap(Optional::stream)
                    .forEach(javaFile -> {
                        try {
                            javaFile.writeTo(processingEnv.getFiler());
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated %s".formatted(javaFile.packageName() + "." + javaFile.typeSpec().name()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        roundNumber++;

        return true;
    }

    private Optional<JavaFile> processMethod(final ExecutableElement method) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing method [%s].".formatted(method));

        if (!method.getModifiers().contains(STATIC)) {
            throw new ProcessingRuntimeException("Annotated method [%s] must be static.".formatted(method), report().element(method));
        }

        if (!(method.getEnclosingElement() instanceof TypeElement typeElt)) {
            throw new ProcessingRuntimeException("Annotated method [%s] must be declared in a top-level class.".formatted(method), report().element(method));
        }

        if (method.getParameters().isEmpty()) {
            // Nothing to do.
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Skipping parameterless method [%s].".formatted(method));
            return Optional.empty();
        }

        final var atNamedParameters = requireNonNull(method.getAnnotation(NamedParameters.class));
        if (!atNamedParameters.value().isEmpty() && !SourceVersion.isName(atNamedParameters.value(), getSupportedSourceVersion())) {
            throw new ProcessingRuntimeException("[%s] is not a legal method name.".formatted(atNamedParameters.value()),
                                                 report().element(method).annotation(NamedParameters.class));
        }
        final var genMethodName = atNamedParameters.value().isEmpty() ? method.getSimpleName() : atNamedParameters.value();

        // The generated methods will be declared in a generated class in the same package as the input method.
        var genClassBuilder = TypeSpec.classBuilder("%s$%s_NamedParams".formatted(typeElt.getSimpleName(), genMethodName))
                .addModifiers(PUBLIC, Modifier.FINAL);

        // Generate a Param<T> type and its instances.
        // TODO Handle primitive types.
        // TODO Handle generic methods.

        final var paramTypeSpec = TypeSpec.interfaceBuilder("Param")
                // .addModifiers(Modifier.STATIC)
                .addTypeVariable(TypeVariableName.get("T"))
                .build();
        genClassBuilder = genClassBuilder.addType(paramTypeSpec);

        final var paramTypeName = ClassName.get("", "Param");

        final var paramFields = method.getParameters()
                .stream()
                .map(param -> FieldSpec.builder(ParameterizedTypeName.get(paramTypeName, TypeName.get(param.asType())),
                                                param.getSimpleName().toString(),
                                                PUBLIC, STATIC, Modifier.FINAL)
                        .initializer("new $T<>(){}", paramTypeName)
                        .build())
                .toList();
        genClassBuilder = genClassBuilder.addFields(paramFields);

        // Generate overloads with 1...n Param<T> parameters.
        for (var i = 1; i <= method.getParameters().size(); i++) {
            genClassBuilder = genClassBuilder.addMethod(createMethod(method, i, paramTypeName, genMethodName));
        }

        genClassBuilder = genClassBuilder.addAnnotation(
                AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", NamedParametersProcessor.class.getCanonicalName())
                        .addMember("date", "$S", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .build());

        final var pkgName = processingEnv.getElementUtils().getPackageOf(typeElt).getQualifiedName();
        return Optional.of(JavaFile.builder(pkgName.toString(), genClassBuilder.build()).build());
    }

    /// ## Example source method
    ///
    /// ```
    /// static Object make(String s, Integer i) { ... }
    /// ```
    ///
    /// ## Example generated methods
    ///
    /// ```
    /// static Param<String> s = ...;
    /// static Param<Integer> i = ...;
    ///
    /// static <T1> Object make(Param<T1> p1, T1 v1) {
    ///     String _s;
    ///     if (p1 == s) _s = v1;
    ///     else _s = null;
    ///
    ///     Integer _i;
    ///     if (p1 == i) _i = v1;
    ///     else _i = null;
    ///
    ///     return make(_s, _i);
    /// }
    ///
    /// static <T1,T2> Object make(Param<T1> p1, T1 v1, Param<T2> p2, T2 v2) {
    ///     String _s;
    ///     if (p1 == s) _s = v1;
    ///     else if (p2 == s) _s = v2;
    ///     else _s = null;
    ///
    ///     Integer _i;
    ///     if (p1 == i) _i = v1;
    ///     else if (p2 == i) _i = v2;
    ///     else _i = null;
    ///
    ///     return make(_s, _i);
    /// }
    /// ```
    ///
    private MethodSpec createMethod(
            final ExecutableElement sourceMethod,
            final int paramCount,
            final ClassName paramClassName,
            final CharSequence methodName)
    {
        if (paramCount <= 0) {
            throw new IllegalArgumentException("paramCount: %s".formatted(paramCount));
        }

        final var typeVars = IntStream.rangeClosed(1, paramCount)
                .mapToObj(i -> TypeVariableName.get("T" + i))
                .toList();

        // TODO Support type variables in the source method.
        final var methodParams = IntStream.rangeClosed(1, paramCount)
                .mapToObj(i -> Stream.of(
                        ParameterSpec.builder(ParameterizedTypeName.get(paramClassName, typeVars.get(i-1)), "p" + i).build(),
                        ParameterSpec.builder(typeVars.get(i-1), "v" + i).build()))
                .flatMap(Function.identity())
                .toList();

        final var localVars = sourceMethod.getParameters()
                .stream()
                .map(m -> "_%s".formatted(m.getSimpleName()))
                .toList();

        var bodyBuilder = CodeBlock.builder();
        for (var i = 0; i < sourceMethod.getParameters().size(); i++) {
            final var sourceParamElt = sourceMethod.getParameters().get(i);
            final var localVarName = localVars.get(i);
            final var localVarType = sourceParamElt.asType();
            bodyBuilder.addStatement("$T $N", localVarType, localVarName);
            for (var j = 0; j < paramCount; j++) {
                bodyBuilder.addStatement((j == 0 ? "if " : "else if ") + "($N == $N) $N = ($T) $N",
                                         methodParams.get(j*2), sourceParamElt.getSimpleName(), localVarName, localVarType, methodParams.get(j*2 + 1));
            }
            bodyBuilder.addStatement("else $N = null", localVarName);
        }
        bodyBuilder.addStatement((sourceMethod.getReturnType().getKind() == TypeKind.VOID ? "" : "return ")
                                 + "$T.$N($L)", sourceMethod.getEnclosingElement().asType(), sourceMethod.getSimpleName(), String.join(", ", localVars));

        return methodBuilder(methodName.toString())
                .addModifiers(PUBLIC, STATIC)
                .addTypeVariables(typeVars)
                .addParameters(methodParams)
                .returns(TypeName.get(sourceMethod.getReturnType()))
                .addCode(bodyBuilder.build())
                .build();
    }
    
}
