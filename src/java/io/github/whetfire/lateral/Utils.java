package io.github.whetfire.lateral;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class Utils {
    public static byte[] toByteArray(ArrayList<Byte> bytes) {
        byte[] result = new byte[bytes.size()];
        for(int i = 0; i < bytes.size(); i ++) {
            result[i] = bytes.get(i);
        }
        return result;
    }

    public static ArrayList<Byte> appendBytes(ArrayList<Byte> target, byte[] source) {
        for(byte b : source) {
            target.add(b);
        }
        return target;
    }

    public static void putShort(byte[] bytes, int index, short val) {
        bytes[index] = (byte)((val >> 8) & 0xFF);
        bytes[index + 1] = (byte)(val & 0xFF);
    }

    public static void putShort(ArrayList<Byte> bytes, short val) {
        bytes.add((byte)((val >> 8) & 0xFF));
        bytes.add((byte)(val & 0xFF));
    }

    public static void putInt(ArrayList<Byte> bytes, int val) {
        bytes.add((byte)((val >> 24) & 0xFF));
        bytes.add((byte)((val >> 16) & 0xFF));
        bytes.add((byte)((val >> 8) & 0xFF));
        bytes.add((byte)(val & 0xFF));
    }

    /**
     * Unpacks a tree into a list of lists (only 2 levels deep)
     * @param list tree to deflate
     * @return a copy of list that is at most only 2 levels deep
     */
    public static LinkedList semiDeflate(LinkedList list) {
        LinkedList output = null;
        // saves a list of pointers to parents of the working node
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(list);
        while(!stack.isEmpty()) {
            LinkedList top = stack.pop();
            Object val = top.getValue();
            if(val instanceof LinkedList && ((LinkedList) val).getValue() instanceof LinkedList) {
                // top is a list of lists; recurse.
                if(top.getNext() != null)
                    stack.push(top.getNext());
                stack.push((LinkedList) val);
            } else {
                output = LinkedList.prepend(val, output);
                if(top.getNext() != null)
                    stack.push(top.getNext());
            }
        }
        return LinkedList.reverseD(output);
    }
}
