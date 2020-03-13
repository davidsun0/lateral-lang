package io.github.whetfire.lateral;

final public class EmptySequence extends Sequence {
    public static final Sequence EMPTY_SEQUENCE = new EmptySequence();

    private EmptySequence() {}

    public Object first() {
        return null;
    }

    public Object second() {
        return null;
    }

    public Object third() {
        return null;
    }

    public Object fourth() {
        return null;
    }

    public Object nth(int n) {
        return null;
    }

    public Sequence rest() {
        return this;
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof EmptySequence;
    }

    public boolean isEmpty() {
        return true;
    }

    public String toString() {
        return "()";
    }
}
