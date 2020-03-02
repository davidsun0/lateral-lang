package io.github.whetfire.lateral;

import java.util.Iterator;

public class LinkedList implements Iterable<Object>{
    private Object value;
    private LinkedList next;

    public LinkedList(Object value, LinkedList next){
        this.value = value;
        this.next = next;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object o) {
        this.value = o;
    }

    public LinkedList getNext() {
        return next;
    }

    public static LinkedList makeList(Object ... values) {
        LinkedList res = null;
        for (int i = values.length - 1; i >= 0; i--) {
            res = new LinkedList(values[i], res);
        }
        return res;
    }

    public static Object first(LinkedList list) {
        return list == null ? null : list.value;
    }

    public static LinkedList next(LinkedList list) {
        return list == null ? null : list.next;
    }

    public static Object second(LinkedList list) {
        return first(next(list));
    }

    public static Object third(LinkedList list) {
        return first(next(next(list)));
    }

    public static Object fourth(LinkedList list) {
        return first(next(next(next(list))));
    }

    public static Object nth(LinkedList list, int n) {
        for(int i = 0; i < n; i ++) {
            if(list == null)
                return null;
            list = list.getNext();
        }
        return list.getValue();
    }

    public static LinkedList prepend(Object value, LinkedList head) {
        return new LinkedList(value, head);
    }

    public static int length(LinkedList list) {
        if(list == null) {
            return 0;
        } else {
            var count = 0;
            while(list != null) {
                count ++;
                list = list.next;
            }
            return count;
        }
    }

    /**
     * Reverse Destructively - reverses a linked list in place by modifying pointers
     * @param head Head node of a LinkedList
     * @return The head of the reversed list
     */
    public static LinkedList reverseD(LinkedList head) {
        if(head == null)
            return null;
        LinkedList prev = null;
        LinkedList next = head.next;
        while(head != null) {
            head.next = prev;

            prev = head;
            head = next;
            if(next != null)
                next = next.next;
        }
        return prev;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        LinkedList list = this;
        while(list != null) {
            if(list.value == null) {
                builder.append("nil");
            } else if (list.value instanceof String) {
                builder.append('\"');
                builder.append(list.value);
                builder.append('\"');
            } else {
                builder.append(list.value.toString());
            }
            list = list.next;
            if(list != null) {
                builder.append(' ');
            }
        }
        builder.append(')');
        return builder.toString();
    }

    static class LinkedListIterator implements Iterator<Object>{
        LinkedList list;

        LinkedListIterator(LinkedList list) {
            this.list = list;
        }

        public boolean hasNext() {
            return list != null;
        }

        public Object next() {
            Object ret = list.getValue();
            list = LinkedList.next(list);
            return ret;
        }
    }

    public Iterator<Object> iterator() {
        return new LinkedListIterator(this);
    }
}
