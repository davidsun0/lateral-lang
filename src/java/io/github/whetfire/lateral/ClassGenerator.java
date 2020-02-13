package io.github.whetfire.lateral;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ClassGenerator {
    static byte[] header = {(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};
    static byte[] version = {0, 0, 0, 55}; // 55.0 = java 11
    ConstantPool pool;
    ArrayList<byte[]> methods;
    byte[] compiledBytes = null;

    ClassGenerator(){
        pool = new ConstantPool();
        methods = new ArrayList<>();
    }

    void addMethod(byte[] bytes) {
        methods.add(bytes);
    }

    public byte[] toBytes() {
        if(compiledBytes != null)
            return compiledBytes;

        short objIdx = pool.put(new ConstantPool.ClassInfo("java/lang/Object"));
        short clsIdx = pool.put(new ConstantPool.ClassInfo("MyClass"));
        // System.out.println(pool);

        ArrayList<Byte> bytes = new ArrayList<>();
        Utils.appendBytes(bytes, header);
        Utils.appendBytes(bytes, version);
        Utils.appendBytes(bytes, pool.toBytes()); // const pool
        Utils.putShort(bytes, (short)0x0021); // class modifyer flags: non-final public
        Utils.putShort(bytes, clsIdx); // class name index
        Utils.putShort(bytes, objIdx); // parent class name index

        Utils.putShort(bytes, (short)0); // zero interfaces
        Utils.putShort(bytes, (short)0); // zero fields

        // METHODS
        Utils.putShort(bytes, (short)methods.size()); // number of methods
        for(byte[] methodBytes : methods) {
            Utils.appendBytes(bytes, methodBytes);
        }

        Utils.putShort(bytes, (short)0); // zero attributes
        compiledBytes = Utils.toBytes(bytes);
        return compiledBytes;
    }

    public void loadAndPrintMethods() {
        Class clazz = new LClassLoader().defineClass(toBytes());
        for(Method m : clazz.getMethods()) {
            System.out.println(m);
        }
    }

    public void writeToFile(String path) {
        try(FileOutputStream stream = new FileOutputStream("MyClass.class")) {
            stream.write(toBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ClassGenerator generator = new ClassGenerator();
        ///*
        Class clazz = new LClassLoader().defineClass(generator.toBytes());
        for(Method m : clazz.getMethods()) {
            System.out.println(m);
        }
        //*/
    }
}
