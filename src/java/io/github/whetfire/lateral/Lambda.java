package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Lambda {
    final private Method method;
    final boolean isMacro, isVarargs;
    final int paramCount;
    final private Sequence invoker;

    Lambda(Method method) {
        this.method = method;
        isMacro = method.isAnnotationPresent(Macro.class);
        isVarargs = method.isAnnotationPresent(Varargs.class);
        paramCount = method.getParameterCount();
        invoker = makeInvoker();
    }

    private Sequence makeInvoker() {
        return new ArraySequence(
                Assembler.INVOKESTATIC,
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method)
        );
    }

    Sequence getInvoker() {
        return invoker;
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
