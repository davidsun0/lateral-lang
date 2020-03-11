package io.github.whetfire.lateral;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * DynamicsManager handles all dynamically generated classes and methods.
 *
 * At runtime, new JVM classes and methods can be generated. They can then be
 * injected into the running JVM instance and used immediately.
 */
class DynamicsManager {

    private HashMap<String, WeakReference<Class<?>>> classMap;
    private ReferenceQueue<Class<?>> referenceQueue;

    DynamicsManager() {
        this.classMap = new HashMap<>();
        this.referenceQueue = new ReferenceQueue<>();
    }

    private void putClass(Class clazz) {
        if (referenceQueue.poll() != null) {
            classMap.entrySet().removeIf((entry) -> {
                System.err.println("class " + entry.getKey() + " was garbage collected");
                return entry.getValue().get() == null;
            });
        }
        classMap.put(clazz.getName(), new WeakReference<Class<?>>(clazz, referenceQueue));
    }

    public Method putMethod(byte[] classBytes, String name, Class<?>[] signature) {
        Class<?> clazz = new ClassDefiner(classBytes).clazz;
        try {
            Method method = clazz.getMethod(name, signature);
            putClass(clazz);
            return method;
        } catch (NoSuchMethodException nsme) {
            throw new RuntimeException(nsme);
        }
    }

    /**
     * Defines a temporary class. Useful for making classes that will only be used once.
     * DynamicsManager does not store the generated class or its containing ClassLoader,
     * so the new class and its ClassLoader will be garbage collected once external references are lost.
     * @param classBytes Byte representation of a JVM class file
     * @return The class defined in classBytes
     */
    public Class<?> defineTemporaryClass(byte[] classBytes) {
        return new ClassDefiner(classBytes).clazz;
    }

    private Class<?> findClass(String name) throws ClassNotFoundException {
        WeakReference<Class<?>> reference = classMap.get(name);
        if(reference == null || reference.get() == null) {
            throw new ClassNotFoundException();
        }
        return reference.get();
    }

    /**
     * Provides a hook to define a new JVM classes at runtime.
     */
    class ClassDefiner extends ClassLoader {
        private Class<?> clazz;

        /*
         * Loads a new class into the JVM at runtime.
         * @param classBytes byte array representing the contents of a JVM class file
         * @return Class defined in classBytes after loading it into the JVM
         */
        ClassDefiner(byte[] classBytes) {
            clazz = super.defineClass(null, classBytes, 0, classBytes.length);
        }

        /**
         * Finds the JVM Class with the given name.
         *
         * Every class asks its own ClassLoader to resolve references to other classes.
         * Since this instance may not have loaded the other class, we defer to the parent
         * DynamicsManager.
         *
         * @param name Name of the class to be found
         * @return The Class object of the class with the given name
         * @throws ClassNotFoundException If neither this ClassDefiner or the parent DynamicsManager
         * could find the class
         * or any of the ClassDefiners in the parent DynamicsManager
         */
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if(name.equals(clazz.getName()))
                return clazz;
            else
                return DynamicsManager.this.findClass(name);
        }
    }
}
