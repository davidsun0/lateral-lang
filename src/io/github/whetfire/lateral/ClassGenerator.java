package io.github.whetfire.lateral;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ClassGenerator {
    static byte[] header = {(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};
    static byte[] version = {0, 0, 0, 55}; // 55.0 = java 11
    ConstantPool pool;

    public static byte[] shortToBytes(short n) {
        byte[] val = new byte[2];
        val[0] = (byte)((n >> 8) & 0xFF);
        val[1] = (byte)(n & 0xFF);
        return val;
    }

    public byte[] makeMethod(ConstantPool pool) {
        ArrayList<Byte> bytes = new ArrayList<>();
        // u2 accessor flags
        // public static
        Utils.putShort(bytes, (short)0x0009);
        // u2 name index (utf8)
        short x = pool.put(new ConstantPool.UTF8Info("myFunction"));
        Utils.putShort(bytes, x);
        // u2 descriptor index (utf8)
        x = pool.put(new ConstantPool.UTF8Info("()V"));
        Utils.putShort(bytes, x);
        // u2 number of attributes
        Utils.putShort(bytes, (short)1);

        /*
        Code attribute
            u2 "Code" index
            u4 attribute size
            u2 max stack
            u2 max locals
            u4 bytecode length
            [bytecode]
            u2 number of exceptions
            [exceptions]
            u2 number of attributes

            StackMapTable attribute
                u2 "StackMapTable" index
                u2 attribute size
                u2 number of entries
                [entries]
        */
        CodeAttribute code = new CodeAttribute();
        Utils.appendBytes(bytes, code.resolveBytes(pool));
        return Utils.arrayListToBytes(bytes);
    }

    public byte[] toBytes() {
        pool = new ConstantPool();
        short objIdx = pool.put(new ConstantPool.ClassInfo("java/lang/Object"));
        short clsIdx = pool.put(new ConstantPool.ClassInfo("MyClass"));
        byte[] myFunction = makeMethod(pool);
        System.out.println(pool);

        ArrayList<Byte> bytes = new ArrayList<>();
        Utils.appendBytes(bytes, header);
        Utils.appendBytes(bytes, version);
        // const pool here
        Utils.appendBytes(bytes, pool.toBytes());
        // class modifyer flags: non-final public
        Utils.appendBytes(bytes, shortToBytes((short)0x0021)); // zero interfaces
        // class name index
        Utils.appendBytes(bytes, shortToBytes(clsIdx));
        // parent class name index
        Utils.appendBytes(bytes, shortToBytes(objIdx));
        Utils.appendBytes(bytes, shortToBytes((short)0)); // zero interfaces
        Utils.appendBytes(bytes, shortToBytes((short)0)); // zero fields
        Utils.appendBytes(bytes, shortToBytes((short)1)); // one method
        Utils.appendBytes(bytes, myFunction);
        Utils.appendBytes(bytes, shortToBytes((short)0)); // zero attributes
        return Utils.arrayListToBytes(bytes);
    }

    public static void main(String[] args) {
        ClassGenerator generator = new ClassGenerator();
        ///*
        Class clazz = new LClassLoader().defineClass(generator.toBytes());
        for(Method m : clazz.getMethods()) {
            System.out.println(m);
        }
        //*/
        /*
        try(FileOutputStream stream = new FileOutputStream("MyClass.class")) {
            stream.write(generator.toBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //*/
    }
}
