import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Mock implements Serializable {

    private String name = "";
    private int data = 1;

    public Mock(String name, int data) {
        this.name = name;
        this.data = data;
    }

    public byte[] serialize() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutput oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        oos.flush();
        
        return baos.toByteArray();
    }

    public static Mock deserialize(byte[] data) throws IOException, ClassNotFoundException {

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        
        return (Mock)ois.readObject();
    }

    @Override
    public String toString() {
        return "Mock [name=" + name + ", data=" + data + "]";
    }
    
}