package io.github.whetfire.lateral;

/**
 * Provides a slightly abstracted view of JVM bytecode.
 * Each ByteCode object represents a logical JVM operation:
 * For example, IntConst can be used to push an int of any size - the correct bytecode will be chosen automatically.
 *
 * ByteCodes also provides the Label pseudo-code which can contain jump target information.
 */
public class ByteCodes {

    static abstract class ByteCode {
        abstract int newStackHeight(int stackHeight);

        byte[] resolveBytes() {
            return null;
        }

        byte[] resolveBytes(ClassBuilder builder) {
            return resolveBytes();
        }

        int newLocalCount(int localCount) {
            return localCount;
        }

        abstract int byteLength();
    }

    static final class SimpleByteCode extends ByteCode {
        final byte value;
        final int stackChange;
        final int localChange;
        final String name;

        SimpleByteCode(String name, byte value, int stackChange) {
            this(name, value, stackChange, 0);
        }

        SimpleByteCode(String name, byte value, int stackChange, int localChange) {
            this.value = value;
            this.name = name;
            this.stackChange = stackChange;
            this.localChange = localChange;
        }

        byte[] resolveBytes() {
            return new byte[]{value};
        }

        int newLocalCount(int localCount) {
            return localCount + localChange;
        }

        int newStackHeight(int stackHeight) {
            return stackHeight + stackChange;
        }

        int byteLength() {
            return 1;
        }

        public String toString() {
            return name;
        }
    }

    static final SimpleByteCode ACONST_NULL = new SimpleByteCode("aconst_null", (byte)0x01, 1);

    static final SimpleByteCode IRETURN = new SimpleByteCode("ireturn", (byte)0xAC, -1);
    static final SimpleByteCode ARETURN = new SimpleByteCode("areturn", (byte)0xB0, 1);

    static final SimpleByteCode POP = new SimpleByteCode("pop", (byte)0x57, -1);
    static final SimpleByteCode DUP = new SimpleByteCode("dup", (byte)0x59, 1);

    static final SimpleByteCode IADD = new SimpleByteCode("iadd", (byte)0x60, 1);
    static final SimpleByteCode IMUL = new SimpleByteCode("imul", (byte)0x68, 1);

    static class Ldc extends ByteCode {
        short index;

        Ldc(ClassBuilder classBuilder, ConstantPool.ConstantEntry resource) {
            index = classBuilder.getPool().put(resource);
        }

        byte[] resolveBytes(ClassBuilder builder) {
            if(index < 256) {
                byte[] bytes = new byte[2];
                bytes[0] = (byte)0x12;
                bytes[1] = (byte)index;
                return bytes;
            } else {
                byte[] bytes = new byte[3];
                bytes[0] = (byte)0x13;
                Utils.putShort(bytes, 1, index);
                return bytes;
            }
        }

        int byteLength() {
            return index < 256 ? 2 : 3;
        }

        int newStackHeight(int stackHeight) {
            return stackHeight + 1;
        }
    }

    static class Label extends ByteCode {
        int stackHeight;
        int localCount;
        int byteCodePosition;

        Label() {
            stackHeight = -1;
            localCount = -1;
            byteCodePosition = -1;
        }

        void setValues(int stackHeight, int localCount) {
            this.stackHeight = stackHeight;
            this.localCount = localCount;
        }

        void setByteCodePosition(int byteCodePosition){
            this.byteCodePosition = byteCodePosition;
        }

        byte[] resolveBytes() {
            return new byte[0];
        }

        int newStackHeight(int stackHeight) {
            return this.stackHeight;
        }

        int newLocalCount(int localCount) {
            return this.localCount;
        }

        int byteLength() {
            return 0;
        }

        public int getByteCodePosition() {
            return byteCodePosition;
        }

        public String toString() {
            // return "label " + hashCode() + ":";
            return String.format("[Label %X]", hashCode());
        }
    }

    static abstract class Jump extends ByteCode {
        int currentOffset;
        Label target;

        void setCurrentOffset(int currentOffset) {
            this.currentOffset = currentOffset;
        }
    }

    static class IfNull extends Jump {
        static final byte ID = (byte)0xC6;

        IfNull(Label label) {
            target = label;
        }

