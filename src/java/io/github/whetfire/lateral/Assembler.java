package io.github.whetfire.lateral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import io.github.whetfire.lateral.ConstantPool.ConstantEntry;

public class Assembler {
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

    int paramCount;
    int maxLocals, maxStack;

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

    ArrayList<StackMapFrame> stackMapTable = new ArrayList<>();

    Assembler(int paramCount){
        this.paramCount = paramCount;
    }

    public byte[] assemble(ClassBuilder builder, Iterable codes) {
        ArrayList<Object> byteops = new ArrayList<>();

        maxStack = 0;
        int stackHeight = 0;
        maxLocals = paramCount;
        int localCount = paramCount;
        int bytecount = 0;
        for(Object op : codes) {
            System.out.println(stackHeight);
            System.out.println(op);
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
                LinkedList oplist = (LinkedList) op;
                Keyword kop = (Keyword)LinkedList.first(oplist);
                if(LABEL.equals(kop)) {
                    // store label
                    JumpLabel target = (JumpLabel)LinkedList.second(oplist);
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
                    byteops.add(op);
                    bytecount += 3;
                    if(!GOTO.equals(kop)) {
                        stackHeight --;
                    }
                    // set stack height at jump target
                    JumpLabel target = (JumpLabel)LinkedList.second(oplist);
                    target.stackHeight = stackHeight;
                } else if(ALOAD.equals(kop)) {
                    int idx = (Integer) LinkedList.second(oplist);
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
                    Object robj = LinkedList.second(oplist);
                    ConstantEntry resource = null;
                    if(robj instanceof String) {
                        resource = new ConstantPool.StringInfo((String) robj);
                    } else {
                        asmError(robj);
                    }

                    int index = builder.getPool().put(resource);
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
                            (String) LinkedList.second(oplist),
                            (String) LinkedList.third(oplist),
                            (String) LinkedList.fourth(oplist)
                    );
                    short x = builder.getPool().put(fieldRefInfo);
                    byteops.add((byte) 0xB2);
                    byteops.add((byte) ((x >> 8) & 0xFF));
                    byteops.add((byte) (x & 0xFF));
                    bytecount += 3;
                    stackHeight++;
                } else if(INVOKESTATIC.equals(kop)) {
                    ConstantPool.MethodRefInfo methodRefInfo = new ConstantPool.MethodRefInfo(
                            (String) LinkedList.second(oplist),
                            (String) LinkedList.third(oplist),
                            (String) LinkedList.fourth(oplist)
                    );
                    short x = builder.getPool().put(methodRefInfo);
                    byteops.add((byte) 0xB8);
                    byteops.add((byte) ((x >> 8) & 0xFF));
                    byteops.add((byte) (x & 0xFF));
                    bytecount += 3;
                    // stackHeight++;
                } else {
                    asmError(kop);
                }
            } else {
                asmError(op);
            }

            if(stackHeight > maxStack)
                maxStack = stackHeight;
        }

        System.out.println("bytes =====");
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
        Utils.putShort(bytes, classBuilder.getPool().put(ConstantPool.STACKMAP_NAME));
        Utils.putInt(bytes, -1); // length in bytes, calculate afterwards
        Utils.putShort(bytes, (short)stackMapTable.size()); // number of entries

        // TODO: replace with actual type checking
        byte[] objectVerification = new byte[3];
        objectVerification[0] = 7; // object info ID
        Utils.putShort(objectVerification, 1, classBuilder.getPool().put(new ConstantPool.ClassInfo("java/lang/Object")));

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
