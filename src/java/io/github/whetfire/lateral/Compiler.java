package io.github.whetfire.lateral;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

public class Compiler {

    public MethodGenerator compile(LinkedList ast, MethodGenerator methodGenerator) {
        // saves a list of pointers to parents of the working node
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.makeList(new LinkedList(ast, null), null));
        while(!stack.isEmpty()) {
            LinkedList pair = stack.pop();
            LinkedList val = (LinkedList) pair.getValue();
            if(val == null) {
                LinkedList prev = (LinkedList) pair.getNext().getValue();
                if (prev == null){
                    break;
                }
                String funcall = prev.getValue().toString();
                MethodGenerator.OpCode opCode;
                if ("+".equals(funcall))
                    opCode = MethodGenerator.CODE_IADD;
                else if("*".equals(funcall))
                    opCode = MethodGenerator.CODE_IMUL;
                else
                    // opCode = new MethodGenerator.InvokeStatic("io.github.whetfire.lateral", funcall, "???");
                    opCode = null;
                methodGenerator.insertOpCode(opCode);
            } else if (val.getValue() instanceof LinkedList) {
                stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                // switch on head here for special forms:
                // quote, and, or, if/cond, let, lambda, def, defmacro
                // Object head = ((LinkedList) val.getValue()).getValue();
                stack.push(LinkedList.makeList(((LinkedList) val.getValue()).getNext(), val.getValue()));
            } else {
                Object element = val.getValue();
                if (element instanceof Integer) {
                    methodGenerator.insertOpCode(new MethodGenerator.IntConstOp((int) element));
                } else {
                    throw new RuntimeException("Can't convert to bytecode: " + element);
                }
                pair.setValue(val.getNext());
                stack.push(pair);
            }
        }
        methodGenerator.insertOpCode(MethodGenerator.CODE_IRETURN);
        return methodGenerator;
    }

    public static void main(String[] args) {
        Compiler c = new Compiler();
        var target = Reader.read("(+ 1 (* 2 3))");
        ClassGenerator cgen = new ClassGenerator();
        MethodGenerator mgen = c.compile((LinkedList)target, new MethodGenerator());
        // mgen.printCode();
        cgen.addMethod(mgen.resolveBytes(cgen.pool));
        try {
            Class<?> clazz = new LClassLoader().defineClass(cgen.toBytes());
            Method m = clazz.getMethod("myFunction", (Class<?>[])null);
            System.out.println("result: " + m.invoke(null));
        } catch (Exception e){
            e.printStackTrace();
        }
        // cgen.writeToFile("MyClass.class");
        // cgen.loadAndPrintMethods();
        // comp = semiDeflate(comp);
        // listPrint(comp);
    }
}