        byte[] resolveBytes(ClassBuilder builder) {
            byte[] bytes = new byte[3];
            bytes[0] = ID;
            // System.out.printf("if stats: target: %d here: %d%n", target.byteCodePosition, currentOffset);
            short relJump = (short)(target.byteCodePosition - currentOffset);
            Utils.putShort(bytes, 1, relJump);
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            // TODO: assert target's stack height is the same
            return stackHeight - 1;
        }

        int byteLength() {
            return 3;
        }

        public String toString() {
            return "ifnull " + target;
        }
    }

    static class IfNonNull extends Jump {
        static final byte ID = (byte)0xC7;

        IfNonNull(Label label) {
            target = label;
        }

        byte[] resolveBytes(ClassBuilder builder) {
            byte[] bytes = new byte[3];
            bytes[0] = ID;
            // System.out.printf("if stats: target: %d here: %d%n", target.byteCodePosition, currentOffset);
            short relJump = (short)(target.byteCodePosition - currentOffset);
            Utils.putShort(bytes, 1, relJump);
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            // TODO: assert target's stack height is the same
            return stackHeight - 1;
        }

        int byteLength() {
            return 3;
        }

        public String toString() {
            return "ifnonnull " + target;
        }
    }

    static class Goto extends Jump {
        static final byte ID = (byte)0xA7;

        Goto(Label label) {
            target = label;
        }

        byte[] resolveBytes(ClassBuilder builder) {
            // TODO: somehow calculate byte currentOffset
            byte[] bytes = new byte[3];
            bytes[0] = ID;
            System.out.printf("goto stats: target: %d here: %d%n", target.byteCodePosition, currentOffset);
            short relJump = (short)(target.byteCodePosition - currentOffset);
            Utils.putShort(bytes, 1, relJump);
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            // TODO: return label's stack info
            return stackHeight;
        }

        int byteLength() {
            return 3;
        }

        public String toString() {
            return "goto " + target;
        }
    }

    static class IntConst extends ByteCode {
        int val;
        byte[] bytes;

        IntConst(int val) {
            this.val = val;

            if(-1 <= val && val <= 5) {
                // iconst_<i>
                bytes = new byte[]{(byte)(val + 3)};
            } else {
                // ldc integer constant
                throw new RuntimeException("Can't push integer constant of " + val);
            }
        }

        byte[] resolveBytes() {
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            return stackHeight + 1;
        }

        int byteLength() {
            return bytes.length;
        }

        public String toString() {
            return "iconst " + val;
        }
    }

    static class InvokeStatic extends ByteCode {
        static final byte ID = (byte)0xB8;
        ConstantPool.MethodRefInfo methodRefInfo;
        int stackDiff;

        InvokeStatic(String className, String methodName, String methodType) {
            methodRefInfo = new ConstantPool.MethodRefInfo(className, methodName, methodType);
            stackDiff = 0;
        }

        // stack change should be calculated from method type
        InvokeStatic(String className, String methodName, String methodType, int stackDiff) {
            methodRefInfo = new ConstantPool.MethodRefInfo(className, methodName, methodType);
            this.stackDiff = stackDiff;
        }

        byte[] resolveBytes(ClassBuilder builder) {
            byte[] bytes = new byte[3];
            bytes[0] = ID;

            short x = builder.getPool().put(methodRefInfo);
            Utils.putShort(bytes, 1, x);
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            return stackHeight + stackDiff;
        }

        int byteLength() {
            return 3;
        }

        public String toString() {
            return "invokestatic " + methodRefInfo;
        }
    }

    static class GetStatic extends ByteCode {
        static final byte ID = (byte)0xB2;
        ConstantPool.FieldRefInfo fieldRefInfo;

        GetStatic(String className, String fieldName, String fieldType) {
            fieldRefInfo = new ConstantPool.FieldRefInfo(className, fieldName, fieldType);
        }

        byte[] resolveBytes(ClassBuilder builder) {
            byte[] bytes = new byte[3];
            bytes[0] = ID;
            short x = builder.getPool().put(fieldRefInfo);
            Utils.putShort(bytes, 1, x);
            return bytes;
        }

        int newStackHeight(int stackHeight) {
            return stackHeight + 1;
        }

        int byteLength() {
            return 3;
        }

        public String toString() {
            return "getstatic " + fieldRefInfo;
        }
    }
}
