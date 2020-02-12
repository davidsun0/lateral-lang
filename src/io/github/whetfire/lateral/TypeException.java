package io.github.whetfire.lateral;

public class TypeException extends RuntimeException {
    TypeException(Class clazz) {
        super("Expected object of type " + clazz.getName());
    }

    TypeException(Class expected, Class got) {
        super("Expected object of type " + expected.getName() + ", but got type " + got.getName());
    }
}
