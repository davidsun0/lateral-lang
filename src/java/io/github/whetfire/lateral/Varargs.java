package io.github.whetfire.lateral;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runtime annotation for variable argument functions (varargs)
 *
 * When calling a varargs function, the caller will pack the varargs into a LinkedList
 * before passing to the callee. The LinkedList will be null if there are no objects
 * in the varargs position.
 *
 * The JVM doesn't enforce that the last argument is an array (LinkedList for
 * Lateral), so it doesn't seem immediately necessary to do here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Varargs {
}
