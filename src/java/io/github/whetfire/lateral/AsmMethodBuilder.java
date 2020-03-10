package io.github.whetfire.lateral;

import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

public class AsmMethodBuilder {

    static Keyword ICONST = Keyword.makeKeyword("iconst");
    static Keyword LDC = Keyword.makeKeyword("ldc");
    static Keyword ARETURN = Keyword.makeKeyword("areturn");
    static Keyword ALOAD = Keyword.makeKeyword("aload");

    static Keyword INVOKESTATIC = Keyword.makeKeyword("invokestatic");
    static Keyword GETSTATIC = Keyword.makeKeyword("getstatic");

    static HashMap<Keyword, Integer> simpleOpMap = new HashMap<>();
    static {
        simpleOpMap.put(ARETURN, Opcodes.ARETURN);
    }

    static Class<?>[] getParameterClasses(int count) {
        Class<?>[] classes = new Class[count];
        for(int i = 0; i < count; i ++) {
            classes[i] = Object.class;
        }
        return classes;
    }

    static int classNum = 0;

    static ClassWriter makeClassWriter() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        // Signature = typed class, e.g. ArrayList<String>. null for none
        // superName: null for java.lang.Object
        // interfaces: null for none
        String className = "AnonClass" + (classNum ++);
        classWriter.visit(55, ACC_PUBLIC, className, null, "java/lang/Object", null);

        return classWriter;
    }

    static MethodVisitor visitOpCodes(MethodVisitor mv, ArrayList<Object> opcodes) {
        for(Object opcode : opcodes) {
            // System.out.println(opcode);
            if(opcode instanceof Sequence) {
                Object head = ((Sequence) opcode).first();
                Sequence body = ((Sequence) opcode).rest();
                if(head.equals(LDC)) {
                    mv.visitLdcInsn(((Sequence) opcode).second());
                } else if(head.equals(INVOKESTATIC)) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
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
                } else if(head.equals(ICONST)) {
                    int value = (Integer) body.first();
                    if(-1 <= value && value <= 4) {
                        mv.visitInsn(Opcodes.ICONST_0 + value);
                    } else {
                        mv.visitLdcInsn(value);
                    }
                } else {
                    throw new RuntimeException(head.toString());
                }
            } else {
                if(opcode instanceof Keyword && simpleOpMap.containsKey(opcode)) {
                    mv.visitInsn(simpleOpMap.get(opcode));
                } else {
                    throw new RuntimeException();
                }
            }
        }
        return mv;
    }

    static byte[] visitMethod(ArrayList<Object> opcodes, String name, String descriptor) {
        ClassWriter visitor = makeClassWriter();
        // visitor.visitMethod(access, name, descriptor, signature, exceptions)
        MethodVisitor mv = visitor.visitMethod(
                ACC_PUBLIC + ACC_STATIC,
                name, descriptor,
                null, null
        );

        /*
        mv.visitAnnotation(Type.getDescriptor(Macro.class), true);
        mv.visitAnnotation(Type.getDescriptor(Varargs.class), true);
        */

        mv.visitCode();
        visitOpCodes(mv, opcodes);
        mv.visitMaxs(0, 0); // we let the class compute max and locals for us
        mv.visitEnd();
        visitor.visitEnd();
        return visitor.toByteArray();
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
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC + ACC_STATIC, name, descriptor, null, null);
        if(isMacro)
            mv.visitAnnotation(Type.getDescriptor(Macro.class), true);
        if(isVarargs)
            mv.visitAnnotation(Type.getDescriptor(Varargs.class), true);

        mv.visitCode();
        visitOpCodes(mv, opcodes);
        mv.visitMaxs(0, argc);
        mv.visitEnd();

        // byte[] classBytes = visitMethod(opcodes, name, descriptor);
        classWriter.visitEnd();
        byte[] classBytes = classWriter.toByteArray();
        AsmCompiler.writeToFile("Test.class", classBytes);
        return dynamicsManager.putMethod(classBytes, name, classes);
    }

    static Method compileMethod(DynamicsManager dynamicsManager, ArrayList<Object> opcodes) {
        return compileMethod(dynamicsManager, false, false, opcodes, "invoke", 0);
    }
}
