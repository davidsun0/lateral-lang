package io.github.whetfire.lateral;

public abstract class LispReader {
    private String token;
    int row, column;

    abstract protected boolean hasNextChar();
    abstract protected char peekChar();
    abstract protected void nextChar();

    private String consumeString() {
        StringBuilder sb = new StringBuilder();
        sb.append('\"');
        nextChar(); // consume initial open quote
        while(hasNextChar()) {
            char c = peekChar();
            if(c == '\"') {
                sb.append('\"');
                break;
            }
            sb.append(c);
            nextChar();
        }
        return sb.toString();
    }

    private void consumeComment() {
        while(hasNextChar()) {
            char c = peekChar();
            if(c == '\n') {
                break;
            }
            nextChar();
        }
    }

    private void consumeWhitespace() {
        while(hasNextChar()) {
            char c = peekChar();
            if(c != ' ' && c != '\n') {
                break;
            }
            nextChar();
        }
    }

    private String peekToken() {
        if(token != null)
            return token;

        if(!hasNextChar()) {
            // uhh do something here
        }

        consumeWhitespace();

        StringBuilder sb = new StringBuilder();
        boolean start = true;
        readToken:
        while(hasNextChar()) {
            char c = peekChar();
            column ++;

            switch(c){
                case '(':
                case ')':
                    if(start) {
                        nextChar();
                        sb.append(c);
                    }
                    break readToken;
                // whitespace
                case '\n':
                    row ++;
                    column = 0;
                case ' ':
                    if(start)
                        break;
                    else
                        break readToken;
                case ';':
                    consumeComment();
                    break;
                case '\"':
                    if(start) {
                        sb.append(consumeString());
                        nextChar();
                    }
                    break readToken;
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
            throw new RuntimeException("Can't read empty atom");
        } else if('0' <= atomStr.charAt(0) && atomStr.charAt(0) <= '9') {
            return Integer.parseInt(atomStr);
        } else if(':' == atomStr.charAt(0)) {
            return Keyword.makeKeyword(atomStr.substring(1));
        } else if('\"' == atomStr.charAt(0)) {
            return atomStr.substring(1, atomStr.length() - 1);
        } else {
            return Symbol.makeSymbol(atomStr);
        }
    }

    private Object readList() {
        LinkedList list = null;
        nextToken(); // consume the open parenthesis
        while(hasNextToken()) {
            String peekToken = peekToken();
            if(")".equals(peekToken)) {
                nextToken();
                return LinkedList.reverseD(list);
            }
            list = LinkedList.prepend(readForm(), list);
        }
        throw new RuntimeException("Failed to read list");
    }

    public Object readForm() {
        if(hasNextToken()) {
            String token = peekToken();
            if ("(".equals(token)) {
                return readList();
            } else {
                return readAtom();
            }
        } else {
            return null;
        }
    }
}
