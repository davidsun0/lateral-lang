package io.github.whetfire.lateral;

/**
 * Provides a hook to define new Java classes at runtime
 */
public class LClassLoader extends ClassLoader {
    /**
     *
     * @param classBytes byte array representing the contents of a JVM class file
     * @return Class defined in classBytes after loading it into the JVM
     */
    public Class<?> defineClass(byte[] classBytes) {
        return super.defineClass(null, classBytes, 0, classBytes.length);
    }
}
