package co.rsk.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by ajlopez on 09/08/2017.
 */
public class WalletFactoryTest {
    @Test
    public void convertBytes() {
        Random random = new Random();
        byte[] value1 = new byte[32];
        byte[] value2 = new byte[32];
        random.nextBytes(value1);
        random.nextBytes(value2);

        byte[] encryptedValues = serializeValues(value1, value2);

        Values values = deserializeValues(encryptedValues);

        Assert.assertArrayEquals(value1, values.value1);
        Assert.assertArrayEquals(value2, values.value2);
    }

    private byte[] serializeValues(byte[] value1, byte[] value2) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            ObjectOutputStream byteStream = new ObjectOutputStream(result);

            ArrayList<byte[]> bytes = new ArrayList<>();
            bytes.add(value1);
            bytes.add(value2);
            byteStream.writeObject(bytes);

            return result.toByteArray();
        } catch (IOException e) {
            //How is this even possible ???
            throw new IllegalStateException(e);
        }
    }

    private Values deserializeValues(byte[] encryptedValues) {
        byte[] valuesBytes = WalletFactory.convertBytes(encryptedValues);
        RLPList rlpList = (RLPList) RLP.decode2(valuesBytes).get(0);
        Values values = new Values();
        values.value1 = rlpList.get(0).getRLPData();
        values.value2 = rlpList.get(1).getRLPData();
        return values;
    }

    private static class Values {
        public byte[] value1;
        public byte[] value2;
    }
}
