package io.github.whetfire.lateral;

import java.lang.invoke.*;
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
            MethodHandle mh = MethodHandles.constant(object.getClass(), object).asType(dynamicType);
            return new ConstantCallSite(mh);
        }
        throw new RuntimeException(dynamicName + " does not exist in global environment");
    }

    /**
     * The Environmental InvokeDynamic bootstrap method.
     * Finds a function by name and returns a CallSite to invoke said function.
     * @param lookup The lookup handle from the invokedynamic instruction
     * @param dynamicName The string name of the function to be called
     * @param dynamicType The expected method type of the function
     * @param namespace To be implemented: the name of the environment that contains the function
     * @return A CallSite representing the function invocation
     * @throws NoSuchMethodException If the requested function does not exist or cannot be applied with the
     * given method signature
     * TODO UnsupportedOperationException ?
     * @throws IllegalAccessException Should not be thrown under normal conditions.
     * Occurs when the function invocation cannot be accessed by the requesting instruction.
     * e.g. the method is private or owned by the wrong ClassLoader
     */
    public static CallSite dynamicFunction(
            MethodHandles.Lookup lookup, String dynamicName, MethodType dynamicType,
            String namespace) throws NoSuchMethodException, IllegalAccessException {
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
            if (function.isVarargs()) {
                int given = dynamicType.parameterCount();
                int expected = function.paramCount();
                Class<?>[] paramClasses = Assembler.getParameterClasses(expected);
                paramClasses[paramClasses.length - 1] = Sequence.class;

                // the actual method to be called
                MethodHandle base = lookup.findVirtual(function.getClass(), "invoke",
                        MethodType.methodType(Object.class, paramClasses)).bindTo(function);
                /*
                asCollector collects (given - expected + 1) arguments into an Array
                the array is fed into ArraySequence.makeList, which returns the varargs as a single Sequence
                 */
                MethodHandle makelist = lookup.findStatic(ArraySequence.class, "makeList",
                        MethodType.methodType(Sequence.class, Object[].class)
                ).asCollector(Object[].class, given - expected + 1);
                /*
                 all but the first expected - 1 arguments are fed into the makelist described above
                 This effectively combines the two MethodHandles into one with automatic varargs to Sequence collection
                 */
                result = MethodHandles.collectArguments(base, expected - 1, makelist);
            } else {
                result = lookup.findVirtual(function.getClass(), "invoke", dynamicType).bindTo(function);
            }
        }
        // TODO: convert to MutableCallSite to allow for redefinition
        // TODO: bind MutableCallSite to object? Can one object have multiple callsites?
        return new ConstantCallSite(result);
    }
}
