package io.github.whetfire.lateral;

import java.lang.invoke.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Parent class for all sequences.
 * Sequences should never be null, instead using EmptySequence to represent either
 * an empty sequence or the end of a sequence.
 */
public abstract class Sequence implements Iterable<Object> {

    public abstract Object first();

    abstract public Object second();

    abstract public Object third();

    abstract public Object fourth();

    public abstract Sequence rest();

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

    // TODO: rename to prepend?
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
        return new ArraySequence(forms.toArray());
    }

    /**
     * InvokeDynamic bootstrap method for creating arbitrary length sequences. Returns a CallSite which
     * packs the number of arguments given in methodType into an ArraySequence
     * @param lookup Lookup handle given by the InvokeDynamic instruction
     * @param name Not used
     * @param methodType Expected type of the CallSite
     * @return A CallSite for creating new Sequences
     * @throws IllegalAccessException Should never be thrown as ArraySequence.makeList is public
     * @throws NoSuchMethodException Should never be thrown as long as ArraySequence.makeList exists
     */
    public static CallSite sequenceBuilder(MethodHandles.Lookup lookup, String name, MethodType methodType)
            throws IllegalAccessException, NoSuchMethodException {
        int params = methodType.parameterCount();
        MethodHandle base = lookup.findStatic(ArraySequence.class, "makeList",
                MethodType.methodType(Sequence.class, Object[].class));
        return new ConstantCallSite(base.asCollector(Object[].class, params));
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
