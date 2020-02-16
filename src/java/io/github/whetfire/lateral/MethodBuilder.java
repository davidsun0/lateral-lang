package io.github.whetfire.lateral;

import java.util.ArrayList;
import java.util.Comparator;

import io.github.whetfire.lateral.ByteCodes.*;

public class MethodBuilder {
    ArrayList<ByteCode> byteCodes;
    ArrayList<Label> stackFrameTable;
    short maxStack, maxLocals;
    ClassBuilder parentClass;

    MethodBuilder(ClassBuilder builder) {
        byteCodes = new ArrayList<>();
        stackFrameTable = new ArrayList<>();
        parentClass = builder;
    }

    public byte[] resolveBytes() {
        ConstantPool pool = parentClass.getPool();
        ArrayList<Byte> bytes = new ArrayList<>();
        // u2 accessor flags
        // TODO: generate accessor flags
        Utils.putShort(bytes, (short)0x0009); // public static
        // u2 name index (utf8)
        // TODO: generate function name
        short x = pool.put(new ConstantPool.UTF8Info("myFunction"));
        Utils.putShort(bytes, x);
        // u2 descriptor index (utf8)
        // TODO: generate descriptor index
        x = pool.put(new ConstantPool.UTF8Info("()Ljava/lang/Object;"));
        Utils.putShort(bytes, x);
        // u2 number of attributes
        // TODO: generate attributes
        Utils.putShort(bytes, (short)1);

        // CODE ATTRIBUTE
        byte[] byteCodes = assemble();
        byte[] stackMapTable = makeStackMapTable(parentClass);
        Utils.putShort(bytes, pool.put(ConstantPool.CODE_NAME));
        Utils.putInt(bytes, 12 + byteCodes.length + stackMapTable.length);
        Utils.putShort(bytes, maxStack);
        Utils.putShort(bytes, maxLocals);
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

    void insertOpCode(ByteCode code) {
        byteCodes.add(code);
    }

    private byte[] assemble() {
        int maxStack = 0;
        int maxLocals = 0; // TODO: set to argument count

        // first assembly pass: resolve labels
        int localCount = 0;
        int stackHeight = 0;
        int offset = 0;
        for(ByteCode code : byteCodes) {
            /*
            resolve labels first because jump / labels depend on
            stack / offset values at the start of the instruction
             */
            if (code instanceof Jump) {
                ((Jump) code).setCurrentOffset(offset);
                Label target = ((Jump) code).target;
                stackFrameTable.add(target);
                target.setValues(code.newStackHeight(stackHeight), localCount);
                // System.out.printf("position: %d, locals: %d, stack: %d%n", target.byteCodePosition, target.localCount, target.stackHeight);
            } else if (code instanceof Label) {
                ((Label) code).setByteCodePosition(offset);
            }

            System.out.printf("[%d sh]%n", stackHeight);
            // calculate stack / local usage
            stackHeight = code.newStackHeight(stackHeight);
            localCount = code.newLocalCount(localCount);
            if(stackHeight > maxStack)
                maxStack = stackHeight;
            if(localCount > maxLocals)
                maxLocals = localCount;
            System.out.print(offset + ": ");
            System.out.println(code);
            offset += code.byteLength();
        }

        // second pass: generate bytes
        ArrayList<Byte> bytes = new ArrayList<>();
        for(ByteCode code : byteCodes) {
            Utils.appendBytes(bytes, code.resolveBytes(parentClass));
        }
        this.maxLocals = (short)maxLocals;
        this.maxStack = (short)maxStack;
        return Utils.toByteArray(bytes);
    }

    /**
     * Creates a StackMapTable Attribute
     * https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.4
     * @param classBuilder ClassBuilder to which this method belongs
     * @return The byte array representation of the StackMapTable
     */
    private byte[] makeStackMapTable(ClassBuilder classBuilder) {
        if(stackFrameTable.size() < 1) {
            return new byte[0];
        }

        // sort by bytecode position and remove duplicates
        stackFrameTable.sort(Comparator.comparingInt(Label::getByteCodePosition));
        {
            int i = 1;
            while (i < stackFrameTable.size()) {
                if (stackFrameTable.get(i) == stackFrameTable.get(i - 1)) {
                    stackFrameTable.remove(i);
                } else {
                    i++;
                }
            }
            for(Label frame : stackFrameTable) {
                System.out.println(frame);
            }
        }

        // write header
        ArrayList<Byte> bytes = new ArrayList<>();
        Utils.putShort(bytes, classBuilder.getPool().put(ConstantPool.STACKMAP_NAME));
        Utils.putInt(bytes, -1); // length in bytes, calculate afterwards
        Utils.putShort(bytes, (short)stackFrameTable.size()); // number of entries

        // TODO: replace with actual type checking
        byte[] objectVerification = new byte[3];
        objectVerification[0] = 7; // object info ID
        Utils.putShort(objectVerification, 1, classBuilder.getPool().put(new ConstantPool.ClassInfo("java/lang/Object")));

        int lastLocal = 0; // TODO: set to argument count
        int lastPosition = -1;
        for(Label label : stackFrameTable) {
            System.out.printf("position: %d, locals: %d, stack: %d%n", label.byteCodePosition, label.localCount, label.stackHeight);
            int offset = label.byteCodePosition - lastPosition - 1;
            if (label.stackHeight == 0 && label.localCount == lastLocal) {
                if (offset < 64) {
                    // same frame: ID 0-63
                    bytes.add((byte) offset);
                } else {
                    // same frame extended: ID 251
                    bytes.add((byte) 251); // frame id
                    Utils.putShort(bytes, (short) offset);
                }
            } else if (label.stackHeight == 1 && label.localCount == lastLocal) {
                if (offset < 64) {
                    // same locals, one stack item: ID 64-127
                    bytes.add((byte) (64 + offset));
                    // one verification info
                    Utils.appendBytes(bytes, objectVerification);
                } else {
                    // same locals, one stack item extended
                    bytes.add((byte) 247); // frame id
                    Utils.putShort(bytes, (short) offset);
                    // one verification info
                    Utils.appendBytes(bytes, objectVerification);
                }
            } else if (label.stackHeight == 0 && 0 < lastLocal - label.localCount && lastLocal - label.localCount < 4) {
                // chop frame: ID 248-250
                bytes.add((byte) (247 + (lastLocal - label.localCount)));
                Utils.putShort(bytes, (short) offset);
                // (lastLocal - label.localCount) number of verification infos
                for (int i = 0; i < lastLocal - label.localCount; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            } else if (label.stackHeight == 0 && 0 < label.localCount - lastLocal && label.localCount - lastLocal < 4) {
                // append frame: ID 252-254
                bytes.add((byte) (251 + (label.localCount - lastLocal)));
                // (label.localCount - lastLocal) number of verification infos
                for (int i = 0; i < label.localCount - lastLocal; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            } else {
                // full frame: ID 255
                bytes.add((byte) 255);
                Utils.putShort(bytes, (short) offset);
                Utils.putShort(bytes, (short) label.localCount);
                for (int i = 0; i < label.localCount; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
                Utils.putShort(bytes, (short) label.stackHeight);
                for (int i = 0; i < label.stackHeight; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            }

            lastPosition = label.byteCodePosition;
            lastLocal = label.localCount;
        }

        // calculate byte length
        // byte length ignores first six bytes
        int byteLength = bytes.size() - 6;
        bytes.set(2, (byte)((byteLength >> 24) & 0xFF));
        bytes.set(3, (byte)((byteLength >> 16) & 0xFF));
        bytes.set(4, (byte)((byteLength >> 8) & 0xFF));
        bytes.set(5, (byte)(byteLength & 0xFF));
        return Utils.toByteArray(bytes);
    }

    void printCode() {
        for(ByteCode code : byteCodes) {
            System.out.println(code);
        }
    }

}
