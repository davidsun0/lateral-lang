package io.github.whetfire.lateral;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class FileLispReader extends LispReader {
    private BufferedReader fileStream;
    private int buffer;

    public FileLispReader(String path) throws FileNotFoundException{
        this.fileStream = new BufferedReader(new FileReader(path));
        nextChar();
    }

    protected boolean hasNextChar() {
        return buffer >= 0;
    }

    protected char peekChar() {
        return (char)buffer;
    }

    protected void nextChar() {
        try {
            // readForm returns -1 at end of stream
            buffer = fileStream.read();
        } catch (IOException e) {
            e.printStackTrace();
            buffer = -1;
        }
    }

    public static void main(String[] args) {
        try {
            FileLispReader reader = new FileLispReader("./src/lisp/test.lisp");
            System.out.println(reader.readForm());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
