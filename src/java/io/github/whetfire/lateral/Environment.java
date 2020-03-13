package io.github.whetfire.lateral;

import java.lang.invoke.*;
import java.util.HashMap;

public class Environment {
    private static HashMap<Symbol, Object> symMap = new HashMap<>();
    private static DynamicsManager dynamicsManager = new DynamicsManager();

    public static Object insert(Symbol symbol, Object obj) {
        symMap.put(symbol, obj);
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

    public static CallSite bootstrapMethod(
            MethodHandles.Lookup lookup, String dynamicName, MethodType dynamicType,
            String envirName
    ) throws Throwable {
        // TODO: look up specific environment with envirName
        // TODO: check that function exists
        // TODO: fallback for method selection: exact args -> varargs -> generic (?) -> error
        Function target = (Function) symMap.get(Symbol.makeSymbol(dynamicName));
        if(target == null) {
            throw new RuntimeException("function " + dynamicName + " does not exist");
        } else if(target.isVarargs()) {
            int given = dynamicType.parameterCount();
            int expected = target.paramCount();
            Class<?>[] paramClasses = Assembler.getParameterClasses(expected);
            paramClasses[paramClasses.length - 1] = Sequence.class;

            // the actual method to be called
            MethodHandle source = lookup.findStatic(target.getClass(), "invokeStatic",
                    MethodType.methodType(Object.class, paramClasses));

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
            MethodHandle x = MethodHandles.collectArguments(source, expected - 1, makelist);
            return new ConstantCallSite(x);
        } else {
            MethodHandle mh = lookup.findStatic(target.getClass(), "invokeStatic", dynamicType);
            return new ConstantCallSite(mh);
        }
        // TODO: convert to MutableCallSite to allow for redefinition
        // TODO: bind MutableCallSite to object? Can one object have multiple callsites?
    }
}
