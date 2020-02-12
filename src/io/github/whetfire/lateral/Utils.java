package io.github.whetfire.lateral;

import java.util.ArrayList;

public class Utils {
    public static byte[] arrayListToBytes(ArrayList<Byte> bytes) {
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
}
