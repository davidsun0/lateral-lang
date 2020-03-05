package io.github.whetfire.lateral;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class LateralReader {
    private Reader stream;
    private String token;
    /*
    n.b. queue operations are add(), remove(), and peek()
     */
    private Deque<Character> deque = new ArrayDeque<>();

    public LateralReader(Reader reader) {
        this.stream = reader;
    }

    public static LateralReader fileReader(String path) throws IOException {
        return new LateralReader(new BufferedReader(new FileReader(path)));
    }

    private boolean hasNextChar() throws IOException {
        if(deque.isEmpty()) {
            int next = stream.read();
            if(next == -1)
                return false;
            else {
                deque.addLast((char)next);
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

    private void unread(char c) {
        deque.addFirst(c);
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

    String peekToken() throws IOException {
        if(token != null)
            return token;
        if(!hasNextChar())
            return null;

        StringBuilder sb = new StringBuilder();
        while(hasNextChar()) {
            char c = peekChar();

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

    LinkedList readList() throws IOException {
        ArrayList<Object> forms = new ArrayList<>();
        Object form;
        while((form = readForm()) != null) {
            if(form.equals(')')) {
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
            LinkedList val = new LinkedList(readForm(), null);
            return new LinkedList(Symbol.makeSymbol("quote"), val);
        } else if(c == '`') {
            LinkedList val = new LinkedList(readForm(), null);
            return new LinkedList(Symbol.makeSymbol("quasiquote"), val);
        } else if(c == ',') {
            LinkedList val = new LinkedList(readForm(), null);
            return new LinkedList(Symbol.makeSymbol("unquote"), val);
        }
        // reader macros here

        unread(c);
        if(hasNextChar()) {
            c = nextChar();
            if(c == '(') {
                return readList();
            } else if(c == ')') {
                return ')';
            }

            StringBuilder sb = new StringBuilder();
            unread(c);
            read:
            while(hasNextChar()) {
                c = peekChar();
                switch (c) {
                    case '(':
                    case ')':
                    case ' ':
                    case '\n':
                    case '\t':
                        break read;
                    default:
                        sb.append(c);
                        nextChar();
                        break;
                }
            }
            //return Symbol.makeSymbol(sb.toString());
            //
            //
            // return sb.toString();
            return readAtom(sb.toString());
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        LateralReader reader = fileReader("./src/lisp/test.lisp");
        Object form;
        while((form = reader.readForm()) != null) {
            System.out.println(form);
        }
    }
}
