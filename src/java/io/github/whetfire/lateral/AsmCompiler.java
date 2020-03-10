package io.github.whetfire.lateral;

import org.objectweb.asm.Type;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;

import static io.github.whetfire.lateral.AsmMethodBuilder.*;
import static io.github.whetfire.lateral.Compiler.CONS;
import static io.github.whetfire.lateral.Compiler.EMPTY_LIST;
import static io.github.whetfire.lateral.Compiler.PARSE_INT;

public class AsmCompiler {

    static Symbol DEFMACRO = Symbol.makeSymbol("defmacro");
    static Symbol DEFUN = Symbol.makeSymbol("defun");
    static Symbol ASM = Symbol.makeSymbol("asm");
    static Symbol DEASM = Symbol.makeSymbol("de-asm");
    static Symbol QUOTE = Symbol.makeSymbol("quote");

    static Keyword REST = Keyword.makeKeyword("rest");

    static String getInternalDescriptor(Class<?> ... classes) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for(int i = 0; i < classes.length - 1; i ++) {
            sb.append(Type.getDescriptor(classes[i]));
        }
        sb.append(')');
        sb.append(Type.getDescriptor(classes[classes.length - 1]));
        return sb.toString();
    }

    static Lambda isMacroCall(Sequence expr) {
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

    static class CompilationFrame {
        Sequence ast; // holds the whole compilation expression
        Sequence current; // first value in current is the rest item to compile

        CompilationFrame(Sequence ast) {
            this.ast = ast;
            this.current = ast;
        }
    }

    public static ArrayList<Object> compileQuote(Object ast, ArrayList<Object> opcodes) {
        ArrayDeque<CompilationFrame> stack = new ArrayDeque<>();
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
                    opcodes.add(EMPTY_LIST);
                    for (int i = 0; i < frame.ast.length(); i++) {
                        opcodes.add(CONS);
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
                    opcodes.add(MethodBuilder.ACONST_NULL);
                } else if (value.equals(EmptySequence.EMPTY_SEQUENCE)) {
                    // quoted empty list '()
                    opcodes.add(EMPTY_LIST);
                } else if (value instanceof Symbol) {
                    opcodes.add(ArraySequence.makeList(LDC, value.toString()));
                    opcodes.add(Compiler.MAKE_SYM);
                } else if (value instanceof Keyword) {
                    opcodes.add(ArraySequence.makeList(LDC, ((Keyword) value).getValue()));
                    opcodes.add(Compiler.MAKE_KEY);
                } else if (value instanceof Integer) {
                    opcodes.add(ArraySequence.makeList(ICONST, value));
                    opcodes.add(Compiler.PARSE_INT);
                } else if (value instanceof String) {
                    opcodes.add(ArraySequence.makeList(LDC, value));
                } else {
                    throw new RuntimeException("Can't quote " + value);
                }
                // update frame
                frame.current = expr.rest();
                stack.push(frame);
            }
        }
        return opcodes;
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
                opExprs.addAll(compileQuote(astSequence.second(), new ArrayList<>()));
            } else {
                if(head instanceof Symbol && Environment.getIfExists((Symbol) head) instanceof Lambda) {
                    for(Object arg : body) {
                        compile(opExprs, arg, locals, false);
                    }
                    Lambda funcall = (Lambda) Environment.get((Symbol) head);
                    if(funcall.isVarargs) {
                        // convert extra args to a single list
                        opExprs.add(EMPTY_LIST);
                        for (int i = funcall.argCount; i < body.length() + 1; i++) {
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
                opExprs.add(ArraySequence.makeList(AsmMethodBuilder.ALOAD, index));
            }

            /*
            // TODO: look up in environment
            opExprs.add(ArraySequence.makeList(AsmMethodBuilder.LDC, ast.toString()));
            opExprs.add(Compiler.MAKE_SYM);
            if(isTail)
                opExprs.add(ARETURN);
            throw new RuntimeException(ast.toString());
            */
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
                Method method = AsmMethodBuilder.compileMethod(dm, isMacro, isVarargs, opcodes, name.toString(), paramCount);
                return Environment.insertMethod(name, method);
            }
        }
        ArrayList<Object> opcodes = compile(new ArrayList<>(), ast, new ArrayList<>(), true);
        Method method = AsmMethodBuilder.compileMethod(dm, opcodes);
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
        LispReader lispReader = LispReader.fileReader("./src/lisp/special.lisp");
        DynamicsManager dynamicsManager = new DynamicsManager();
        Object form;
        while((form = lispReader.readForm()) != null) {
            System.out.println(eval(dynamicsManager, form));
        }
    }
}
