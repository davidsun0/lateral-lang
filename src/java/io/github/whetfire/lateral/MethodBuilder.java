package io.github.whetfire.lateral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import io.github.whetfire.lateral.ConstantPool.ConstantEntry;

public class MethodBuilder {
    /*
    Each simple code is encoded as the stack change and bytecode stored in a single
    Integer. I'm too lazy to write out a whole new class and that would take so much boilerplate
    that I really don't want to write right now.
    0x 00 00 XX YY
    Where XX is the change in stack height
    Where YY is the JVM bytecode value
     */
    static HashMap<Keyword, Integer> simpleCodes;
    static Keyword LABEL = Keyword.makeKeyword("label");
    static Keyword LOCALLABEL = Keyword.makeKeyword("label");
    static Keyword IFNULL = Keyword.makeKeyword("ifnull");
    static Keyword IFNONNULL = Keyword.makeKeyword("ifnonnull");
    static Keyword GOTO = Keyword.makeKeyword("goto");

    static Keyword LDC = Keyword.makeKeyword("ldc");
    static Keyword GETSTATIC = Keyword.makeKeyword("getstatic");
    static Keyword INVOKESTATIC = Keyword.makeKeyword("invokestatic");

    static Keyword ALOAD = Keyword.makeKeyword("aload");
    static Keyword ASTORE = Keyword.makeKeyword("astore");
    static Keyword ARETURN = Keyword.makeKeyword("areturn");

    static Keyword ICONST = Keyword.makeKeyword("iconst");

    static {
        simpleCodes = new HashMap<>();
        simpleCodes.put(Keyword.makeKeyword("aconst_null"), (1 << 8) | 0x01);
        // simpleCodes.put(Keyword.makeKeyword("ireturn"), (1 << 8) | 0xAC);
        simpleCodes.put(Keyword.makeKeyword("areturn"), (-1 << 8) | 0xB0);
        simpleCodes.put(Keyword.makeKeyword("pop"), (-1 << 8) | 0x57);
        simpleCodes.put(Keyword.makeKeyword("dup"), (1 << 8) | 0x59);
    }

    static void asmError(Object op) {
        throw new RuntimeException("Can't assemble " + op);
    }

    static class StackMapFrame {
        int stackHeight;
        int localCount;
        int byteCodePosition;

        StackMapFrame(int stackHeight, int localCount, int byteCodePosition) {
            this.stackHeight = stackHeight;
            this.localCount = localCount;
            this.byteCodePosition = byteCodePosition;
        }

