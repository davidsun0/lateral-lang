package io.github.whetfire.lateral;

import java.util.Iterator;

public abstract class Sequence implements Iterable<Object> {

    public abstract Object first();

    public abstract Sequence rest();

    public Sequence cons(Object obj) {
        return new LinkedList(obj, this);
    }

    public static Sequence cons(Object obj, Sequence sequence) {
        return new LinkedList(obj, sequence);
    }

    public boolean isEmpty() {
        return false;
    }

    public Object second() {
        return rest().first();
    }

    public Object third() {
        return rest().rest().first();
    }

    public Object fourth() {
        return rest().rest().rest().first();
    }

    public Object nth(int n) {
        Sequence sequence = this;
        for(int i = 0; i < n; i ++) {
            sequence = sequence.rest();
        }
        return sequence.first();
    }

    public int length() {
        Sequence sequence = this;
        int count = 0;
        while(!sequence.isEmpty()) {
            sequence = sequence.rest();
            count ++;
        }
        return count;
    }


    public final Iterator<Object> iterator() {
        return new SequenceIterator(this);
    }

    // TODO: refactor out to string builder for mixed type sequences
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        Sequence list = this;
        while(!list.isEmpty()) {
            if (list.first() instanceof String) {
                builder.append('\"');
                builder.append(list.first());
                builder.append('\"');
            } else {
                builder.append(list.first().toString());
            }
            list = list.rest();
            if(!list.isEmpty()) {
                builder.append(' ');
            }
        }
        builder.append(')');
        return builder.toString();
    }

    class SequenceIterator implements Iterator<Object> {
        private Sequence sequence;

        SequenceIterator(Sequence sequence) {
            this.sequence = sequence;
        }

        public boolean hasNext() {
            return !sequence.isEmpty();
        }

        public Object next() {
            Object ret = sequence.first();
            sequence = sequence.rest();
            return ret;
        }
    }
}
