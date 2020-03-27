package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Compiler {
    static Symbol LAMBDA = Symbol.makeSymbol("function");
    static Symbol DEFMACRO = Symbol.makeSymbol("defmacro");
    static Symbol DEFUN = Symbol.makeSymbol("defun");
    static Symbol DEFINE = Symbol.makeSymbol("def");
    static Symbol ASM = Symbol.makeSymbol("asm-quote");
    static Symbol DEASM = Symbol.makeSymbol("unquote");
    static Symbol LET = Symbol.makeSymbol("let");
    static Symbol IF = Symbol.makeSymbol("if");
    static Symbol QUOTE = Symbol.makeSymbol("quote");
    static Symbol LIST = Symbol.makeSymbol("list");

    static Symbol DEFMETHOD = Symbol.makeSymbol("defmethod");
    static Symbol DEFCLASS = Symbol.makeSymbol("defclass");
    static Symbol DEFFIELD = Symbol.makeSymbol("deffield");

    static Keyword REST = Keyword.makeKeyword("rest");

    static Sequence PARSE_INT = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Integer.class),
            "valueOf", Assembler.getMethodDescriptor(int.class, Integer.class)
    );

    // Environment.dynamicObject
    // TODO: convert to dynamic LDC
    static Sequence ENVIR_OBJECT = new ArraySequence(
            Type.getInternalName(Environment.class), "dynamicObject",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                    MethodType.class, String.class).toMethodDescriptorString()
    );

    // Environment.dynamicFunction
    static Sequence ENVIR_FUNCTION = new ArraySequence(
            Type.getInternalName(Environment.class), "dynamicFunction",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                                MethodType.class, String.class).toMethodDescriptorString()
    );

    // Sequence.sequenceBuilder
    static Sequence SEQUENCE_BOOTSTRAP = new ArraySequence(
            Type.getInternalName(Sequence.class), "sequenceBuilder",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                    MethodType.class).toMethodDescriptorString()
    );

    private static class CompClass {
        static int CLASS_NUM = 0;

        String name;
        ArrayList<Object> methods;
        HashSet<Symbol> captured;
        boolean isMacro = false;

        CompClass() {
            this(EmptySequence.EMPTY_SEQUENCE);
        }

        CompClass(Sequence params) {
            methods = new ArrayList<>();
            captured = new HashSet<>();
            this.name = "AnonFunc" + (CLASS_NUM ++);
        }

        String getName() {
            return this.name;
        }

        String getConstructor() {
            return Assembler.getMethodDescriptor(void.class, captured.size());
        }

        ArrayList<Symbol> generateConstructor() {
            ArrayList<Object> opcodes = new ArrayList<>();
            opcodes.add(Sequence.makeList(Assembler.ALOAD, 0));
            opcodes.add(Sequence.makeList(
                    Assembler.INVOKESPECIAL,
                    "io/github/whetfire/lateral/Function",
                    "<init>",
                    "()V"
            ));

            ArrayList<Symbol> constructorArguments = new ArrayList<>();
            opcodes.add(Sequence.makeList(Assembler.ALOAD, 0));
            int localSlotNum = 1;
            for(Symbol sym : captured) {
                // generate byecode to set fields
                opcodes.add(Assembler.DUP);
                opcodes.add(Sequence.makeList(Assembler.ALOAD, localSlotNum));
                opcodes.add(Sequence.makeList(
                        Assembler.PUTFIELD,
                        name, sym.toString(), Type.getDescriptor(Object.class)
                ));
                // also generate fields
                methods.add(Sequence.makeList(DEFFIELD, sym.toString(), Type.getDescriptor(Object.class)));
                // list of arguments to give back to closing function (to preserve order)
                constructorArguments.add(sym);
                localSlotNum ++;
            }
            opcodes.add(Assembler.RETURN);

            // bundle into method
            Sequence header = Sequence.makeList(
                    DEFMETHOD, "<init>",
                    getConstructor(), EmptySequence.EMPTY_SEQUENCE
            );
            Sequence body = Sequence.makeList(opcodes.toArray());
            methods.add(Sequence.concat(new ArraySequence(header, body)));
            return constructorArguments;
        }

        void generateInvoker(int paramCount, boolean isVarargs, ArrayList<Object> opcodes) {
            Class<?>[] params = Assembler.getParameterClasses(paramCount);
            if(isVarargs)
                params[params.length - 1] = Sequence.class;

            Sequence header = Sequence.makeList(
                    DEFMETHOD, "invoke",
                    MethodType.methodType(Object.class, params).toMethodDescriptorString(),
                    // Assembler.getMethodDescriptor(Object.class, paramCount),
                    EmptySequence.EMPTY_SEQUENCE
            );

            Sequence body = new ArraySequence(opcodes.toArray());
            methods.add(Sequence.concat(new ArraySequence(header, body)));
        }

        void generateInherits(boolean isMacro, boolean isVarargs, int paramCount) {
            this.isMacro = isMacro;
            methods.add(Sequence.makeList(
                    DEFMETHOD, "isMacro", "()Z", EmptySequence.EMPTY_SEQUENCE,
                    Sequence.makeList(Assembler.ICONST, isMacro ? 1 : 0),
                    Assembler.IRETURN
            ));

            methods.add(Sequence.makeList(
                    DEFMETHOD, "isVarargs", "()Z", EmptySequence.EMPTY_SEQUENCE,
                    Sequence.makeList(Assembler.ICONST, isVarargs ? 1 : 0),
                    Assembler.IRETURN
            ));

            methods.add(Sequence.makeList(
                    DEFMETHOD, "paramCount", "()I", EmptySequence.EMPTY_SEQUENCE,
                    Sequence.makeList(Assembler.ICONST, paramCount),
                    Assembler.IRETURN
            ));

            // APPLY GENERATOR IS NOT FUN TO WRITE IN JAVA
            ArrayList<Object> applyOps = new ArrayList<>();
            applyOps.add(DEFMETHOD);
            applyOps.add("apply");
            applyOps.add("([Ljava/lang/Object;)Ljava/lang/Object;");
            applyOps.add(EmptySequence.EMPTY_SEQUENCE);

            applyOps.add(Sequence.makeList(Assembler.ALOAD, 1));
            applyOps.add(Assembler.ARRAYLENGTH);
            applyOps.add(Sequence.makeList(Assembler.ICONST, paramCount));
            Symbol exceptionLabel = Symbol.makeSymbol("exception");
            // TODO: convert to tableswitch for multi-arity functions
            applyOps.add(Sequence.makeList(Assembler.IF_ICMPNE, exceptionLabel));
            // applyOps.add(ArraySequence.makeList())
            applyOps.add(Sequence.makeList(Assembler.ALOAD, 0));
            for(int i = 0; i < paramCount; i ++) {
                applyOps.add(Sequence.makeList(Assembler.ALOAD, 1));
                applyOps.add(Sequence.makeList(Assembler.ICONST, i));
                applyOps.add(Assembler.AALOAD);
            }
            /*
            if(isVarargs)
                classes[classes.length - 2] = Sequence.class;
             */
            String descriptor = Assembler.getMethodDescriptor(Object.class, paramCount);
            applyOps.add(Sequence.makeList(Assembler.INVOKEVIRTUAL, this.name, "invoke", descriptor));
            applyOps.add(Assembler.ARETURN);

            applyOps.add(Sequence.makeList(Assembler.LABEL, exceptionLabel));
            applyOps.add(Sequence.makeList(Assembler.NEW, Type.getInternalName(RuntimeException.class)));
            applyOps.add(Assembler.DUP);
            /*
            // display debug info about the array
            applyOps.add(ArraySequence.makeList(Assembler.ALOAD, 1));
            applyOps.add(ArraySequence.makeList(Assembler.INVOKESTATIC, Type.getInternalName(Arrays.class),
                    "deepToString", "([Ljava/lang/Object;)Ljava/lang/String;"));
            applyOps.add(ArraySequence.makeList(Assembler.INVOKESPECIAL,
                    Type.getInternalName(RuntimeException.class),
                    "<init>", "(Ljava/lang/String;)V"));
             */
            applyOps.add(Sequence.makeList(Assembler.INVOKESPECIAL,
                    Type.getInternalName(RuntimeException.class),
                    "<init>", "()V"));
            applyOps.add(Assembler.ATHROW);
            methods.add(Sequence.makeList(applyOps.toArray()));
        }

        Sequence toTree() {
            Sequence header = Sequence.makeList(
                    DEFCLASS,
                    getName(),
                    // meta?
                    EmptySequence.EMPTY_SEQUENCE
            );
            Sequence body = new ArraySequence(methods.toArray());
            // TODO: better concat
            return Sequence.concat(new ArraySequence(header, body));
        }

        void generateToString(String name) {
            String value = String.format("#<%s %s>", isMacro ? "macro" : "function", name);
            methods.add(Sequence.makeList(
                    DEFMETHOD, "toString", "()Ljava/lang/String;", EmptySequence.EMPTY_SEQUENCE,
                    Sequence.makeList(Assembler.LDC, value),
                    Assembler.ARETURN
            ));
        }
    }

    private static class CompEnvir {
        CompEnvir parent;
        CompClass closure;
        HashMap<Symbol, Integer> bindings = new HashMap<>();
        int bindCount;

        CompEnvir(CompEnvir parent) {
            this(parent, null);
        }

        CompEnvir(CompEnvir parent, CompClass closure) {
            this.parent = parent;
            this.closure = closure;
            // restart locals if this is top envir or when making a closure
            if(parent == null || closure != null)
                // first local slot is taken by Function, locals start at 1
                bindCount = 1;
            else
                bindCount = parent.bindCount + parent.bindings.size();
        }

        int insert(Symbol symbol) {
            bindings.put(symbol, bindCount);
            bindCount ++;
            return bindCount - 1;
        }

        boolean contains(Symbol symbol) {
            CompEnvir envir = this;
            while(envir != null) {
                if(envir.bindings.containsKey(symbol))
                    return true;
                envir = envir.parent;
            }
            return false;
        }
    }

    static Object macroExpand(Object expr) {
        while(true) {
            if(!(expr instanceof Sequence)) {
                return expr;
            }
            Object head = ((Sequence) expr).first();
            if(!(head instanceof Symbol)) {
                return expr;
            }
            Object resource = Environment.getIfExists((Symbol) head);
            if(resource instanceof Function && ((Function) resource).isMacro()) {
                Function macro = (Function) resource;
                Object[] args = new Object[((Sequence) expr).rest().length()];
                // ignore the first arg, which is the macro name
                Sequence argSeq = ((Sequence) expr).rest();
                for(int i = 0; i < args.length; i ++) {
                    args[i] = argSeq.first();
                    argSeq = argSeq.rest();
                }
                expr = macro.apply(args);
            } else {
                return expr;
            }
        }
    }

    static void packAndApply(Sequence args, CompEnvir envir, CompClass current,
                             ArrayList<Object> bytecode, ArrayList<CompClass> classes) {
        int argc = args.length();
        bytecode.add(Sequence.makeList(Assembler.ICONST, argc));
        bytecode.add(Sequence.makeList(Assembler.ANEWARRAY, Type.getInternalName(Object.class)));
        for(int i = 0; i < argc; i ++) {
            bytecode.add(Assembler.DUP);
            bytecode.add(Sequence.makeList(Assembler.ICONST, i));
            compile(args.first(), false, envir, current, bytecode, classes);
            bytecode.add(Assembler.AASTORE);
            args = args.rest();
        }
        // call apply
        bytecode.add(Sequence.makeList(
                Assembler.INVOKEVIRTUAL,
                Type.getInternalName(Function.class),
                "apply",
                "([Ljava/lang/Object;)Ljava/lang/Object;"
        ));
    }

    static CompClass[] compileLambda(Sequence body, String name, boolean isMacro) {
        // compile inner class
        Object lambdaBody = body.second();
        CompClass lambdaClass = new CompClass();
        CompEnvir lambdaEnvir = new CompEnvir(new CompEnvir(null), lambdaClass);

        Sequence params = (Sequence) body.first();
        int paramCount = params.length();
        boolean isVarargs = false;
        // TODO: destructuring
        Sequence destruct = params;
        while(!destruct.isEmpty()) {
            if(REST.equals(destruct.first()) && destruct.rest().rest().isEmpty()) {
                lambdaEnvir.insert((Symbol) destruct.second());
                isVarargs = true;
                paramCount --;
                break;
            } else {
                lambdaEnvir.insert((Symbol) destruct.first());
            }
            destruct = destruct.rest();
        }

        // compile body first
        ArrayList<CompClass> classes = new ArrayList<>();
        classes.add(lambdaClass);
        ArrayList<Object> invoker = compile(lambdaBody, true, lambdaEnvir, lambdaClass,
                new ArrayList<>(), classes);

        // then generate class methods (depends on body compilation)
        lambdaClass.generateInvoker(paramCount, isVarargs, invoker);
        lambdaClass.generateConstructor();
        lambdaClass.generateInherits(isMacro, isVarargs, paramCount);
        lambdaClass.generateToString(name);
        return classes.toArray(new CompClass[0]);
    }

    static ArrayList<Object> compile(Object ast, boolean isTail, CompEnvir envir, CompClass current,
                             ArrayList<Object> bytecode, ArrayList<CompClass> classes) {
        ast = macroExpand(ast);
        if(ast instanceof Sequence) {
            Sequence astSequence = (Sequence) ast;
            Object head = astSequence.first();
            Sequence body = astSequence.rest();
            if(LIST.equals(head)) {
                for(Object arg : astSequence.rest()) {
                    compile(arg, false, envir, current, bytecode, classes);
                }
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKEDYNAMIC,
                        SEQUENCE_BOOTSTRAP,
                        "makeSequence",
                        Assembler.getMethodDescriptor(Sequence.class, body.length())
                ));
            } else if(IF.equals(head)) {
                // TODO: error if arglen != 3
                Symbol targetLabel = Symbol.gensym("if");
                Object test = body.first();
                Object thenClause = body.second();
                Object elseClause = body.third();
                compile(test, false, envir, current, bytecode, classes);
                bytecode.add(Sequence.makeList(Assembler.IFNULL, targetLabel));
                compile(thenClause, isTail, envir, current, bytecode, classes);
                bytecode.add(Sequence.makeList(Assembler.LABEL, targetLabel));
                compile(elseClause, isTail, envir, current, bytecode, classes);
                // skip end of method's isTail test
                return bytecode;
            } else if(QUOTE.equals(head)) {
                throw new RuntimeException();
            } else if (LAMBDA.equals(head)) {
                // compile inner class
                // TODO: factor out common code with compileLambda
                Sequence params = (Sequence) body.first();
                Object lambdaBody = body.second();
                CompClass lambdaClass = new CompClass();
                CompEnvir lambdaEnvir = new CompEnvir(envir, lambdaClass);
                for (Object param : params) {
                    lambdaEnvir.insert((Symbol) param);
                }

                // compile body first
                ArrayList<Object> invoker = compile(lambdaBody, true, lambdaEnvir, lambdaClass,
                        new ArrayList<>(), classes);

                // then generate class methods (depends on body compilation)
                lambdaClass.generateInvoker(params.length(), false, invoker);
                ArrayList<Symbol> constructorArgs = lambdaClass.generateConstructor();
                lambdaClass.generateInherits(false, false, params.length());
                classes.add(lambdaClass);

                // generate code to make new lambda at the site
                bytecode.add(Sequence.makeList(Assembler.NEW, lambdaClass.getName()));
                bytecode.add(Assembler.DUP);
                // load constructor arguments onto stack
                for (Symbol sym : constructorArgs) {
                    compile(sym, false, envir, current, bytecode, classes);
                }
                // invoke constructor
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKESPECIAL,
                        lambdaClass.getName(),
                        "<init>",
                        lambdaClass.getConstructor()
                ));
            } else if(LET.equals(head)) {
                CompEnvir letEnvir = new CompEnvir(envir);
                Sequence bindings = (Sequence) body.first();
                while(!bindings.isEmpty()) {
                    int index = letEnvir.insert((Symbol) bindings.first());
                    compile(bindings.second(), false, letEnvir, current, bytecode, classes);
                    bytecode.add(Sequence.makeList(Assembler.ASTORE, index));
                    bindings = bindings.rest().rest();
                }
                compile(body.second(), isTail, letEnvir, current, bytecode, classes);
            } else if(ASM.equals(head)) {
                for (Object obj : astSequence.rest()) {
                    if(obj instanceof Sequence && DEASM.equals(((Sequence) obj).first()))
                        compile(((Sequence) obj).second(), false, envir, current, bytecode, classes);
                    else
                        bytecode.add(obj);
                }
            } else if(head instanceof Symbol) {
                // head is a symbol, look for a function with that name in the environment
                if(envir.contains((Symbol) head)) {
                    // load object onto stack and call at the end
                    compile(head, false, envir, current, bytecode, classes);
                    packAndApply(body, envir, current, bytecode, classes);
                } else {
                    // head symbol does not exist in local environment: use invokedynamic
                    // load arguments onto stack
                    for(Object arg : astSequence.rest()) {
                        compile(arg, false, envir, current, bytecode, classes);
                    }
                    bytecode.add(Sequence.makeList(
                            Assembler.INVOKEDYNAMIC,
                            ENVIR_FUNCTION,
                            head.toString(),
                            Assembler.getMethodDescriptor(Object.class, body.length()),
                            "test"
                    ));
                }
            } else {
                // head of s-expr is not a symbol, assume that it produces a lambda and call it
                compile(head, false, envir, current, bytecode, classes);
                // cast to function
                bytecode.add(Sequence.makeList(Assembler.CHECKCAST, Type.getInternalName(Function.class)));
                // pack arguments into array
                packAndApply(astSequence.rest(), envir, current, bytecode, classes);
            }
        } else if(ast instanceof Symbol) {
            /*
            resolve a symbol: determine if it is
            - local field (argument or let variable): aload
            - closed-over variable: get field
            - global variable: invokedynamic for object from envir
             */
            Symbol symAst = (Symbol) ast;
            CompEnvir compEnvir = envir;
            boolean closureSeen = false;
            boolean closedVariable = false;
            ArrayDeque<CompEnvir> envirChain = new ArrayDeque<>();
            while(compEnvir != null) {
                if(closureSeen)
                    closedVariable = true;
                if(compEnvir.closure != null)
                    closureSeen = true;
                envirChain.push(compEnvir);

                if(compEnvir.bindings.containsKey(symAst))
                    break;
                else
                    compEnvir = compEnvir.parent;
            }
            // searched through all parent envirs and did not find symbol
            if(compEnvir == null) {
                // System.out.println(symAst + " is global var");
                // if not in parents, invokedynamic to bind to global
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKEDYNAMIC,
                        ENVIR_OBJECT,
                        ast.toString(),
                        Assembler.getMethodDescriptor(Object.class, 0),
                        "test"
                ));
            } else {
                CompEnvir top = envirChain.pop();
                if(closedVariable) {
                    // pull the closed variable through all of the closures
                    while(!envirChain.isEmpty()) {
                        compEnvir = envirChain.pop();
                        if(compEnvir.closure != null) {
                            compEnvir.closure.captured.add(symAst);
                        }
                    }

                    // load the function object onto the stack
                    bytecode.add(Sequence.makeList(Assembler.ALOAD, 0));
                    // get the closed variable from the closed field
                    bytecode.add(Sequence.makeList(
                            Assembler.GETFIELD,
                            current.name, symAst.toString(), Type.getDescriptor(Object.class)
                    ));
                    // System.out.println("closed over var " + symAst.toString());
                } else {
                    int localSlot = top.bindings.get(symAst);
                    // System.out.println(symAst.toString() + " is localvar@" + localSlot);
                    bytecode.add(Sequence.makeList(
                            Assembler.ALOAD, localSlot
                    ));
                }
            }
        } else if(ast instanceof Integer) {
            bytecode.add(Sequence.makeList(Assembler.ICONST, ast));
            bytecode.add(PARSE_INT);
        } else {
            // TODO: keywords
            throw new RuntimeException(ast.toString());
        }
        if(isTail)
            bytecode.add(Assembler.ARETURN);
        return bytecode;
    }

    static Object eval(Object ast) {
        if(ast instanceof Sequence) {
            Object head = ((Sequence) ast).first();
            Sequence body = ((Sequence) ast).rest();
            if(DEFINE.equals(head)) {
                Symbol name = (Symbol) body.first();
                Object value = eval(body.second());
                return Environment.insert(name, value);
            } else if(DEFUN.equals(head) || DEFMACRO.equals(head)) {
                Symbol name = (Symbol) body.first();
                CompClass[] classes = compileLambda(body.rest(), name.toString(), DEFMACRO.equals(head));
                byte[][] classBytes = new byte[classes.length][];
                for(int i = 0; i < classes.length; i ++) {
                    classBytes[i] = Assembler.buildClass(classes[i].toTree());
                }
                Class<?> clazz = ClassDefiner.hotloadClasses(classBytes);
                try {
                    Constructor<?> constructor = clazz.getConstructor();
                    Object instance = constructor.newInstance();
                    Environment.insert(name, instance);
                    return instance;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        CompClass mainClass = new CompClass();
        ArrayList<CompClass> classes = new ArrayList<>();
        classes.add(mainClass);
        ArrayList<Object> mainops = compile(ast, true, new CompEnvir(null), mainClass,
                new ArrayList<>(), classes);
        mainClass.generateConstructor();
        mainClass.generateInherits(false, false, 0);
        mainClass.generateInvoker(0, false, mainops);

        byte[][] classBytes = new byte[classes.size()][];
        int i = 0;
        for(CompClass compClass : classes) {
            // System.out.println(compClass.toTree());
            classBytes[i] = Assembler.buildClass(compClass.toTree());
            i ++;
        }

        Class<?> clazz = ClassDefiner.hotloadClasses(classBytes);
        try {
            Constructor<?> constructor = clazz.getConstructor();
            Object instance = constructor.newInstance();
            return ((Function) instance).apply();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void repl() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            String input = reader.readLine();
            if(input == null)
                break;
            if(input.isEmpty() || input.isBlank())
                continue;
            LispReader lispReader = LispReader.stringReader(input);
            Object form = lispReader.readForm();
            Object result = eval(form);
            System.out.println("=> " + result);
        }
        System.out.println("bye! (^o^ )/");
    }

    public static void main(String[] args) throws IOException {
        LispReader lispReader = LispReader.fileReader("./src/lisp/lambda.lisp");
        Object form;
        while((form = lispReader.readForm()) != null) {
            try {
                System.out.println("=> " + eval(form));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // repl();
    }
}
