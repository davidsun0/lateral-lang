package io.github.whetfire.lateral;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Compiler {
    static Sequence PUSH_TRUE = LinkedList.makeList(
            Keyword.makeKeyword("getstatic"),
            "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;"
    );
    static Sequence EMPTY_LIST = LinkedList.makeList(
            Keyword.makeKeyword("getstatic"),
            "io/github/whetfire/lateral/EmptySequence", "EMPTY_SEQUENCE",
            "Lio/github/whetfire/lateral/Sequence;"
    );

    static Sequence PARSE_INT = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"
    );
    static Sequence MAKE_SYM = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Symbol", "makeSymbol",
            "(Ljava/lang/String;)Lio/github/whetfire/lateral/Symbol;"
    );
    static Sequence MAKE_KEY = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Keyword", "makeKeyword",
            "(Ljava/lang/String;)Lio/github/whetfire/lateral/Keyword;"
    );

    static Sequence ENVIR_LOOKUP = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Environment", "get",
            "(Lio/github/whetfire/lateral/Symbol;)Ljava/lang/Object;"
    );
    static Sequence ENVIR_STORE = LinkedList.makeList(
            Keyword.makeKeyword("invokestatic"),
            "io/github/whetfire/lateral/Environment", "insert",
            "(Lio/github/whetfire/lateral/Symbol;Ljava/lang/Object;)Ljava/lang/Object;"
    );

    static Sequence CONS;
    static Symbol QUOTE_SYM = Symbol.makeSymbol("quote");

    static Symbol IF_SYM = Symbol.makeSymbol("if");
    static Symbol AND_SYM = Symbol.makeSymbol("and");
    static Symbol OR_SYM = Symbol.makeSymbol("or");

    static Symbol DEF_SYM = Symbol.makeSymbol("def");
    static Symbol DEFUN_SYM = Symbol.makeSymbol("defun");
    static Symbol DEFMACRO_SYM = Symbol.makeSymbol("defmacro");
    static Symbol LET_SYM = Symbol.makeSymbol("let");

    static Symbol ASM_SYM = Symbol.makeSymbol("asm");
    static Symbol DEASM_SYM = Symbol.makeSymbol("de-asm");
    static Symbol LIST_ASM = Symbol.makeSymbol("list");

    static {
        CONS = LinkedList.makeList(MethodBuilder.INVOKESTATIC,
                "io/github/whetfire/lateral/Sequence", "cons",
                "(Ljava/lang/Object;Lio/github/whetfire/lateral/Sequence;)Lio/github/whetfire/lateral/Sequence;"
        );
    }

    static class CompilationFrame {
        Sequence ast; // holds the whole compilation expression
        Sequence current; // first value in current is the rest item to compile

        CompilationFrame(Sequence ast) {
            this.ast = ast;
            this.current = ast;
        }
    }

    private static int classNum = 0;
    static String genClassName() {
        classNum ++;
        return "AnonClass" + classNum;
    }

    private MethodBuilder methodBuilder;

    Compiler() {
        methodBuilder = null;
    }


    public void compileQuote(Object ast) {
        Deque<CompilationFrame> stack = new ArrayDeque<>();
        // base frame is special; it doesn't have a base ast value
        // this allows simple objects to be compiled with the same logic that handles lists
        stack.push(new CompilationFrame(new LinkedList(ast)));
        while (!stack.isEmpty()) {
            CompilationFrame frame = stack.pop();
            Sequence expr = frame.current;

            // done with the list on the current level
            if (expr.isEmpty()) {
                // build a list from symbols on the stack
                if (stack.isEmpty()) {
                    // reached base frame; exit
                    break;
                } else {
                    methodBuilder.insertOpCode(EMPTY_LIST);
                    for (int i = 0; i < frame.ast.length(); i++) {
                        methodBuilder.insertOpCode(CONS);
                    }
                }
                continue;
            }

            Object value = expr.first();
            if (value instanceof Sequence && !((Sequence) value).isEmpty()) {
                // value is a list; recurse.
                // push parent expression back onto the stack
                frame.current = expr.rest();
                stack.push(frame);
                stack.push(new CompilationFrame((Sequence) value));
            } else {
                // non-list objects
                if(value == null) {
                    methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
                } else if (value.equals(EmptySequence.EMPTY_SEQUENCE)) {
                    // quoted empty list '()
                    methodBuilder.insertOpCode(EMPTY_LIST);
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
                frame.current = expr.rest();
                stack.push(frame);
            }
        }
    }

    Lambda isMacroCall(Sequence expr) {
        Object obj = expr.first();
        if(obj instanceof Symbol) {
            Object resource = Environment.getIfExists((Symbol) obj);
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
            if(!(expr instanceof Sequence)) {
                return expr;
            }

            Lambda macro = isMacroCall((Sequence) expr);
            if (macro == null) {
                // not a macro call
                return expr;
            } else {
                // loop because macros may evaluate to macros
                expr = macro.invoke(((Sequence)expr).rest());
            }
        }
    }

    public void compile(Object ast, ArrayList<Symbol> locals, boolean isTail) {
        ast = macroExpand(ast);
        if(ast instanceof Sequence) {
            Sequence expr = (Sequence) ast;
            Object head = expr.first();
            expr = expr.rest();
            // switch on head here for special forms:
            // quote, and, or, if/cond, let, lambda, def, defmacro
            if (expr.isEmpty()) {
                // special case of empty list
                methodBuilder.insertOpCode(EMPTY_LIST);
                if(isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else if (QUOTE_SYM.equals(head)) {
                // TODO: assert that quote only has 1 argument
                compileQuote(expr.first());
                if(isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else if(DEF_SYM.equals(head)) {
                // insert into environment
                // macro expand name?
                Symbol name = (Symbol)expr.first();
                compileQuote(name);
                compile(expr.second(), locals, false);
                // somehow insert into environment
                // return value
                methodBuilder.insertOpCode(ENVIR_STORE);
                if(isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else if (IF_SYM.equals(head)) {
                if (expr.length() != 3) {
                    throw new SyntaxException("if expects 3 arguments in body: test, then, else.");
                }
                // TODO: extend to if-else with arbitrary number of branches
                Symbol elseLab = Symbol.gensym("else");
                Symbol endLab = Symbol.gensym("end");

                // TEST
                compile(expr.first(), locals, false);
                methodBuilder.insertOpCode(MethodBuilder.IFNULL, elseLab);
                // TRUE BRANCH
                compile(expr.second(), locals, isTail);
                if(!isTail) {
                    // if tail, the inner expression will return automatically
                    methodBuilder.insertOpCode(MethodBuilder.GOTO, endLab);
                }
                // ELSE BRANCH
                methodBuilder.insertOpCode(MethodBuilder.LABEL, elseLab);
                compile(expr.third(), locals, isTail);
                // END
                if(!isTail) {
                    // if tail, the inner expression will return automatically
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
                    // MethodBuilder.JumpLabel endLab = new MethodBuilder.JumpLabel();
                    Symbol endLab = Symbol.gensym("end");
                    while (expr.rest() != null) {
                        compile(expr.first(), locals, false);
                        methodBuilder.insertOpCode(Keyword.makeKeyword("dup"));
                        if (OR_SYM.equals(head))
                            methodBuilder.insertOpCode(MethodBuilder.IFNONNULL, endLab);
                            // TRUE BRANCH
                        else // AND
                            methodBuilder.insertOpCode(MethodBuilder.IFNULL, endLab);
                        methodBuilder.insertOpCode(Keyword.makeKeyword("pop"));
                        expr = expr.rest();
                    }
                    // when result depends on last element
                    compile(expr.first(), locals, false);
                    methodBuilder.insertOpCode(MethodBuilder.LABEL, endLab);
                    if(isTail) {
                        methodBuilder.insertOpCode(MethodBuilder.ARETURN);
                    }
                }
            } else if (LET_SYM.equals(head)) {
                // TODO: assert that let has 2 arguments
                Sequence bindList = (Sequence) expr.first();
                // TODO: assert correct number and type of vars / bindings
                int localCount = locals.size();
                // bind locals
                while(!bindList.isEmpty()) {
                    int localIndex = -1;
                    Symbol bindSym = (Symbol) bindList.first();
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
                    compile(bindList.rest().first(), locals, false);
                    methodBuilder.insertOpCode(MethodBuilder.ASTORE, localIndex);
                    bindList = bindList.rest().rest();
                }
                // create a frame indicating new local count
                methodBuilder.insertOpCode(MethodBuilder.LOCALLABEL, locals.size());
                Object body = expr.second();
                compile(body, locals, isTail);
                // pop locals after body completes
                locals.subList(localCount, locals.size()).clear();
                // not necessary to remove locals? doesn't compile sometimes with let
                // shouldn't be a problem as locals ArrayList is modified
                // methodBuilder.insertOpCode(MethodBuilder.LOCALLABEL, localCount);
            } else if(ASM_SYM.equals(head)) {
                // literal bytecode assembly; inject its values directly
                // TODO: better de-asm with tree traversal
                for (Object o : expr) {
                    if(o instanceof Sequence && DEASM_SYM.equals(((Sequence) o).first())) {
                        // deasm cancels out asm and compilation continues normally
                        Object deasm = ((Sequence) o).rest().first();
                        // should be asm writer's responsibility to determine when to return
                        compile(deasm, locals, false);
                    } else {
                        // asm command
                        methodBuilder.insertOpCode(o);
                    }
                }
                // treating asm like any other expression makes the expression blend in with the language better
                // making sure that the expression return only one value is up to the programmer
                if (isTail)
                    methodBuilder.insertOpCode(MethodBuilder.ARETURN);
            } else if(LIST_ASM.equals(head)) {
                // TODO: out from compiler once varargs are written
                for(Object o : expr) {
                    compile(o, locals, false);
                }

                methodBuilder.insertOpCode(EMPTY_LIST);
                for(int i = 0; i < expr.length(); i ++) {
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
                Object funObj = Environment.get((Symbol) head);
                Lambda funcall;
                if(funObj instanceof Lambda) {
                    funcall = (Lambda) funObj;
                } else {
                    throw new RuntimeException(head + " can't be used as a function call");
                }
                // evaluate each sub expression
                if(expr != null) {
                    for (Object sub : expr) {
                        compile(sub, locals, false);
                    }
                }

                if(funcall.isVarargs) {
                    // methodBuilder.insertOpCode(MethodBuilder.ACONST_NULL);
                    methodBuilder.insertOpCode(EMPTY_LIST);
                    for(int i = funcall.argCount; i < expr.length() + 1; i ++) {
                        methodBuilder.insertOpCode(CONS);
                    }
                }
                // generate head calling code
                methodBuilder.insertOpCode(funcall.invoker);
                /*
                for (Object op : funcall) {
                    methodBuilder.insertOpCode(op);
                }
                */
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
                    // have the class look up the symbol in the environment
                    methodBuilder.insertOpCode(MethodBuilder.LDC, ast.toString());
                    methodBuilder.insertOpCode(MAKE_SYM);
                    methodBuilder.insertOpCode(ENVIR_LOOKUP);
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
        if(!(ast instanceof Sequence)) {
            throw new SyntaxException();
        }

        Sequence fundef = (Sequence) ast;
        boolean isMacro = false;
        if(DEFMACRO_SYM.equals(fundef.first())) {
            isMacro = true;
        } else if(!DEFUN_SYM.equals(fundef.first())){
            throw new SyntaxException("expected function definition");
        }

        fundef = fundef.rest();
        Symbol funName;
        if(fundef.first() instanceof Symbol) {
            funName = (Symbol) fundef.first();
        } else {
            throw new SyntaxException("name of function must be a Sybmol");
        }

        fundef = fundef.rest();
        Sequence params;
        if(fundef.first() == null || fundef.first() instanceof Sequence) {
            params = (Sequence) fundef.first();
        } else {
            throw new SyntaxException("parameter list of function must be a Sequence");
        }
        int paramCount = params.length();
        boolean isVarargs = false;
        if(paramCount >= 2 && params.nth(paramCount - 2).equals(Keyword.makeKeyword("rest"))) {
            paramCount --;
            isVarargs = true;
            Object[] newParams = new Object[paramCount];
            for(int i = 0; i < paramCount; i ++) {
                newParams[i] = params.first();
                params = params.rest();
            }
            newParams[paramCount - 1] = params.first();
            params = LinkedList.makeList(newParams);
        }
        methodBuilder = new MethodBuilder(funName, paramCount, isMacro, isVarargs);

        fundef = fundef.rest();
        if(fundef == null) {
            throw new RuntimeException("function can't have an empty body");
        }
        Object body = fundef.first();

        ArrayList<Symbol> locals = new ArrayList<>();
        while(!params.isEmpty()) {
            locals.add((Symbol) params.first());
            params = params.rest();
        }

        compile(body, locals, true);
        return Environment.insertMethod(funName, methodBuilder);
    }

    public Object compileTopLevel(Object ast) throws VerifyError, InvocationTargetException {
        if(ast instanceof LinkedList) {
            LinkedList astList = (LinkedList) ast;
            if (DEFUN_SYM.equals(astList.first()) || DEFMACRO_SYM.equals(astList.first())) {
                return compileMethod(ast);
            }
        }
        methodBuilder = new MethodBuilder(Symbol.makeSymbol("main"), 0);
        compile(ast, new ArrayList<>(), true);
        String className = genClassName();
        ClassBuilder builder = new ClassBuilder(className);
        builder.addMethod(methodBuilder);
        Class<?> clazz = Environment.defineTemporaryClass(builder);
        try {
            Method m = clazz.getMethod("main", (Class<?>[]) null);
            return m.invoke(null);
            // } catch (NoSuchMethodException | IllegalAccessException e) {
        } catch (VerifyError e) {
            builder.writeToFile("./" + className + ".class");
            methodBuilder.printCodes();
            e.printStackTrace();
            return null;
        } catch (NoSuchMethodException | IllegalAccessException eb) {
            eb.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        Compiler compiler = new Compiler();
        LispReader lispReader = LispReader.fileReader("./src/lisp/test.lisp");

        long start = System.currentTimeMillis();
        Object form;
        while ((form = lispReader.readForm()) != null) {
            try {
                System.out.println(compiler.compileTopLevel(form));
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        long delta = System.currentTimeMillis() - start;
        System.out.println(delta);
    }
}