        public int getByteCodePosition() {
            return byteCodePosition;
        }

        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else {
                return (obj instanceof StackMapFrame &&
                        ((StackMapFrame) obj).stackHeight == stackHeight &&
                        ((StackMapFrame) obj).localCount == localCount &&
                        ((StackMapFrame) obj).byteCodePosition == byteCodePosition);
            }
        }
    }

    // immutable values
    private final Symbol name;
    private final int paramCount;
    // generated before assembly
    private ArrayList<Object> codes;

    // for use during assembly
    private int localCount, stackHeight, bytecount;
    private int maxLocals, maxStack;
    private ArrayList<StackMapFrame> stackMapTable;
    private ArrayList<Object> byteops;

    MethodBuilder(Symbol name, int paramCount){
        this.name = name;
        this.paramCount = paramCount;
        codes = new ArrayList<>();
        stackMapTable = new ArrayList<>();
    }

    public Symbol getName() {
        return name;
    }

    public Class<?>[] getParameterTypes() {
        Class<?>[] params = new Class<?>[paramCount];
        for(int i = 0; i < paramCount; i ++) {
            params[i] = Object.class;
        }
        return params;
    }

    public void insertOpCode(Object ... codes) {
        if(codes.length == 1) {
            this.codes.add(codes[0]);
        } else {
            this.codes.add(LinkedList.makeList(codes));
        }
    }

    public void printCodes() {
        for(Object code : codes) {
            System.out.println(code);
        }
    }

    public byte[] assembleMethod(ClassBuilder builder) {
        ConstantPool pool = builder.pool;
        ArrayList<Byte> bytes = new ArrayList<>();
        // u2 accessor flags
        // TODO: generate accessor flags
        Utils.putShort(bytes, (short)0x0009); // public static
        // u2 name index (utf8)
        short x = pool.put(new ConstantPool.UTF8Info(name.toString()));
        Utils.putShort(bytes, x);
        // u2 descriptor index (utf8)
        // TODO: generate descriptor index
        x = pool.put(new ConstantPool.UTF8Info(Lambda.makeMethodSignature(paramCount)));
        Utils.putShort(bytes, x);
        // u2 number of attributes
        // TODO: generate attributes
        Utils.putShort(bytes, (short)1);

        // CODE ATTRIBUTE
        // MethodBuilder assembler = new MethodBuilder(paramCount);
        byte[] byteCodes = assembleCode(builder);
        byte[] stackMapTable = makeStackMapTable(builder);
        Utils.putShort(bytes, pool.put(ConstantPool.CODE_NAME));
        Utils.putInt(bytes, 12 + byteCodes.length + stackMapTable.length);
        Utils.putShort(bytes, (short)maxStack);
        Utils.putShort(bytes, (short)maxLocals);
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

    /**
     * Compiles expressions of the form '(:opcode args ...) into JVM bytecode,
     * where :opcode is a Keyword containing the name of a JVM instruction and args
     * are the appropriate arguments to the opcode.
     * Simple op codes that do not need arguments are handled automatically by assembleCode.
     * @param builder ClassBuilder to which class references are resolved
     * @param compOp LinkedList containg the complexOp.
     */
    private void complexOp(ClassBuilder builder, LinkedList compOp) {
        Keyword kop = (Keyword)LinkedList.first(compOp);
        if(LABEL.equals(kop)) {
            // store label
            JumpLabel target = (JumpLabel)LinkedList.second(compOp);
            if(target.stackHeight < 0)
                asmError("backwards jump");
            stackHeight = target.stackHeight;
            stackMapTable.add(new StackMapFrame(stackHeight, localCount, bytecount));
            target.byteCodePosition = bytecount;
        } else if(LOCALLABEL.equals(kop)) {
            asmError(kop);
            //TODO
            // set locals
            // byteops.add(op);
        } else if(GOTO.equals(kop) || IFNONNULL.equals(kop) || IFNULL.equals(kop)) {
            byteops.add(compOp);
            bytecount += 3;
            if(!GOTO.equals(kop)) {
                stackHeight --;
            }
            // set stack height at jump target
            JumpLabel target = (JumpLabel)LinkedList.second(compOp);
            target.stackHeight = stackHeight;
        } else if(ALOAD.equals(kop)) {
            int idx = (Integer) LinkedList.second(compOp);
            if (idx < 4) {
                byteops.add((byte) (0x2A + idx));
                bytecount += 1;
            } else {
                byteops.add((byte) 0x19);
                byteops.add((byte) idx);
                bytecount += 2;
            }
            stackHeight++;
        } else if(LDC.equals(kop)) {
            Object robj = LinkedList.second(compOp);
            ConstantEntry resource = null;
            if(robj instanceof String) {
                resource = new ConstantPool.StringInfo((String) robj);
            } else {
                asmError(robj);
            }

            int index = builder.pool.put(resource);
            if(index < 256) {
                // ldc
                byteops.add((byte) 0x12);
                byteops.add((byte) index);
                bytecount += 2;
            } else {
                // ldc_w
                byteops.add((byte) 0x13);
                byteops.add((byte) ((index >> 8) & 0xFF));
                byteops.add((byte) (index & 0xFF));
                bytecount += 3;
            }
            stackHeight ++;
        } else if(GETSTATIC.equals(kop)) {
            ConstantPool.FieldRefInfo fieldRefInfo = new ConstantPool.FieldRefInfo(
                    (String) LinkedList.second(compOp),
                    (String) LinkedList.third(compOp),
                    (String) LinkedList.fourth(compOp)
            );
            short x = builder.pool.put(fieldRefInfo);
            byteops.add((byte) 0xB2);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            stackHeight++;
        } else if(INVOKESTATIC.equals(kop)) {
            ConstantPool.MethodRefInfo methodRefInfo = new ConstantPool.MethodRefInfo(
                    (String) LinkedList.second(compOp),
                    (String) LinkedList.third(compOp),
                    (String) LinkedList.fourth(compOp)
            );
            short x = builder.pool.put(methodRefInfo);
            byteops.add((byte) 0xB8);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            // stackHeight++;
            // TODO: generate stack height
        } else if(ICONST.equals(kop)) {
            Integer value = (Integer) LinkedList.second(compOp);
            if(-1 <= value && value <= 5) {
                byteops.add((byte)(value + 0x3));
                bytecount ++;
            } else {
                // TODO: fall back to ldc
                asmError(kop);
            }
            stackHeight ++;
        } else {
            asmError(kop);
        }
    }

    private byte[] assembleCode(ClassBuilder builder) {
        byteops = new ArrayList<>();

        maxStack = 0;
        stackHeight = 0;
        maxLocals = paramCount;
        localCount = paramCount;
        bytecount = 0;
        for(Object op : codes) {
            // System.out.println(stackHeight);
            // System.out.println(op);
            if(op instanceof Keyword) {
                Integer opcode = simpleCodes.get(op);
                if(opcode == null) {
                    asmError(op);
                } else {
                    // each simpleCode is encoded as (stack change << 8) | codevalue
                    byteops.add((byte)(opcode & 0xFF));
                    bytecount ++;
                    stackHeight += opcode >> 8;
                }
            } else if(op instanceof LinkedList && LinkedList.first((LinkedList) op) instanceof Keyword) {
                complexOp(builder, (LinkedList) op);
            } else {
                asmError(op);
            }

            if(stackHeight > maxStack)
                maxStack = stackHeight;
        }

        // System.out.println("bytes =====");
        ArrayList<Byte> bytes = new ArrayList<>(byteops.size());
        for(Object op : byteops) {
            // System.out.println(op);
            if(op instanceof Byte) {
                bytes.add((Byte)op);
            } else {
                LinkedList list = (LinkedList)op;
                Object optype = LinkedList.first(list);
                JumpLabel target = (JumpLabel)LinkedList.second(list);
                int offset = target.byteCodePosition - bytes.size();
                System.out.printf("%s %d %d%n", list, target.byteCodePosition, bytes.size());
                if (IFNONNULL.equals(optype)) {
                    bytes.add((byte)0xC7);
                } else if (IFNULL.equals(optype)) {
                    bytes.add((byte)0xC6);
                } else if (GOTO.equals(optype)) {
                    bytes.add((byte)0xA7);
                }
                bytes.add((byte)((offset >> 8) & 0xFF));
                bytes.add((byte)(offset & 0xFF));
            }
        }
        return Utils.toByteArray(bytes);
    }

    /**
     * Creates a StackMapTable Attribute
     * https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.4
     * @param classBuilder ClassBuilder to which this method belongs
     * @return The byte array representation of the StackMapTable
     */
    byte[] makeStackMapTable(ClassBuilder classBuilder) {
        if(stackMapTable.size() < 1) {
            return new byte[0];
        }

        // sort by bytecode position and remove duplicates
        stackMapTable.sort(Comparator.comparingInt(StackMapFrame::getByteCodePosition));
        ArrayList<StackMapFrame> uniqueFrameTable = new ArrayList<>(stackMapTable.size());
        uniqueFrameTable.add(stackMapTable.get(0));
        for(int i = 1; i < stackMapTable.size(); i ++) {
            if(!stackMapTable.get(i).equals(stackMapTable.get(i - 1)))
                uniqueFrameTable.add(stackMapTable.get(i));
        }
        stackMapTable = uniqueFrameTable;

        // write header
        ArrayList<Byte> bytes = new ArrayList<>();
        Utils.putShort(bytes, classBuilder.pool.put(ConstantPool.STACKMAP_NAME));
        Utils.putInt(bytes, -1); // length in bytes, calculate afterwards
        Utils.putShort(bytes, (short)stackMapTable.size()); // number of entries

        // TODO: replace with actual type checking
        byte[] objectVerification = new byte[3];
        objectVerification[0] = 7; // object info ID
        Utils.putShort(objectVerification, 1, classBuilder.pool.put(new ConstantPool.ClassInfo("java/lang/Object")));

        int lastLocal = paramCount;
        int lastPosition = -1;
        for(StackMapFrame stackMapFrame: stackMapTable) {
            int offset = stackMapFrame.byteCodePosition - lastPosition - 1;
            // System.out.println(stackMapFrame);
            if (stackMapFrame.stackHeight == 0 && stackMapFrame.localCount == lastLocal) {
                if (offset < 64) {
                    // same frame: ID 0-63
                    bytes.add((byte) offset);
                } else {
                    // same frame extended: ID 251
                    bytes.add((byte) 251); // frame id
                    Utils.putShort(bytes, (short) offset);
                }
            } else if (stackMapFrame.stackHeight == 1 && stackMapFrame.localCount == lastLocal) {
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
            } else if (stackMapFrame.stackHeight == 0 && 0 < lastLocal - stackMapFrame.localCount
                    && lastLocal - stackMapFrame.localCount < 4) {
                // chop frame: ID 248-250
                bytes.add((byte) (247 + (lastLocal - stackMapFrame.localCount)));
                Utils.putShort(bytes, (short) offset);
                // (lastLocal - jumpLabel.localCount) number of verification infos
                for (int i = 0; i < lastLocal - stackMapFrame.localCount; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            } else if (stackMapFrame.stackHeight == 0 && 0 < stackMapFrame.localCount - lastLocal
                    && stackMapFrame.localCount - lastLocal < 4) {
                // append frame: ID 252-254
                bytes.add((byte) (251 + (stackMapFrame.localCount - lastLocal)));
                Utils.putShort(bytes, (short)offset);
                // (jumpLabel.localCount - lastLocal) number of verification infos
                for (int i = 0; i < stackMapFrame.localCount - lastLocal; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            } else {
                // full frame: ID 255
                bytes.add((byte) 255);
                Utils.putShort(bytes, (short) offset);
                Utils.putShort(bytes, (short) stackMapFrame.localCount);
                for (int i = 0; i < stackMapFrame.localCount; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
                Utils.putShort(bytes, (short) stackMapFrame.stackHeight);
                for (int i = 0; i < stackMapFrame.stackHeight; i++) {
                    Utils.appendBytes(bytes, objectVerification);
                }
            }

            lastPosition = stackMapFrame.byteCodePosition;
            lastLocal = stackMapFrame.localCount;
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

    static class JumpLabel {
        int stackHeight;
        int byteCodePosition;

        JumpLabel() {
            stackHeight = -1;
            byteCodePosition = -1;
        }

        public boolean equals(Object obj) {
            if(this == obj){
                return true;
            } else if(obj instanceof JumpLabel) {
                JumpLabel lab = (JumpLabel)obj;
                return this.stackHeight == lab.stackHeight &&
                        this.byteCodePosition == lab.byteCodePosition;
            } else {
                return false;
            }
        }

        public String toString() {
            return String.format("[JumpLabel %X]", hashCode());
        }
    }

    static class LocalLabel {
        int localCount;
        int byteCodePosition;

        LocalLabel(int localCount) {
            this.localCount = localCount;
        }

        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else if(obj instanceof LocalLabel) {
                return this.localCount == ((LocalLabel) obj).localCount &&
                        this.byteCodePosition == ((LocalLabel) obj).byteCodePosition;
            } else {
                return false;
            }
        }
    }
}
