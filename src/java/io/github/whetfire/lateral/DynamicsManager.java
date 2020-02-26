package io.github.whetfire.lateral;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class DynamicsManager {

    private Map<String, ClassDefiner> classDefinerMap = new HashMap<>();
    private Map<Symbol, Object> resourceMap = new HashMap<>();

    public Lambda putMethod(MethodBuilder methodBuilder, boolean isMacro) {
        // If the Class / ClassLoader overhead is too much, try lazily producing classes:
        // Keep a list of queued methods and only compile into a class when needed
        // Cons: harder to revoke methods when overwritten?
        ClassBuilder classBuilder = new ClassBuilder(Compiler.genClassName());
        classBuilder.addMethod(methodBuilder);
        System.out.println(methodBuilder.getName());
        Class<?> clazz = defineClass(classBuilder.toBytes());
        try {
            Method method = clazz.getMethod(methodBuilder.getName().toString(), methodBuilder.getParameterTypes());
            Lambda wrapper = new Lambda(method, isMacro);
            resourceMap.put(methodBuilder.getName(), wrapper);
            return wrapper;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Object getResource(Symbol symbol) {
        return resourceMap.get(symbol);
    }

    /**
     * Defines a temporary class. Useful for making classes that will only be used once.
     * DynamicsManager does not store the generated class or its containing ClassLoader,
     * so the new class and its ClassLoader will be garbage collected once external references are lost.
     * @param classBytes Byte representation of a JVM class file
     * @return The class defined in classBytes
     */
    public Class<?> defineTemporaryClass(byte[] classBytes) {
        return new ClassDefiner().defineClass(classBytes);
    }

    private Class<?> defineClass(byte[] classBytes) {
        ClassDefiner classDefiner = new ClassDefiner();
        Class clazz = classDefiner.defineClass(classBytes);
        classDefinerMap.put(clazz.getName(), classDefiner);
        return clazz;
    }

    private Class<?> findClass(String name) throws ClassNotFoundException {
        ClassDefiner classDefiner = classDefinerMap.get(name);
        if(classDefiner == null)
            throw new ClassNotFoundException();
        Class clazz = classDefiner.findClass(name);
        if(clazz == null) {
            throw new ClassNotFoundException();
        }
        return clazz;
    }

    /**
     * Provides a hook to define new Java classes at runtime
     */
    class ClassDefiner extends ClassLoader {

        private Map<String, Class> classMap = new HashMap<>();

        /**
         * Loads a new class into the JVM at runtime.
         * @param classBytes byte array representing the contents of a JVM class file
         * @return Class defined in classBytes after loading it into the JVM
         */
        public Class<?> defineClass(byte[] classBytes) {
            Class clazz = super.defineClass(null, classBytes, 0, classBytes.length);
            classMap.put(clazz.getName(), clazz);
            return clazz;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> clazz = classMap.get(name);
            if(clazz == null)
                return DynamicsManager.this.findClass(name);
            else
                return clazz;
        }
    }
}
