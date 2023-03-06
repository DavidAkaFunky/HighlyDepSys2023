package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.*;

public class Serializer {

    public static <T extends Serializable> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    public static <T extends Serializable> T deserialize(byte[] b, Class<T> cl) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object o = ois.readObject();
        ois.close();
        return cl.cast(o);
    }
}
