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

    static Symbol QUOTE = Symbol.makeSymbol("quote");
    static Symbol LET = Symbol.makeSymbol("let");

    static Keyword REST = Keyword.makeKeyword("rest");

    static Sequence EMPTY_LIST = new ArraySequence(
            Assembler.GETSTATIC, Type.getInternalName(EmptySequence.class),
            "EMPTY_SEQUENCE", Type.getDescriptor(Sequence.class)
    );
    static Sequence PARSE_INT = new ArraySequence(
            Assembler.INVOKESTATIC, Type.getInternalName(Integer.class),
            "valueOf", Assembler.getMethodDescriptor(int.class, Integer.class)
    );
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

    static Lambda isMacroCall(Sequence expr) {
        Object obj = expr.first();
        if(obj instanceof Symbol) {
            Object resource = Environment.getIfExists((Symbol) obj);
            if(resource instanceof Lambda) {
                Lambda lambda = (Lambda) resource;
                if(lambda.isMacro) {
                    return lambda;
                }
            }
        }
        return null;
    }

    static Object macroExpand(Object expr) {
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

    private static void compileQuote(Object ast, ArrayList<Object> opcodes) {
        if(ast == null) {
            throw new NullPointerException();
            // opcodes.add(ACONST_NULL);
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
            /* if(head.equals(DEFUN) || head.equals(DEFMACRO)) {
                boolean isMacro = head.equals(DEFMACRO);
                // (defun name (args) body)
                return compileFunction(astSequence);
                // throw new RuntimeException();
            } else */ if(head.equals(ASM)) {
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
                    compile(opExprs, bindList.second(), locals, false);
                    opExprs.add(ArraySequence.makeList(Assembler.ASTORE, localIndex));
                    bindList = bindList.rest().rest();
                }
                Object letBody = body.second();
                compile(opExprs, letBody, locals, isTail);
                // pop locals after body completes
                locals.subList(localCount, locals.size()).clear();
            } else {
                if(head instanceof Symbol && Environment.getIfExists((Symbol) head) instanceof Lambda) {
                    for(Object arg : body) {
                        compile(opExprs, arg, locals, false);
                    }
                    Lambda funcall = (Lambda) Environment.get((Symbol) head);
                    if(funcall.isVarargs) {
                        // convert extra args to a single list
                        opExprs.add(EMPTY_LIST);
                        for (int i = funcall.paramCount; i < body.length() + 1; i++) {
                            opExprs.add(CONS);
                        }
                    }
                    opExprs.add(funcall.getInvoker());
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
                Method method = Assembler.compileMethod(dm, isMacro, isVarargs, opcodes, name.toString(), paramCount);
                return Environment.insertMethod(name, method);
            }
        }
        ArrayList<Object> opcodes = compile(new ArrayList<>(), ast, new ArrayList<>(), true);
        Method method = Assembler.compileMethod(dm, opcodes);
        try {
            return method.invoke(null, (Object[]) null);
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
        DynamicsManager dynamicsManager = new DynamicsManager();
        Object form;
        while((form = lispReader.readForm()) != null) {
            System.out.println(eval(dynamicsManager, form));
        }
    }
}
