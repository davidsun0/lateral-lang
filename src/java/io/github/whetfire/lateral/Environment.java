package io.github.whetfire.lateral;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.HashMap;

public class Environment {
    private static class ValueAndCall {
        Object value;
        CallSite asObject;
        CallSite asFunction;

        ValueAndCall(CallSite asObject, CallSite asFunction) {
            this.asObject = asObject;
            this.asFunction = asFunction;
        }

        CallSite getFunction(MethodType methodType) {
            return null;
        }
    }

    private static HashMap<Symbol, Object> symMap = new HashMap<>();
    private static HashMap<Symbol, ValueAndCall> callSiteMap = new HashMap<>();

    public static Object insert(Symbol symbol, Object obj) {
        symMap.put(symbol, obj);
        // create two MethodHandles
        // replace if exists
        // else make MutableCallSite
        return obj;
    }

    public static Object get(Symbol symbol) {
        Object ret = symMap.get(symbol);
        if(ret == null)
            throw new RuntimeException("Can't find symbol in environment: " + symbol);
        return ret;
    }

    public static Object getIfExists(Symbol symbol) {
        return symMap.get(symbol);
    }

    public static CallSite dynamicObject(
            MethodHandles.Lookup lookup, String dynamicName, MethodType dynamicType,
            String namespace) {
        Symbol key = Symbol.makeSymbol(dynamicName);
        if(symMap.containsKey(key)) {
            Object object = symMap.get(Symbol.makeSymbol(dynamicName));
            MethodHandle mh = MethodHandles.constant(Object.class, object).asType(dynamicType);
            return new ConstantCallSite(mh);
        }
        throw new RuntimeException(dynamicName + " does not exist in global environment");
    }

    /**
     * The Environmental InvokeDynamic bootstrap method.
     * Finds a function by name and returns a CallSite to invoke said function.
     *
     * @param lookup The lookup handle from the invokedynamic instruction
     * @param dynamicName The string name of the function to be called
     * @param dynamicType The expected method type of the function
     * @param unused To be implemented: the name of the environment that contains the function
     * @return A CallSite representing the function invocation
     *
     * The CallSite returned may throw an exception if the function request can't be fulfilled.
     * If the needed resource is fulfilled in the future, the returned CallSite should be mutated
     * with the new value and no longer throw an exception.
     *
     * @throws NoSuchMethodException If the requested function does not exist or cannot be applied with the
     * given method signature
     * @throws IllegalAccessException Should not be thrown under normal conditions.
     * Occurs when the function invocation cannot be accessed by the requesting instruction.
     * e.g. the method is private or owned by the wrong ClassLoader
     */
    public static CallSite dynamicFunction(
            MethodHandles.Lookup lookup, String unused, MethodType dynamicType,
            String dynamicName) throws NoSuchMethodException, IllegalAccessException {
        /*
         dynamicName is stored in extra arguments because second arg must be valid unqualified name:
         https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.2.2
         none of .;[/ and no <> unless the method name is <init> or <clinit>

         If the second arg is going to be namespace information, namespaces will also have to follow these
         rules. Maybe just leave it unused?
         */
        // TODO: look up specific environment with envirName
        Symbol name = Symbol.makeSymbol(dynamicName);
        // TODO: store MutableCallSite even if function lookup fails, because it might change in the future
        MethodHandle result;
        if(!symMap.containsKey(name)) {
            result = MethodHandles.dropArguments(
                    MethodHandles.throwException(Object.class, NoSuchMethodException.class)
                    .bindTo(new NoSuchMethodException("function " + dynamicName + " does not exist")),
                    0, dynamicType.parameterList());
        } else if(!(symMap.get(name) instanceof Function)) {
            result = MethodHandles.dropArguments(
                    MethodHandles.throwException(Object.class, TypeException.class)
                            .bindTo(new TypeException(symMap.get(name) + " can't be used as a function")),
                    0, dynamicType.parameterList());
        } else {
            // TODO: function fallbacks: invoke (static) -> invoke (virtual) -> apply -> NoSuchMethod
            // TODO: assert that function is not macro
            Function function = (Function) symMap.get(Symbol.makeSymbol(dynamicName));
            Method[] methods = function.getClass().getMethods();
            result = null;
            for(Method m : methods) {
                // System.out.println(m.getName());
                if("invoke".equals(m.getName())) {
                    Class<?>[] params = m.getParameterTypes();
                    if(m.getParameterCount() == dynamicType.parameterCount()) {
                        result = lookup.unreflect(m).bindTo(function);
                    } else if(params.length > 0 && params[params.length - 1] == Sequence.class
                            && params.length - 1 <= dynamicType.parameterCount()) {
                        // the actual method to be called
                        MethodHandle base = lookup.unreflect(m).bindTo(function);
                        /*
                        asCollector collects (given - expected + 1) arguments into an Array
                        the array is fed into ArraySequence.makeList, which returns the varargs as a single Sequence
                         */
                        MethodHandle makelist = lookup.findStatic(ArraySequence.class, "makeList",
                                MethodType.methodType(Sequence.class, Object[].class)
                        ).asCollector(Object[].class, dynamicType.parameterCount() - m.getParameterCount() + 1);
                        /*
                         all but the first expected - 1 arguments are fed into the makelist described above
                         This effectively combines the two MethodHandles into one with automatic varargs to Sequence collection
                         */
                        result = MethodHandles.collectArguments(base, m.getParameterCount() - 1, makelist);
                    }
                }
            }
            if(result == null) {
                result = MethodHandles.dropArguments(
                        MethodHandles.throwException(Object.class, TypeException.class)
                                .bindTo(new SyntaxException(symMap.get(name) + " can't be applied to " + dynamicType.toString())),
                        0, dynamicType.parameterList());
            }
        }
        // TODO: convert to MutableCallSite to allow for redefinition
        // TODO: bind MutableCallSite to object? Can one object have multiple callsites?
        return new ConstantCallSite(result);
    }
}
