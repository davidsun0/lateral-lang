package io.github.whetfire.lateral;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

// TODO: add line and column metadata to symbols
public class LispReader {
    private Reader stream;
    private Deque<Character> deque = new ArrayDeque<>();

    public LispReader(Reader reader) {
        this.stream = reader;
    }

    public static LispReader fileReader(String path) throws IOException {
        return new LispReader(new BufferedReader(new FileReader(path)));
    }

    private boolean hasNextChar() throws IOException {
        if (deque.isEmpty()) {
            int next = stream.read();
            if (next == -1)
                return false;
            else {
                deque.addLast((char) next);
                return true;
            }
        } else {
            return true;
        }
    }

    private char nextChar() throws IOException {
        if(!deque.isEmpty()) {
            return deque.removeFirst();
        } else {
            return (char)stream.read();
        }
    }

    private char peekChar() throws IOException {
        if(deque.isEmpty()) {
            int next = stream.read();
            if(next != -1)
                deque.addLast((char) next);
            return (char) next;
        } else {
            return deque.peekFirst();
        }
    }

    private void consumeWhitespace() throws IOException {
        while(hasNextChar()) {
            char c = peekChar();
            if(c != ' ' && c != '\n') {
                break;
            }
            nextChar();
        }
    }

    private void consumeComment() throws IOException {
        while(hasNextChar()) {
            char c = peekChar();
            if(c == '\n')
                break;
            nextChar();
        }
    }

    private String consumeString() throws IOException {
        StringBuilder sb = new StringBuilder();
        while(hasNextChar()) {
            char c = peekChar();
            if(c == '"') {
                nextChar();
                break;
            } else if (c == '\\'){
                // escape sequences
                throw new RuntimeException();
            }
            sb.append(c);
            nextChar();
        }
        return sb.toString();
    }

    Object readAtom(String value) {
        if(value == null || value.length() == 0) {
            throw new RuntimeException();
        }

        if(value.charAt(0) == ':') {
            return Keyword.makeKeyword(value.substring(1));
        } else if('0' <= value.charAt(0) && value.charAt(0) <= '9') {
            // TODO: other numerical literals
            return Integer.parseInt(value);
        } else {
            return Symbol.makeSymbol(value);
        }
    }

    Sequence readList() throws IOException {
        ArrayList<Object> forms = new ArrayList<>();
        Object form;
        while(hasNextChar()) {
            form = readForm();
            if(form != null && form.equals(')')) {
                // end of list
                return LinkedList.makeList(forms.toArray());
            } else {
                forms.add(form);
            }
        }
        throw new RuntimeException("got EOF while reading list");
    }

    Object readForm() throws IOException {
        consumeWhitespace();
        if(!hasNextChar())
            return null;

        char c = nextChar();
        if(c == ';') {
            consumeComment();
            return readForm();
        } else if(c == '"') {
            return consumeString();
        } else if(c == '\'') {
            LinkedList val = new LinkedList(readForm());
            return new LinkedList(Symbol.makeSymbol("quote"), val);
        } else if(c == '`') {
            LinkedList val = new LinkedList(readForm());
            return new LinkedList(Symbol.makeSymbol("quasiquote"), val);
        }
        // reader macros here

        else if(c == '(') {
            return readList();
        } else if(c == ')') {
            return ')';
        }

        StringBuilder sb = new StringBuilder();
        sb.append(c);
        read:
        while(hasNextChar()) {
            c = peekChar();
            switch (c) {
                case '(':
                case ')':
                case ' ':
                case '\n':
                    break read;
                default:
                    sb.append(c);
                    nextChar();
                    break;
            }
        }
        return readAtom(sb.toString());
    }

    public static void main(String[] args) throws IOException {
        LispReader reader = fileReader("./src/lisp/test.lisp");
        Object form;
        while((form = reader.readForm()) != null) {
            System.out.println(form);
        }
    }
}
