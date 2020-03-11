package io.github.whetfire.lateral;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

public class Assembler {
    static final private int JAVA_VERSION = 55; // 55.0 = Java 11

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
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        //ClassWriter classWriter = new ClassWriter(0);
        // signature:  typed class for generics, e.g. ArrayList<String>. null for none
        // superName:  parent class
        // interfaces: null for none
        String className = "AnonClass" + (classNum ++);
        classWriter.visit(JAVA_VERSION, ACC_PUBLIC, className, null,
                Type.getInternalName(Object.class), null);

        return classWriter;
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
                            (String) ((Sequence) opcode).second(),
                            (String) ((Sequence) opcode).third(),
                            (String) ((Sequence) opcode).fourth(), false);
                } else if(head.equals(GETSTATIC)) {
                    mv.visitFieldInsn(Opcodes.GETSTATIC,
                            (String) ((Sequence) opcode).second(),
                            (String) ((Sequence) opcode).third(),
                            (String) ((Sequence) opcode).fourth());
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

    static Method compileMethod(DynamicsManager dynamicsManager,
                                boolean isMacro, boolean isVarargs, ArrayList<Object> opcodes, String name, int argc) {
        Class<?>[] classes = getParameterClasses(argc);
        Type[] types = new Type[classes.length];
        for(int i = 0; i < classes.length; i ++) {
            types[i] = Type.getType(classes[i]);
        }
        String descriptor = Type.getMethodDescriptor(Type.getType(Object.class), types);

        ClassWriter classWriter = makeClassWriter();
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, name, descriptor, null, null);
        if(isMacro)
            mv.visitAnnotation(Type.getDescriptor(Macro.class), true);
        if(isVarargs)
            mv.visitAnnotation(Type.getDescriptor(Varargs.class), true);

        mv.visitCode();
        visitOpCodes(mv, opcodes);
        mv.visitMaxs(10, argc);
        mv.visitEnd();

        classWriter.visitEnd();
        byte[] classBytes = classWriter.toByteArray();
        /*
        PrintWriter printWriter = new PrintWriter(System.err);
        CheckClassAdapter.verify(new ClassReader(classBytes), true, printWriter);
        Compiler.writeToFile("Test.class", classBytes);
        */
        return dynamicsManager.putMethod(classBytes, name, classes);
    }

    static Method compileMethod(DynamicsManager dynamicsManager, ArrayList<Object> opcodes) {
        return compileMethod(dynamicsManager, false, false, opcodes, "invoke", 0);
    }
}
