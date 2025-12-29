package homedir.named_params;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotates a method parameter to indicate that it is a _named parameter_.
///
/// This annotation should be used only in conjunction with [NamedParameters].
///
/// If a method call does not specify an argument for a named parameter, a default value is used.
/// * For primitive types, the [Named] annotation must specify the default value using one of the `default*` members.
/// * For other types, `null` is used as the default value.
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

    int defaultInt() default 0;

    char defaultChar() default ' ';

    boolean defaultBool() default true;

    byte defaultByte() default 0;

    short defaultShort() default 0;

    long defaultLong() default 0;

    float defaultFloat() default 0;

    double defaultDouble() default 0;

}
