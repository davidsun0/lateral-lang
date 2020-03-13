package io.github.whetfire.lateral;

/**
 * Parent class of all Lateral functions
 * TODO: convert to interface?
 */
abstract public class Function {

    abstract public boolean isMacro();
    abstract public boolean isVarargs();
    abstract public int paramCount();

    // Function(Namespace ns, Object ... capturedVars);
    public Object invoke() {
        throw new RuntimeException();
    }

    public Object invoke(Object object) {
        throw new RuntimeException();
    }

    public Object invoke(Object arg1, Object arg2) {
        throw new RuntimeException();
    }

    public Object invokeSeq(Sequence args) {
        throw new RuntimeException();
    }

    public Object apply(Sequence args) {
        int length = args.length();
        if(length == 0) {
            return invoke();
        } else if(length == 1) {
            return invoke(args.first());
        } else if(length == 2) {
            return invoke(args.first(), args.second());
        } else {
            return invokeSeq(args);
        }
    }

    public String toString() {
        return "<function>";
    }
}
