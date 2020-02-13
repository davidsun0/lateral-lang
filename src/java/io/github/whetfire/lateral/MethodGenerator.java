package io.github.whetfire.lateral;

import java.util.ArrayList;

public class MethodGenerator {
    ArrayList<OpCode> opCodes;

    MethodGenerator() {
        opCodes = new ArrayList<>();
    }

    public byte[] resolveBytes(ConstantPool pool) {
        ArrayList<Byte> bytes = new ArrayList<>();
        // u2 accessor flags
        // public static
        Utils.putShort(bytes, (short)0x0009);
        // u2 name index (utf8)
        short x = pool.put(new ConstantPool.UTF8Info("myFunction"));
        Utils.putShort(bytes, x);
        // u2 descriptor index (utf8)
        x = pool.put(new ConstantPool.UTF8Info("()I"));
        Utils.putShort(bytes, x);
        // u2 number of attributes
        Utils.putShort(bytes, (short)1);

        // MethodGenerator code = new MethodGenerator(this);
        byte[] byteCodes = codeBytes(); // first resolve code body

        // CODE ATTRIBUTE
        short codeIdx = pool.put(new ConstantPool.UTF8Info("Code"));
        Utils.putShort(bytes, codeIdx);
        Utils.putInt(bytes, 12 + byteCodes.length);
        Utils.putShort(bytes, maxStack());
        Utils.putShort(bytes, maxLocals());
        Utils.putInt(bytes, byteCodes.length);
        Utils.appendBytes(bytes, byteCodes);

        Utils.putShort(bytes, (short)0); // zero exceptions
        Utils.putShort(bytes, (short)0); // zero attributes

        return Utils.toBytes(bytes);
    }

    private byte[] codeBytes() {
        ArrayList<Byte> bytes = new ArrayList<>();
        for(OpCode code : opCodes) {
            Utils.appendBytes(bytes, code.resolveBytes());
        }
        return Utils.toBytes(bytes);
    }

    private short maxStack() {
        int stackHeight = 0;
        int maxStack = stackHeight;
        for(OpCode code : opCodes) {
            stackHeight = code.newStackHeight(stackHeight);
            if(stackHeight > maxStack)
                maxStack = stackHeight;
        }
        return (short) maxStack;
    }

    private short maxLocals() {
        // needs to be adjusted; starting number is number of arguments
        int maxLocals = 0;
        int localCount = 0;
        for(OpCode code : opCodes) {
            localCount = code.newLocalCount(localCount);
            if(localCount > maxLocals)
                maxLocals = localCount;
        }
        return (short) maxLocals;
    }

    void insertOpCode(OpCode code) {
        opCodes.add(code);
    }

    void printCode() {
        for(OpCode code : opCodes) {
            System.out.println(code);
        }
    }

    static abstract class OpCode {
        abstract byte[] resolveBytes();
        abstract int newStackHeight(int stackHeight);
        int newLocalCount(int localCount) {
            return localCount;
        }
    }

    static final class SimpleOpCode extends OpCode {
        final byte value;
        final int stackChange;
        final int localChange;
        final String name;

        SimpleOpCode(String name, byte value, int stackChange) {
            this(name, value, stackChange, 0);
        }

        SimpleOpCode(String name, byte value, int stackChange, int localChange) {
            this.value = value;
            this.name = name;
            this.stackChange = stackChange;
            this.localChange = localChange;
        }

        byte[] resolveBytes() {
            return new byte[]{value};
        }

        @Override
        int newLocalCount(int localCount) {
            return localCount + localChange;
        }

        @Override
        int newStackHeight(int stackHeight) {
            return stackHeight + stackChange;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final SimpleOpCode CODE_IADD = new SimpleOpCode("iadd", (byte)0x60, 1);
    static final SimpleOpCode CODE_IMUL = new SimpleOpCode("imul", (byte)0x68, 1);
    static final SimpleOpCode CODE_IRETURN = new SimpleOpCode("ireturn", (byte)0xAC, -1);

    static class IntConstOp extends OpCode {
        int val;
        IntConstOp(int val) {
            this.val = val;
        }

        byte[] resolveBytes() {
            if(-1 <= val && val <= 5) {
                // iconst_<i>
                return new byte[]{(byte)(val + 3)};
            } else {
                // ldc integer constant
                throw new RuntimeException("Can't push integer constant of " + val);
            }
        }

        @Override
        int newStackHeight(int stackHeight) {
            return stackHeight + 1;
        }

        @Override
        public String toString() {
            return "iconst " + val;
        }
    }

    abstract static class InvokeStatic extends OpCode {
        InvokeStatic(String className, String methodName, String methodType) {

        }
    }
}
