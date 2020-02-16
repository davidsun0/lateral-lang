package io.github.whetfire.lateral;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Lateral {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Compiler compiler = new Compiler();
        while(true) {
            System.out.print("> ");
            String text;
            try {
                text = scanner.nextLine();
            } catch (NoSuchElementException e) {
                System.out.println();
                System.out.println("Bye bye! ('v' )/");
                return;
            }
            Object ast = new StringLispReader(text).readForm();
            if(ast instanceof LinkedList) {
                try {
                    // compile and invoke repl input
                    MethodBuilder methodBuilder = compiler.compile((LinkedList) ast, new MethodBuilder());
                    ClassBuilder classBuilder = new ClassBuilder();
                    classBuilder.addMethod(methodBuilder.resolveBytes(classBuilder));
                    Class<?> clazz = new LClassLoader().defineClass(classBuilder.toBytes());
                    Method method = clazz.getMethod("myFunction", (Class<?>[]) null);
                    System.out.println("=> " + method.invoke(null));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println(ast);
            }
        }
    }
}