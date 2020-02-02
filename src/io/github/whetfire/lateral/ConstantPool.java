package io.github.whetfire.lateral;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ConstantPool {
    HashMap<ConstantEntry, Short> entries;
    short entryCount;

    static abstract class ConstantEntry {
        abstract byte[] toBytes();
        void resolve(HashMap<ConstantEntry, Short> map) {
            ;
        }
    }

    static class UTF8Info extends ConstantEntry {
        static byte ID = 0x01;
        String value;

        UTF8Info(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else {
                return obj instanceof UTF8Info && value.equals(((UTF8Info) obj).value);
            }
        }

        byte[] toBytes() {
            byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = utf8Bytes.length;

            byte[] values = new byte[length + 3];
            values[0] = ID;
            values[1] = (byte)((length >> 8) & 0xFF);
            values[2] = (byte)(length & 0xFF);
            System.arraycopy(utf8Bytes, 0, values, 3, length);
            return values;
        }
    }

    static class ClassInfo extends ConstantEntry {
        static byte ID = 0x07;
        String name;
        short nameIndex;

        ClassInfo(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else {
                return obj instanceof ClassInfo && name.equals(((ClassInfo) obj).name);
            }
        }

        byte[] toBytes() {
            byte[] bytes = new byte[3];
            bytes[0] = ID;
            bytes[1] = (byte)((nameIndex >> 8) & 0xFF);
            bytes[2] = (byte)(nameIndex & 0xFF);
            return bytes;
        }
    }
}
