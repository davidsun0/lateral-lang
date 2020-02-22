package io.github.whetfire.lateral;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.*;

public class Compiler {
    Map<Symbol, LinkedList> functionMap;
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

    static LinkedList CONS;

    static Symbol QUOTE_SYM = Symbol.makeSymbol("quote");
    static Symbol IF_SYM = Symbol.makeSymbol("if");
    static Symbol AND_SYM = Symbol.makeSymbol("and");
    static Symbol OR_SYM = Symbol.makeSymbol("or");
    static Symbol DEFUN_SYM = Symbol.makeSymbol("defun");
    static Symbol DEFMACRO_SYM = Symbol.makeSymbol("defmacro");
    static Symbol LET_SYM = Symbol.makeSymbol("let");

    MethodBuilder methodBuilder;

    static {
        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";
        CONS = LinkedList.makeList(Keyword.makeKeyword("invokestatic"),
                langClass, "cons", methodSignature(2));
    }

    Compiler() {
        functionMap = new HashMap<>();

        String langClass = "io/github/whetfire/lateral/Lang";
        String objClass = "java/lang/Object";

        insertFuncall(Symbol.makeSymbol("cons"), CONS);
        insertFuncall(Symbol.makeSymbol("car"),
                LinkedList.makeList(Keyword.makeKeyword("invokestatic"), langClass, "car", methodSignature(1)));
        insertFuncall(Symbol.makeSymbol("cdr"),
                LinkedList.makeList(Keyword.makeKeyword("invokestatic"), langClass, "cdr", methodSignature(1)));
        insertFuncall(Symbol.makeSymbol("class"),
                LinkedList.makeList(Keyword.makeKeyword("invokestatic"), langClass, "class", methodSignature(1)));
    }

    void insertFuncall(Symbol name, Object ... values) {
        functionMap.put(name, LinkedList.makeList(values));
    }

    LinkedList resolveFuncall(Symbol name) {
        var ret = functionMap.get(name);
        if(ret == null) {
            throw new RuntimeException("Couldn't resolve function " + name);
        } else {
            return ret;
        }
    }

