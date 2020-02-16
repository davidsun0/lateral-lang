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
    static ByteCode PARSE_INT = new InvokeStatic(
            "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
    static ByteCode MAKE_SYM = new InvokeStatic(
            "io/github/whetfire/lateral/Symbol", "makeSymbol",
            "(Ljava/lang/String;)Lio/github/whetfire/lateral/Symbol;"
    );
    static ByteCode CONS;

    static Symbol IF_SYM = Symbol.makeSymbol("if");
    static Symbol AND_SYM = Symbol.makeSymbol("and");
    static Symbol OR_SYM = Symbol.makeSymbol("or");
    static Symbol QUOTE_SYM = Symbol.makeSymbol("quote");

    Compiler() {
        functionMap = new HashMap<>();

        String listClass = "io/github/whetfire/lateral/LinkedList";
        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";

        CONS = new InvokeStatic(langClass,
                        "cons",
                        String.format("(L%s;L%s;)L%s;", objClass, objClass, objClass), -1);
        functionMap.put(Symbol.makeSymbol("cons"), CONS);
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

    public MethodBuilder compileQuote(Object ast, MethodBuilder methodBuilder) {
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.makeList(new LinkedList(ast, null), null));
        while(!stack.isEmpty()) {
            LinkedList pair = stack.pop();
            LinkedList val = (LinkedList) pair.getValue();
            if(val == null) {
                if(pair.getNext().getValue() != null) {
                    methodBuilder.insertOpCode(ByteCodes.ACONST_NULL);
                    for (int i = 0; i < LinkedList.length((LinkedList) pair.getNext().getValue()); i++) {
                        methodBuilder.insertOpCode(CONS);
                    }
                } else {
                    break;
                }
            } else if (val.getValue() instanceof LinkedList) {
                // push parent expression back onto the stack
                stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                stack.push(LinkedList.makeList(val.getValue(), val.getValue()));
            } else {
                methodBuilder.insertOpCode(new Ldc(methodBuilder.parentClass,
                        new ConstantPool.StringInfo(val.getValue().toString())));
                methodBuilder.insertOpCode(MAKE_SYM);
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        return methodBuilder;
    }

    public MethodBuilder compile(Object object, MethodBuilder methodBuilder) {
        if (object instanceof Integer) {
            methodBuilder.insertOpCode(new IntConst((int) object));
            methodBuilder.insertOpCode(PARSE_INT);
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
                // push parent expression back onto the stack
                stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                LinkedList expr = (LinkedList) val.getValue();
                Object head = expr.getValue();
                expr = expr.getNext();
                // switch on head here for special forms:
                // quote, and, or, if/cond, let, lambda, def, defmacro
                if (IF_SYM.equals(head)) {
                    // TODO: extend to if-else with arbitrary number of branches
                    Label elseLab = new Label();
                    Label endLab = new Label();

                    // TEST
                    // System.out.println("test: " + LinkedList.second(expr));
                    compile(LinkedList.first(expr), methodBuilder);
                    methodBuilder.insertOpCode(new IfNull(elseLab));
                    // TRUE BRANCH
                    // System.out.println("true: " + LinkedList.third(expr));
                    compile(LinkedList.second(expr), methodBuilder);
                    methodBuilder.insertOpCode(new Goto(endLab));
                    // ELSE BRANCH
                    // System.out.println("false: " + LinkedList.fourth(expr));
                    methodBuilder.insertOpCode(elseLab);
                    compile(LinkedList.third(expr), methodBuilder);
                    // END
                    methodBuilder.insertOpCode(endLab);
                } else if (OR_SYM.equals(head) || AND_SYM.equals(head)) {
                    // or: return first true value
                    // and : return first false value
                    if (expr == null) {
                        // empty expressions
                        if (OR_SYM.equals(head))
                            methodBuilder.insertOpCode(ByteCodes.ACONST_NULL);
                        else
                            methodBuilder.insertOpCode(PUSH_TRUE);
                    } else {
                        Label endLab = new Label();
                        while (expr.getNext() != null) {
                            compile(expr.getValue(), methodBuilder);
                            methodBuilder.insertOpCode(ByteCodes.DUP);
                            if (OR_SYM.equals(head))
                                methodBuilder.insertOpCode(new IfNonNull(endLab));
                            else // AND
                                methodBuilder.insertOpCode(new IfNull(endLab));
                            methodBuilder.insertOpCode(ByteCodes.POP);
                            expr = expr.getNext();
                        }
                        // when result depends on last element
                        compile(expr.getValue(), methodBuilder);
                        methodBuilder.insertOpCode(endLab);
                    }
                } else if (QUOTE_SYM.equals(head)) {
                    compileQuote(expr.getValue(), methodBuilder);
                } else {
                    // recurse on inner list normally
                    stack.push(LinkedList.makeList(((LinkedList) val.getValue()).getNext(), val.getValue()));
                }
            } else {
                compile(val.getValue(), methodBuilder);
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        return methodBuilder;
    }

    MethodBuilder compileMethod(ClassBuilder builder, Object ast) {
        MethodBuilder methodBuilder = compile(ast, new MethodBuilder(builder));
        methodBuilder.insertOpCode(ByteCodes.ARETURN);
        return methodBuilder;
    }

    public static void main(String[] args) throws FileNotFoundException {
        Compiler c = new Compiler();
        // var target = new StringLispReader("(+ 1 (* 2 3))").readForm();
        Object target = new FileLispReader("./src/lisp/test.lisp").readForm();
        System.out.println(target);
        ClassBuilder cgen = new ClassBuilder();
        MethodBuilder mgen = c.compileMethod(cgen, target);
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
