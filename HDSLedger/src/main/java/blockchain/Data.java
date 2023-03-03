package blockchain;

import java.io.Serializable;

public class Data implements Serializable {

    private String name;
    private int data;

    public Data(String name, int data) {
        this.name = name;
        this.data = data;
    }

    @Override
    public String toString() {
        return "Data [name=" + name + ", data=" + data + "]";
    }

}