package io.github.whetfire.lateral;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Assembler {
    static final private int JAVA_VERSION = 55; // 55.0 = Java 11

    static Keyword LABEL = Keyword.makeKeyword("label");

    static Keyword ICONST = Keyword.makeKeyword("iconst");
    static Keyword LDC = Keyword.makeKeyword("ldc");
    static Keyword ARETURN = Keyword.makeKeyword("areturn");
    static Keyword RETURN = Keyword.makeKeyword("return");
    static Keyword IRETURN = Keyword.makeKeyword("ireturn");
    static Keyword ALOAD = Keyword.makeKeyword("aload");
    static Keyword ASTORE = Keyword.makeKeyword("astore");
    static Keyword AALOAD = Keyword.makeKeyword("aaload");
    static Keyword AASTORE = Keyword.makeKeyword("aastore");
    static Keyword ARRAYLENGTH = Keyword.makeKeyword("arraylength");
    static Keyword ATHROW = Keyword.makeKeyword("athrow");

    static Keyword CHECKCAST = Keyword.makeKeyword("checkcast");
    static Keyword NEW = Keyword.makeKeyword("new");
    static Keyword ANEWARRAY = Keyword.makeKeyword("anewarray");

    static Keyword DUP = Keyword.makeKeyword("dup");

    static Keyword INVOKESTATIC = Keyword.makeKeyword("invokestatic");
    static Keyword INVOKEVIRTUAL = Keyword.makeKeyword("invokevirtual");
    static Keyword INVOKESPECIAL = Keyword.makeKeyword("invokespecial");
    static Keyword INVOKEINTERFACE = Keyword.makeKeyword("invokeinterface");

    static Keyword INVOKEDYNAMIC = Keyword.makeKeyword("invokedynamic");

    static Keyword IF_ICMPNE = Keyword.makeKeyword("if_icmpne");

    static Keyword GETSTATIC = Keyword.makeKeyword("getstatic");
    static Keyword PUTSTATIC = Keyword.makeKeyword("putstatic");
    static Keyword GETFIELD = Keyword.makeKeyword("getfield");
    static Keyword PUTFIELD = Keyword.makeKeyword("putfield");

    // TODO: convert to immutable map with Map.of
    private static Map<Keyword, Integer> simpleOpMap;
    private static Map<Keyword, Integer> jumpOpMap;
    private static Map<Keyword, Integer> opMap;

    static {
        simpleOpMap = Map.ofEntries(
            Map.entry(ARETURN, Opcodes.ARETURN),
            Map.entry(RETURN, Opcodes.RETURN),
            Map.entry(IRETURN, Opcodes.IRETURN),
            Map.entry(DUP, Opcodes.DUP),
            Map.entry(AALOAD, Opcodes.AALOAD),
            Map.entry(AASTORE, Opcodes.AASTORE),
            Map.entry(ARRAYLENGTH, Opcodes.ARRAYLENGTH),
            Map.entry(ATHROW, Opcodes.ATHROW),
            Map.entry(Keyword.makeKeyword("dup2"), Opcodes.DUP2),
            Map.entry(Keyword.makeKeyword("dup_x1"), Opcodes.DUP_X1),
            Map.entry(Keyword.makeKeyword("pop"), Opcodes.POP),
            Map.entry(Keyword.makeKeyword("swap"), Opcodes.SWAP),
            Map.entry(Keyword.makeKeyword("isub"), Opcodes.ISUB),
            Map.entry(Keyword.makeKeyword("iadd"), Opcodes.IADD)
        );

        jumpOpMap = Map.ofEntries(
            Map.entry(Keyword.makeKeyword("ifnull"), Opcodes.IFNULL),
            Map.entry(Keyword.makeKeyword("ifnonnull"), Opcodes.IFNULL),
            Map.entry(Keyword.makeKeyword("ifne"), Opcodes.IFNE),
            Map.entry(Keyword.makeKeyword("ifeq"), Opcodes.IFEQ),
            Map.entry(Keyword.makeKeyword("ifgt"), Opcodes.IFGT),
            Map.entry(Keyword.makeKeyword("iflt"), Opcodes.IFLT),
            Map.entry(Keyword.makeKeyword("if_icmpgt"), Opcodes.IF_ICMPGT),
            Map.entry(IF_ICMPNE, Opcodes.IF_ICMPNE),
            Map.entry(Keyword.makeKeyword("goto"), Opcodes.GOTO)
        );

        opMap = Map.ofEntries(
            Map.entry(INVOKESTATIC, Opcodes.INVOKESTATIC),
            Map.entry(INVOKEVIRTUAL, Opcodes.INVOKEVIRTUAL),
            Map.entry(INVOKESPECIAL, Opcodes.INVOKESPECIAL),
            Map.entry(INVOKEINTERFACE, Opcodes.INVOKEINTERFACE),
            Map.entry(PUTSTATIC, Opcodes.PUTSTATIC),
            Map.entry(PUTFIELD, Opcodes.PUTFIELD),
            Map.entry(GETSTATIC, Opcodes.GETSTATIC),
            Map.entry(GETFIELD, Opcodes.GETFIELD),
            Map.entry(CHECKCAST, Opcodes.CHECKCAST),
            Map.entry(ANEWARRAY, Opcodes.ANEWARRAY),
            Map.entry(NEW, Opcodes.NEW)
        );
    }

    private static int classNum = 0;

    static Class<?>[] getParameterClasses(int count) {
        Class<?>[] classes = new Class[count];
        for(int i = 0; i < count; i ++) {
            classes[i] = Object.class;
        }
        return classes;
    }

    static String getMethodDescriptor(Class<?> ... classes) {
        if(classes == null || classes.length < 1)
            throw new RuntimeException("malformed method descriptor");
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for(int i = 0; i < classes.length - 1; i ++) {
            sb.append(Type.getDescriptor(classes[i]));
        }
        sb.append(')');
        sb.append(Type.getDescriptor(classes[classes.length - 1]));
        return sb.toString();
    }

    static void visitEmptyConstructor(ClassWriter classWriter) {
        MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Function.class),
                "<init>","()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitCapturingConstructor(ClassWriter classWriter, ArrayList<Symbol> symbols) {

    }

    static void visitToString(ClassWriter classWriter, String string) {
        MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
                "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(string);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitApplier(ClassWriter classWriter, String className,
                             String invokeDescriptor, int paramCount, boolean isVarargs) {
        /*
        Object apply(Object .. args) {
            if(args.length == n)
                invoke(args[0] ... args[n]);
            else
                throw new RuntimeException();
        }
         */
        /*
        // for varargs
        if(args.length >= n - 1)
            invoke(args[0] ... ArraySequence.makeList(n-1, args));
         */
        MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS,
                "apply", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        Label excep = new Label(); // when throwing exceptions

        // if(args.length == n)
        // local variable 0
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        // TODO: optimization when paramCount < 4
        if(isVarargs) {
            mv.visitLdcInsn(paramCount - 1);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, excep);
        } else {
            mv.visitLdcInsn(paramCount);
            mv.visitJumpInsn(Opcodes.IF_ICMPNE, excep);
        }

        //      this.invoke(args[0] ... args[n]);
        // load "this" from args
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        // load paramCount elements from the argument array
        for(int i = 0; i < paramCount - 1; i ++) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(i);
            mv.visitInsn(Opcodes.AALOAD);
        }

        if(paramCount > 0) {
            if (isVarargs) {
                mv.visitLdcInsn(paramCount);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        Type.getInternalName(ArraySequence.class),
                        "makeList",
                        MethodType.methodType(Sequence.class, int.class, Object[].class).toMethodDescriptorString(),
                        false);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitLdcInsn(paramCount - 1);
                mv.visitInsn(Opcodes.AALOAD);
            }
        }
        // param count + 1 for return type, all of Object.class
        Class<?>[] classes = getParameterClasses(paramCount + 1);
        if(isVarargs)
            classes[classes.length - 2] = Sequence.class;
        String descriptor = getMethodDescriptor(classes);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke", descriptor, false);
        mv.visitInsn(Opcodes.ARETURN);

        // else
        //      throw new RuntimeException();
        mv.visitLabel(excep);
        mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(UnsupportedOperationException.class));
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                Type.getInternalName(UnsupportedOperationException.class),
                "<init>",
                "()V",
                false
        );
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(paramCount + 2, 1);
        mv.visitEnd();
    }

    /**
     * Generates a virtual method which returns a constant boolean value
     * @param classWriter The class to which the method belongs
     * @param name The name of the method
     * @param value The constant value to return
     */
    static void visitReturnBoolean(ClassWriter classWriter, String name, boolean value) {
        MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
                name, "()Z", null, null);
        mv.visitCode();
        mv.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitParamCount(ClassWriter classWriter, int paramc) {
        MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
                "paramCount", "()I", null, null);
        mv.visitCode();
        if (paramc <= 4) {
            mv.visitInsn(Opcodes.ICONST_0 + paramc);
        } else {
            mv.visitLdcInsn(paramc);
        }
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
    }

    static void visitOpCodes(MethodVisitor mv, Iterable<Object> opcodes) {
        HashMap<Symbol, Label> labelMap = new HashMap<>();
        for(Object opcode : opcodes) {
            // System.out.println(opcode);
            if(opcode instanceof Sequence) {
                Keyword head = (Keyword) ((Sequence) opcode).first();
                Sequence body = ((Sequence) opcode).rest();
                if(jumpOpMap.containsKey(head)) {
                    int opcodeValue = jumpOpMap.get(head);
                    Symbol labelName = (Symbol) body.first();
                    if(labelMap.containsKey(labelName)) {
                        mv.visitJumpInsn(opcodeValue, labelMap.get(labelName));
                    } else {
                        Label label = new Label();
                        mv.visitJumpInsn(opcodeValue, label);
                        labelMap.put(labelName, label);
                    }
                } else if(head.equals(LABEL)) {
                    Symbol labelName = (Symbol) body.first();
                    if(labelMap.containsKey(labelName)) {
                        mv.visitLabel(labelMap.get(labelName));
                    } else {
                        Label label = new Label();
                        mv.visitLabel(label);
                        labelMap.put(labelName, label);
                    }
                } else if(head.equals(LDC)) {
                    mv.visitLdcInsn(((Sequence) opcode).second());
                } else if(INVOKESTATIC.equals(head) || INVOKEVIRTUAL.equals(head)
                        || INVOKESPECIAL.equals(head) || INVOKEINTERFACE.equals(head)) {
                    mv.visitMethodInsn(opMap.get(head),
                            (String) body.first(),
                            (String) body.second(),
                            (String) body.third(),
                            INVOKEINTERFACE.equals(head));
                } else if(INVOKEDYNAMIC.equals(head)) {
                    // contain the handle information in the invokedynamic ir?
                    // (:invokedynamic (handle-class handle-name handle-type) dyn-name dyn-type bsma ...)
                    Sequence handleArgs = (Sequence) body.first();
                    Handle dynamicHandle = new Handle(
                            Opcodes.H_INVOKESTATIC,
                            (String) handleArgs.first(),
                            (String) handleArgs.second(),
                            (String) handleArgs.third(),
                            false);

                    Object[] bootstrapArgs = new Object[body.length() - 3];
                    for(int i = 0; i < bootstrapArgs.length; i ++) {
                        bootstrapArgs[i] = body.nth(i + 3);
                    }

                    mv.visitInvokeDynamicInsn(
                            (String) body.second(),
                            (String) body.third(),
                            dynamicHandle,
                            bootstrapArgs
                    );
                } else if(head.equals(GETSTATIC) || head.equals(GETFIELD) ||
                head.equals(PUTSTATIC) || head.equals(PUTFIELD)) {
                    mv.visitFieldInsn(opMap.get(head),
                            (String) body.first(),
                            (String) body.second(),
                            (String) body.third());
                } else if(head.equals(ALOAD)) {
                    int value = (Integer) body.first();
                    mv.visitVarInsn(Opcodes.ALOAD, value);
                } else if(head.equals(ASTORE)) {
                    int value = (Integer) body.first();
                    mv.visitVarInsn(Opcodes.ASTORE, value);
                } else if(head.equals(ICONST)) {
                    int value = (Integer) body.first();
                    if (-1 <= value && value <= 5) {
                        mv.visitInsn(Opcodes.ICONST_0 + value);
                    } else {
                        mv.visitLdcInsn(value);
                    }
                } else if(head.equals(CHECKCAST) || head.equals(NEW) || head.equals(ANEWARRAY)) {
                    // checkcast, new, anewarray, instanceof
                    mv.visitTypeInsn(opMap.get(head), (String) body.first());
                } else {
                    throw new RuntimeException(head.toString());
                }
            } else {
                if(opcode instanceof Keyword && simpleOpMap.containsKey(opcode)) {
                    mv.visitInsn(simpleOpMap.get(opcode));
                } else {
                    throw new RuntimeException(opcode.toString());
                }
            }
        }
    }

    static Function compileMethod(boolean isMacro, boolean isVarargs, ArrayList<Object> opcodes, String name, int argc) {

        // let ASM library compute method frames and max values
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        //ClassWriter classWriter = new ClassWriter(0);
        // signature:  typed class for generics, e.g. ArrayList<String>. null for none
        // superName:  parent class
        // interfaces: null for none
        String className = "AnonFunc" + (classNum ++);
        classWriter.visit(JAVA_VERSION, Opcodes.ACC_PUBLIC, className, null,
                Type.getInternalName(Function.class), null);

        // generate boilerplate methods
        visitEmptyConstructor(classWriter);
        visitToString(classWriter, name);
        visitReturnBoolean(classWriter, "isMacro", isMacro);
        visitReturnBoolean(classWriter, "isVarargs", isVarargs);
        visitParamCount(classWriter, argc);

        Class<?>[] methodClasses = getParameterClasses(argc + 1);
        if(isVarargs)
            methodClasses[argc - 1] = Sequence.class;
        String descriptor = getMethodDescriptor(methodClasses);
        visitApplier(classWriter, className, descriptor, argc, isVarargs);
        MethodVisitor invoker = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
                "invoke", descriptor, null, null);
        invoker.visitCode();
        visitOpCodes(invoker, opcodes);
        invoker.visitMaxs(0, argc);
        invoker.visitEnd();

        classWriter.visitEnd();
        byte[] classBytes = classWriter.toByteArray();
        /*
        System.out.println();
        PrintWriter printWriter = new PrintWriter(System.out);
        CheckClassAdapter.verify(new ClassReader(classBytes), true, printWriter);
        Compiler.writeToFile("Test.class", classBytes);
        //*/
        Class<?> clazz = ClassDefiner.hotload(classBytes);
        try {
            Constructor<?> constructor = clazz.getConstructor();
            return (Function) constructor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    static Function compileMethod(ArrayList<Object> opcodes) {
        return compileMethod(false, false, opcodes, "anon", 0);
    }
}
