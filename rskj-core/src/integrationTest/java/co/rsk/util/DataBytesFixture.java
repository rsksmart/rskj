package co.rsk.util;

import java.util.Random;

public class DataBytesFixture {
    public static byte[] generateBytesFromRandom(Random random, int size) {
        byte[] byteArray = new byte[size];
        random.nextBytes(byteArray);
        return byteArray;
    }
}
