package io.github.whetfire.lateral;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * DynamicsManager handles all dynamically generated classes and methods.
 *
 * At runtime, new JVM classes and methods can be generated. They can then be
 * injected into the running JVM instance and used immediately.
 */
class DynamicsManager {

    private Map<String, ClassDefiner> classDefinerMap = new HashMap<>();
    private Map<Symbol, Object> resourceMap = new HashMap<>();

    /**
     * Takes a MethodBuilder and inserts the method it represents into the runtime environment.
     *
     * @param methodBuilder The builder to be used to generate a binary method
     * @param isMacro If the method represents a Lisp macro - If true, it will be run at compile time
     * @return A Lambda object wrapping the java.lang.reflect.Method
     */
    public Lambda putMethod(MethodBuilder methodBuilder, boolean isMacro) {
        // If the Class / ClassLoader overhead is too much, try lazily producing classes:
        // Keep a list of queued methods and only compile into a class when needed
        // Cons: harder to revoke methods when overwritten?
        ClassBuilder classBuilder = new ClassBuilder(Compiler.genClassName());
        classBuilder.addMethod(methodBuilder);
        // System.out.println(methodBuilder.getName());
        Class<?> clazz = defineClass(classBuilder.toBytes());
        try {
            Method method = clazz.getMethod(methodBuilder.getName().toString(), methodBuilder.getParameterTypes());
            Lambda wrapper = new Lambda(method, isMacro);
            resourceMap.put(methodBuilder.getName(), wrapper);
            return wrapper;
        } catch (NoSuchMethodException nsme) {
            // something has gone seriously wrong
            // we just made this class and put the method inside
            throw new RuntimeException("compiler broken?", nsme);
        } catch (VerifyError ve) {
            // Bug in the compiler - the class is malformed
            methodBuilder.printCodes();
            throw new SyntaxException(ve);
        }
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
     * ClassDefiner provides a hook to define new Java classes at runtime.
     * ClassLoader.defineClass is used to inject the class.
     *
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

        /**
         * Finds the JVM Class with the given name.
         *
         * Every class asks its own ClassLoader to resolve references to other classes.
         * Since this instance may not have loaded the other class, we defer to the parent
         * DynamicsManager. Then the DynamicsManager attempts to find the class or throws
         * a ClassNotFoundException when appropriate.
         *
         * @param name Name of the class to be found
         * @return The Class object of the class with the given name
         * @throws ClassNotFoundException If the class was not loaded by this ClassDefiner
         * or any of the ClassDefiners in the parent DynamicsManager
         */
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> clazz = classMap.get(name);
            if(clazz == null)
                return DynamicsManager.this.findClass(name);
            else
                return clazz;
        }
    }
}
