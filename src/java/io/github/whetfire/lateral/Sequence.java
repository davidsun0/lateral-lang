package io.github.whetfire.lateral;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Parent class for all sequences.
 * Sequences should never be null, instead using EmptySequence to represent either
 * an empty sequence or the end of a sequence.
 */
public abstract class Sequence implements Iterable<Object> {

    public abstract Object first();

    public abstract Sequence rest();

    public Sequence cons(Object obj) {
        return new LinkedList(obj, this);
    }

    public static Sequence cons(Object obj, Sequence sequence) {
        if(sequence == null)
            throw new RuntimeException("Sequence cannot be null");
        return sequence.cons(obj);
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

    public static Sequence concat(Sequence seqs) {
        ArrayList<Object> forms = new ArrayList<>();
        while(!seqs.isEmpty()) {
            if(seqs.first() instanceof Sequence) {
                Sequence seq = (Sequence) seqs.first();
                while(!seq.isEmpty()) {
                    forms.add(seq.first());
                    seq = seq.rest();
                }
            } else {
                throw new TypeException(seqs.first().getClass(), Sequence.class);
            }
            seqs = seqs.rest();
        }
        return LinkedList.makeList(forms.toArray());
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
                builder.append(list.first());
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
