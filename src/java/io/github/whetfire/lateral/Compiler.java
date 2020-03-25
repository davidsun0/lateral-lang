package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;

import static io.github.whetfire.lateral.Assembler.*;

public class Compiler {

    static Symbol ASM = Symbol.makeSymbol("asm-quote");
    static Symbol DEASM = Symbol.makeSymbol("unquote");

    static Symbol DEFMACRO = Symbol.makeSymbol("defmacro");
    static Symbol DEFUN = Symbol.makeSymbol("defun");
    static Symbol DEFINE = Symbol.makeSymbol("define");

    static Symbol QUOTE = Symbol.makeSymbol("quote");
    static Symbol LET = Symbol.makeSymbol("let");
    static Symbol LIST = Symbol.makeSymbol("list");

    static Keyword REST = Keyword.makeKeyword("rest");

    static Sequence EMPTY_LIST = new ArraySequence(
            Assembler.GETSTATIC, Type.getInternalName(EmptySequence.class),
            "EMPTY_SEQUENCE", Type.getDescriptor(Sequence.class)
    );

    static Sequence PARSE_INT = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Integer.class),
            "valueOf", Assembler.getMethodDescriptor(int.class, Integer.class)
    );

    /*
    TODO: convert MAKE_SYM and MAKE_KEY into invokedynamic calls
    Use MethodHandles.constant(Symbol.class | Keyword.class, value) so the value is only created once
    as needed
     */
    static Sequence MAKE_SYM = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Symbol.class),
            "makeSymbol", Assembler.getMethodDescriptor(String.class, Symbol.class)
    );

    static Sequence MAKE_KEY = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Keyword.class),
            "makeKeyword", Assembler.getMethodDescriptor(String.class, Keyword.class)
    );

    static Sequence ENVIR_BOOTSTRAP = new ArraySequence(
            Type.getInternalName(Environment.class), "dynamicMethod",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                                MethodType.class, String.class).toMethodDescriptorString()
    );

    static Sequence SEQUENCE_BOOTSTRAP = new ArraySequence(
            Type.getInternalName(Sequence.class), "sequenceBuilder",
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                                MethodType.class).toMethodDescriptorString()
    );

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
                Object[] args = new Object[macro.paramCount()];
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

    private static void compileQuote(Object ast, ArrayList<Object> opcodes) {
        if(ast == null) {
            throw new NullPointerException();
        } else if (ast.equals(EmptySequence.EMPTY_SEQUENCE)) {
            // quoted empty list '()
            opcodes.add(EMPTY_LIST);
        } else if (ast instanceof Sequence) {
            Sequence astSeq = (Sequence) ast;
            int seqLen = astSeq.length();
            for(Object o : astSeq) {
                compileQuote(o, opcodes);
            }
            Class<?>[] methodClasses = Assembler.getParameterClasses(seqLen + 1);
            methodClasses[methodClasses.length - 1] = Sequence.class;
            opcodes.add(Sequence.makeList(
                    Assembler.INVOKEDYNAMIC,
                    SEQUENCE_BOOTSTRAP,
                    "makeSequence",
                    Assembler.getMethodDescriptor(methodClasses)
            ));
        } else if (ast instanceof Symbol) {
            opcodes.add(Sequence.makeList(LDC, ast.toString()));
            opcodes.add(MAKE_SYM);
        } else if (ast instanceof Keyword) {
            opcodes.add(Sequence.makeList(LDC, ((Keyword) ast).getValue()));
            opcodes.add(MAKE_KEY);
        } else if (ast instanceof Integer) {
            opcodes.add(Sequence.makeList(ICONST, ast));
            opcodes.add(PARSE_INT);
        } else if (ast instanceof String) {
            opcodes.add(Sequence.makeList(LDC, ast));
        } else {
            throw new RuntimeException("Can't quote " + ast);
        }
    }

    static ArrayList<Object> compile(
            ArrayList<Object> opExprs,
            Object ast, ArrayList<Symbol> locals, boolean isTail) {
        ast = macroExpand(ast);
        if(ast instanceof Sequence) {
            Sequence astSequence = (Sequence) ast;
            Object head = astSequence.first();
            Sequence body = astSequence.rest();
            if(astSequence.isEmpty()) {
                opExprs.add(EMPTY_LIST);
            } else if(head.equals(ASM)) {
                // literal bytecode assembly; inject its values directly
                // TODO: better de-asm with tree traversal
                for (Object asmExpr : body) {
                    if(asmExpr instanceof Sequence && DEASM.equals(((Sequence) asmExpr).first())) {
                        // deasm cancels out asm and compilation continues normally
                        compile(opExprs, ((Sequence) asmExpr).second(), locals, false);
                    } else {
                        opExprs.add(asmExpr);
                    }
                }
                // treating asm like any other expression makes the expression blend in with the language better
                // making sure that the expression return only one value is up to the programmer
            } else if(head.equals(QUOTE)) {
                compileQuote(astSequence.second(), opExprs);
            } else if(head.equals(LET)) {
                if(body.length() != 2) {
                    // assert that let has 2 arguments
                    throw new SyntaxException("let expects two arguments");
                }
                Sequence bindList = (Sequence) body.first();
                // TODO: assert correct number and type of vars / bindings
                int localCount = locals.size();
                // bind locals
                while (!bindList.isEmpty()) {
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
                    compile(opExprs, bindList.second(), locals, false);
                    opExprs.add(Sequence.makeList(Assembler.ASTORE, localIndex));
                    bindList = bindList.rest().rest();
                }
                Object letBody = body.second();
                compile(opExprs, letBody, locals, isTail);
                // pop locals after body completes
                locals.subList(localCount, locals.size()).clear();
            } else if(head.equals(LIST)) {
                for(Object arg : body) {
                    compile(opExprs, arg, locals, false);
                }
                Class<?>[] methodClasses = Assembler.getParameterClasses(body.length() + 1);
                methodClasses[methodClasses.length - 1] = Sequence.class;
                opExprs.add(Sequence.makeList(
                        Assembler.INVOKEDYNAMIC,
                        SEQUENCE_BOOTSTRAP,
                        "makeSequence",
                        Assembler.getMethodDescriptor(methodClasses)
                ));
            } else {
                // TODO: implement tail recursion optimization; need function's name or recur keyword
                if(head instanceof Symbol) {
                    // load arguments onto stack
                    for(Object arg : body) {
                        compile(opExprs, arg, locals, false);
                    }
                    /*
                    (invokedynamic head (...)LObject;
                    use invokedynamic to get function at runtime
                    see Environment.bootstrapMethod
                     */
                    opExprs.add(Sequence.makeList(
                            Assembler.INVOKEDYNAMIC,
                            ENVIR_BOOTSTRAP,
                            head.toString(),
                            Assembler.getMethodDescriptor(Assembler.getParameterClasses(body.length() + 1)),
                            "test"
                    ));
                } else {
                    throw new TypeException(head + " can't be used as a function");
                }
            }
        } else if(ast instanceof Symbol) {
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
                opExprs.add(Sequence.makeList(Assembler.ALOAD, index));
            } else {
                // TODO: look up in closure environment, then global environment
                throw new RuntimeException(ast.toString());
            }
        } else if(ast instanceof Keyword) {
            opExprs.add(Sequence.makeList(Assembler.LDC, ((Keyword) ast).getValue()));
            opExprs.add(MAKE_KEY);
        } else if(ast instanceof Integer) {
            opExprs.add(Sequence.makeList(ICONST, ast));
            opExprs.add(PARSE_INT);
        } else if(ast instanceof String) {
            opExprs.add(Sequence.makeList(LDC, ast));
        } else {
            throw new RuntimeException(ast.toString());
        }
        if (isTail)
            opExprs.add(ARETURN);
        return opExprs;
    }

    static Object eval(Object ast) {
        if(ast instanceof Sequence) {
            Sequence astSequence = (Sequence)ast;
            if(astSequence.first().equals(DEFUN) || astSequence.first().equals(DEFMACRO)) {
                boolean isMacro = astSequence.first().equals(DEFMACRO);
                // (defun name (args) body)
                Symbol name = (Symbol) astSequence.second();

                Sequence params = (Sequence) astSequence.third();
                int paramCount = params.length();
                boolean isVarargs = paramCount > 1 && REST.equals(params.nth(params.length() - 2));
                ArrayList<Symbol> locals = new ArrayList<>();
                locals.add(null); // first local is 'this'
                for(int i = 0; i < paramCount; i ++) {
                    if(!(isVarargs && i == paramCount - 2)) {
                        locals.add((Symbol) params.nth(i));
                    }
                }
                if(isVarargs)
                    paramCount --;

                Object body = astSequence.fourth();

                ArrayList<Object> opcodes = compile(new ArrayList<>(), body, locals, true);
                Function function = Assembler.compileMethod(
                        isMacro, isVarargs, opcodes, name.toString(), paramCount
                );
                return Environment.insert(name, function);
            } else if(astSequence.first().equals(DEFINE)) {
                Symbol name = (Symbol) astSequence.second();
                Object value = astSequence.third();
                return Environment.insert(name, eval(value));
            }
        }
        ArrayList<Symbol> locals = new ArrayList<>();
        locals.add(null);
        ArrayList<Object> opcodes = compile(new ArrayList<>(), ast, locals, true);
        Function function = Assembler.compileMethod(opcodes);
        try {
            return function.apply();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void writeToFile(String path, byte[] bytes) {
        try(FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        LispReader lispReader = LispReader.fileReader("./src/lisp/lang.lisp");
        Object form;
        // long last = System.currentTimeMillis();
        while((form = lispReader.readForm()) != null) {
            System.out.println(eval(form));
            /*
            long now = System.currentTimeMillis();
            System.out.println(now - last);
            last = now;
            */
        }
    }
}