    public MethodBuilder compileQuote(Object ast) {
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.makeList(new LinkedList(ast, null), null));
        while(!stack.isEmpty()) {
            LinkedList pair = stack.pop();
            LinkedList val = (LinkedList) pair.getValue();
            if(val == null) {
                // build a list from symbols on the stack
                if(pair.getNext().getValue() != null) {
                    methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
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
                Object atom = val.getValue();
                // construct symbol
                if(atom instanceof Symbol) {
                    methodBuilder.insertOpCode(Keyword.makeKeyword("ldc"), atom.toString());
                    methodBuilder.insertOpCode(MAKE_SYM);
                } else if(atom instanceof Integer) {
                    methodBuilder.insertOpCode(Keyword.makeKeyword("iconst"), atom);
                    methodBuilder.insertOpCode(PARSE_INT);
                } else {
                    throw new RuntimeException("Can't quote " + atom);
                }
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        return methodBuilder;
    }

    public MethodBuilder compile(Object object, ArrayList<Symbol> locals) {
        if(object instanceof Symbol) {
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
                if (object.equals(locals.get(i))) {
                    index = i;
                    break;
                }
            }
            // symbol exists in locals / environment
            if (index >= 0) {
                methodBuilder.insertOpCode(Keyword.makeKeyword("aload"), index);
            } else if(Symbol.NIL_SYMBOL.equals(object)) {
                methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
            } else if(Symbol.TRUE_SYMBOL.equals(object)) {
                methodBuilder.insertOpCode(PUSH_TRUE);
            } else {
                throw new RuntimeException("Can't find symbol in environment: " + object);
            }
        } else if (object instanceof Integer) {
            methodBuilder.insertOpCode(Keyword.makeKeyword("iconst"), object);
            methodBuilder.insertOpCode(PARSE_INT);
        } else if (object instanceof LinkedList) {
            return compile((LinkedList) object, locals);
        } else if (object instanceof String) {
            methodBuilder.insertOpCode(Keyword.makeKeyword("ldc"), object);
        } else if(object instanceof Keyword) {
            methodBuilder.insertOpCode(Keyword.makeKeyword("ldc"), object.toString());
            methodBuilder.insertOpCode(MAKE_SYM);
        } else {
            throw new RuntimeException("Can't convert to bytecode: " + object);
        }
        return methodBuilder;
    }

    public MethodBuilder compile(LinkedList ast, ArrayList<Symbol> locals) {
        // saves a list of pointers to parents of the working node
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.makeList(new LinkedList(ast, null), null));
        while(!stack.isEmpty()) {
            LinkedList pair = stack.pop();
            LinkedList val = (LinkedList) pair.getValue();
            if(val == null) {
                LinkedList prev = (LinkedList) pair.getNext().getValue();
                if (prev == null){
                    // reached base of the syntax tree
                    break;
                } else if(!(prev.getValue() instanceof Symbol)) {
                    throw new SyntaxException(String.format("%s can't be used as a function", prev.getValue()));
                }
                Symbol funcall = (Symbol)prev.getValue();
                for(Object opcode : resolveFuncall(funcall)) {
                    methodBuilder.insertOpCode(opcode);
                }
            } else if (val.getValue() instanceof LinkedList) {
                // push parent expression back onto the stack
                stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                LinkedList expr = (LinkedList) val.getValue();
                Object head = expr.getValue();
                expr = expr.getNext();
                // switch on head here for special forms:
                // quote, and, or, if/cond, let, lambda, def, defmacro
                if (QUOTE_SYM.equals(head)) {
                    // TODO: assert that quote only has 1 argument
                    compileQuote(expr.getValue());
                } else if (IF_SYM.equals(head)) {
                    if(LinkedList.length(expr) != 3){
                        throw new SyntaxException("if expects 3 arguments in body: test, then, else.");
                    }
                    // TODO: extend to if-else with arbitrary number of branches
                    Assembler.JumpLabel elseLab = new Assembler.JumpLabel();
                    Assembler.JumpLabel endLab = new Assembler.JumpLabel();

                    // TEST
                    compile(LinkedList.first(expr), locals);
                    methodBuilder.insertOpCode(Assembler.IFNULL, elseLab);
                    // TRUE BRANCH
                    compile(LinkedList.second(expr), locals);
                    methodBuilder.insertOpCode(Assembler.GOTO, endLab);
                    // ELSE BRANCH
                    methodBuilder.insertOpCode(Assembler.LABEL, elseLab);
                    compile(LinkedList.third(expr), locals);
                    // END
                    methodBuilder.insertOpCode(Assembler.LABEL, endLab);
                } else if (OR_SYM.equals(head) || AND_SYM.equals(head)) {
                    // or: return first true value
                    // and : return first false value
                    if (expr == null) {
                        // empty expressions
                        if (OR_SYM.equals(head))
                            methodBuilder.insertOpCode(Keyword.makeKeyword("aconst_null"));
                        else
                            methodBuilder.insertOpCode(PUSH_TRUE);
                    } else {
                        Assembler.JumpLabel endLab = new Assembler.JumpLabel();
                        while (expr.getNext() != null) {
                            compile(expr.getValue(), locals);
                            methodBuilder.insertOpCode(Keyword.makeKeyword("dup"));
                            if (OR_SYM.equals(head))
                                methodBuilder.insertOpCode(Assembler.IFNONNULL, endLab);
                            // TRUE BRANCH
                            else // AND
                                methodBuilder.insertOpCode(Assembler.IFNULL, endLab);
                            methodBuilder.insertOpCode(Keyword.makeKeyword("pop"));
                            expr = expr.getNext();
                        }
                        // when result depends on last element
                        compile(expr.getValue(), locals);
                        methodBuilder.insertOpCode(endLab);
                    }
                } else if(LET_SYM.equals(head)) {
                    // TODO: assert that let has 2 arguments
                    LinkedList bindList = (LinkedList)LinkedList.first(expr);
                    // TODO: assert correct number and type of vars / bindings
                    int localCount = locals.size();
                    // bind locals
                    while(bindList != null) {
                        int localIndex = -1;
                        Symbol bindSym = (Symbol) bindList.getValue();
                        // if symbol already exists in this let environment, rebind value
                        for(int i = localCount; i < locals.size(); i ++){
                            if(bindSym.equals(locals.get(i))) {
                                localIndex = i;
                                break;
                            }
                        }
                        // otherwise, append symbol to local list
                        if(localIndex < 0) {
                            locals.add(bindSym);
                            localIndex = locals.size() - 1;
                        }
                        compile(bindList.getNext().getValue(), locals);
                        // methodBuilder.insertOpCode(new AStore((byte)localIndex));
                        methodBuilder.insertOpCode(Keyword.makeKeyword("astore"), localIndex);
                        bindList = bindList.getNext().getNext();
                    }
                    // create a frame indicating new local count
                    // methodBuilder.insertOpCode(new LocalLabel(locals.size()));
                    methodBuilder.insertOpCode(Assembler.LOCALLABEL, locals.size());
                    Object body = LinkedList.second(expr);
                    compile(body, locals);
                    // pop locals after body completes
                    locals.subList(localCount, locals.size()).clear();
                    // methodBuilder.insertOpCode(new LocalLabel(localCount));
                    methodBuilder.insertOpCode(Assembler.LOCALLABEL, localCount);
                } else {
                    // not a special form; recurse on inner list normally
                    stack.push(LinkedList.makeList(((LinkedList) val.getValue()).getNext(), val.getValue()));
                }
            } else {
                compile(val.getValue(), locals);
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        return methodBuilder;
    }

    MethodBuilder compileMethod(MethodBuilder methodBuilder, Object ast) {
        if(!(ast instanceof LinkedList)) {
            throw new SyntaxException();
        }

        LinkedList fundef = (LinkedList) ast;
        if(!DEFUN_SYM.equals(LinkedList.first(fundef))) {
            throw new SyntaxException("expected function definition");
        } else if(!(LinkedList.second(fundef) instanceof Symbol)) {
            throw new SyntaxException("name of function must be a Sybmol");
        } else if(LinkedList.third(fundef) != null && !(LinkedList.third(fundef) instanceof LinkedList)) {
            throw new SyntaxException("parameter list of function must be a LinkedList");
        }
        Symbol funame = (Symbol) LinkedList.second(fundef);
        LinkedList params = (LinkedList) LinkedList.third(fundef);
        Object body = LinkedList.fourth(fundef);

        this.methodBuilder = methodBuilder;
        methodBuilder.setName(funame);
        methodBuilder.setArgCount(LinkedList.length(params));

        ArrayList<Symbol> locals = new ArrayList<>();
        while(params != null) {
            locals.add((Symbol) params.getValue());
            params = params.getNext();
        }

        compile(body, locals);
        methodBuilder.insertOpCode(Keyword.makeKeyword("areturn"));
        return methodBuilder;
    }

    MethodBuilder compileMacro(MethodBuilder methodBuilder, Object ast) {
        return methodBuilder;
    }

    public void importFunction(Object object) {
        if(object instanceof LinkedList) {
            LinkedList expr = (LinkedList) object;
            if(DEFUN_SYM.equals(expr.getValue()) || DEFMACRO_SYM.equals(expr.getValue())) {
                Symbol name = (Symbol)LinkedList.second(expr);
                int paramCount = LinkedList.length((LinkedList) LinkedList.third(expr));
                //insertFuncall(name, new InvokeStatic("MyClass", name.toString(), methodSignature(paramCount),
                        //1 - paramCount));
                insertFuncall(name, LinkedList.makeList(Keyword.makeKeyword("invokestatic"),
                        "MyClass", name.toString(), methodSignature(paramCount)));
            }
        }
    }

    public static ClassBuilder compileFile(String path) throws FileNotFoundException {
        ClassBuilder classBuilder = new ClassBuilder();
        Compiler compiler = new Compiler();
        LispReader reader = new FileLispReader(path);
        ArrayList<Object> forms = new ArrayList<>();
        {
            Object form;
            while ((form = reader.readForm()) != null) {
                forms.add(form);
            }
        }
        // TODO: syntax checking
        // extract method names and signatures
        for(Object form : forms) {
            compiler.importFunction(form);
        }

        /*
        // first compile macros
        for(Object form : forms) {
            compiler.compileMacro(classBuilder.makeMethodBuilder(), form);
        }
        */

        // then compile functions
        for(Object form : forms) {
            compiler.compileMethod(classBuilder.makeMethodBuilder(), form);
        }
        return classBuilder;
    }

    public static String methodSignature(int count) {
        // should be refactored to somewhere more convenient
        // Also used by MethodBuilder
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for(int i = 0; i < count; i ++) {
            sb.append("Ljava/lang/Object;");
        }
        sb.append(")Ljava/lang/Object;");
        return sb.toString();
    }

    public static void main(String[] args) throws FileNotFoundException {
        ClassBuilder cgen = Compiler.compileFile("./src/lisp/test.lisp");
        cgen.writeToFile("MyClass.class");
        //*
        try {
            Class<?> clazz = new LClassLoader().defineClass(cgen.toBytes());
            Method m = clazz.getMethod("main", (Class<?>[])null);
            System.out.println(m);
            System.out.println("=> " + m.invoke(null));
        } catch (Exception e){
            e.printStackTrace();
        }
        //*/
    }
}
