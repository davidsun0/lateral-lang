package lateral.lang;

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

    // TODO: add mutators and copy on write ImmutableSequence
    // public abstract Object setFirst(Object object);
    // public abstract Sequence setRest(Sequence sequence);
    // ImmutableSequence would necessitate a copy method

    public abstract Object nth(int n);
    public abstract int length();
    public Object second() {
        return nth(1);
    }
    public Object third() {
        return nth(2);
    }
    public Object fourth() {
        return nth(3);
    }

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

    public static Sequence concat(Sequence seqs) {
        ArrayList<Object> forms = new ArrayList<>();
        while(!seqs.isEmpty()) {
            if(seqs.first() instanceof Sequence) {
                Sequence inner = (Sequence) seqs.first();
                while (!inner.isEmpty()) {
                    forms.add(inner.first());
                    inner = inner.rest();
                }
            } else {
                forms.add(seqs.first());
            }
            seqs = seqs.rest();
        }
        return new ArraySequence(forms.toArray());
    }

    public static Sequence makeList(Object ... values) {
        if(values == null || values.length == 0)
            return EmptySequence.EMPTY_SEQUENCE;
        else
            return new ArraySequence(values);
    }

    public static Sequence makeList(Object[] values, int index) {
        if(index == values.length) {
            return EmptySequence.EMPTY_SEQUENCE;
        } else if(index < values.length) {
            return new ArraySequence(values, index);
        } else {
            throw new RuntimeException("invalid ArraySequence");
        }
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

    public final Iterator<Object> iterator() {
        return new SequenceIterator(this);
    }

    static class SequenceIterator implements Iterator<Object> {
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
