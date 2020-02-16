package io.github.whetfire.lateral;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ClassBuilder {
    static byte[] header = {(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};
    static byte[] version = {0, 0, 0, 55}; // 55.0 = java 11

    private ConstantPool pool;
    private ArrayList<byte[]> methods;
    private byte[] compiledBytes = null;

    ClassBuilder(){
        pool = new ConstantPool();
        methods = new ArrayList<>();
    }

    public ConstantPool getPool() {
        return pool;
    }

    void addMethod(byte[] bytes) {
        methods.add(bytes);
    }

    void addMethod(MethodBuilder methodBuilder) {
        if(methodBuilder.parentClass != this)
            throw new RuntimeException("Attempting to put method in wrong class");
        methods.add(methodBuilder.resolveBytes());
    }

    public byte[] toBytes() {
        if(compiledBytes != null)
            return compiledBytes;

        // TODO: generate class names
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

        // TODO: generate interfaces and fields
        Utils.putShort(bytes, (short)0); // zero interfaces
        Utils.putShort(bytes, (short)0); // zero fields

        // METHODS
        Utils.putShort(bytes, (short)methods.size()); // number of methods
        for(byte[] methodBytes : methods) {
            Utils.appendBytes(bytes, methodBytes);
        }

        // TODO: generate attributes
        Utils.putShort(bytes, (short)0); // zero attributes
        compiledBytes = Utils.toByteArray(bytes);
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
}
