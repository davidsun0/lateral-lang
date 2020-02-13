package io.github.whetfire.lateral;

public class Reader {
    int index;
    String input;

    String token;
    int row, column;

    public Reader() {
        input = "";
    }

    public Reader(String input) {
        this.input = input;
        index = 0;

        row = 0;
        column = 0;
    }

    public char nextChar() {
        char ret = input.charAt(index);
        index ++;
        return ret;
    }

    public char peekChar() {
        return input.charAt(index);
    }

    public boolean hasNextChar() {
        return input.length() > index;
    }

    public String peekToken() {
        if(token != null)
            return token;

        StringBuilder sb = new StringBuilder();
        boolean start = true;
        readToken:
        while(hasNextChar()) {
            char c = peekChar();
            column ++;
            switch(c){
                case '(':
                case ')':
                    if (start) {
                        sb.append(c);
                        nextChar();
                    }
                    break readToken;

                case '\n':
                    row ++;
                    column = 0;
                case ' ':
                    if (!start) {
                        break readToken;
                    }
                    break;

                default:
                    start = false;
                    sb.append(c);
                    break;
            }
            nextChar();
        }
        token = sb.toString();
        return token;
    }

    public String nextToken() {
        String ret = token == null ? peekToken() : token;
        token = null;
        return ret;
    }

    public boolean hasNextToken() {
        return !"".equals(peekToken());
    }

    public Object readAtom() {
        String atomStr = nextToken();
        if(atomStr.length() == 0) {
            // wat
            return null;
        } else if('0' <= atomStr.charAt(0) && atomStr.charAt(0) <= '9') {
            return Integer.parseInt(atomStr);
        } else {
            return atomStr;
        }
    }

    public Object readList() {
        LinkedList list = null;
        nextToken(); // consume the open parenthesis
        while(hasNextToken()) {
            String peekToken = peekToken();
            if(")".equals(peekToken)) {
                nextToken();
                return LinkedList.reverseD(list);
            }
            list = LinkedList.prepend(read(), list);
        }
        System.err.println("failed to read list");
        return null;
    }

    public Object read() {
        String token = peekToken();
        if("(".equals(token)) {
            return readList();
        } else {
            return readAtom();
        }
    }

    public static Object read(String input) {
        Reader reader = new Reader(input);
        return reader.read();
    }
}
