package io.github.whetfire.lateral;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class ClassGenerator {
    static byte[] header = {(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};
    static byte[] version = {0, 0, 0, 55}; // 55.0 = java 11

    public void writeEmptyClass(String path) {
        ConstantPool.ConstantEntry objectString = ConstantPool.ConstantEntry.utf8Info("java/lang/Object");
        ConstantPool.ConstantEntry objectClass = ConstantPool.ConstantEntry.classInfo((short)1);
        ConstantPool.ConstantEntry nameString = ConstantPool.ConstantEntry.utf8Info("MyClass");
        ConstantPool.ConstantEntry nameClass = ConstantPool.ConstantEntry.classInfo((short)3);

        try(FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(header);
            stream.write(version);
            // const pool here
            stream.write(new byte[]{0x00, 0x05}); // four constant entries
            stream.write(objectString.id);
            stream.write(objectString.value);
            stream.write(objectClass.id);
            stream.write(objectClass.value);

            stream.write(nameString.id);
            stream.write(nameString.value);
            stream.write(nameClass.id);
            stream.write(nameClass.value);
            stream.write(new byte[]{0x00, 0x21}); // non-final public
            // class name index
            stream.write(new byte[]{0x00, 0x04});
            // parent class name index
            stream.write(new byte[]{0x00, 0x02});

            stream.write(new byte[]{0x00, 0x00}); // zero interfaces
            stream.write(new byte[]{0x00, 0x00}); // zero fields
            stream.write(new byte[]{0x00, 0x00}); // zero methods
            stream.write(new byte[]{0x00, 0x00}); // zero attributes
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ClassGenerator generator = new ClassGenerator();
        generator.writeEmptyClass("MyClass.class");
    }
}
