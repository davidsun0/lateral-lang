package io.github.whetfire.lateral;

public class ClassDefiner extends ClassLoader {
    private ClassDefiner() { ; }

    private Class<?> loadClass(byte[] classBytes) {
        return super.defineClass(null, classBytes, 0, classBytes.length);
    }

    /**
     * Lateral's hook to define new classes at runtime.
     * No manager is needed to keep track of sister classes because all references
     * are resolved via invokedyamic and Environment's bootstrap method.
     * When there are no more references to the loaded class and its ClassDefiner, both will
     * be garbage collected.
     * @param classBytes A byte array containing a valid representation of a JVM class
     * @return The class object created from classBytes
     */
    public static Class<?> hotload(byte[] classBytes) {
        return new ClassDefiner().loadClass(classBytes);
    }
}
