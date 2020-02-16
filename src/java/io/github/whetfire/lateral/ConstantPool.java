package io.github.whetfire.lateral;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents the list of constants in the constant pool in a Java class file
 *
 * Contains ConstantEntry subclasses representing the various types of constants in the pool
 * (e.g. UTF-8 text, Class References, Method References)
 */
public class ConstantPool {
    private HashMap<ConstantEntry, Short> entries;
    private ArrayList<Byte> entryBytes;
    private short entryCount;

    public static UTF8Info CODE_NAME = new UTF8Info("Code");
    public static UTF8Info STACKMAP_NAME = new UTF8Info("StackMapTable");

    ConstantPool(){
        entries = new HashMap<>();
        entryBytes = new ArrayList<>();
        entryCount = 1;
    }

    /**
     * Inserts a new constant into the ConstantPool if not already present
     * @param entry The ConstantEntry representation of a Java class constant
     * @return The index (starts at 1) of the entry in the pool
     */
    short put(ConstantEntry entry) {
        if(entries.containsKey(entry)) {
            return entries.get(entry);
        } else {
            byte[] bytes = entry.resolveBytes(this);
            entries.put(entry, entryCount);
            for(Byte b : bytes) {
                entryBytes.add(b);
            }
            return entryCount ++;
        }
    }

    /**
     * Converts current state of the ConstantPool into a byte array to be inserted into a .class file
     * @return Byte array representation of the object
     */
    byte[] toBytes() {
        byte[] poolBytes = new byte[entryBytes.size() + 2];
        Utils.putShort(poolBytes, 0, entryCount);
        int offset = 2;
        for(int i = 0; i < entryBytes.size(); i ++) {
            poolBytes[offset + i] = entryBytes.get(i);
        }
        return poolBytes;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for(var entry : entries.entrySet()) {
            stringBuilder.append('#');
            stringBuilder.append(entry.getValue());
            stringBuilder.append(" : ");
            stringBuilder.append(entry.getKey());
            stringBuilder.append('\n');
        }
        return stringBuilder.toString();
    }

    /**
     * Base class for all constants that can be inserted into the ConstantPool.
     * Both hashCode() and equals() are necessary for hashing; making them abstract forces children to implement them
     */
    static abstract class ConstantEntry {
        /**
         * Resolves any dependencies if they exists and converts the entry into a byte array representation
         * @param pool ConstantPool to which this entry will be added
         * @return The byte array representation of this entry in pool
         */
        abstract byte[] resolveBytes(ConstantPool pool);
        abstract public int hashCode();
        abstract public boolean equals(Object obj);
    }

    static class UTF8Info extends ConstantEntry {
        static final byte ID = 1;
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

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = utf8Bytes.length;

            byte[] values = new byte[length + 3];
            values[0] = ID;
            values[1] = (byte)((length >> 8) & 0xFF);
            values[2] = (byte)(length & 0xFF);
            System.arraycopy(utf8Bytes, 0, values, 3, length);
            return values;
        }

