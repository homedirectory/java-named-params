package homedir.named_params.processor;

import com.palantir.javapoet.*;
import homedir.named_params.Named;
import homedir.named_params.NamedParameters;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.palantir.javapoet.MethodSpec.methodBuilder;
import static homedir.named_params.processor.Report.report;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.*;

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

        if (!(method.getEnclosingElement() instanceof TypeElement typeElt)) {
            throw new ProcessingRuntimeException("Annotated method [%s] must be declared in a top-level class.".formatted(method), report().element(method));
        }

        method.getParameters()
                .stream()
                .dropWhile(param -> param.getAnnotation(Named.class) == null)
                .filter(param -> param.getAnnotation(Named.class) == null)
                .findFirst()
                .ifPresent(param -> {
                    throw new ProcessingRuntimeException("Mandatory parameters cannot be declared after named parameters.", report().element(param));
                });

        method.getParameters()
                .stream()
                .filter(param -> param.getAnnotation(Named.class) != null && param.asType().getKind().isPrimitive() && !hasDefault(param))
                .findFirst()
                .ifPresent(param -> {
                    throw new ProcessingRuntimeException("Named parameter with a primitive type must specify a default value.", report().element(param).annotation(Named.class));
                });

        final var namedParameters = method.getParameters()
                .stream()
                .filter(p -> p.getAnnotation(Named.class) != null)
                .toList();

        if (namedParameters.isEmpty()) {
            // Nothing to do.
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Skipping method without named parameters: [%s].".formatted(method));
            return Optional.empty();
        }

        final var atNamedParameters = requireNonNull(method.getAnnotation(NamedParameters.class));
        if (!atNamedParameters.value().isEmpty() && !SourceVersion.isName(atNamedParameters.value(), getSupportedSourceVersion())) {
            throw new ProcessingRuntimeException("[%s] is not a legal method name.".formatted(atNamedParameters.value()),
                                                 report().element(method).annotation(NamedParameters.class));
        }
        final var genMethodName = atNamedParameters.value().isEmpty() ? method.getSimpleName() : atNamedParameters.value();

        // The generated methods will be declared in a generated class in the same package as the input method.
        var genClassBuilder = TypeSpec.interfaceBuilder("%s$%s_NamedParams".formatted(typeElt.getSimpleName(), genMethodName))
                .addModifiers(PUBLIC);

        // Generate a Param<T> type and its instances.

        final var paramTypeSpec = TypeSpec.interfaceBuilder("Param")
                .addModifiers(PUBLIC, STATIC)
                .addTypeVariable(TypeVariableName.get("T"))
                .build();
        genClassBuilder = genClassBuilder.addType(paramTypeSpec);

        final var paramTypeName = ClassName.get("", "Param");

        final var paramFields = namedParameters
                .stream()
                .map(param -> FieldSpec.builder(ParameterizedTypeName.get(paramTypeName, TypeName.get(genParamType(param)).box()),
                                                genParameterName(param),
                                                PUBLIC, STATIC, FINAL)
                        .initializer("new $T<>(){}", paramTypeName)
                        .build())
                .toList();
        genClassBuilder = genClassBuilder.addFields(paramFields);

        // Generate overloads with 0...n Param<T> named parameters.
        final var mandatoryParamCount = method.getParameters().size() - namedParameters.size();
        for (var i = 0; i <= namedParameters.size(); i++) {
            genClassBuilder = genClassBuilder.addMethod(createMethod(method, mandatoryParamCount, i, paramTypeName, genMethodName));
        }

        genClassBuilder = genClassBuilder.addAnnotation(
                AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", NamedParametersProcessor.class.getCanonicalName())
                        .addMember("date", "$S", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .build());

        final var pkgName = processingEnv.getElementUtils().getPackageOf(typeElt).getQualifiedName();
        return Optional.of(JavaFile.builder(pkgName.toString(), genClassBuilder.build()).build());
    }

    private boolean hasDefault(final VariableElement param) {
        return maybeNamedParamDefaultValue(param).isPresent();
    }

    private String genParameterName(VariableElement parameter) {
        return Optional.ofNullable(parameter.getAnnotation(Named.class))
                .map(Named::value)
                .filter(not(String::isEmpty))
                .orElseGet(() -> parameter.getSimpleName().toString());
    }

    private TypeMirror genParamType(VariableElement param) {
        return genParamType_(param.asType());
    }

    private TypeMirror genParamType_(TypeMirror tm) {
        return switch (tm) {
            case PrimitiveType it -> it;
            case ArrayType it -> processingEnv.getTypeUtils().erasure(it);
            case DeclaredType it -> processingEnv.getTypeUtils().erasure(it);
            case TypeVariable it -> processingEnv.getTypeUtils().erasure(it);
            default -> throw new IllegalStateException(format("Unsupported named parameter type: %s", tm));
        };
    }

    /// ## Example source method
    ///
    /// ```
    /// static Object make(@Named String s, @Named(defaultInt = 1) int i) { ... }
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
    ///     if (p1 == s) _s = (String) v1;
    ///     else _s = null;
    ///
    ///     int _i;
    ///     if (p1 == i) _i = (int) v1;
    ///     else _i = 1;
    ///
    ///     return make(_s, _i);
    /// }
    ///
    /// static <T1,T2> Object make(Param<T1> p1, T1 v1, Param<T2> p2, T2 v2) {
    ///     String _s;
    ///     if (p1 == s) _s = (String) v1;
    ///     else if (p2 == s) _s = (String) v2;
    ///     else _s = null;
    ///
    ///     int _i;
    ///     if (p1 == i) _i = (int) v1;
    ///     else if (p2 == i) _i = (int) v2;
    ///     else _i = 1;
    ///
    ///     return make(_s, _i);
    /// }
    /// ```
    ///
    private MethodSpec createMethod(
            final ExecutableElement sourceMethod,
            final int mandatoryParamCount,
            final int namedParamCount,
            final ClassName paramClassName,
            final CharSequence methodName)
    {
        if (mandatoryParamCount < 0) {
            throw new IllegalArgumentException("mandatoryParamCount: %s".formatted(mandatoryParamCount));
        }
        if (namedParamCount < 0) {
            throw new IllegalArgumentException("paramCount: %s".formatted(namedParamCount));
        }

        final var totalNamedParamCount = sourceMethod.getParameters().size() - mandatoryParamCount;

        final var typeVars = IntStream.rangeClosed(1, namedParamCount)
                .mapToObj(i -> TypeVariableName.get("T" + i))
                .toList();

        // TODO Support type variables in the source method.

        final var genMandatoryParams = sourceMethod.getParameters().subList(0, mandatoryParamCount)
                .stream()
                .map(ParameterSpec::get)
                .toList();

        final var genNamedParams = IntStream.rangeClosed(1, namedParamCount)
                .mapToObj(i -> Stream.of(
                        ParameterSpec.builder(ParameterizedTypeName.get(paramClassName, typeVars.get(i-1)), "p" + i).build(),
                        ParameterSpec.builder(typeVars.get(i-1), "v" + i).build()))
                .flatMap(Function.identity())
                .toList();

        final var localVars = sourceMethod.getParameters()
                .stream()
                .skip(mandatoryParamCount)
                .map(param -> "_%s".formatted(genParameterName(param)))
                .toList();

        final var isStatic = sourceMethod.getModifiers().contains(STATIC);

        var bodyBuilder = CodeBlock.builder();
        for (var i = 0; i < totalNamedParamCount; i++) {
            final var sourceParamElt = sourceMethod.getParameters().get(i + mandatoryParamCount);
            final var localVarName = localVars.get(i);
            final var localVarType = genParamType(sourceParamElt);
            bodyBuilder.addStatement("$T $N = $L",
                                     localVarType,
                                     localVarName,
                                     localVarType.getKind().isPrimitive()
                                             ? namedParamDefaultValue(sourceParamElt)
                                             : "null");
            for (var j = 0; j < namedParamCount; j++) {
                bodyBuilder.addStatement((j == 0 ? "if " : "else if ") + "($N == $N) $N = ($T) $N",
                                         genNamedParams.get(j*2), genParameterName(sourceParamElt), localVarName, localVarType, genNamedParams.get(j*2 + 1));
            }
        }

        final var callTarget = isStatic
                ? CodeBlock.of("$T", sourceMethod.getEnclosingElement().asType())
                : CodeBlock.of("(($T) this)", sourceMethod.getEnclosingElement().asType());
        bodyBuilder.addStatement((sourceMethod.getReturnType().getKind() == TypeKind.VOID ? "" : "return ") + "$L.$N($L)",
                                 callTarget,
                                 sourceMethod.getSimpleName(),
                                 Stream.concat(genMandatoryParams.stream().map(ParameterSpec::name), localVars.stream()).collect(joining(", ")));

        return methodBuilder(methodName.toString())
                .addModifiers(PUBLIC, isStatic ? STATIC : DEFAULT)
                .addTypeVariables(typeVars)
                .addParameters(Stream.concat(genMandatoryParams.stream(), genNamedParams.stream()).toList())
                .returns(TypeName.get(sourceMethod.getReturnType()))
                .addCode(bodyBuilder.build())
                .build();
    }

    private Optional<AnnotationValue> maybeNamedParamDefaultValue(final VariableElement param) {
        final var amNamed = findAnnotationMirror(param, Named.class).orElseThrow();
        final var memberName = switch (param.asType().getKind()) {
            case BOOLEAN -> "defaultBool";
            case BYTE -> "defaultByte";
            case SHORT -> "defaultShort";
            case INT -> "defaultInt";
            case LONG -> "defaultLong";
            case CHAR -> "defaultChar";
            case FLOAT -> "defaultFloat";
            case DOUBLE -> "defaultDouble";
            default -> throw new IllegalStateException(format("Unexpected named parameter type: %s", param));
        };
        return maybeAnnotationMemberValue(amNamed, memberName);
    }

    private AnnotationValue namedParamDefaultValue(final VariableElement param) {
        return maybeNamedParamDefaultValue(param)
                .orElseThrow(() -> new IllegalStateException("Named parameter [%s] does not specify a default value."));
    }

    @SuppressWarnings("unchecked")
    private Optional<AnnotationValue> maybeAnnotationMemberValue(final AnnotationMirror mirror, final String memberName) {
        return (Optional<AnnotationValue>) mirror.getElementValues()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().getSimpleName().contentEquals(memberName))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private AnnotationValue annotationMemberValue(final AnnotationMirror mirror, final String memberName) {
        return maybeAnnotationMemberValue(mirror, memberName)
                .orElseThrow(() -> new IllegalStateException(format("Member [%s] is absent in annotation %s", memberName, mirror)));
    }

    @SuppressWarnings("unchecked")
    private Optional<AnnotationMirror> findAnnotationMirror(final AnnotatedConstruct element, final Class<? extends Annotation> type) {
        return (Optional<AnnotationMirror>) element.getAnnotationMirrors()
                .stream()
                .filter(am -> am.getAnnotationType().asElement() instanceof TypeElement typeElt
                              && typeElt.getQualifiedName().contentEquals(type.getCanonicalName()))
                .findFirst();
    }

}
