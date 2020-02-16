package io.github.whetfire.lateral;

import java.lang.invoke.*;

public class Lang {
    public static Object car(Object a) {
        if(a == null)
            return a;
        else if(a instanceof LinkedList)
            return LinkedList.value((LinkedList) a);
        else
            throw new TypeException(LinkedList.class, a.getClass());
    }

    public static Object cdr(Object a) {
        if(a == null)
            return a;
        else if(a instanceof LinkedList)
            return LinkedList.next((LinkedList) a);
        else
            throw new TypeException(LinkedList.class, a.getClass());
    }

    public static Object cons(Object a, Object b) {
        if(b == null || b instanceof LinkedList)
            return new LinkedList(a, (LinkedList) b);
        else
            throw new TypeException(LinkedList.class, a.getClass());
    }

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
