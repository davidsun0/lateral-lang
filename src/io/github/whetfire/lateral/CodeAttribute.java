package io.github.whetfire.lateral;

import java.util.ArrayList;

public class CodeAttribute {
    int offset;
    ArrayList<OpCode> opCodes;
    static final int LOAD1 = 1;

    CodeAttribute () {
        opCodes = new ArrayList<>();
    }

    byte[] resolveBytes(ConstantPool pool) {
        /*
        Code attribute
            u2 "Code" index
            u4 attribute size
            u2 max stack
            u2 max locals
            u4 bytecode length
            [bytecode]
            u2 number of exceptions
            [exceptions]
            u2 number of attributes

            StackMapTable attribute
                u2 "StackMapTable" index
                u2 attribute size
                u2 number of entries
                [entries]
        */
        ArrayList<Byte> bytes = new ArrayList<>();
        short codeIdx = pool.put(new ConstantPool.UTF8Info("Code"));
        System.out.println(codeIdx);
        Utils.putShort(bytes, codeIdx);
        Utils.putInt(bytes, 13);
        Utils.putShort(bytes, (short)0); // max stack
        Utils.putShort(bytes, (short)0); // max locals
        Utils.putInt(bytes, 1); // 1 byte of code
        bytes.add((byte)0xB1);
        Utils.putShort(bytes, (short)0); // zero exceptions
        Utils.putShort(bytes, (short)0); // zero attributes
        return Utils.arrayListToBytes(bytes);
    }

    short maxStack() {
        return -1;
    }

    short maxLocals() {
        return -1;
    }



    static abstract class OpCode {

    }

    class Label {
        int offset;
        Label(){
            offset = CodeAttribute.this.offset;
        }
    }

    static class Push extends OpCode {
       int argIndex;
    }

    static class InvokeStatic extends OpCode {
        InvokeStatic(String className, String methodName, String methodType) {

        }
    }
}
