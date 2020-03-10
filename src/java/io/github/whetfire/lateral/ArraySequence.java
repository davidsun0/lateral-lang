package io.github.whetfire.lateral;

/**
 * Sequence backed by an array for better performance
 */
public class ArraySequence extends Sequence {

    private Object[] values;
    private int index;
    private Sequence next;

    public ArraySequence(Object ... values) {
        this(values, 0);
    }

    public ArraySequence(Object[] values, int index) {
        this(values, index, EmptySequence.EMPTY_SEQUENCE);
    }

    public ArraySequence(Object[] values, int index, Sequence next) {
        this.values = values;
        if(index < 0 || index >= values.length)
            throw new RuntimeException("invalid ArraySequence");
        this.index = index;
        this.next = next;
    }

    public static Sequence makeList(Object ... values) {
        if(values == null || values.length == 0)
            return EmptySequence.EMPTY_SEQUENCE;
        else
            return new ArraySequence(values);
    }

    public Object first() {
        return values[index];
    }

    public Sequence rest() {
        if(index + 1 >= values.length) {
            return next;
        } else {
            return new ArraySequence(values, index + 1, next);
        }
    }
}
