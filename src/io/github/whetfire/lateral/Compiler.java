package io.github.whetfire.lateral;

import java.util.ArrayDeque;
import java.util.Deque;

public class Compiler {

    /**
     * Converts a Lisp S-expression into a list of JVM stack operations
     * @param ast Lisp s-expression
     * @return A simple tree representing stack operations: use semiDeflate to convert to a simple list
     */
    public LinkedList compile(Object ast) {
        if (ast instanceof LinkedList) {
            LinkedList sexpr = (LinkedList)ast;
            LinkedList res = null;
            Object fun = sexpr.getValue();
            sexpr = sexpr.getNext();
            LinkedList call = LinkedList.makeList("funcall", fun, LinkedList.length(sexpr));
            while(sexpr != null) {
                res = LinkedList.prepend(compile(sexpr.getValue()), res);
                sexpr = LinkedList.next(sexpr);
            }
            res = LinkedList.prepend(call, res);
            return LinkedList.reverseD(res);
        } else {
            // switch on type
            return LinkedList.makeList("push", ast);
        }
    }

    public LinkedList compile2(LinkedList ast) {
        LinkedList output = null;
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
                LinkedList ret = LinkedList.makeList("funcall", prev.getValue(), LinkedList.length(prev) - 1);
                output = LinkedList.prepend(ret, output);
                continue;
            } else if (val.getValue() instanceof LinkedList) {
                stack.push(LinkedList.makeList(val.getNext(), pair.getNext().getValue()));
                Object head = ((LinkedList) val.getValue()).getValue();
                System.out.println(":: " + head);
                // switch on head here for special forms
                stack.push(LinkedList.makeList(((LinkedList) val.getValue()).getNext(), val.getValue()));
                continue;
            } else {
                LinkedList ret = LinkedList.makeList("push", val.getValue());
                output = LinkedList.prepend(ret, output);
            }
            pair.setValue(val.getNext());
            stack.push(pair);
        }
        return output;
    }

    /*
    public static Object postTreeMacro(Object tree) {
        Object output = initialValue;
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(LinkedList.prepend(tree, null));
        while(!stack.isEmpty()) {
            LinkedList top = stack.pop();
        }
        return output;
    }
    */

    public static void listPrint(LinkedList list) {
        while(list != null) {
            System.out.println(list.getValue());
            list = list.getNext();
        }
    }

    /**
     * Unpacks a tree into a list of lists (only 2 levels deep)
     * @param list tree to deflate
     * @return a copy of list that is at most only 2 levels deep
     */
    public static LinkedList semiDeflate(LinkedList list) {
        LinkedList output = null;
        // saves a list of pointers to parents of the working node
        Deque<LinkedList> stack = new ArrayDeque<>();
        stack.push(list);
        while(!stack.isEmpty()) {
            LinkedList top = stack.pop();
            Object val = top.getValue();
            if(val instanceof LinkedList && ((LinkedList) val).getValue() instanceof LinkedList) {
                // top is a list of lists; recurse.
                if(top.getNext() != null)
                    stack.push(top.getNext());
                stack.push((LinkedList) val);
            } else {
                output = LinkedList.prepend(val, output);
                if(top.getNext() != null)
                    stack.push(top.getNext());
            }
        }
        return LinkedList.reverseD(output);
    }

    public static void main(String[] args) {
        Compiler c = new Compiler();
        var target = Reader.read("(+ 1 (* 2 3 4) 5)");
        LinkedList comp = c.compile2((LinkedList)target);
        // comp = semiDeflate(comp);
        listPrint(comp);
    }
}
