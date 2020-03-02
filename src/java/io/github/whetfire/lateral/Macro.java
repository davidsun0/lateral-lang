package io.github.whetfire.lateral;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runtime annotation for macro functions.
 *
 * A function labeled with @Macro will be evaluated at compile time.
 *
 * TODO: should the compiler ensure that macros are static?
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Macro {
}
