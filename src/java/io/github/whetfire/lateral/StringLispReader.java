package io.github.whetfire.lateral;

public class StringLispReader extends LispReader {
    private int index;
    private String input;

    StringLispReader(String input) {
        this.input = input;
        index = 0;
        row = 0;
        column = 0;
    }

    protected void nextChar() {
        index ++;
    }

    protected char peekChar() {
        return input.charAt(index);
    }

    protected boolean hasNextChar() {
        return input.length() > index;
    }
}
