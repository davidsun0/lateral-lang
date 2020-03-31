package io.github.whetfire.lateral;

/**
 * Parent class of all Lateral functions.
 * TODO: convert to interface?
 */

abstract public class Function {
    abstract public boolean isMacro();
    // abstract public boolean isVarargs();
    // abstract public int paramCount();

    /**
     * apply is the preferred run-time way to call a first class function object.
     * apply should dispatch to calls of Function.invoke(). invoke methods should be
     * public virtual and be non-void. invoke is the preferred compile-time way to
     * call an object. If the function object represents a varargs call, the last
     * argument of invoke should be of type Sequence.
     *
     * As an optimization, invokedynamic calls resolved by Environment will attempt to
     * directly call invoke with the given arguments instead of apply.
     *
     * @param args The objects of the function call
     * @return The result of calling this function on the given arguments
     * @throws UnsupportedOperationException
     * When the function cannot be applied to the given number of arguments
     */
    abstract public Object apply(Object ... args);

    public String toString() {
        return "#<function>";
    }
}
