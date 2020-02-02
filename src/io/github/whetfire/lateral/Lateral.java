package io.github.whetfire.lateral;

import java.util.Scanner;

public class Lateral {

    public static Object read(String s) {
        return new Reader(s).read();
    }

    public static Object eval(Object ast) {
        return ast;
    }

    public static void print(Object o) {
        if(o == null) {
            System.out.println("nil");
        } else {
            System.out.println(o.toString());
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while(true) {
            System.out.print("> ");
            String text = scanner.nextLine();
            print(eval(read(text)));
        }
    }
}