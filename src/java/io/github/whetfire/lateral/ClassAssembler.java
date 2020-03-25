package io.github.whetfire.lateral;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;

public class ClassAssembler {

    static final private int JAVA_VERSION = 55; // 55.0 = Java 11

    /**
     * Converts a tree representing a JVM class into the byte array representation of the class
     * @param asmTree Sequence based tree
     * @return byte array representation of the asmTree class
     */
    static byte[] visitClass(Sequence asmTree) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        // TODO: assert first arguments match expected
        // first is defclass
        String name = (String) asmTree.second();
        // TODO: get meta
        Sequence meta = (Sequence) asmTree.third();

        classWriter.visit(JAVA_VERSION, Opcodes.ACC_PUBLIC, name, null,
                Type.getInternalName(Function.class), null);

        for(int i = 0; i < 3; i ++) {
            asmTree = asmTree.rest();
        }

        for(Object obj : asmTree) {
            if(obj instanceof Sequence) {
                Sequence member = (Sequence) obj;
                Object head = member.first();
                if(ClassCompiler.DEFMETHOD.equals(head)) {
                    //System.out.println(member);
                    String mname = (String) member.second();
                    String descriptor = (String) member.third();
                    // TODO: get meta
                    Sequence mmeta = (Sequence) member.fourth();

                    MethodVisitor mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC, mname, descriptor,
                            null, null);
                    for (int i = 0; i < 4; i++) {
                        member = member.rest();
                    }
                    //System.out.println(member);
                    mv.visitCode();
                    Assembler.visitOpCodes(mv, member);
                    mv.visitMaxs(-1, -1);
                    mv.visitEnd();
                } else if(ClassCompiler.DEFFIELD.equals(head)) {
                    classWriter.visitField(Opcodes.ACC_PUBLIC,
                            (String) member.second(),
                            (String) member.third(),
                            null, null);
                } else {
                    // TODO subclass
                    throw new SyntaxException();
                }
            } else {
                throw new SyntaxException();
            }
        }

        byte[] classBytes = classWriter.toByteArray();
        /*
        PrintWriter printWriter = new PrintWriter(System.out);
        CheckClassAdapter.verify(new ClassReader(classBytes), true, printWriter);
        System.out.println("================");
        // */
        return classBytes;
    }
}
