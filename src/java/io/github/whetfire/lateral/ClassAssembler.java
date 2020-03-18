package io.github.whetfire.lateral;

import org.objectweb.asm.ClassWriter;

public class ClassAssembler {

    static final private int JAVA_VERSION = 55; // 55.0 = Java 11

    Class<?> visitClass(Sequence asmTree) {
        // assert first arguments match expected
        /*
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        classWriter.visit(JAVA_VERSION, access, name, parent, interfaces);

        classWriter.visitField(access, name, descriptor, signature, value);
        */
        return null;
    }
}
