package io.github.whetfire.lateral;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Compiler {
    static LinkedList PUSH_TRUE = LinkedList.makeList(
            Keyword.makeKeyword("getstatic"),
            "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;"
    );
    static LinkedList PARSE_INT = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"
    );
    static LinkedList MAKE_SYM = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Symbol", "makeSymbol",
            "(Ljava/lang/String;)Lio/github/whetfire/lateral/Symbol;"
    );
    static LinkedList MAKE_KEY = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Keyword", "makeKeyword",
            "(Ljava/lang/String;)Lio/github/whetfire/lateral/Keyword;"
    );
    static LinkedList CONS;
    static Symbol QUOTE_SYM = Symbol.makeSymbol("quote");
    static Symbol IF_SYM = Symbol.makeSymbol("if");
    static Symbol AND_SYM = Symbol.makeSymbol("and");
    static Symbol OR_SYM = Symbol.makeSymbol("or");
    static Symbol DEF_SYM = Symbol.makeSymbol("def");
    static Symbol DEFUN_SYM = Symbol.makeSymbol("defun");
    static Symbol DEFMACRO_SYM = Symbol.makeSymbol("defmacro");
    static Symbol LET_SYM = Symbol.makeSymbol("let");
    static Symbol ASM_SYM = Symbol.makeSymbol("asm");
    static Symbol LIST_ASM = Symbol.makeSymbol("list");

    static {
        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";
        CONS = LinkedList.makeList(MethodBuilder.INVOKESTATIC,
                langClass, "cons", Lambda.makeMethodSignature(2));
    }

    static class CompilationFrame {
        LinkedList ast; // holds the whole compilation expression
        LinkedList current; // first value in current is the next item to compile

        CompilationFrame(LinkedList ast) {
            this(ast, ast, false);
        }

        CompilationFrame(LinkedList ast, LinkedList current, boolean isTail) {
            this.current = current;
            this.ast = ast;
        }
    }

    private static int classNum = 0;
    static String genClassName() {
        classNum ++;
        return "AnonClass" + classNum;
    }

    private Map<Symbol, LinkedList> functionMap;
    private MethodBuilder methodBuilder;
    private DynamicsManager metaClassLoader;

    Compiler(Environment environment) {
        metaClassLoader = new DynamicsManager();
        // methodBuilder = new MethodBuilder(Symbol.makeSymbol("myFunction"), 0);
        methodBuilder = null;
        functionMap = new HashMap<>();

        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";

        insertFuncall(Symbol.makeSymbol("cons"), CONS);
        insertFuncall(Symbol.makeSymbol("car"),
                LinkedList.makeList(MethodBuilder.INVOKESTATIC, langClass, "car", Lambda.makeMethodSignature(1)));
        insertFuncall(Symbol.makeSymbol("cdr"),
                LinkedList.makeList(MethodBuilder.INVOKESTATIC, langClass, "cdr", Lambda.makeMethodSignature(1)));
        insertFuncall(Symbol.makeSymbol("class"),
                LinkedList.makeList(MethodBuilder.INVOKESTATIC, langClass, "class", Lambda.makeMethodSignature(1)));
    }


    void insertFuncall(Symbol name, Object... values) {
        functionMap.put(name, LinkedList.makeList(values));
    }

    private LinkedList resolveFuncall(Symbol name) {
        LinkedList ret = functionMap.get(name);
        if(ret == null) {
            throw new RuntimeException("Couldn't resolve function " + name);
        } else {
            return ret;
        }
    }

    public void compileQuote(Object ast) {
        Deque<CompilationFrame> stack = new ArrayDeque<>();
        // base frame is special; it doesn't have a base ast value
        // this allows simple objects to be compiled with the same logic that handles lists
        stack.push(new CompilationFrame(new LinkedList(ast, null)));
        while (!stack.isEmpty()) {
            CompilationFrame frame = stack.pop();
            LinkedList expr = frame.current;

            // done with the list on the current level
            if (expr == null) {
                // build a list from symbols on the stack
                if (stack.isEmpty()) {
                    // reached base frame; exit
                    break;
                } else {
                    methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
                    for (int i = 0; i < LinkedList.length(frame.ast); i++) {
                        methodBuilder.insertOpCode(CONS);
                    }
                }
                continue;
            }

            Object value = expr.getValue();
            if (value instanceof LinkedList) {
                // value is a list; recurse.
                // push parent expression back onto the stack
                frame.current = expr.getNext();
                stack.push(frame);
                stack.push(new CompilationFrame((LinkedList) value));
            } else {
                // non-list objects
                if (value == null) {
                    // quoted empty list '()
                    methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
                } else if (value instanceof Symbol) {
                    methodBuilder.insertOpCode(MethodBuilder.LDC, value.toString());
                    methodBuilder.insertOpCode(MAKE_SYM);
                } else if (value instanceof Integer) {
                    methodBuilder.insertOpCode(MethodBuilder.ICONST, value);
                    methodBuilder.insertOpCode(PARSE_INT);
                } else {
                    throw new RuntimeException("Can't quote " + value);
                }
                // update frame
                frame.current = expr.getNext();
                stack.push(frame);
            }
        }
    }

    Lambda isMacroCall(LinkedList expr) {
        Object obj = expr.getValue();
        if(obj instanceof Symbol) {
            Object resource = metaClassLoader.getResource((Symbol) obj);
            if(resource instanceof Lambda) {
                Lambda lambda = (Lambda) resource;
                if(lambda.isMacro()) {
                    return lambda;
                }
            }
        }
        return null;
    }

    Object macroExpand(Object expr) {
        while(true) {
            // macro calls must be linked lists
            if(!(expr instanceof LinkedList)) {
                return expr;
            }

            Lambda macro = isMacroCall((LinkedList) expr);
            if (macro == null) {
                // not a macro call
                return expr;
            } else {
                // loop because macros may evaluate to macros
                expr = macro.invoke(((LinkedList)expr).getNext());
            }
        }
    }

    public void compile(Object ast, ArrayList<Symbol> locals, boolean isTail) {
        ast = macroExpand(ast);
        if(ast instanceof LinkedList) {
            LinkedList expr = (LinkedList) ast;
            Object head = expr.getValue();
            expr = expr.getNext();
            // switch on head here for special forms:
            // quote, and, or, if/cond, let, lambda, def, defmacro
            if (QUOTE_SYM.equals(head)) {
                // TODO: assert that quote only has 1 argument
                compileQuote(expr.getValue());
            } else if(DEF_SYM.equals(head)) {
                // insert into environment
                // macro expand name?
                Symbol name = (Symbol)expr.getValue();
                compileQuote(name);
                compile(LinkedList.second(expr), locals, false);
                // somehow insert into environment
                // return value
                throw new RuntimeException();
            } else if (IF_SYM.equals(head)) {
                if (LinkedList.length(expr) != 3) {
                    throw new SyntaxException("if expects 3 arguments in body: test, then, else.");
                }
                // TODO: extend to if-else with arbitrary number of branches
                MethodBuilder.JumpLabel elseLab = new MethodBuilder.JumpLabel();
                MethodBuilder.JumpLabel endLab = new MethodBuilder.JumpLabel();

                // TEST
                compile(LinkedList.first(expr), locals, false);
                methodBuilder.insertOpCode(MethodBuilder.IFNULL, elseLab);
                // TRUE BRANCH
                compile(LinkedList.second(expr), locals, isTail);
                if(isTail) {
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
                } else {
                    methodBuilder.insertOpCode(MethodBuilder.GOTO, endLab);
                }
                // ELSE BRANCH
                methodBuilder.insertOpCode(MethodBuilder.LABEL, elseLab);
                compile(LinkedList.third(expr), locals, isTail);
                // END
                if(isTail) {
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
                } else {
                    methodBuilder.insertOpCode(MethodBuilder.LABEL, endLab);
                }
            } else if (OR_SYM.equals(head) || AND_SYM.equals(head)) {
                // or: return first true value
                // and : return first false value
                if (expr == null) {
                    // empty expressions
                    if (OR_SYM.equals(head))
                        methodBuilder.insertOpCode(MethodBuilder.ACONST_NULL);
                    else
                        methodBuilder.insertOpCode(PUSH_TRUE);
                } else {
                    MethodBuilder.JumpLabel endLab = new MethodBuilder.JumpLabel();
                    while (expr.getNext() != null) {
                        compile(expr.getValue(), locals, false);
                        methodBuilder.insertOpCode(Keyword.makeKeyword("dup"));
                        if (OR_SYM.equals(head))
                            methodBuilder.insertOpCode(MethodBuilder.IFNONNULL, endLab);
                            // TRUE BRANCH
                        else // AND
                            methodBuilder.insertOpCode(MethodBuilder.IFNULL, endLab);
                        methodBuilder.insertOpCode(Keyword.makeKeyword("pop"));
                        expr = expr.getNext();
                    }
                    // when result depends on last element
                    compile(expr.getValue(), locals, false);
                    methodBuilder.insertOpCode(MethodBuilder.LABEL, endLab);
                    if(isTail) {
                        methodBuilder.insertOpCode(MethodBuilder.ARETURN);
                    }
                }
            } else if (LET_SYM.equals(head)) {
                // TODO: assert that let has 2 arguments
                LinkedList bindList = (LinkedList) LinkedList.first(expr);
                // TODO: assert correct number and type of vars / bindings
                int localCount = locals.size();
                // bind locals
                while (bindList != null) {
                    int localIndex = -1;
                    Symbol bindSym = (Symbol) bindList.getValue();
                    // if symbol already exists in this let environment, rebind value
                    for (int i = localCount; i < locals.size(); i++) {
                        if (bindSym.equals(locals.get(i))) {
                            localIndex = i;
                            break;
                        }
                    }
                    // otherwise, append symbol to local list
                    if (localIndex < 0) {
                        locals.add(bindSym);
                        localIndex = locals.size() - 1;
                    }
                    compile(bindList.getNext().getValue(), locals, false);
                    methodBuilder.insertOpCode(MethodBuilder.ASTORE, localIndex);
                    bindList = bindList.getNext().getNext();
                }
                // create a frame indicating new local count
                methodBuilder.insertOpCode(MethodBuilder.LOCALLABEL, locals.size());
                Object body = LinkedList.second(expr);
                compile(body, locals, isTail);
                // pop locals after body completes
                locals.subList(localCount, locals.size()).clear();
                // not necessary to remove locals? doesn't compile sometimes with let
                // shouldn't be a problem as locals ArrayList is modified
                // methodBuilder.insertOpCode(MethodBuilder.LOCALLABEL, localCount);
            } else if(ASM_SYM.equals(head)) {
                // literal bytecode assembly; inject its values directly
                for (Object o : expr) {
                    if(o instanceof Symbol) {
                        /*
                        way for asm to insert variables
                        not sure if this is a good idea
                         */
                        Symbol symbol = (Symbol) o;
                        int index = -1;
                        for (int i = locals.size() - 1; i >= 0; i--) {
                            if (symbol.equals(locals.get(i))) {
                                index = i;
                                break;
                            }
                        }
                        if (index >= 0) {
                            methodBuilder.insertOpCode(MethodBuilder.ALOAD, index);
                        } else {
                            System.out.println(o);
                            throw new SyntaxException();
                        }
                    } else {
                        // asm command
                        methodBuilder.insertOpCode(o);
                    }
                }
                // TODO: evaluate whether this is a good idea
                // perhaps returning should be up to the user?
                if (isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else if(LIST_ASM.equals(head)) {
                // TODO: out from compiler once varargs are written
                int length = LinkedList.length(expr);
                for(Object o : expr) {
                    compile(o, locals, false);
                }
                methodBuilder.insertOpCode(MethodBuilder.ACONST_NULL);
                for(int i = 0; i < length; i ++) {
                    methodBuilder.insertOpCode(CONS);
                }
                if(isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else {
                // not a special form; recurse on inner list normally

                // look up head
                if(!(head instanceof Symbol)) {
                    throw new SyntaxException(head + " can't be used as a function call");
                }
                LinkedList funcall = resolveFuncall((Symbol) head);
                if(funcall == null) {
                    throw new RuntimeException("function " + head + " couldn't be found");
                }
                // evaluate each sub expression
                for (Object sub : expr) {
                    compile(sub, locals, false);
                }
                // generate head calling code
                for (Object op : funcall) {
                    methodBuilder.insertOpCode(op);
                }
                if(isTail) {
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
                }
            }
        } else {
            if (ast instanceof Symbol) {
                /*
                Iterate backwards through the locals to find the innermost occurrence of a variable.
                For example:
                (let (x 5)
                  (let (x 3)
                    x))
                 => 3
                 In this example, the outer x binding will have local index 0 and the inner binding will have index 1.
                 It may be useful to think of the locals as a stack of bindings.
                 By searching backwards, we find the top most occurrence of the variable in the stack.
                 */
                int index = -1;
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (ast.equals(locals.get(i))) {
                        index = i;
                        break;
                    }
                }
                // symbol exists in locals / environment
                if (index >= 0) {
                    methodBuilder.insertOpCode(MethodBuilder.ALOAD, index);
                } else if (Symbol.NULL_SYMBOL.equals(ast)) {
                    methodBuilder.insertOpCode(MethodBuilder.ACONST_NULL);
                } else if (Symbol.TRUE_SYMBOL.equals(ast)) {
                    methodBuilder.insertOpCode(PUSH_TRUE);
                } else {
                    throw new RuntimeException("Can't find symbol in environment: " + ast);
                }
            } else if (ast instanceof Integer) {
                methodBuilder.insertOpCode(MethodBuilder.ICONST, ast);
                methodBuilder.insertOpCode(PARSE_INT);
            } else if (ast instanceof String) {
                methodBuilder.insertOpCode(MethodBuilder.LDC, ast);
            } else if (ast instanceof Keyword) {
                methodBuilder.insertOpCode(MethodBuilder.LDC, ((Keyword) ast).getValue());
                methodBuilder.insertOpCode(MAKE_KEY);
            } else {
                throw new RuntimeException("Can't convert to bytecode: " + ast);
            }

            if(isTail) {
                methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            }
        }
    }

    public Lambda compileMethod(Object ast) {
        if(!(ast instanceof LinkedList)) {
            throw new SyntaxException();
        }

        LinkedList fundef = (LinkedList) ast;
        boolean isMacro = false;
        if(DEFMACRO_SYM.equals(fundef.getValue())) {
            isMacro = true;
        } else if(!DEFUN_SYM.equals(fundef.getValue())){
            throw new SyntaxException("expected function definition");
        }

        fundef = fundef.getNext();
        Symbol funName;
        if(fundef.getValue() instanceof Symbol) {
            funName = (Symbol) fundef.getValue();
        } else {
            throw new SyntaxException("name of function must be a Sybmol");
        }

        fundef = fundef.getNext();
        LinkedList params;
        if(fundef.getValue() == null || fundef.getValue() instanceof LinkedList) {
            params = (LinkedList) fundef.getValue();
        } else {
            throw new SyntaxException("parameter list of function must be a LinkedList");
        }
        int paramCount = LinkedList.length(params);

        fundef = fundef.getNext();
        if(fundef == null) {
            throw new RuntimeException("function can't have an empty body");
        }
        Object body = fundef.getValue();

        methodBuilder = new MethodBuilder(funName, paramCount);

        ArrayList<Symbol> locals = new ArrayList<>();
        while(params != null) {
            locals.add((Symbol) params.getValue());
            params = params.getNext();
        }

        compile(body, locals, true);
        Lambda method = metaClassLoader.putMethod(methodBuilder, isMacro);
        // Lambda wrapper = new Lambda(m, isMacro);
        functionMap.put(funName, LinkedList.makeList(method.makeInvoker()));
        return method;
    }

    public Object compileTopLevel(Object ast) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if(ast instanceof LinkedList) {
            LinkedList astList = (LinkedList) ast;
            if (DEFUN_SYM.equals(astList.getValue()) || DEFMACRO_SYM.equals(astList.getValue())) {
                return compileMethod(ast);
            }
        }
        methodBuilder = new MethodBuilder(Symbol.makeSymbol("main"), 0);
        compile(ast, new ArrayList<>(), true);
        // methodBuilder.printCodes();
        ClassBuilder builder = new ClassBuilder(genClassName());
        builder.addMethod(methodBuilder);
        Class<?> clazz = metaClassLoader.defineTemporaryClass(builder.toBytes());
        try {
            Method m = clazz.getMethod("main", (Class<?>[]) null);
            return m.invoke(null);
        } catch (Exception | Error e) {
            methodBuilder.printCodes();
            builder.writeToFile("MyClass.class");
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        Environment environment = new Environment();
        Compiler compiler = new Compiler(environment);
        LispReader reader = new FileLispReader("./src/lisp/test.lisp");

        Object form;
        while ((form = reader.readForm()) != null) {
            try {
                System.out.println(compiler.compileTopLevel(form));
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
