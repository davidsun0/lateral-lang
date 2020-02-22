package io.github.whetfire.lateral;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Lateral {

    public static void main(String[] args) {
        /*
        Scanner scanner = new Scanner(System.in);
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
            try {
                // compile and invoke repl input
                ClassBuilder classBuilder = new ClassBuilder();
                Compiler compiler = new Compiler(classBuilder.makeMethodBuilder());
                compiler.compileMethod(ast);
                Class<?> clazz = new LClassLoader().defineClass(classBuilder.toBytes());
                Method method = clazz.getMethod("myFunction", (Class<?>[]) null);
                System.out.println("=> " + method.invoke(null));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        */
    }
}