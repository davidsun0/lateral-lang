package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static io.github.whetfire.lateral.Assembler.*;

public class Compiler {

    private static Symbol ASM = Symbol.makeSymbol("asm");
    private static Symbol DEASM = Symbol.makeSymbol("de-asm");

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

    static Sequence CONS = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Sequence.class),
            "cons", Assembler.getMethodDescriptor(Object.class, Sequence.class, Sequence.class)
    );

    static Method isMacroCall(Sequence expr) {
        Object obj = expr.first();
        if (obj instanceof Symbol) {
            Object resource = Environment.getIfExists((Symbol) obj);
            if(resource instanceof Function && ((Function) resource).isMacro()) {
                try {
                    Class<?>[] classes = Assembler.getParameterClasses(expr.length() - 1);
                    return resource.getClass().getMethod("invokeStatic", classes);
                } catch (NoSuchMethodException nsme) {
                    return null;
                }
            }
        }
        return null;
    }

    static Object macroExpand(Object expr) {
        while(true) {
            if(!(expr instanceof Sequence)) {
                return expr;
            }
            Method macro = isMacroCall((Sequence) expr);
            if(macro != null) {
                try {
                    // System.out.println(macro);
                    Object[] args = new Object[macro.getParameterCount()];
                    // ignore the first arg, which is the macro name
                    for(int i = 0; i < args.length; i ++) {
                        args[i] = ((Sequence) expr).nth(i + 1);
                    }
                    //expr = macro.invoke(null, ((Sequence) expr).second());
                    expr = macro.invoke(null, args);
                } catch (Exception e) {
                    e.printStackTrace();
                    return expr;
                }
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
            opcodes.add(EMPTY_LIST);
            for(int i = 0; i < seqLen; i ++) {
                opcodes.add(CONS);
            }
        } else if (ast instanceof Symbol) {
            opcodes.add(ArraySequence.makeList(LDC, ast.toString()));
            opcodes.add(MAKE_SYM);
            /*
            opcodes.add(ArraySequence.makeList(
                    Assembler.INVOKEDYNAMIC, ast.toString(),
                    "()" + Type.getInternalName(Symbol.class)
            ));
            */
        } else if (ast instanceof Keyword) {
            opcodes.add(ArraySequence.makeList(LDC, ((Keyword) ast).getValue()));
            opcodes.add(MAKE_KEY);
        } else if (ast instanceof Integer) {
            opcodes.add(ArraySequence.makeList(ICONST, ast));
            opcodes.add(PARSE_INT);
        } else if (ast instanceof String) {
            opcodes.add(ArraySequence.makeList(LDC, ast));
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
                // TODO: assert that let has 2 arguments
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
                    opExprs.add(ArraySequence.makeList(Assembler.ASTORE, localIndex));
                    bindList = bindList.rest().rest();
                }
                Object letBody = body.second();
                compile(opExprs, letBody, locals, isTail);
                // pop locals after body completes
                locals.subList(localCount, locals.size()).clear();
            } else if(head.equals(LIST)) {
                // TODO: more efficient list creation (use ArraySequence?)
                // TODO: same in quote compile
                for(Object arg : body) {
                    compile(opExprs, arg, locals, false);
                }
                opExprs.add(EMPTY_LIST);
                for (int i = 0; i < body.length(); i++) {
                    opExprs.add(CONS);
                }
            } else {
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
                    opExprs.add(ArraySequence.makeList(
                            Assembler.INVOKEDYNAMIC, head.toString(),
                            Assembler.getMethodDescriptor(Assembler.getParameterClasses(body.length() + 1))
                    ));
                } else {
                    throw new RuntimeException(head + " can't be used as a function");
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
                opExprs.add(ArraySequence.makeList(Assembler.ALOAD, index));
            } else {
                throw new RuntimeException(ast.toString());
            }

            /*
            // TODO: look up in environment
            opExprs.add(ArraySequence.makeList(Assembler.LDC, ast.toString()));
            opExprs.add(Compiler.MAKE_SYM);
            if(isTail)
                opExprs.add(ARETURN);
            throw new RuntimeException(ast.toString());
            */
        } else if(ast instanceof Keyword) {
            opExprs.add(ArraySequence.makeList(Assembler.LDC, ((Keyword) ast).getValue()));
            opExprs.add(MAKE_KEY);
        } else if(ast instanceof Integer) {
            opExprs.add(ArraySequence.makeList(ICONST, ast));
            opExprs.add(PARSE_INT);
        } else if(ast instanceof String) {
            opExprs.add(ArraySequence.makeList(LDC, ast));
        } else {
            throw new RuntimeException(ast.toString());
        }
        if (isTail)
            opExprs.add(ARETURN);
        return opExprs;
    }

    static Object eval(DynamicsManager dm, Object ast) {
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
                        dm, isMacro, isVarargs, opcodes, name.toString(), paramCount
                );
                return Environment.insert(name, function);
            }
        }
        ArrayList<Object> opcodes = compile(new ArrayList<>(), ast, new ArrayList<>(), true);
        Function function = Assembler.compileMethod(dm, opcodes);
        try {
            /*
            Method m = function.getClass().getMethod("invoke");
            return m.invoke(null);
            */
            return function.invoke();
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
        LispReader lispReader = LispReader.fileReader("./src/lisp/test.lisp");
        DynamicsManager dynamicsManager = new DynamicsManager();
        Object form;
        // long last = System.currentTimeMillis();
        while((form = lispReader.readForm()) != null) {
            System.out.println(eval(dynamicsManager, form));
            /*
            long now = System.currentTimeMillis();
            System.out.println(now - last);
            last = now;
            */
        }
    }
}
