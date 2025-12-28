package homedir.named_params;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotates a method parameter to indicate that it is a _named parameter_.
///
/// This annotation should be used only in conjunction with [NamedParameters].
///
/// @see NamedParameters
///
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Named {

    /// Name of the parameter to be used by generated methods.
    /// Defaults to the declared parameter name.
    ///
    String value() default "";

}
