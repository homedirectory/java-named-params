package homedir.named_params;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NamedParameters {

    /// The name for each generated method.
    /// Defaults to the name of the annotated method.
    ///
    String value() default "";

}
