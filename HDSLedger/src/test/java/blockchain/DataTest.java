package blockchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataTest {

    @Test
    void testToString() {
        Data d = new Data("compra", 20);
        assertEquals("Data [name=compra, data=20]", d.toString());
    }
}