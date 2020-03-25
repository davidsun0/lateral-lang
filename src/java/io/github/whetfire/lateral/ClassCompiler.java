package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ClassCompiler {
    static Symbol LAMBDA = Symbol.makeSymbol("lambda");
    static Symbol DEFINE = Symbol.makeSymbol("def");
    static Symbol ASM = Symbol.makeSymbol("asm-quote");
    static Symbol LET = Symbol.makeSymbol("let");

    static Symbol DEFMETHOD = Symbol.makeSymbol("defmethod");
    static Symbol DEFCLASS = Symbol.makeSymbol("defclass");
    static Symbol DEFFIELD = Symbol.makeSymbol("deffield");

    static Sequence ENVIR_OBJECT = new ArraySequence(
            Type.getInternalName(Environment.class), "dynamicObject",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                    MethodType.class, String.class).toMethodDescriptorString()
    );

    private static class CompClass {
        static private int CLASS_NUM = 0;

        String name;
        ArrayList<Object> methods;
        HashSet<Symbol> captured;

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
            Class<?>[] type = Assembler.getParameterClasses(captured.size() + 1);
            type[type.length - 1] = void.class;
            return Assembler.getMethodDescriptor(type);
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

        void generateInvoker(Sequence params, ArrayList<Object> opcodes) {
            Sequence header = Sequence.makeList(
                    DEFMETHOD, "invoke",
                    Assembler.getMethodDescriptor(Assembler.getParameterClasses(params.length() + 1)),
                    EmptySequence.EMPTY_SEQUENCE
            );

            Sequence body = new ArraySequence(opcodes.toArray());
            methods.add(Sequence.concat(new ArraySequence(header, body)));
        }

        void generateInherits(boolean isMacro, boolean isVarargs, int paramCount) {
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
            applyOps.add(Sequence.makeList(Assembler.IF_ICMPNE, exceptionLabel));
            // applyOps.add(ArraySequence.makeList())
            applyOps.add(Sequence.makeList(Assembler.ALOAD, 0));
            for(int i = 0; i < paramCount; i ++) {
                applyOps.add(Sequence.makeList(Assembler.ALOAD, 1));
                applyOps.add(Sequence.makeList(Assembler.ICONST, i));
                applyOps.add(Assembler.AALOAD);
            }
            Class<?>[] classes = Assembler.getParameterClasses(paramCount + 1);
            /*
            if(isVarargs)
                classes[classes.length - 2] = Sequence.class;
             */
            String descriptor = Assembler.getMethodDescriptor(classes);
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
    }

    private static class CompEnvir {
        CompEnvir parent;
        CompClass closure;
        HashMap<Symbol, Integer> bindings = new HashMap<>();
        int bindCount;

        CompEnvir() {
            this(null, null);
        }

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
    }

    static ArrayList<Object> compile(Object ast, boolean isTail, CompEnvir envir, CompClass current,
                             ArrayList<Object> bytecode, ArrayList<CompClass> classes) {
        ast = Compiler.macroExpand(ast);
        if(ast instanceof Sequence) {
            Sequence astSequence = (Sequence) ast;
            Object head = astSequence.first();
            Sequence body = astSequence.rest();
            if (LAMBDA.equals(head)) {
                // compile inner class
                Sequence params = (Sequence) body.first();
                Object lambdaBody = body.second();
                CompClass lambdaClass = new CompClass();
                CompEnvir lambdaEnvir = new CompEnvir(envir, lambdaClass);
                for(Object param : params) {
                    lambdaEnvir.insert((Symbol) param);
                }

                // compile body first
                ArrayList<Object> invoker = compile(lambdaBody, true, lambdaEnvir, lambdaClass,
                        new ArrayList<>(), classes);

                // then generate class methods (depends on body compilation)
                lambdaClass.generateInvoker(params, invoker);
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
                    if(obj instanceof Sequence && Compiler.DEASM.equals(((Sequence) obj).first()))
                        compile(((Sequence) obj).second(), false, envir, current, bytecode, classes);
                    else
                        bytecode.add(obj);
                }
            } else if(head instanceof Symbol) {
                // invokedynamic for methods
                // TODO: check if head exists in envir first
                // load arguments onto stack
                for(Object arg : astSequence.rest()) {
                    compile(arg, false, envir, current, bytecode, classes);
                }
                /*
                (invokedynamic head (...)LObject;
                use invokedynamic to get function at runtime
                see Environment.bootstrapMethod
                 */
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKEDYNAMIC,
                        Compiler.ENVIR_BOOTSTRAP,
                        head.toString(),
                        Assembler.getMethodDescriptor(Assembler.getParameterClasses(body.length() + 1)),
                        "test"
                ));
            } else {
                compile(head, false, envir, current, bytecode, classes);
                // cast to function
                bytecode.add(Sequence.makeList(Assembler.CHECKCAST, Type.getInternalName(Function.class)));
                // pack arguments into array
                astSequence = astSequence.rest();
                int argLen = astSequence.length();
                bytecode.add(Sequence.makeList(Assembler.ICONST, argLen));
                bytecode.add(Sequence.makeList(Assembler.ANEWARRAY, Type.getInternalName(Object.class)));
                for(int i = 0; i < argLen; i ++) {
                    bytecode.add(Assembler.DUP);
                    bytecode.add(Sequence.makeList(Assembler.ICONST, i));
                    compile(astSequence.first(), false, envir, current, bytecode, classes);
                    bytecode.add(Assembler.AASTORE);
                    astSequence = astSequence.rest();
                }
                // call apply
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKEVIRTUAL,
                        Type.getInternalName(Function.class),
                        "apply",
                        "([Ljava/lang/Object;)Ljava/lang/Object;"
                ));
                // throw new RuntimeException(astSequence.first().toString());
            }
        } else if(ast instanceof Symbol) {
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
                System.out.println(symAst + " is global var");
                // if not in parents, invokedynamic to bind to global
                bytecode.add(Sequence.makeList(
                        Assembler.INVOKEDYNAMIC,
                        ENVIR_OBJECT,
                        ast.toString(),
                        Assembler.getMethodDescriptor(Assembler.getParameterClasses(1)),
                        "test"
                ));
            } else {
                System.out.println(closedVariable);
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
                    System.out.println("closed over var " + symAst.toString());
                } else {
                    int localSlot = top.bindings.get(symAst);
                    System.out.println(symAst.toString() + " is localvar@" + localSlot);
                    bytecode.add(Sequence.makeList(
                            Assembler.ALOAD, localSlot
                    ));
                }
            }
        } else if(ast instanceof Integer) {
            bytecode.add(Sequence.makeList(Assembler.ICONST, ast));
            bytecode.add(Compiler.PARSE_INT);
        } else {
            // TODO: keywords
            throw new RuntimeException(ast.toString());
        }
        if(isTail)
            bytecode.add(Assembler.ARETURN);
        return bytecode;
    }

    static Object eval(Object ast) {
        if(ast instanceof Sequence && DEFINE.equals(((Sequence) ast).first())) {
            Symbol name = (Symbol) ((Sequence) ast).second();
            Object value = eval(((Sequence) ast).third());
            return Environment.insert(name, value);
        }
        CompClass mainClass = new CompClass();
        ArrayList<CompClass> classes = new ArrayList<>();
        classes.add(mainClass);
        ArrayList<Object> mainops = compile(ast, true, new CompEnvir(), mainClass,
                new ArrayList<>(), classes);
        mainClass.generateConstructor();
        mainClass.generateInherits(false, false, 0);
        mainClass.generateInvoker(EmptySequence.EMPTY_SEQUENCE, mainops);

        byte[][] classBytes = new byte[classes.size()][];
        int i = 0;
        for(CompClass compClass : classes) {
            // System.out.println(compClass.toTree());
            classBytes[i] = ClassAssembler.visitClass(compClass.toTree());
            i ++;
        }

        Class<?> clazz = ClassDefiner.hotloadClasses(classBytes);
        try {
            Constructor<?> constructor = clazz.getConstructor();
            Object instance = constructor.newInstance();
            Method invoke = clazz.getMethod("invoke");
            return invoke.invoke(instance);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
            System.out.println("=> " + eval(form));
        }
        // repl();
    }
}
