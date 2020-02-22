package io.github.whetfire.lateral;

import java.util.ArrayList;

public class MethodBuilder {
    private ArrayList<Object> byteCodes = new ArrayList<>();
    private short maxStack, maxLocals;
    private int argCount;
    private Symbol name;
    ClassBuilder parentClass;

    static class StackMapFrame {
        int stackHeight;
        int localCount;
        int byteCodePosition;

        StackMapFrame(Assembler.JumpLabel label, int localCount) {
            this.stackHeight = label.stackHeight;
            this.byteCodePosition = label.byteCodePosition;
            this.localCount = localCount;
        }

        StackMapFrame(Assembler.LocalLabel label, int stackHeight) {
            this.localCount = label.localCount;
            this.byteCodePosition = label.byteCodePosition;
            this.stackHeight = stackHeight;
        }

        public int getByteCodePosition() {
            return byteCodePosition;
        }

        public String toString() {
            return String.format("StackMapFrame: s %d, l %d, bcp %d", stackHeight, localCount, byteCodePosition);
        }
    }

    MethodBuilder(ClassBuilder builder) {
        byteCodes = new ArrayList<>();
        // stackFrameTable = new ArrayList<>();
        parentClass = builder;
    }

    public byte[] resolveBytes() {
        ConstantPool pool = parentClass.getPool();
        ArrayList<Byte> bytes = new ArrayList<>();
        // u2 accessor flags
        // TODO: generate accessor flags
        Utils.putShort(bytes, (short)0x0009); // public static
        // u2 name index (utf8)
        short x = pool.put(new ConstantPool.UTF8Info(name.toString()));
        Utils.putShort(bytes, x);
        // u2 descriptor index (utf8)
        // TODO: generate descriptor index
        x = pool.put(new ConstantPool.UTF8Info(Compiler.methodSignature(argCount)));
        Utils.putShort(bytes, x);
        // u2 number of attributes
        // TODO: generate attributes
        Utils.putShort(bytes, (short)1);

        // CODE ATTRIBUTE
        Assembler assembler = new Assembler(argCount);
        byte[] byteCodes = assembler.assemble(parentClass, this.byteCodes);
        byte[] stackMapTable = assembler.makeStackMapTable(parentClass);
        Utils.putShort(bytes, pool.put(ConstantPool.CODE_NAME));
        Utils.putInt(bytes, 12 + byteCodes.length + stackMapTable.length);
        Utils.putShort(bytes, (short)assembler.maxStack);
        Utils.putShort(bytes, (short)assembler.maxLocals);
        Utils.putInt(bytes, byteCodes.length);
        Utils.appendBytes(bytes, byteCodes);

        Utils.putShort(bytes, (short)0); // zero exceptions

        if(stackMapTable.length == 0) {
            Utils.putShort(bytes, (short)0); // zero attributes
        } else {
            Utils.putShort(bytes, (short)1);
            Utils.appendBytes(bytes, stackMapTable);
        }

        return Utils.toByteArray(bytes);
    }

    void insertOpCode(Object ... codes) {
        if(codes.length == 1) {
            byteCodes.add(codes[0]);
        } else {
            byteCodes.add(LinkedList.makeList(codes));
        }
    }

    public void setArgCount(int argCount) {
        this.argCount = argCount;
    }

    public void setName(Symbol name) {
        this.name = name;
    }

    void printCode() {
        for(Object code : byteCodes) {
            System.out.println(code);
        }
    }
}
