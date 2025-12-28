package homedir.named_params;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotates a method with named parameters.
///
/// If the annotated method is static, the annotation processor generates an interface that declares matching static methods
/// with named parameters that call the annotated method.
///
/// If the annotated method is not static, the annotation processor generates an interface that declares instance methods
/// with named parameters that call the annotated method on `this`.
/// To use generated instance methods, the generated interface should be implemented by the class declaring the annotated method.
///
/// Named parameters are those that are annotated with [Named].
/// Unannotated parameters will be declared as mandatory in generated methods.
/// If none of the parameters are annotated with [Named], no code will be generated.
///
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NamedParameters {

    /// The name for each generated method.
    /// Defaults to the name of the annotated method.
    ///
    String value() default "";

}
