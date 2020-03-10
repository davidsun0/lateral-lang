package io.github.whetfire.lateral;

/**
 * Simple linked list implementation of Sequence
 */

public class LinkedList extends Sequence {
    private Object value;
    private Sequence next;

    public LinkedList(Object value) {
        this(value, EmptySequence.EMPTY_SEQUENCE);
    }

    public LinkedList(Object value, Sequence next){
        this.value = value;
        if(next == null)
            throw new RuntimeException("Sequence cannot be null");
        this.next = next;
    }

    public Object first() {
        return value;
    }

    public Sequence rest() {
        return next;
    }

    public static Sequence makeList(Object ... values) {
        Sequence res = EmptySequence.EMPTY_SEQUENCE;
        for (int i = values.length - 1; i >= 0; i--) {
            res = new LinkedList(values[i], res);
        }
        return res;
    }
}
