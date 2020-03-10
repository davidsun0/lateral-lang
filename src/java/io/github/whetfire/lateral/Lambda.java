package io.github.whetfire.lateral;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Lambda {
    private Method method;
    final boolean isMacro, isVarargs;
    final int argCount;
    Sequence invoker;

    Lambda(Method method) {
        this.method = method;
        isMacro = method.isAnnotationPresent(Macro.class);
        isVarargs = method.isAnnotationPresent(Varargs.class);
        argCount = method.getParameterCount();
        invoker = makeInvoker();
    }

    private Sequence makeInvoker() {
        return new ArraySequence(
                MethodBuilder.INVOKESTATIC,
                Compiler.internalClassName(method.getDeclaringClass()),
                method.getName(),
                Compiler.internalMethodType(method)
        );
    }

    Sequence getInvoker() {
        return invoker;
    }

    boolean isMacro() {
        return isMacro;
    }

    Object invoke(Sequence argVals) {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        // TODO: check arg counts
        for(int i = 0; i < params.length; i ++) {
            args[i] = argVals.first();
            // TODO: type checking
            // params[i].isInstance(args[i])
            argVals = argVals.rest();
        }
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        if(!isMacro) {
            sb.append("function");
        } else {
            sb.append("macro");
        }
        sb.append(' ');
        sb.append(method.getName());
        sb.append('>');
        return sb.toString();
    }
}