        @Override
        public String toString() {
            return "UTF8Info " + value;
        }
    }

    static class ClassInfo extends ConstantEntry {
        static final byte ID = 7;
        String name;

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

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            UTF8Info dependency = new UTF8Info(name);
            short nameIndex = pool.put(dependency);

            byte[] bytes = new byte[3];
            bytes[0] = ID;
            bytes[1] = (byte)((nameIndex >> 8) & 0xFF);
            bytes[2] = (byte)(nameIndex & 0xFF);
            return bytes;
        }

        @Override
        public String toString() {
            return "ClassInfo " + name;
        }
    }

    static class NameAndTypeInfo extends ConstantEntry {
        static final byte ID = 12;
        String name;
        String type;

        NameAndTypeInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else {
                return obj instanceof NameAndTypeInfo &&
                        name.equals(((NameAndTypeInfo)obj).name) &&
                        type.equals(((NameAndTypeInfo)obj).type);
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode() + type.hashCode();
        }

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            short nameIndex = pool.put(new UTF8Info(name));
            short typeIndex = pool.put(new UTF8Info(type));

            byte[] bytes = new byte[5];
            bytes[0] = ID;
            bytes[1] = (byte)((nameIndex >> 8) & 0xFF);
            bytes[2] = (byte)(nameIndex & 0xFF);
            bytes[3] = (byte)((typeIndex >> 8) & 0xFF);
            bytes[4] = (byte)(typeIndex & 0xFF);
            return bytes;
        }
    }

    static class FieldRefInfo extends ConstantEntry {
        static final byte ID = 0x09;
        ClassInfo cinfo;
        NameAndTypeInfo nameType;

        FieldRefInfo(String className, String methodName, String methodType) {
            cinfo = new ClassInfo(className);
            nameType = new NameAndTypeInfo(methodName, methodType);
        }

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            byte[] bytes = new byte[5];
            bytes[0] = ID;

            short cinfoIndex = pool.put(cinfo);
            bytes[1] = (byte)((cinfoIndex >> 8) & 0xFF);
            bytes[2] = (byte)(cinfoIndex & 0xFF);

            short nameTypeIndex = pool.put(nameType);
            bytes[3] = (byte)((nameTypeIndex >> 8) & 0xFF);
            bytes[4] = (byte)(nameTypeIndex& 0xFF);
            return bytes;
        }

        @Override
        public int hashCode() {
            return cinfo.hashCode() + nameType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else {
                return (obj instanceof MethodRefInfo) &&
                        (cinfo.equals(((MethodRefInfo) obj).cinfo)) &&
                        (nameType.equals(((MethodRefInfo) obj).nameType));
            }
        }

        public String toString() {
            return String.format("%s.%s:%s", cinfo.name, nameType.name, nameType.type);
        }
    }

    /*
     * FieldRef and InterfaceMethodRef are the same, except with tag ID 9 and 11 respectively
     */
    static class MethodRefInfo extends ConstantEntry {
        static final byte ID = 0x0A;
        ClassInfo cinfo;
        NameAndTypeInfo nameType;

        MethodRefInfo(String className, String methodName, String methodType) {
            cinfo = new ClassInfo(className);
            nameType = new NameAndTypeInfo(methodName, methodType);
        }

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            byte[] bytes = new byte[5];
            bytes[0] = ID;

            short cinfoIndex = pool.put(cinfo);
            bytes[1] = (byte)((cinfoIndex >> 8) & 0xFF);
            bytes[2] = (byte)(cinfoIndex & 0xFF);

            short nameTypeIndex = pool.put(nameType);
            bytes[3] = (byte)((nameTypeIndex >> 8) & 0xFF);
            bytes[4] = (byte)(nameTypeIndex& 0xFF);
            return bytes;
        }

        @Override
        public int hashCode() {
            return cinfo.hashCode() + nameType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else {
                return (obj instanceof MethodRefInfo) &&
                        (cinfo.equals(((MethodRefInfo) obj).cinfo)) &&
                        (nameType.equals(((MethodRefInfo) obj).nameType));
            }
        }

        public String toString() {
            return String.format("%s.%s:%s", cinfo.name, nameType.name, nameType.type);
        }
    }

    static class MethodHandleInfo extends ConstantEntry {
        static final byte ID = 15;
        short referenceKind;
        short referenceIndex;

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            byte[] bytes = new byte[5];
            bytes[0] = ID;
            bytes[1] = (byte)((referenceKind >> 8) & 0xFF);
            bytes[2] = (byte)(referenceKind & 0xFF);
            bytes[3] = (byte)((referenceIndex >> 8) & 0xFF);
            bytes[4] = (byte)(referenceIndex & 0xFF);
            return bytes;
        }

        @Override
        public int hashCode() {
            return Short.hashCode(referenceKind) + Short.hashCode(referenceIndex);
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else {
                return (obj instanceof MethodHandleInfo) &&
                        (referenceKind == ((MethodHandleInfo) obj).referenceKind) &&
                        (referenceIndex == ((MethodHandleInfo) obj).referenceIndex);
            }
        }
    }

    static class InvokeDynamicInfo extends ConstantEntry {
        static final byte ID = 18;
        short bootstrapIndex;
        NameAndTypeInfo nameType;

        @Override
        byte[] resolveBytes(ConstantPool pool) {
            byte[] bytes = new byte[5];
            bytes[0] = ID;
            bytes[1] = (byte)((bootstrapIndex >> 8) & 0xFF);
            bytes[2] = (byte)(bootstrapIndex & 0xFF);
            short nameTypeIndex = pool.put(nameType);
            bytes[3] = (byte)((nameTypeIndex >> 8) & 0xFF);
            bytes[4] = (byte)(nameTypeIndex & 0xFF);
            return bytes;
        }

        @Override
        public int hashCode() {
            return nameType.hashCode() + Short.hashCode(bootstrapIndex);
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else {
                return (obj instanceof  InvokeDynamicInfo) &&
                        (bootstrapIndex == ((InvokeDynamicInfo) obj).bootstrapIndex) &&
                        (nameType.equals(((InvokeDynamicInfo) obj).nameType));
            }
        }
    }
}
