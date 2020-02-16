package io.github.whetfire.lateral;

public abstract class LispReader {
    private String token;
    int row, column;

    private static String OPEN_PAREN = "(";
    private static String CLOSE_PAREN = ")";
    abstract protected boolean hasNextChar();
    abstract protected char peekChar();
    abstract protected void nextChar();

    private String peekToken() {
        if(token != null)
            return token;

        StringBuilder sb = new StringBuilder();
        boolean start = true;
        readToken:
        while(hasNextChar()) {
            char c = peekChar();
            column ++;
            if(start) {
                if(c == '(') {
                    nextChar();
                    token = OPEN_PAREN;
                    return token;
                } else if(c == ')') {
                    nextChar();
                    token = CLOSE_PAREN;
                    return token;
                }
            }
            switch(c){
                case '(':
                case ')':
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

    private String nextToken() {
        String ret = token == null ? peekToken() : token;
        token = null;
        return ret;
    }

    private boolean hasNextToken() {
        return !"".equals(peekToken());
    }

    private Object readAtom() {
        String atomStr = nextToken();
        if(atomStr.length() == 0) {
            // wat
            return null;
        } else if('0' <= atomStr.charAt(0) && atomStr.charAt(0) <= '9') {
            return Integer.parseInt(atomStr);
        } else {
            return Symbol.makeSymbol(atomStr);
        }
    }

    private Object readList() {
        LinkedList list = null;
        nextToken(); // consume the open parenthesis
        while(hasNextToken()) {
            String peekToken = peekToken();
            if(CLOSE_PAREN.equals(peekToken)) {
                nextToken();
                return LinkedList.reverseD(list);
            }
            list = LinkedList.prepend(readForm(), list);
        }
        throw new RuntimeException("Failed to read list");
    }

    public Object readForm() {
        String token = peekToken();
        if(OPEN_PAREN.equals(token)) {
            return readList();
        } else {
            return readAtom();
        }
    }

}
