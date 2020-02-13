package io.github.whetfire.lateral;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class StreamingReader extends Reader {
    BufferedReader fileStream;
    Character buffer;
    boolean eof;

    StreamingReader (String path) {
        try {
            fileStream = new BufferedReader(new FileReader(path));
            eof = false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public char nextChar() {
        if(buffer != null) {
            char c = buffer;
            buffer = null;
            return c;
        }

        try {
            int x = fileStream.read();
            if(x >= 0)
                return (char)x;
            else {
                eof = true;
                System.err.println("end of file");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public char peekChar() {
        if(buffer == null) {
            buffer = nextChar();
        }
        return buffer;
    }

    @Override
    public boolean hasNextChar() {
        return !eof;
    }

    public static void main(String[] args) {
        StreamingReader reader = new StreamingReader("./src/lisp/test.lisp");
        System.out.println(reader.read());
    }
}
