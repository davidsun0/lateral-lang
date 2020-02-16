package io.github.whetfire.lateral;

import io.github.whetfire.lateral.ByteCodes.*;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class Compiler {
    Map<Symbol, ByteCode> functionMap;
    static ByteCode PUSH_TRUE = new GetStatic(
            "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");

    static Symbol IFSYM = Symbol.makeSymbol("if");

    Compiler() {
        functionMap = new HashMap<>();

        String listClass = "io/github/whetfire/lateral/LinkedList";
        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";

        functionMap.put(Symbol.makeSymbol("cons"),
                new InvokeStatic(langClass,
                        "cons",
                        String.format("(L%s;L%s;)L%s;", objClass, objClass, objClass)));
        functionMap.put(Symbol.makeSymbol("car"),
                new InvokeStatic(langClass,
                        "car",
                        String.format("(L%s;)L%s;", objClass, objClass)));
        functionMap.put(Symbol.makeSymbol("cdr"),
                new InvokeStatic(langClass,
                        "cdr",
                        String.format("(L%s;)L%s;", objClass, objClass)));
    }

    public ByteCode resolveFuncall(String name) {
        var ret = functionMap.get(Symbol.makeSymbol(name));
        if(ret == null) {
            throw new RuntimeException("Couldn't resolve function " + name);
        } else {
            return ret;
        }
    }

    public MethodBuilder compile(Object object, MethodBuilder methodBuilder) {
        if (object instanceof Integer) {
            methodBuilder.insertOpCode(new IntConstOp((int) object));
        } else if(Symbol.NIL_SYMBOL.equals(object)) {
            methodBuilder.insertOpCode(ByteCodes.ACONST_NULL);
        } else if(Symbol.TRUE_SYMBOL.equals(object)) {
            methodBuilder.insertOpCode(PUSH_TRUE);
        } else if (object instanceof LinkedList) {
            return compile((LinkedList)object, methodBuilder);
        } else {
            throw new RuntimeException("Can't convert to bytecode: " + object);
        }
        return methodBuilder;
    }

    public MethodBuilder compile(LinkedList ast, MethodBuilder methodBuilder) {
        // saves a list of pointers to parents of the working node
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.makeList(new LinkedList(ast, null), null));
        while(!stack.isEmpty()) {
            LinkedList pair = stack.pop();
            LinkedList val = (LinkedList) pair.getValue();
            System.out.println(val);
            if(val == null) {
                LinkedList prev = (LinkedList) pair.getNext().getValue();
                if (prev == null){
                    break;
                }
                String funcall = prev.getValue().toString();
                ByteCode byteCode = resolveFuncall(funcall);
                methodBuilder.insertOpCode(byteCode);
            } else if (val.getValue() instanceof LinkedList) {
                // switch on head here for special forms:
                // quote, and, or, if/cond, let, lambda, def, defmacro
                LinkedList expr = (LinkedList) val.getValue();
                Object head = expr.getValue();
                if(IFSYM.equals(head)) {
                    // stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                    Label elseLab = new Label();
                    Label endLab = new Label();

                    // TEST
                    System.out.println("test: " + LinkedList.second(expr));
                    compile(LinkedList.second(expr), methodBuilder);
                    methodBuilder.insertOpCode(new IfNull(elseLab));
                    // TRUE BRANCH
                    System.out.println("true: " + LinkedList.third(expr));
                    compile(LinkedList.third(expr), methodBuilder);
                    methodBuilder.insertOpCode(new Goto(endLab));
                    // ELSE BRANCH
                    System.out.println("false: " + LinkedList.fourth(expr));
                    methodBuilder.insertOpCode(elseLab);
                    compile(LinkedList.fourth(expr), methodBuilder);
                    // END
                    methodBuilder.insertOpCode(endLab);
                    System.out.println("=======");
                    methodBuilder.printCode();
                    System.out.println(stack.isEmpty());
                    System.out.println("=======");
                } else {
                    stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                    stack.push(LinkedList.makeList(((LinkedList) val.getValue()).getNext(), val.getValue()));
                }
            } else {
                Object element = val.getValue();
                if (element instanceof Integer) {
                    methodBuilder.insertOpCode(new IntConstOp((int) element));
                } else if(Symbol.NIL_SYMBOL.equals(element)) {
                    methodBuilder.insertOpCode(ByteCodes.ACONST_NULL);
                } else if(Symbol.TRUE_SYMBOL.equals(element)) {
                    // methodBuilder.insertOpCode(new MethodBuilder.IntConstOp(1));
                    methodBuilder.insertOpCode(PUSH_TRUE);
                } else {
                    throw new RuntimeException("Can't convert to bytecode: " + element);
                }
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        return methodBuilder;
    }

    MethodBuilder compileMethod(Object ast) {
        MethodBuilder methodBuilder = compile(ast, new MethodBuilder());
        methodBuilder.insertOpCode(ByteCodes.ARETURN);
        return methodBuilder;
    }

    public static void main(String[] args) throws FileNotFoundException {
        Compiler c = new Compiler();
        // var target = new StringLispReader("(+ 1 (* 2 3))").readForm();
        Object target = new FileLispReader("./src/lisp/test.lisp").readForm();
        System.out.println(target);
        ClassBuilder cgen = new ClassBuilder();
        MethodBuilder mgen = c.compileMethod(target);
        // mgen.printCode();
        System.out.println("***");
        cgen.addMethod(mgen);
        cgen.writeToFile("MyClass.class");
        //*
        try {
            Class<?> clazz = new LClassLoader().defineClass(cgen.toBytes());
            Method m = clazz.getMethod("myFunction", (Class<?>[])null);
            System.out.println("result: " + m.invoke(null));
        } catch (Exception e){
            e.printStackTrace();
        }
        //*/
        // cgen.loadAndPrintMethods();
    }
}
