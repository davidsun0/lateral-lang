package io.github.whetfire.lateral;

public class LinkedList {
    private Object value;
    private LinkedList next;

    public LinkedList(Object value, LinkedList next){
        this.value = value;
        this.next = next;
    }

    public static LinkedList prepend(Object value, LinkedList head) {
        return new LinkedList(value, head);
    }

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
