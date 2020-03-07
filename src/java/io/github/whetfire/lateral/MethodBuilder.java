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
    static Keyword LOCALLABEL = Keyword.makeKeyword("locallabel");
    static Keyword IFNULL = Keyword.makeKeyword("ifnull");
    static Keyword IFNONNULL = Keyword.makeKeyword("ifnonnull");
    static Keyword GOTO = Keyword.makeKeyword("goto");

    static Keyword LDC = Keyword.makeKeyword("ldc");
    static Keyword GETSTATIC = Keyword.makeKeyword("getstatic");

    static Keyword INVOKESTATIC = Keyword.makeKeyword("invokestatic");
    static Keyword INVOKESPECIAL = Keyword.makeKeyword("invokespecial");
    static Keyword INVOKEVIRTUAL = Keyword.makeKeyword("invokevirtual");

    static Keyword ALOAD = Keyword.makeKeyword("aload");
    static Keyword ASTORE = Keyword.makeKeyword("astore");
    static Keyword ARETURN = Keyword.makeKeyword("areturn");

    static Keyword ICONST = Keyword.makeKeyword("iconst");
    static Keyword ACONST_NULL = Keyword.makeKeyword("aconst_null");

    static Keyword NEW = Keyword.makeKeyword("new");
    static Keyword CHECKCAST = Keyword.makeKeyword("checkcast");

    static {
        simpleCodes = new HashMap<>();
        simpleCodes.put(ACONST_NULL, (1 << 8) | 0x01);
        // simpleCodes.put(Keyword.makeKeyword("ireturn"), (1 << 8) | 0xAC);
        simpleCodes.put(ARETURN, (-1 << 8) | 0xB0);
        simpleCodes.put(Keyword.makeKeyword("pop"), (-1 << 8) | 0x57);
        simpleCodes.put(Keyword.makeKeyword("dup"), (1 << 8) | 0x59);
    }

    static void asmError(Object op) {
        throw new RuntimeException("Can't assemble " + op);
    }

    //TODO: find a better home for this method
    static int countArguments(String internalName) {
        int count = 0;
        int idx = 1;
        char c;
        boolean inClass = false;
        // JVM field descriptors
        // https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.3.2
        while(')' != (c = internalName.charAt(idx))) {
            if(!inClass) {
                switch (c) {
                    // TODO: handle double and long (they need two arg slots)
                    case 'L': // start of a class
                        inClass = true;
                    case 'B': // byte
                    case 'C': // char
                    // case 'D': // double
                    case 'F': // float
                    case 'I': // int
                    // case 'J': // long
                    case 'S': // short
                    case 'Z': // bool
                        count ++;
                    case '[':
                        // each '[' only adds dimension, doesn't change arg counts
                        break;
                    default:
                        // error?
                        throw new RuntimeException();
                }
            } else if(c == ';'){
                inClass = false;
            }
            idx ++;
        }
        return count;
    }

    static class StackMapFrame {
        int stackHeight;
        Class<?>[] virtualStack;
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
    private final boolean isMacro, isVarargs;
    // generated before assembly
    private ArrayList<Object> codes;

    // for use during assembly
    private int localCount, stackHeight, bytecount;
    private int maxLocals, maxStack;
    private HashMap<Symbol, StackMapFrame> jumpMap;
    private ArrayList<Class<?>> virtualStack;
    // private ArrayList<Class<?>> virtualLocals;
    private ArrayList<StackMapFrame> stackMapTable;
    private ArrayList<Object> byteops;

    MethodBuilder(Symbol name, int paramCount) {
        this(name, paramCount, false, false);
    }

    MethodBuilder(Symbol name, int paramCount, boolean isMacro, boolean isVarargs){
        this.name = name;
        this.paramCount = paramCount;
        this.isMacro = isMacro;
        this.isVarargs = isVarargs;

        jumpMap = new HashMap<>();
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
        short attributeCount = 1;
        if(isMacro || isVarargs)
            attributeCount ++;
        Utils.putShort(bytes, attributeCount);

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

        // attributes of the code attributes
        if(stackMapTable.length == 0) {
            Utils.putShort(bytes, (short)0);
        } else {
            Utils.putShort(bytes, (short)1);
            Utils.appendBytes(bytes, stackMapTable);
        }

        if(isMacro || isVarargs) {
            byte[] annotations = assembleAnnotations(builder);
            Utils.appendBytes(bytes, annotations);
        }

        return Utils.toByteArray(bytes);
    }

    public byte[] assembleAnnotations(ClassBuilder builder) {
        ArrayList<Byte> bytes = new ArrayList<>();
        short x = builder.pool.put(new ConstantPool.UTF8Info("RuntimeVisibleAnnotations"));

        Utils.putShort(bytes, x);
        // length of annotations, to be calculated at the end
        Utils.putInt(bytes, -1);

        // one annotation
        short annotationCount = 0;
        if(isVarargs)
            annotationCount ++;
        if(isMacro)
            annotationCount ++;
        Utils.putShort(bytes, annotationCount);

        if(isMacro) {
            x = builder.pool.put(new ConstantPool.UTF8Info("Lio/github/whetfire/lateral/Macro;"));
            Utils.putShort(bytes, x);
            // no values held by varargs annotation
            Utils.putShort(bytes, (byte) 0);
        }

        if(isVarargs) {
            // varargs annotation
            x = builder.pool.put(new ConstantPool.UTF8Info("Lio/github/whetfire/lateral/Varargs;"));
            Utils.putShort(bytes, x);
            // no values held by varargs annotation
            Utils.putShort(bytes, (byte) 0);
        }

        // calculate byte length, ignoring first six bytes
        int byteLength = bytes.size() - 6;
        bytes.set(2, (byte)((byteLength >> 24) & 0xFF));
        bytes.set(3, (byte)((byteLength >> 16) & 0xFF));
        bytes.set(4, (byte)((byteLength >> 8) & 0xFF));
        bytes.set(5, (byte)(byteLength & 0xFF));
        return Utils.toByteArray(bytes);
    }

    /**
     * Compiles expressions of the form (:opcode args ...) into JVM bytecode,
     * where :opcode is a Keyword containing the name of a JVM instruction and args
     * are the appropriate arguments to the opcode.
     * Simple op codes that do not need arguments are handled automatically by assembleCode.
     * @param builder ClassBuilder to which class references are resolved
     * @param compOp Sequence containing the complexOp.
     */
    private void complexOp(ClassBuilder builder, Sequence compOp) {
        Keyword kop = (Keyword) compOp.first();
        if(LABEL.equals(kop)) {
            // store label
            Symbol targetSym = (Symbol) compOp.second();
            StackMapFrame target = jumpMap.get(targetSym);
            if(target == null || target.stackHeight < 0)
                asmError("backwards jump");
            stackHeight = target.stackHeight;

            target.localCount = localCount;
            target.byteCodePosition = bytecount;
            stackMapTable.add(target);
        } else if(LOCALLABEL.equals(kop)) {
            localCount = (Integer) compOp.second();
            stackMapTable.add(new StackMapFrame(stackHeight, localCount, bytecount));
        } else if(GOTO.equals(kop) || IFNONNULL.equals(kop) || IFNULL.equals(kop)) {
            // defer assembling jumps to second pass
            byteops.add(compOp);
            bytecount += 3;
            if(!GOTO.equals(kop)) {
                // conditional jumps pop off argument
                stackHeight --;
            }
            // set stack height at jump target

            Symbol targetSym = (Symbol) compOp.second();
            StackMapFrame target = new StackMapFrame(stackHeight, -1, -1);
            jumpMap.put(targetSym, target);
            // asmError(compOp);
        } else if(ALOAD.equals(kop)) {
            int idx = (Integer) compOp.second();
            if (idx < 4) {
                // specific codes for aload_0 to aload_4
                byteops.add((byte) (0x2A + idx));
                bytecount ++;
            } else {
                // general aload
                byteops.add((byte) 0x19);
                byteops.add((byte) idx);
                bytecount += 2;
            }
            stackHeight++;
        } else if(ASTORE.equals(kop)) {
            // all astore opcodes are exactly 0x21 more than aload
            // but it's probably better to leave them separate for readability
            int idx = (Integer) compOp.second();
            if(idx < 4) {
                // specific codes for astore_0 to astore_4
                byteops.add((byte) (0x4B + idx));
                bytecount ++;
            } else {
                // general astore
                byteops.add((byte) 0x3A);
                byteops.add((byte) idx);
                bytecount += 2;
            }
            stackHeight --;
        } else if(LDC.equals(kop)) {
            Object robj = compOp.second();
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
            byteops.add((byte) 0xB2);
            ConstantPool.FieldRefInfo fieldRefInfo = new ConstantPool.FieldRefInfo(
                    (String) compOp.second(),
                    (String) compOp.third(),
                    (String) compOp.fourth()
            );
            short x = builder.pool.put(fieldRefInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            stackHeight++;
        } else if(INVOKESTATIC.equals(kop)) {
            // TODO: merge all the invokes together (except for invokedynamic?)
            byteops.add((byte) 0xB8);
            String methodType = (String) compOp.fourth();
            ConstantPool.MethodRefInfo methodRefInfo = new ConstantPool.MethodRefInfo(
                    (String) compOp.second(),
                    (String) compOp.third(),
                    methodType
            );
            short x = builder.pool.put(methodRefInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            // TODO: generate stack height in a better way
            // to find the new stack height, subtract number of arguments
            stackHeight -= countArguments(methodType);
            // add the returned object to the stack (if it isn't void)
            if (methodType.charAt(methodType.length() - 1) != 'V')
                stackHeight++;
        } else if(INVOKESPECIAL.equals(kop)) {
            // same as invokestatic
            byteops.add((byte) 0xB7);
            String methodType = (String) compOp.fourth();
            ConstantPool.MethodRefInfo methodRefInfo = new ConstantPool.MethodRefInfo(
                    (String) compOp.second(),
                    (String) compOp.third(),
                    methodType
            );
            short x = builder.pool.put(methodRefInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            // TODO: generate stack height in a better way
            // to find the new stack height, subtract number of arguments
            stackHeight -= countArguments(methodType);
            // add the returned object to the stack (if it isn't void)
            if (methodType.charAt(methodType.length() - 1) != 'V')
                stackHeight++;
        } else if(INVOKEVIRTUAL.equals(kop)) {
            // same as invokestatic
            byteops.add((byte) 0xB6);
            String methodType = (String) compOp.fourth();
            ConstantPool.MethodRefInfo methodRefInfo = new ConstantPool.MethodRefInfo(
                    (String) compOp.second(),
                    (String) compOp.third(),
                    methodType
            );
            short x = builder.pool.put(methodRefInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            // TODO: generate stack height in a better way
            // to find the new stack height, subtract number of arguments
            stackHeight -= countArguments(methodType);
            // add the returned object to the stack (if it isn't void)
            if (methodType.charAt(methodType.length() - 1) != 'V')
                stackHeight++;
        } else if(ICONST.equals(kop)) {
            int value = (Integer) compOp.second();
            if (-1 <= value && value <= 5) {
                byteops.add((byte) (value + 0x3));
                bytecount++;
            } else {
                // TODO: fall back to ldc
                asmError(kop);
            }
            stackHeight++;
        } else if(NEW.equals(kop)) {
            byteops.add((byte) 0xBB);
            ConstantPool.ClassInfo classInfo = new ConstantPool.ClassInfo((String) compOp.second());
            short x = builder.pool.put(classInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            stackHeight++;
        } else if(CHECKCAST.equals(kop)) {
            byteops.add((byte) 0xC0);
            ConstantPool.ClassInfo classInfo = new ConstantPool.ClassInfo((String) compOp.second());
            short x = builder.pool.put(classInfo);
            byteops.add((byte) ((x >> 8) & 0xFF));
            byteops.add((byte) (x & 0xFF));
            bytecount += 3;
            // stack height stays the same, checkcast only changes the type of the top object
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
            } else if(op instanceof Sequence && ((Sequence) op).first() instanceof Keyword) {
                complexOp(builder, (Sequence) op);
            } else {
                asmError(op);
            }

            if(stackHeight > maxStack)
                maxStack = stackHeight;
            if(localCount > maxLocals)
                maxLocals = localCount;
        }

        ArrayList<Byte> bytes = new ArrayList<>(byteops.size());
        for(Object op : byteops) {
            if(op instanceof Byte) {
                bytes.add((Byte)op);
            } else {
                Sequence list = (Sequence) op;
                Object optype = list.first();

                Symbol targetSym = (Symbol) list.second();
                StackMapFrame target = jumpMap.get(targetSym);
                int offset = target.byteCodePosition - bytes.size();
                // System.out.printf("%s %d %d%n", list, target.byteCodePosition, bytes.size());

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
}
