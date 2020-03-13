package io.github.whetfire.lateral;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

public class Assembler {
    static final private int JAVA_VERSION = 55; // 55.0 = Java 11
    static final Handle ENVIR_BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC, Type.getInternalName(Environment.class),
            "bootstrapMethod",
            MethodType.methodType(
                    CallSite.class,
                    MethodHandles.Lookup.class,
                    String.class,
                    MethodType.class,
                    String.class
            ).toMethodDescriptorString(),
            false
    );

    static Keyword LABEL = Keyword.makeKeyword("label");
    static HashMap<Keyword, Integer> jumpOpMap = new HashMap<>();

    static Keyword ICONST = Keyword.makeKeyword("iconst");
    static Keyword LDC = Keyword.makeKeyword("ldc");
    static Keyword ARETURN = Keyword.makeKeyword("areturn");
    static Keyword ALOAD = Keyword.makeKeyword("aload");
    static Keyword ASTORE = Keyword.makeKeyword("astore");

    static Keyword CHECKCAST = Keyword.makeKeyword("checkcast");
    static Keyword NEW = Keyword.makeKeyword("new");

    static Keyword INVOKESTATIC = Keyword.makeKeyword("invokestatic");
    static Keyword INVOKEVIRTUAL = Keyword.makeKeyword("invokevirtual");
    static Keyword INVOKESPECIAL = Keyword.makeKeyword("invokespecial");
    static Keyword INVOKEDYNAMIC = Keyword.makeKeyword("invokedynamic");

    static Keyword GETSTATIC = Keyword.makeKeyword("getstatic");

    static HashMap<Keyword, Integer> simpleOpMap = new HashMap<>();
    static HashMap<Keyword, Integer> opMap = new HashMap<>();

    static {
        simpleOpMap.put(ARETURN, Opcodes.ARETURN);
        simpleOpMap.put(Keyword.makeKeyword("dup"), Opcodes.DUP);
        simpleOpMap.put(Keyword.makeKeyword("dup2"), Opcodes.DUP2);
        simpleOpMap.put(Keyword.makeKeyword("dup_x1"), Opcodes.DUP_X1);
        simpleOpMap.put(Keyword.makeKeyword("pop"), Opcodes.POP);
        simpleOpMap.put(Keyword.makeKeyword("swap"), Opcodes.SWAP);

        simpleOpMap.put(Keyword.makeKeyword("isub"), Opcodes.ISUB);
        simpleOpMap.put(Keyword.makeKeyword("iadd"), Opcodes.IADD);

        jumpOpMap.put(Keyword.makeKeyword("ifnull"), Opcodes.IFNULL);
        jumpOpMap.put(Keyword.makeKeyword("ifnonnull"), Opcodes.IFNULL);
        jumpOpMap.put(Keyword.makeKeyword("ifne"), Opcodes.IFNE);
        jumpOpMap.put(Keyword.makeKeyword("ifeq"), Opcodes.IFEQ);
        jumpOpMap.put(Keyword.makeKeyword("ifgt"), Opcodes.IFGT);
        jumpOpMap.put(Keyword.makeKeyword("iflt"), Opcodes.IFLT);
        jumpOpMap.put(Keyword.makeKeyword("if_icmpgt"), Opcodes.IF_ICMPGT);
        jumpOpMap.put(Keyword.makeKeyword("goto"), Opcodes.GOTO);

        opMap.put(INVOKESTATIC, Opcodes.INVOKESTATIC);
        opMap.put(INVOKEVIRTUAL, Opcodes.INVOKEVIRTUAL);
        opMap.put(INVOKESPECIAL, Opcodes.INVOKESPECIAL);
        opMap.put(CHECKCAST, Opcodes.CHECKCAST);
        opMap.put(NEW, Opcodes.NEW);
    }

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

    private static int classNum = 0;

    static ClassWriter makeClassWriter() {
        // let ASM library compute method frames and max values
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        //ClassWriter classWriter = new ClassWriter(0);
        // signature:  typed class for generics, e.g. ArrayList<String>. null for none
        // superName:  parent class
        // interfaces: null for none
        String className = "AnonClass" + (classNum ++);
        classWriter.visit(JAVA_VERSION, ACC_PUBLIC, className, null,
                Type.getInternalName(Function.class), null);

        return classWriter;
    }

    static void visitEmptyConstructor(ClassWriter classWriter) {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Function.class),
                "<init>","()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitToString(ClassWriter classWriter, String string) {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(string);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitStaticInvokeHook(ClassWriter classWriter, String className) {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC,
                "invoke", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                className, "invokeStatic", "()Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitMacroAndVarargs(ClassWriter classWriter, boolean isMacro, boolean isVarargs) {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC,
                "isMacro", "()Z", null, null);
        mv.visitCode();
        if(isMacro) {
            mv.visitInsn(Opcodes.ICONST_1);
        } else {
            mv.visitInsn(Opcodes.ICONST_0);
        }
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();


        mv = classWriter.visitMethod(ACC_PUBLIC,
                "isVarargs", "()Z", null, null);
        mv.visitCode();
        if(isVarargs) {
            mv.visitInsn(Opcodes.ICONST_1);
        } else {
            mv.visitInsn(Opcodes.ICONST_0);
        }
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    static void visitParamCount(ClassWriter classWriter, int paramc) {
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "paramCount", "()I", null, null);
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

    static void visitOpCodes(MethodVisitor mv, ArrayList<Object> opcodes) {
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
                } else if(INVOKESTATIC.equals(head) || INVOKEVIRTUAL.equals(head) || INVOKESPECIAL.equals(head)) {
                    // TODO: invokeinterface
                    mv.visitMethodInsn(opMap.get(head),
                            (String) body.first(),
                            (String) body.second(),
                            (String) body.third(),
                            false);
                } else if(INVOKEDYNAMIC.equals(head)) {
                    // contain the handle information in the invokedynamic ir?
                    // (:invokedynamic (handle-class handle-name handle-type) dyn-name dyn-type bsma ...)
                    mv.visitInvokeDynamicInsn(
                            (String) body.first(),
                            (String) body.second(),
                            ENVIR_BOOTSTRAP, "test"
                    );
                } else if(head.equals(GETSTATIC)) {
                    mv.visitFieldInsn(Opcodes.GETSTATIC,
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
                    if (-1 <= value && value <= 4) {
                        mv.visitInsn(Opcodes.ICONST_0 + value);
                    } else {
                        mv.visitLdcInsn(value);
                    }
                } else if(head.equals(CHECKCAST) || head.equals(NEW)) {
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

    static Function compileMethod(DynamicsManager dynamicsManager,
                                boolean isMacro, boolean isVarargs, ArrayList<Object> opcodes, String name, int argc) {
        Class<?>[] classes = getParameterClasses(argc);
        if(isVarargs)
            classes[argc - 1] = Sequence.class;
        Type[] types = new Type[classes.length];
        for(int i = 0; i < classes.length; i ++) {
            types[i] = Type.getType(classes[i]);
        }
        String descriptor = Type.getMethodDescriptor(Type.getType(Object.class), types);

        ClassWriter classWriter = makeClassWriter();
        visitEmptyConstructor(classWriter);
        visitToString(classWriter, name);
        visitStaticInvokeHook(classWriter, "AnonClass" + (classNum - 1));

        visitMacroAndVarargs(classWriter, isMacro, isVarargs);
        visitParamCount(classWriter, argc);

        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "invokeStatic", descriptor, null, null);
        mv.visitCode();
        visitOpCodes(mv, opcodes);
        mv.visitMaxs(10, argc);
        mv.visitEnd();

        classWriter.visitEnd();
        byte[] classBytes = classWriter.toByteArray();
        /*
        System.out.println();
        PrintWriter printWriter = new PrintWriter(System.out);
        CheckClassAdapter.verify(new ClassReader(classBytes), true, printWriter);
        Compiler.writeToFile("Test.class", classBytes);
        //*/
        Class<?> clazz = dynamicsManager.defineTemporaryClass(classBytes);
        try {
            Constructor<?> constructor = clazz.getConstructor();
            return (Function) constructor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    static Function compileMethod(DynamicsManager dynamicsManager, ArrayList<Object> opcodes) {
        return compileMethod(dynamicsManager, false, false, opcodes, "anon", 0);
    }
}
