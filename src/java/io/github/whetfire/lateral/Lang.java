package io.github.whetfire.lateral;

import java.lang.invoke.*;

public class Lang {
    public static Integer sum(Integer a, Integer b) {
        return a + b;
    }

    public static Integer sub(Integer a, Integer b) {
        return a - b;
    }

    public static CallSite langBSM(
            MethodHandles.Lookup callerClass, String dynMethodName, MethodType dynMethodType)
        throws Throwable {
        // BSM stands for bootstrap method
        MethodHandle mh = callerClass.findStatic(
                Lang.class,
                dynMethodName,
                MethodType.methodType(LinkedList.class, Object.class, LinkedList.class));
        if(!dynMethodType.equals(mh.type())) {
            mh = mh.asType(dynMethodType);
        }
        return new ConstantCallSite(mh);
    }
}
