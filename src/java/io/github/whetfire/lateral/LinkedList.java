package io.github.whetfire.lateral;

public class LinkedList {
    private Object value;
    private LinkedList next;

    public LinkedList(Object value, LinkedList next){
        this.value = value;
        this.next = next;
    }

    public static LinkedList makeList(Object ... values) {
        LinkedList res = null;
        for (int i = values.length - 1; i >= 0; i--) {
            res = new LinkedList(values[i], res);
        }
        return res;
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

    public static LinkedList next(LinkedList list) {
        if(list == null)
            return null;
        else
            return list.next;
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        LinkedList list = this;
        while(list != null) {
            if(list.value == null) {
                builder.append("nil");
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
}
